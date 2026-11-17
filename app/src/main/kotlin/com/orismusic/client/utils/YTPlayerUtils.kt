/*
 * Oris Music stream resolution.
 *
 * Oris direct-client implementation informed by the anonymous playback
 * approach in Mochy Music / oris-music-foundation. No source blocks copied.
 * Existing API and extractor dependencies remain GPL-3.0; see BORROWED.md.
 * Resolves a YouTube Music
 * video id to a playable audio stream through the app's InnerTubeXResolver
 * (InnerTubeX library, GPL-3.0, Metrolist Group) and exposes the request
 * headers each resolved stream requires.
 */
package com.orismusic.client.utils

import com.orismusic.client.constants.AudioQuality
import java.io.IOException
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import com.metrolist.innertubex.extraction.StreamResolveException
import kotlinx.coroutines.withContext
import timber.log.Timber

object YTPlayerUtils {
    private val visitorLock = Mutex()
    private val directHeaders = ConcurrentHashMap<String, Map<String, String>>()

    // Informed by foundation's anonymous visitor bootstrap and VISIONOS-first
    // strategy. A refusal is not treated as proof that an account is required.
    private suspend fun directAnonymous(videoId: String, maxKbps: Int): PlaybackData? {
        visitorLock.withLock {
            if (YouTube.visitorData.isNullOrBlank()) {
                YouTube.refreshVisitorData().onFailure {
                    Timber.tag(TAG).w("Anonymous visitor bootstrap failed: ${it::class.simpleName}")
                }
            }
        }
        val response = YouTube.player(videoId, null, VISIONOS, null, null).getOrElse {
            if (it is CancellationException) throw it
            Timber.tag(TAG).w("Direct client request failed: ${it::class.simpleName}")
            return null
        }
        if (response.playabilityStatus.status != "OK") {
            val status = response.playabilityStatus.status
            val reason = response.playabilityStatus.reason.orEmpty()
                .replace(Regex("[\\r\\n]+"), " ").take(240)
            PlaybackDiagnostic.report("VISIONOS: $status. $reason")
            Timber.tag(TAG).w("Direct client refusal: status=$status reason=$reason")
            if (status == "LOGIN_REQUIRED" && reason.contains("bot", ignoreCase = true)) {
                throw GuestAccessRefused("YouTube asked this connection to confirm it is not a bot. Automatic retries stopped. This does not mean all songs require sign-in.")
            }
            Timber.tag(TAG).w("Direct VISIONOS status=${response.playabilityStatus.status}")
            return null
        }
        val formats = response.streamingData?.adaptiveFormats.orEmpty().filter {
            it.isAudio && it.isOriginal && !it.url.isNullOrBlank() &&
                it.signatureCipher.isNullOrBlank() && it.cipher.isNullOrBlank() &&
                android.net.Uri.parse(it.url).getQueryParameter("n") == null
        }
        val format = formats.filter { it.bitrate.toLong() <= maxKbps.toLong() * 1000 }.maxByOrNull { it.bitrate }
            ?: formats.minByOrNull { it.bitrate } ?: return null
        val url = format.url ?: return null
        val uri = android.net.Uri.parse(url)
        if (uri.scheme != "https" || uri.host?.endsWith(".googlevideo.com") != true) return null
        if (directHeaders.size > 64) directHeaders.clear()
        directHeaders[url] = mapOf(
            "User-Agent" to VISIONOS.userAgent,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "https://www.youtube.com/",
            "Origin" to "https://www.youtube.com",
        )
        Timber.tag(TAG).d("Resolved $videoId via VISIONOS-direct (${format.bitrate / 1000}kbps)")
        return PlaybackData(url, videoId, mimeType = format.mimeType.substringBefore(';'), bitrate = format.bitrate)
    }
    private const val TAG = "YTPlayerUtils"

    @Volatile var forceOpusEnabled: Boolean = false

    @Volatile
    var playbackEngine: PlaybackEngine = PlaybackEngine.AUTO

    data class PlaybackData(
        val streamUrl: String,
        val cacheKey: String?,
        val isHls: Boolean = false,
        val isDash: Boolean = false,
        val widevineLicense: String? = null,
        val decryptionKey: String? = null,
        val mimeType: String = "audio/mp4",
        val bitrate: Int = 0,
    )

    fun initialize() {
        // Engine warmup is owned by OrisApp through InnerTubeXResolver.init().
    }

    /**
     * Resolve [videoId] to a playable audio stream.
     *
     * Throws [IOException] when no stream can be resolved so the loading
     * DataSource fails the request cleanly instead of playing silence.
     */
    suspend fun getPlaybackData(
        videoId: String,
        audioQuality: AudioQuality,
        context: android.content.Context
    ): PlaybackData = withContext(IO) {
        val maxKbps = when (audioQuality) {
            AudioQuality.LOW -> 64
            AudioQuality.AUTO, AudioQuality.MEDIUM -> 128
            AudioQuality.HIGH -> 256
            else -> Int.MAX_VALUE
        }
        PlaybackDiagnostic.report("Resolving selected song anonymously...")
        Timber.tag(TAG).d("Resolving stream for $videoId (maxKbps=$maxKbps)")
        try {
            directAnonymous(videoId, maxKbps)?.let {
                PlaybackDiagnostic.report("Stream resolved through VISIONOS-direct. Listen and check that the playback time advances.")
                return@withContext it
            }
        } catch (refused: GuestAccessRefused) {
            PlaybackDiagnostic.report(refused.message.orEmpty())
            throw refused
        }
        val extracted = try {
            InnerTubeXResolver.extract(videoId = videoId, maxKbps = maxKbps)
                ?: throw IOException("No playable stream found for $videoId")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: StreamResolveException) {
            PlaybackDiagnostic.report(PlaybackDiagnostic.message.value + "\nFallback: ${failure.reason}. No playable stream was returned.")
            Timber.tag(TAG).w("Extraction failed for $videoId: ${failure.reason}")
            failure.diagnostics?.attempts?.forEach { attempt ->
                Timber.tag(TAG).w(
                    "Client ${attempt.clientName}/${attempt.profileId}: ${attempt.outcome}",
                )
            }
            // ExoPlayer's loading contract expects IOException for a normal
            // network/extraction failure, not an unexpected runtime exception.
            throw IOException("Cannot resolve $videoId: ${failure.reason}", failure)
        }
        Timber.tag(TAG).d("Resolved $videoId via ${extracted.clientName} (${extracted.kbps}kbps ${extracted.mimeType})")
        PlaybackDiagnostic.report("Stream resolved through ${extracted.clientName}. Listen and check that playback time advances.")
        PlaybackData(
            streamUrl = extracted.url,
            cacheKey = videoId,
            mimeType = extracted.mimeType.substringBefore(';').ifBlank { "audio/mp4" },
            bitrate = extracted.bitrate ?: extracted.kbps * 1000,
        )
    }

    /** Headers a media fetch for [url] must carry, when the engine minted it. */
    fun headersFor(url: String): Map<String, String> =
        directHeaders[url] ?: InnerTubeXResolver.headersFor(url) ?: emptyMap()

    enum class PlaybackEngine {
        POTOKEN,
        BRAVEPIPE,
        AUTO,
    }
}

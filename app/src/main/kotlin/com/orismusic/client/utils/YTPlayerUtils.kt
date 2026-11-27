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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import timber.log.Timber

object YTPlayerUtils {
    private val visitorLock = Mutex()
    private val directHeaders = ConcurrentHashMap<String, Map<String, String>>()

    // Informed by foundation's anonymous visitor bootstrap and VISIONOS-first
    // strategy. A refusal is not treated as proof that an account is required.
    private suspend fun directAnonymous(videoId: String, maxKbps: Int, quiet: Boolean): PlaybackData? {
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
            if (!quiet) PlaybackDiagnostic.reportFailure("YouTube refused the guest request ($status). $reason", videoId)
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

    /** Fresh resolutions, kept briefly so retries and the next song cost no network. */
    internal val resolved = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, PlaybackData>>()
    private const val RESOLVED_TTL_MS = 10 * 60 * 1000L

    /** Hard bound on one resolve attempt, so failure feedback reaches the user in seconds
     *  even when the OS still shows a network but packets go nowhere. */
    // Var (not const) so unit tests can shrink it; production leaves the default.
    internal var resolveTimeoutMs = 30_000L

    /** Forget a resolved stream, for example after it failed to play. */
    fun evict(videoId: String) {
        resolved.keys.removeIf { it.startsWith("$videoId|") }
    }

    /** Warm the resolver for an upcoming song; failures are ignored. */
    suspend fun prefetch(videoId: String, audioQuality: AudioQuality, context: android.content.Context) {
        runCatching { getPlaybackData(videoId, audioQuality, context, quiet = true) }
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
        context: android.content.Context,
        quiet: Boolean = false,
    ): PlaybackData = withContext(IO) {
        val maxKbps = when (audioQuality) {
            AudioQuality.LOW -> 64
            AudioQuality.AUTO, AudioQuality.MEDIUM -> 128
            AudioQuality.HIGH -> 256
            else -> Int.MAX_VALUE
        }
        val key = "$videoId|$maxKbps"
        resolved[key]?.let { (at, data) ->
            if (System.currentTimeMillis() - at < RESOLVED_TTL_MS) {
                Timber.tag(TAG).d("Reusing fresh stream for $videoId")
                return@withContext data
            }
        }
        val data = resolveFresh(videoId, maxKbps, context, quiet)
        resolved[key] = System.currentTimeMillis() to data
        data
    }

    // Test seams. Production never overrides these; unit tests use them to drive
    // failure paths deterministically without a network.
    internal var networkOnline: (android.content.Context) -> Boolean = { ctx ->
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val active = cm.activeNetwork
        active != null &&
            cm.getNetworkCapabilities(active)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    internal val defaultResolveStage: suspend (String, Int, Boolean, Int) -> PlaybackData =
        { v, k, q, a -> resolveInner(v, k, q, a) }
    internal var resolveStage: suspend (String, Int, Boolean, Int) -> PlaybackData = defaultResolveStage
    internal val defaultDirectStage: suspend (String, Int, Boolean) -> PlaybackData? =
        { v, k, q -> directAnonymous(v, k, q) }
    internal var directStage: suspend (String, Int, Boolean) -> PlaybackData? = defaultDirectStage
    internal val defaultExtractStage: suspend (String, Int) -> InnerTubeXResolver.Extracted? =
        { v, k -> InnerTubeXResolver.extract(videoId = v, maxKbps = k) }
    internal var extractStage: suspend (String, Int) -> InnerTubeXResolver.Extracted? = defaultExtractStage

    private suspend fun resolveFresh(
        videoId: String,
        maxKbps: Int,
        context: android.content.Context,
        quiet: Boolean,
    ): PlaybackData = withContext(IO) {
        if (!quiet) PlaybackDiagnostic.report("Resolving selected song anonymously...")
        val attempt = PlaybackDiagnostic.beginResolveAttempt(videoId)
        Timber.tag(TAG).d("Resolving stream for $videoId (maxKbps=$maxKbps, attempt=r$attempt)")
        // Fail fast when the OS reports no network at all. OS state can only prove
        // absence, never reachability, so this only short-circuits doomed attempts.
        if (!networkOnline(context)) {
            PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "failed: no network (OS)")
            if (!quiet) PlaybackDiagnostic.reportFailure("No playable stream came back. NETWORK", videoId)
            throw IOException("Cannot resolve $videoId: no network")
        }
        try {
            return@withContext withTimeout(resolveTimeoutMs) {
                resolveStage(videoId, maxKbps, quiet, attempt)
            }
        } catch (timeout: TimeoutCancellationException) {
            PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "failed: timed out after ${resolveTimeoutMs / 1000}s")
            if (!quiet) PlaybackDiagnostic.reportFailure("No playable stream came back. TIMED OUT", videoId)
            throw IOException("Cannot resolve $videoId: timed out", timeout)
        }
    }

    private suspend fun resolveInner(
        videoId: String,
        maxKbps: Int,
        quiet: Boolean,
        attempt: Int,
    ): PlaybackData {
        // A refusal of the direct stage rules out that stage, not the connection: the
        // catalog is the supported fallback for a challenged one (innertubex classifies
        // LOGIN_REQUIRED as a per-client token failure, not a stop signal).
        var guestRefusal: GuestAccessRefused? = null
        try {
            directStage(videoId, maxKbps, quiet)?.let {
                PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "ok VISIONOS-direct")
                if (!quiet) PlaybackDiagnostic.report("Stream resolved through VISIONOS-direct. Listen and check that the playback time advances.")
                return it
            }
        } catch (refused: GuestAccessRefused) {
            guestRefusal = refused
            if (!quiet) PlaybackDiagnostic.report("Direct guest playback refused; trying the catalog.")
        }
        val extracted = try {
            extractStage(videoId, maxKbps)
                ?: run {
                    PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "failed: no playable stream")
                    throw IOException("No playable stream found for $videoId")
                }
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) {
                PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "cancelled")
            }
            throw cancelled
        } catch (failure: StreamResolveException) {
            PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "failed: ${failure.reason}")
            if (!quiet) PlaybackDiagnostic.reportFailure(guestRefusal?.message ?: "No playable stream came back. ${failure.reason}", videoId)
            Timber.tag(TAG).w("Extraction failed for $videoId: ${failure.reason}")
            failure.diagnostics?.attempts?.forEach { attempt ->
                Timber.tag(TAG).w(
                    "Client ${attempt.clientName}/${attempt.profileId}: ${attempt.outcome}",
                )
            }
            // ExoPlayer's loading contract expects IOException for a normal
            // network/extraction failure, not an unexpected runtime exception.
            throw IOException("Cannot resolve $videoId: ${failure.reason}", failure).also { io ->
                // Keep the refusal on the final error so retry suppression still engages.
                guestRefusal?.let(io::addSuppressed)
            }
        } catch (failure: IOException) {
            guestRefusal?.let(failure::addSuppressed)
            throw failure
        }
        PlaybackDiagnostic.endResolveAttempt(videoId, attempt, "ok ${extracted.clientName}")
        Timber.tag(TAG).d("Resolved $videoId via ${extracted.clientName} (${extracted.kbps}kbps ${extracted.mimeType})")
        if (!quiet) PlaybackDiagnostic.report("Stream resolved through ${extracted.clientName}. Listen and check that playback time advances.")
        return PlaybackData(
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

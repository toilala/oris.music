/*
 * Oris Music stream resolution.
 *
 * Genuine Oris implementation for the rebuild: resolves a YouTube Music
 * video id to a playable audio stream through the app's InnerTubeXResolver
 * (InnerTubeX library, GPL-3.0, Metrolist Group) and exposes the request
 * headers each resolved stream requires.
 */
package com.orismusic.client.utils

import com.orismusic.client.constants.AudioQuality
import java.io.IOException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import timber.log.Timber

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
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
        Timber.tag(TAG).d("Resolving stream for $videoId (maxKbps=$maxKbps)")
        val extracted = InnerTubeXResolver.extract(
            videoId = videoId,
            maxKbps = maxKbps,
        ) ?: throw IOException("No playable stream found for $videoId")
        Timber.tag(TAG).d("Resolved $videoId via ${extracted.clientName} (${extracted.kbps}kbps ${extracted.mimeType})")
        PlaybackData(
            streamUrl = extracted.url,
            cacheKey = videoId,
            mimeType = extracted.mimeType.substringBefore(';').ifBlank { "audio/mp4" },
            bitrate = extracted.bitrate ?: extracted.kbps * 1000,
        )
    }

    /** Headers a media fetch for [url] must carry, when the engine minted it. */
    fun headersFor(url: String): Map<String, String> =
        InnerTubeXResolver.headersFor(url) ?: emptyMap()

    enum class PlaybackEngine {
        POTOKEN,
        BRAVEPIPE,
        AUTO,
    }
}

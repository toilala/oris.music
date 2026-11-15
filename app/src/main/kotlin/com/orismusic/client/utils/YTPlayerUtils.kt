/** Metrolist Project (C) 2026 Licensed under GPL-3.0 | See git history for contributors */
package com.orismusic.client.utils

import android.net.Uri
import androidx.media3.common.MediaItem
import com.orismusic.client.constants.AudioQuality
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    @Volatile var forceOpusEnabled: Boolean = false

    @Volatile
    var playbackEngine: PlaybackEngine = PlaybackEngine.AUTO

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

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
        // Placeholder
    }

    suspend fun getPlaybackData(
        videoId: String,
        audioQuality: AudioQuality,
        context: android.content.Context
    ): PlaybackData {
        return withContext(IO) {
            PlaybackData(
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                cacheKey = videoId,
                mimeType = "audio/mp4",
                bitrate = 128000
            )
        }
    }

    enum class PlaybackEngine {
        POTOKEN,
        BRAVEPIPE,
        AUTO,
    }
}
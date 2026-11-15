package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    override val preloadItem: MediaItem? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    override val mediaItems: List<MediaItem> = emptyList()
    override val shuffleModeEnabled = false
    override val repeatMode = Player.REPEAT_MODE_OFF
    override val title: String? = playlistTitle

    override fun getInitialStatus(): InitialStatus {
        return InitialStatus(emptyList())
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun loadMore(): List<MediaItem> {
        return withContext(IO) {
            emptyList()
        }
    }

    override fun refresh(): Queue = this
}
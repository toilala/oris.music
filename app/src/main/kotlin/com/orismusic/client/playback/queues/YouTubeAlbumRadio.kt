package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeAlbumRadio(
    private var playlistId: String,
) : Queue {
    override val preloadItem: MediaItem? = null

    override val mediaItems: List<MediaItem> = emptyList()
    override val shuffleModeEnabled = false
    override val repeatMode = Player.REPEAT_MODE_OFF
    override val title: String? = "Album Radio"

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override fun getInitialStatus(): InitialStatus {
        return InitialStatus(emptyList())
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun loadMore(): List<MediaItem> = withContext(IO) { emptyList() }

    override fun refresh(): Queue = this
}
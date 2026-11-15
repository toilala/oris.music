package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMixQueue(
    private val database: com.orismusic.client.di.MusicDatabase,
    private val playlistId: String,
    private val maxMixSize: Int = 500,
) : Queue {
    override val preloadItem: MediaItem? = null

    override val mediaItems: List<MediaItem> = emptyList()
    override val shuffleModeEnabled = false
    override val repeatMode = Player.REPEAT_MODE_OFF
    override val title: String? = "Mix from Playlist"

    override fun getInitialStatus(): InitialStatus {
        return withContext(Dispatchers.IO) {
            InitialStatus(emptyList())
        }
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun loadMore(): List<MediaItem> = emptyList()

    override fun refresh(): Queue = this
}
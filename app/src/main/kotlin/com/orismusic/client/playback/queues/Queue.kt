package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

interface Queue {
    val mediaItems: List<MediaItem>
    val shuffleModeEnabled: Boolean
    val repeatMode: Int
    suspend fun loadMore(): List<MediaItem>
    fun refresh(): Queue
    val preloadItem: MediaItem?
        get() = null
    val title: String?
        get() = null
    fun getInitialStatus(): InitialStatus
    fun hasNextPage(): Boolean = false
}

data class InitialStatus(
    val items: List<MediaItem>,
    val mediaItemIndex: Int = 0,
    val position: Long = 0,
    val title: String? = null,
)

data class ListQueue(
    override val title: String? = null,
    override val mediaItems: List<MediaItem> = emptyList(),
    val startIndex: Int = 0,
    val startPosition: Long = 0,
    override val preloadItem: MediaItem? = null,
) : Queue {
    override val shuffleModeEnabled = false
    override val repeatMode = Player.REPEAT_MODE_OFF

    override suspend fun loadMore(): List<MediaItem> = emptyList()

    override fun refresh(): Queue = this

    override fun getInitialStatus(): InitialStatus {
        val safeIndex = if (mediaItems.isEmpty()) 0 else startIndex.coerceIn(0, mediaItems.lastIndex)
        return InitialStatus(
            items = mediaItems,
            mediaItemIndex = safeIndex,
            position = startPosition,
            title = title,
        )
    }
}

object EmptyQueue : Queue {
    override val mediaItems = emptyList<MediaItem>()
    override val shuffleModeEnabled = false
    override val repeatMode = Player.REPEAT_MODE_OFF
    override suspend fun loadMore(): List<MediaItem> = emptyList()
    override fun refresh(): Queue = this
    override fun getInitialStatus(): InitialStatus = InitialStatus(emptyList())
    override fun hasNextPage(): Boolean = false
}

// Extension functions for filtering
fun InitialStatus.filterExplicit(hideExplicit: Boolean): InitialStatus {
    if (!hideExplicit) return this
    return copy(items = items.filter { it.mediaMetadata.extras?.getBoolean("explicit") != true })
}

fun InitialStatus.filterVideoSongs(hideVideoSongs: Boolean): InitialStatus {
    if (!hideVideoSongs) return this
    return copy(items = items.filter { it.mediaMetadata.extras?.getBoolean("isVideoSong") != true })
}
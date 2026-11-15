package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?
        get() = null

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean = false

    suspend fun nextPage(): List<MediaItem> = emptyList()

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    )
}
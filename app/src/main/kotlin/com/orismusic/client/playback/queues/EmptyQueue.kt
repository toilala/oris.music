package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem

object EmptyQueue : Queue {
    override suspend fun getInitialStatus() = Queue.Status(null, emptyList(), -1, 0L)

    override fun hasNextPage() = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import com.orismusic.client.models.MediaMetadata

object EmptyQueue : Queue {
  override val preloadItem: MediaMetadata? = null

  override suspend fun getInitialStatus() = Status(null, emptyList(), 0)

  override fun hasNextPage() = false

  override suspend fun nextPage() = emptyList()
}
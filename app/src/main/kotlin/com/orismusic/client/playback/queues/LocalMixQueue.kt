package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import com.orismusic.client.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simplified LocalMixQueue - returns empty queue since database is not available.
 * In a full implementation, this would create a mix from a local playlist using
 * the database and YouTube related songs.
 */
class LocalMixQueue(
  private val playlistId: String,
  private val maxMixSize: Int = 500,
) : Queue {
  override val preloadItem: MediaMetadata? = null

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      Queue.Status(
        title = "Mix from Playlist",
        items = emptyList(),
        mediaItemIndex = 0,
      )
    }

  override fun hasNextPage(): Boolean = false

  override suspend fun nextPage(): List<MediaItem> = emptyList()
}
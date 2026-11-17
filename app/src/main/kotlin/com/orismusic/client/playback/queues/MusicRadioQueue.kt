package com.orismusic.client.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.models.MediaMetadata

/** Start with the user's chosen group, then request music radio from its selected song. */
class MusicRadioQueue(
    private val name: String,
    private val songs: List<SongItem>,
    private val selectedId: String,
) : Queue {
    override val preloadItem: MediaMetadata? = null
    private val filter = RadioQueuePageFilter()
    private var radio = WatchEndpoint(videoId = selectedId, playlistId = "RDAMVM$selectedId")
    private var cursor: String? = null
    private var started = false
    private var finished = false
    override suspend fun getInitialStatus(): Queue.Status {
        filter.reset(); cursor = null; started = false; finished = false
        val initial = filter.filter(songs)
        return Queue.Status(name, initial.map { it.toMediaItem() }, initial.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
    }
    override fun hasNextPage(): Boolean = !finished
    override suspend fun nextPage(): List<MediaItem> {
        repeat(3) {
            if (finished) return emptyList()
            val previous = cursor
            val page = YouTube.next(radio, if (started) cursor else null).getOrThrow()
            started = true
            radio = page.endpoint
            cursor = page.continuation
            finished = cursor == null || (previous != null && cursor == previous)
            val next = filter.filter(page.items)
            if (next.isNotEmpty()) return next.map { it.toMediaItem() }
        }
        return emptyList()
    }
}

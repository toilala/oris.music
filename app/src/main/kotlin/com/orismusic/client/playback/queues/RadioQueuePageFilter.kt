package com.orismusic.client.playback.queues

import com.music.innertube.models.SongItem
import com.orismusic.client.music.MusicCandidates

/** One radio session. Never let a later page repeat a recording already queued. */
internal class RadioQueuePageFilter {
    private val ids = hashSetOf<String>()
    private val recordings = hashSetOf<String>()
    fun reset() { ids.clear(); recordings.clear() }
    fun filter(songs: List<SongItem>): List<SongItem> = MusicCandidates.select(songs).filter {
        val key = MusicCandidates.trackKey(it)
        if (it.id in ids || key in recordings) false
        else { ids += it.id; recordings += key; true }
    }
}

package com.orismusic.client.music

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.orismusic.client.playback.queues.RadioQueuePageFilter
import org.junit.Assert.*
import org.junit.Test

class MusicCandidatesTest {
    private fun song(id: String, type: String? = "MUSIC_VIDEO_TYPE_ATV", title: String = "One") =
        SongItem(id=id, title=title, artists=listOf(Artist("Singer", "artist-id")),
            album=Album("Album", "album-id"), thumbnail="", musicVideoType=type)
    @Test fun audioReplacesDuplicateVideoWithoutReorderingOtherSongs() {
        val result = MusicCandidates.select(listOf(song("video", "MUSIC_VIDEO_TYPE_OMV"), song("two", title="Two"), song("audio")))
        assertEquals(listOf("audio", "two"), result.map { it.id })
    }
    @Test fun podcastsAndUserUploadsNeverEnterMusicRadio() {
        assertTrue(MusicCandidates.select(listOf(song("podcast", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"), song("upload", "MUSIC_VIDEO_TYPE_UGC"))).isEmpty())
    }
    @Test fun missingTypeNeedsSupportingMusicMetadata() {
        assertTrue(MusicCandidates.eligible(song("catalog", null)))
        assertFalse(MusicCandidates.eligible(song("unknown", null).copy(album=null)))
    }
    @Test fun unrelatedTracksKeepServiceRelevance() {
        assertEquals(listOf("a", "b"), MusicCandidates.select(listOf(song("a", title="A"),song("b", title="B"))).map { it.id })
    }
    @Test fun radioRemovesDuplicatesAcrossPagesAndResets() {
        val filter = RadioQueuePageFilter()
        assertEquals(1, filter.filter(listOf(song("a"))).size)
        assertTrue(filter.filter(listOf(song("b"))).isEmpty())
        filter.reset()
        assertEquals(1, filter.filter(listOf(song("b"))).size)
    }
    @Test fun sameTitleByDifferentArtistsIsNotSameTrack() {
        assertEquals(2, MusicCandidates.select(listOf(song("a"),song("b").copy(artists=listOf(Artist("Other", "other"))))).size)
    }
}

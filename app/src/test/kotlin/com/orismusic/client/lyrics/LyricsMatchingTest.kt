package com.orismusic.client.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsMatchingTest {
    private fun candidate(title: String = "Yesterday", artist: String = "The Beatles", album: String = "Help!", duration: Double = 125.0, synced: String = "[00:01]Hello") =
        LyricsCandidate(title, artist, album, duration, synced, "Hello")
    @Test fun prefersSyncedWithinMatchingRecording() {
        val plain = candidate(synced="")
        val synced = candidate(duration=126.0)
        assertEquals(synced, chooseLyricsCandidate(listOf(plain, synced), "Yesterday", "The Beatles", "Help!", 125))
    }
    @Test fun rejectsOtherArtistWithSameTitle() {
        assertNull(chooseLyricsCandidate(listOf(candidate(artist="Matt Monro")), "Yesterday", "The Beatles", "", 125))
    }
    @Test fun doesNotConfuseLiveAndStudio() {
        assertNull(chooseLyricsCandidate(listOf(candidate(title="Yesterday (Live)")), "Yesterday", "The Beatles", "", 125))
    }
    @Test fun acceptsRemasteredTitleButNotWrongDuration() {
        val good = candidate(title="Yesterday (Remastered 2009)")
        assertEquals(good, chooseLyricsCandidate(listOf(candidate(duration=160.0),good), "Yesterday", "The Beatles", "", 125))
    }
    @Test fun albumBreaksTiesBeforeDuration() {
        val album = candidate(duration=126.0)
        assertEquals(album, chooseLyricsCandidate(listOf(candidate(album="1"),album), "Yesterday", "The Beatles", "Help!", 125))
    }
    @Test fun blankArtistNeverAcceptsArbitraryResult() {
        assertNull(chooseLyricsCandidate(listOf(candidate()), "Yesterday", "", "", 125))
    }
}

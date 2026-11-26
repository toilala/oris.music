package com.orismusic.client.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedSongsTest {
    private fun song(id: String) = SavedSong(id, "t$id", "a", null)

    @Test fun likingAddsToFront() {
        assertEquals(listOf("b", "a"), listOf(song("a")).toggled(song("b")).map { it.id })
    }

    @Test fun likingTwiceRemoves() {
        assertEquals(emptyList<String>(), listOf(song("a")).toggled(song("a")).map { it.id })
    }

    @Test fun replayMovesToFrontWithoutDuplicate() {
        assertEquals(listOf("b", "a"), listOf(song("a"), song("b")).withPlay(song("b")).map { it.id })
    }

    @Test fun historyIsBounded() {
        val full = (1..HISTORY_LIMIT).map { song("$it") }
        assertEquals(HISTORY_LIMIT, full.withPlay(song("new")).size)
    }

    @Test fun blankQueryKeepsEverything() {
        val songs = listOf(song("a"), song("b"))
        assertEquals(songs, songs.matching("  "))
    }

    @Test fun filterMatchesTitleCaseInsensitively() {
        val songs = listOf(SavedSong("a", "Midnight City", "M83", null), song("b"))
        assertEquals(listOf("a"), songs.matching("midnight").map { it.id })
    }

    @Test fun filterMatchesArtist() {
        val songs = listOf(SavedSong("a", "One", "M83", null), SavedSong("b", "Two", "Hania Rani", null))
        assertEquals(listOf("b"), songs.matching("hania").map { it.id })
    }

    @Test fun filterWithoutMatchIsEmpty() {
        assertEquals(emptyList<SavedSong>(), listOf(song("a")).matching("zzz"))
    }
}

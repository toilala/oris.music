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
}

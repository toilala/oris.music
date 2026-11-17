package com.orismusic.client.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistsTest {
    private fun song(id: String) = SavedSong(id, "t$id", "a", null)

    @Test fun blankNameIsIgnored() {
        assertEquals(0, emptyList<UserPlaylist>().withNew("1", "  ").size)
    }

    @Test fun songIsAddedOnlyOnce() {
        val lists = emptyList<UserPlaylist>().withNew("1", "Road").withSong("1", song("a")).withSong("1", song("a"))
        assertEquals(listOf("a"), lists.single().songs.map { it.id })
    }

    @Test fun removeAndDelete() {
        val lists = emptyList<UserPlaylist>().withNew("1", "Road").withSong("1", song("a")).withSong("1", song("b"))
        assertEquals(listOf("b"), lists.withoutSong("1", "a").single().songs.map { it.id })
        assertEquals(0, lists.without("1").size)
    }
}

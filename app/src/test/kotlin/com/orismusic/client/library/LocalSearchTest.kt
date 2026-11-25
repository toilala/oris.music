package com.orismusic.client.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {
    private fun song(id: String, title: String = "t$id", artist: String = "a$id") =
        SavedSong(id, title, artist, null)

    private fun playlist(id: String, songs: List<SavedSong>) = UserPlaylist(id, id, songs)

    @Test fun blankQueryFindsNothing() {
        val results = localLibrarySearch("  ", listOf(song("a")), emptyList(), emptyList(), emptyList())
        assertEquals(emptyList<LocalSearchResult>(), results)
    }

    @Test fun emptyQueryFindsNothing() {
        val results = localLibrarySearch("", listOf(song("a")), emptyList(), emptyList(), emptyList())
        assertEquals(emptyList<LocalSearchResult>(), results)
    }

    @Test fun deduplicatesBySongIdAcrossSources() {
        val s = song("a")
        val results = localLibrarySearch("ta", listOf(s), listOf(s), listOf(playlist("p", listOf(s))), listOf(s))
        assertEquals(1, results.size)
    }

    @Test fun downloadedOnlySongIsFoundAndMarked() {
        val s = song("a", "Midnight City", "M83")
        val results = localLibrarySearch("midnight", emptyList(), emptyList(), emptyList(), listOf(s))
        assertEquals(listOf("a"), results.map { it.song.id })
        assertTrue(results[0].downloaded)
    }

    @Test fun libraryPresenceIsNotMarkedDownloaded() {
        val s = song("a", "Midnight City", "M83")
        val results = localLibrarySearch("midnight", listOf(s), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("a"), results.map { it.song.id })
        assertFalse(results[0].downloaded)
    }

    @Test fun titlePrefixRanksBeforeContainsBeforeArtist() {
        val prefix = song("p", "Midnight City", "M83")
        val contains = song("c", "City of Midnight", "Someone")
        val artist = song("r", "One", "Midnight Oil")
        val results = localLibrarySearch("midnight", listOf(artist, contains, prefix), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("p", "c", "r"), results.map { it.song.id })
    }

    @Test fun sameRankKeepsMergeOrder() {
        val a = song("a", "Midnight A", "x")
        val b = song("b", "Midnight B", "x")
        val results = localLibrarySearch("midnight", listOf(b), listOf(a), emptyList(), emptyList())
        assertEquals(listOf("b", "a"), results.map { it.song.id })
    }

    @Test fun matchesCaseInsensitively() {
        val s = song("a", "Midnight City", "M83")
        val results = localLibrarySearch("MIDNIGHT", listOf(s), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("a"), results.map { it.song.id })
    }

    @Test fun playlistSongsAreSearchable() {
        val s = song("a", "Ocean Eyes", "Billie Eilish")
        val results = localLibrarySearch("ocean", emptyList(), emptyList(), listOf(playlist("p", listOf(s))), emptyList())
        assertEquals(listOf("a"), results.map { it.song.id })
    }

    @Test fun nonMatchingSongsAreExcluded() {
        val a = song("a", "Midnight City", "M83")
        val b = song("b", "Sunny Day", "Someone")
        val results = localLibrarySearch("midnight", listOf(a, b), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("a"), results.map { it.song.id })
    }
}

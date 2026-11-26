package com.orismusic.client.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    private fun song(id: String, artist: String = "Artist $id") = SavedSong(id, "Song $id", artist, null)

    @Test
    fun `empty counts give an empty report`() {
        val s = listeningStats(emptyMap(), listOf(song("a")))
        assertEquals(0, s.totalStarts)
        assertEquals(0, s.countedSongs)
        assertEquals(0, s.namedSongs)
        assertTrue(s.topSongs.isEmpty())
        assertTrue(s.topArtists.isEmpty())
    }

    @Test
    fun `songs rank by starts, most first`() {
        val counts = mapOf("a" to 2, "b" to 5, "c" to 1)
        val s = listeningStats(counts, listOf(song("a"), song("b"), song("c")))
        assertEquals(listOf("b", "a", "c"), s.topSongs.map { it.song.id })
        assertEquals(5, s.topSongs[0].starts)
    }

    @Test
    fun `ties keep metadata order so recent-first callers favor recent songs`() {
        val counts = mapOf("a" to 3, "b" to 3)
        val s = listeningStats(counts, listOf(song("b"), song("a")))
        assertEquals(listOf("b", "a"), s.topSongs.map { it.song.id })
    }

    @Test
    fun `total and counted include songs whose metadata is gone`() {
        val counts = mapOf("a" to 4, "ghost" to 6)
        val s = listeningStats(counts, listOf(song("a")))
        assertEquals(10, s.totalStarts)
        assertEquals(2, s.countedSongs)
        assertEquals(1, s.namedSongs)
        assertEquals(listOf("a"), s.topSongs.map { it.song.id })
    }

    @Test
    fun `zero and negative counts never rank or total`() {
        val counts = mapOf("a" to 0, "b" to -2, "c" to 3)
        val s = listeningStats(counts, listOf(song("a"), song("b"), song("c")))
        assertEquals(3, s.totalStarts)
        assertEquals(1, s.countedSongs)
        assertEquals(listOf("c"), s.topSongs.map { it.song.id })
    }

    @Test
    fun `artist totals add every credited song's starts`() {
        val counts = mapOf("a" to 3, "b" to 2)
        val meta = listOf(song("a", "M83"), song("b", "M83, Daft Punk"))
        val s = listeningStats(counts, meta)
        assertEquals(listOf(ArtistPlays("M83", 5), ArtistPlays("Daft Punk", 2)), s.topArtists)
    }

    @Test
    fun `blank and whitespace-only artist credits are skipped`() {
        val counts = mapOf("a" to 3)
        val s = listeningStats(counts, listOf(song("a", "  ")))
        assertTrue(s.topArtists.isEmpty())
        assertEquals(3, s.totalStarts)
    }

    @Test
    fun `top limit caps songs and artists`() {
        val counts = (1..15).associate { "s$it" to it }
        val meta = (1..15).map { song("s$it", "A$it") }
        val s = listeningStats(counts, meta, top = 5)
        assertEquals(5, s.topSongs.size)
        assertEquals(5, s.topArtists.size)
        assertEquals("s15", s.topSongs.first().song.id)
    }

    @Test
    fun `duplicate metadata rows for one song count once`() {
        val counts = mapOf("a" to 4)
        val s = listeningStats(counts, listOf(song("a"), song("a")))
        assertEquals(1, s.topSongs.size)
        assertEquals(4, s.topSongs[0].starts)
    }
}

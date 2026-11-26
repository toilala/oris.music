package com.orismusic.client.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportTest {

    @Test
    fun `title artist lines parse in source order`() {
        val p = parsePlaylistImport("Song One - Artist A\nSong Two - Artist B")
        assertEquals(ImportFormat.TITLE_ARTIST_LINES, p.format)
        assertEquals(listOf("Song One", "Song Two"), p.songs.map { it.title })
        assertEquals("Artist B", p.songs[1].artist)
        assertNull(p.unsupportedReason)
    }

    @Test
    fun `lines without the separator are skipped verbatim`() {
        val p = parsePlaylistImport("Song One - Artist A\njust some words\n- Artist B")
        assertEquals(1, p.songs.size)
        assertEquals(listOf("just some words", "- Artist B"), p.skipped)
    }

    @Test
    fun `duplicate title artist pairs collapse and keep first position`() {
        val p = parsePlaylistImport("Song One - Artist A\nSong Two - Artist B\nsong one - artist a")
        assertEquals(2, p.songs.size)
        assertEquals(1, p.duplicates)
        assertEquals(listOf("Song One", "Song Two"), p.songs.map { it.title })
    }

    @Test
    fun `whitespace and case variants are the same identity`() {
        val p = parsePlaylistImport("Song  One - ARTIST A\n song one - artist a ")
        assertEquals(1, p.songs.size)
        assertEquals(1, p.duplicates)
    }

    @Test
    fun `csv with quoted commas and a video id column parses`() {
        val csv = "Track Name,Artist Name(s),Video ID\n\"Hello, World\",\"A, B\",dQw4w9WgXcQ\nPlain,Artist C,"
        val p = parsePlaylistImport(csv)
        assertEquals(ImportFormat.CSV, p.format)
        assertEquals(2, p.songs.size)
        assertEquals("Hello, World", p.songs[0].title)
        assertEquals("A, B", p.songs[0].artist)
        assertEquals("dQw4w9WgXcQ", p.songs[0].videoId)
        assertNull(p.songs[1].videoId)
    }

    @Test
    fun `a malformed video id is not treated as playable identity`() {
        val p = parsePlaylistImport("Title,Artist,Video ID\nSome Song,Some Artist,notavalidid!")
        assertEquals(1, p.songs.size)
        assertNull(p.songs[0].videoId)
        assertTrue(p.songs[0].toSavedSong().isUnmatchedImport())
    }

    @Test
    fun `same video id dedupes even when titles differ`() {
        val csv = "Title,Artist,Video ID\nOld Title,Artist,dQw4w9WgXcQ\nNew Title,Artist,dQw4w9WgXcQ"
        val p = parsePlaylistImport(csv)
        assertEquals(1, p.songs.size)
        assertEquals(1, p.duplicates)
        assertEquals("Old Title", p.songs[0].title)
    }

    @Test
    fun `csv without recognizable title and artist columns is unsupported`() {
        val p = parsePlaylistImport("Video ID,Playlist Video Creation Timestamp\nabc123,2024-01-01")
        assertNull(p.format)
        assertTrue(p.songs.isEmpty())
        assertTrue(p.unsupportedReason!!.isNotBlank())
    }

    @Test
    fun `a playlist link is reported unsupported, not silently dropped`() {
        val p = parsePlaylistImport("https://music.youtube.com/playlist?list=PLabc123")
        assertNull(p.format)
        assertTrue(p.songs.isEmpty())
        assertTrue(p.unsupportedReason!!.contains("links"))
    }

    @Test
    fun `blank input is unsupported`() {
        val p = parsePlaylistImport("   \n  ")
        assertNull(p.format)
        assertTrue(p.unsupportedReason!!.isNotBlank())
    }

    @Test
    fun `input where nothing parses is unsupported and keeps the lines`() {
        val p = parsePlaylistImport("gibberish one\ngibberish two")
        assertNull(p.format)
        assertTrue(p.songs.isEmpty())
        assertEquals(2, p.skipped.size)
        assertTrue(p.unsupportedReason!!.isNotBlank())
    }

    @Test
    fun `saved song identity is stable across repeated imports`() {
        val a = parsePlaylistImport("Song One - Artist A").songs[0].toSavedSong()
        val b = parsePlaylistImport("Song One - Artist A").songs[0].toSavedSong()
        assertEquals(a.id, b.id)
        assertTrue(a.isUnmatchedImport())
        val withVideo = ImportedSong("Song", "Artist", "dQw4w9WgXcQ").toSavedSong()
        assertEquals("dQw4w9WgXcQ", withVideo.id)
        assertTrue(!withVideo.isUnmatchedImport())
    }

    @Test
    fun `mixed url lines are skipped while song lines parse`() {
        val p = parsePlaylistImport("Song One - Artist A\nhttps://example.com/watch?v=xyz")
        assertEquals(1, p.songs.size)
        assertEquals(1, p.skipped.size)
    }

    @Test
    fun `only import provenance and blank ids are guarded, legacy non-shaped ids stay attempt eligible`() {
        assertTrue(SavedSong("import:song|artist", "Song", "Artist", null).isUnmatchedImport())
        assertTrue(SavedSong("", "Song", "Artist", null).isUnmatchedImport())
        assertFalse(SavedSong("notavalidid!", "Song", "Artist", null).isUnmatchedImport())
        assertFalse(SavedSong("lf-4", "Song", "Artist", null).isUnmatchedImport())
        assertFalse(SavedSong("offline-fixture-tone", "Song", "Artist", null).isUnmatchedImport())
        assertFalse(SavedSong("dQw4w9WgXcQ", "Song", "Artist", null).isUnmatchedImport())
    }

    @Test
    fun `matchedForPlayback drops unmatched entries and keeps order`() {
        val songs = listOf(
            SavedSong("dQw4w9WgXcQ", "First", "A", null),
            SavedSong("import:song two|b", "Second", "B", null),
            SavedSong("notavalidid!", "Third", "C", null),
            SavedSong("kJQP7kiw5Fk", "Fourth", "D", null),
        )
        assertEquals(listOf("dQw4w9WgXcQ", "notavalidid!", "kJQP7kiw5Fk"), songs.matchedForPlayback().map { it.id })
    }

    @Test
    fun `an all-metadata list yields an empty playback queue`() {
        val songs = listOf(
            SavedSong("import:song one|a", "One", "A", null),
            SavedSong("import:song two|b", "Two", "B", null),
        )
        assertTrue(songs.matchedForPlayback().isEmpty())
    }

    @Test
    fun `row taps map to queue positions by identity after filtering`() {
        val shown = listOf(
            SavedSong("dQw4w9WgXcQ", "First", "A", null),
            SavedSong("import:song two|b", "Second", "B", null),
            SavedSong("lf-4", "Third", "C", null),
            SavedSong("kJQP7kiw5Fk", "Fourth", "D", null),
        )
        val queue = shown.matchedForPlayback()
        // The same mapping the list rows apply: queue position by identity, -1 when metadata only.
        assertEquals(0, queue.indexOfFirst { it.id == shown[0].id })
        assertEquals(-1, queue.indexOfFirst { it.id == shown[1].id })
        assertEquals(1, queue.indexOfFirst { it.id == shown[2].id })
        assertEquals(2, queue.indexOfFirst { it.id == shown[3].id })
    }
}

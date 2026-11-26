package com.orismusic.client.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    private fun fsong(id: String) = FilteredSong(id, "t$id", "a$id")
    private fun song(id: String, vararg artists: String) = id to artists.toList()

    private fun List<Pair<String, List<String>>>.applyF(f: DiscoveryFilters) =
        withoutFiltered(f, { it.first }, { it.second }).map { it.first }

    @Test fun hiddenSongIsExcluded() {
        val f = DiscoveryFilters().withHidden(fsong("a"))
        assertEquals(listOf("b"), listOf(song("a", "X"), song("b", "Y")).applyF(f))
    }

    @Test fun dislikedSongIsExcluded() {
        val f = DiscoveryFilters().withDisliked(fsong("a"))
        assertEquals(listOf("b"), listOf(song("a", "X"), song("b", "Y")).applyF(f))
    }

    @Test fun blockedArtistExcludesAllTheirSongs() {
        val f = DiscoveryFilters().withBlockedArtist("M83")
        val songs = listOf(song("a", "M83"), song("b", "Hania Rani"), song("c", "M83"))
        assertEquals(listOf("b"), songs.applyF(f))
    }

    @Test fun blockedArtistMatchesWholeCreditOnlyCaseInsensitively() {
        val f = DiscoveryFilters().withBlockedArtist("  m83 ")
        assertTrue(f.blocks("z", listOf("Someone", "M83")))
        assertTrue(f.blocks("z", listOf("m83")))
        // A longer name that merely contains the blocked one is not a match.
        assertFalse(f.blocks("z", listOf("M83 Revival")))
    }

    @Test fun blockingBlankArtistChangesNothing() {
        val f = DiscoveryFilters().withBlockedArtist("   ")
        assertEquals(DiscoveryFilters(), f)
    }

    @Test fun duplicateEntriesAreNotAdded() {
        val f = DiscoveryFilters().withHidden(fsong("a")).withHidden(fsong("a"))
            .withBlockedArtist("M83").withBlockedArtist("m83")
        assertEquals(1, f.hidden.size)
        assertEquals(1, f.blockedArtists.size)
        assertEquals("M83", f.blockedArtists[0])
    }

    @Test fun undoRestores() {
        val f = DiscoveryFilters().withHidden(fsong("a")).withoutHidden("a")
            .withDisliked(fsong("b")).withoutDisliked("b")
            .withBlockedArtist("X").withoutBlockedArtist("x")
        assertEquals(DiscoveryFilters(), f)
        assertFalse(f.blocks("a", listOf("X")))
    }

    @Test fun unfilteredKeepsOrder() {
        val songs = listOf(song("a", "X"), song("b", "Y"), song("c", "Z"))
        assertEquals(listOf("a", "b", "c"), songs.applyF(DiscoveryFilters()))
    }

    @Test fun tasteArtistsRankFirstKeepingFeedOrderWithinGroups() {
        val songs = listOf(song("a", "New Act"), song("b", "M83"), song("c", "Other"), song("d", "Hania Rani"))
        val ranked = songs.rankedByTaste(listOf("Hania Rani", "M83")) { it.second }
        assertEquals(listOf("d", "b", "a", "c"), ranked.map { it.first })
    }

    @Test fun emptyTasteKeepsFeedOrder() {
        val songs = listOf(song("a", "X"), song("b", "Y"))
        assertEquals(listOf("a", "b"), songs.rankedByTaste(emptyList()) { it.second }.map { it.first })
    }

    @Test fun blocksReportsEachCase() {
        val f = DiscoveryFilters(hidden = listOf(fsong("h")), disliked = listOf(fsong("d")),
            blockedArtists = listOf("X"))
        assertTrue(f.blocks("h", listOf("Y")))
        assertTrue(f.blocks("d", listOf("Y")))
        assertTrue(f.blocks("z", listOf("X")))
        assertFalse(f.blocks("z", listOf("Y")))
    }

    @Test fun displayMetadataSurvivesForTheManagementList() {
        val f = DiscoveryFilters().withHidden(FilteredSong("id1", "Midnight City", "M83"))
        assertEquals("Midnight City", f.hidden[0].title)
        assertEquals("M83", f.hidden[0].artist)
    }
}

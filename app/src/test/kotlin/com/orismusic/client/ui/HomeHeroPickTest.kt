package com.orismusic.client.ui

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.orismusic.client.library.SavedSong
import com.orismusic.client.settings.HomeSettings
import com.orismusic.client.ui.screens.FeedPhase
import com.orismusic.client.ui.screens.HeroPick
import com.orismusic.client.ui.screens.chooseHero
import org.junit.Assert.*
import org.junit.Test

class HomeHeroPickTest {
    private fun song(id: String, art: String = "https://img/$id") =
        SongItem(id = id, title = id, artists = listOf(Artist("A", "a")), album = Album("Al", "al"),
            thumbnail = art, musicVideoType = "MUSIC_VIDEO_TYPE_ATV")
    private fun saved(id: String) = SavedSong(id, id, "Artist", "https://img/$id")

    private fun pick(
        phase: FeedPhase, feed: List<SongItem> = emptyList(), resolved: Boolean = true,
        quick: List<SongItem>? = null, history: List<SavedSong> = emptyList(), enabled: Boolean = true,
    ) = chooseHero(enabled, phase, feed, resolved, quick, history)

    @Test fun loadingFeedIsPending() = assertEquals(HeroPick.Pending, pick(FeedPhase.Loading))

    @Test fun playlistOnlyFeedFallsToQuickPicks() {
        val r = pick(FeedPhase.Ready, quick = listOf(song("q1"), song("q2")))
        assertEquals(listOf("q1", "q2"), (r as HeroPick.Ready).songs.map { it.id })
    }

    @Test fun playlistOnlyFeedWaitsForQuickPicks() =
        assertEquals(HeroPick.Pending, pick(FeedPhase.Ready, resolved = false))

    @Test fun feedSongsWinOverQuickPicks() {
        val r = pick(FeedPhase.Ready, feed = listOf(song("f1")), quick = listOf(song("q1")), resolved = false)
        assertEquals(listOf("f1"), (r as HeroPick.Ready).songs.map { it.id })
    }

    @Test fun historyFallbackWhenQuickPicksNeverCame() {
        val r = pick(FeedPhase.Ready, quick = null, history = listOf(saved("h1"), saved("h2")))
        assertEquals(listOf("h1", "h2"), (r as HeroPick.Ready).songs.map { it.id })
    }

    @Test fun failedFeedStillUsesHistory() {
        val r = pick(FeedPhase.Failed, history = listOf(saved("h1")))
        assertEquals(listOf("h1"), (r as HeroPick.Ready).songs.map { it.id })
    }

    @Test fun noContentIsEmptyNotPending() {
        assertEquals(HeroPick.Empty, pick(FeedPhase.Ready, quick = emptyList()))
        assertEquals(HeroPick.Empty, pick(FeedPhase.Failed))
    }

    @Test fun songsWithoutArtworkAreNotFabricated() =
        assertEquals(HeroPick.Empty, pick(FeedPhase.Ready, quick = listOf(song("x", art = ""))))

    @Test fun disabledBannerIsEmpty() =
        assertEquals(HeroPick.Empty, pick(FeedPhase.Ready, feed = listOf(song("f1")), enabled = false))

    @Test fun legacyMinimalKeepsBannerOnlyWhenOptedIn() {
        val prevHero = HomeSettings.hero; val prevMin = HomeSettings.minimal; val prevBanner = HomeSettings.minimalBanner
        try {
            HomeSettings.hero = true; HomeSettings.minimal = true
            HomeSettings.minimalBanner = false
            assertFalse(HomeSettings.bannerOn)
            HomeSettings.minimalBanner = true
            assertTrue(HomeSettings.bannerOn)
            HomeSettings.minimal = false; HomeSettings.minimalBanner = false
            assertTrue(HomeSettings.bannerOn)
            HomeSettings.hero = false
            assertFalse(HomeSettings.bannerOn)
        } finally {
            HomeSettings.hero = prevHero; HomeSettings.minimal = prevMin; HomeSettings.minimalBanner = prevBanner
        }
    }
}

package com.orismusic.client.ui.screens

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.orismusic.client.library.SavedSong
import com.orismusic.client.library.matchedForPlayback
import com.orismusic.client.music.MusicCandidates

sealed interface HeroPick {
    data object Pending : HeroPick
    data object Empty : HeroPick
    data class Ready(val songs: List<SongItem>) : HeroPick
}

enum class FeedPhase { Loading, Failed, Ready }

const val HERO_SIZE = 5

/**
 * Picks what the banner shows from real data only: songs in the feed, then quick picks, then listening history.
 * Pending means the chosen source can still arrive; Empty means nothing will.
 */
fun chooseHero(
    enabled: Boolean,
    phase: FeedPhase,
    feedSongs: List<SongItem>,
    quickPicksResolved: Boolean,
    quickPicks: List<SongItem>?,
    history: List<SavedSong>,
): HeroPick {
    if (!enabled) return HeroPick.Empty
    if (phase == FeedPhase.Loading) return HeroPick.Pending
    val fromFeed = MusicCandidates.select(feedSongs).withArtwork().take(HERO_SIZE)
    if (fromFeed.isNotEmpty()) return HeroPick.Ready(fromFeed)
    if (phase == FeedPhase.Ready) {
        if (!quickPicksResolved) return HeroPick.Pending
        val fromPicks = quickPicks.orEmpty().withArtwork().take(HERO_SIZE)
        if (fromPicks.isNotEmpty()) return HeroPick.Ready(fromPicks)
    }
    val fromHistory = history.matchedForPlayback().map { it.asSongItem() }.withArtwork().distinctBy { it.id }.take(HERO_SIZE)
    return if (fromHistory.isEmpty()) HeroPick.Empty else HeroPick.Ready(fromHistory)
}

private fun List<SongItem>.withArtwork() = filter { it.thumbnail.isNotBlank() }.distinctBy { it.id }

private fun SavedSong.asSongItem() =
    SongItem(id = id, title = title, artists = listOf(Artist(artist, null)), thumbnail = thumbnail.orEmpty())

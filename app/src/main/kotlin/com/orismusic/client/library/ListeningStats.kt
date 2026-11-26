package com.orismusic.client.library

/** One song with its recorded start count. */
data class SongPlays(val song: SavedSong, val starts: Int)

/** One artist with the starts of every song that credits them. */
data class ArtistPlays(val artist: String, val starts: Int)

/** What the phone has recorded about listening. Counts are song starts: each time a
 *  song began playing, consecutive repeats of the same song collapsed. Recording began
 *  with the version that added counting, and clearing the library history resets it,
 *  so these are "recorded since then" figures, never all-time history.
 *
 *  Rankings only name songs whose metadata still lives in a local store (history,
 *  liked, playlists, offline saves). A counted song whose details are gone still adds
 *  to totalStarts but cannot appear in a named ranking. */
data class ListeningStats(
    val totalStarts: Int,
    val countedSongs: Int,
    val namedSongs: Int,
    val topSongs: List<SongPlays>,
    val topArtists: List<ArtistPlays>,
)

/** Aggregates LibraryStore playCounts against whatever metadata is still on the phone.
 *  Ranked by starts, ties keep the order metadata arrived in (callers pass the most
 *  recent-first stores first, so a tie favors the more recently played song). Artist
 *  credits split on ", " exactly like the Home taste weighting. */
fun listeningStats(
    counts: Map<String, Int>,
    metadata: List<SavedSong>,
    top: Int = 10,
): ListeningStats {
    val byId = metadata.distinctBy { it.id }.associateBy { it.id }
    val order = metadata.map { it.id }.distinct().withIndex().associate { it.value to it.index }
    val known = counts.filter { it.key in byId && it.value > 0 }
    val topSongs = known.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { order[it.key] ?: Int.MAX_VALUE })
        .take(top)
        .map { SongPlays(byId.getValue(it.key), it.value) }
    val artistStarts = linkedMapOf<String, Int>()
    known.forEach { (id, n) ->
        byId.getValue(id).artist.split(", ").map { it.trim() }.filter { it.isNotBlank() }
            .forEach { name -> artistStarts[name] = (artistStarts[name] ?: 0) + n }
    }
    val topArtists = artistStarts.entries
        .sortedByDescending { it.value }
        .take(top)
        .map { ArtistPlays(it.key, it.value) }
    return ListeningStats(
        totalStarts = counts.values.filter { it > 0 }.sum(),
        countedSongs = counts.count { it.value > 0 },
        namedSongs = known.size,
        topSongs = topSongs,
        topArtists = topArtists,
    )
}

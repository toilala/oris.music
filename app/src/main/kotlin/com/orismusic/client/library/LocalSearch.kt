package com.orismusic.client.library

// One song found in the on-device library. The downloaded flag is set only from validated
// READY ids (full cached coverage), so a partially cached or unvalidated save is never
// advertised as available offline.
data class LocalSearchResult(
    val song: SavedSong,
    val downloaded: Boolean,
)

/**
 * Searches the on-device library (liked, history, playlists, saved offline) with no
 * network call. Songs are deduplicated by id, keeping the first occurrence in merge
 * order (liked, then history, then saved offline, then playlist songs). Ranking:
 * title prefix, then title contains, then artist prefix, then artist contains;
 * the merge order stays stable inside each rank.
 */
fun localLibrarySearch(
    query: String,
    liked: List<SavedSong>,
    history: List<SavedSong>,
    playlists: List<UserPlaylist>,
    downloaded: List<SavedSong>,
    readyIds: Set<String>,
): List<LocalSearchResult> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, SavedSong>()
    fun addAll(songs: List<SavedSong>) {
        for (song in songs) if (!merged.containsKey(song.id)) merged[song.id] = song
    }
    addAll(liked)
    addAll(history)
    addAll(downloaded)
    playlists.forEach { addAll(it.songs) }

    fun rank(song: SavedSong): Int {
        val title = song.title.lowercase()
        val artist = song.artist.lowercase()
        return when {
            title.startsWith(q) -> 0
            q in title -> 1
            artist.startsWith(q) -> 2
            q in artist -> 3
            else -> -1
        }
    }

    return merged.values
        .mapNotNull { song -> rank(song).takeIf { it >= 0 }?.let { song to it } }
        .sortedBy { it.second }
        .map { LocalSearchResult(it.first, it.first.id in readyIds) }
}

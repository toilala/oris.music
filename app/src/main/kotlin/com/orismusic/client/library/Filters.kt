package com.orismusic.client.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

// A song the listener filtered out of discovery. The display fields make the
// management list human-readable; matching is by stable id only.
data class FilteredSong(
    val id: String,
    val title: String,
    val artist: String,
)

// User-curated discovery filters, kept on the phone: hidden and disliked songs by id,
// blocked artists by exact (case-insensitive) name, never substrings. Quick picks
// apply them locally; the feed itself still comes from the network. Hiding and
// disliking both exclude the song from picks; blocking an artist excludes anything
// that credits them. Every entry can be removed again from Home settings.
data class DiscoveryFilters(
    val hidden: List<FilteredSong> = emptyList(),
    val disliked: List<FilteredSong> = emptyList(),
    val blockedArtists: List<String> = emptyList(),
) {
    fun withHidden(song: FilteredSong): DiscoveryFilters =
        if (hidden.any { it.id == song.id }) this else copy(hidden = hidden + song)

    fun withDisliked(song: FilteredSong): DiscoveryFilters =
        if (disliked.any { it.id == song.id }) this else copy(disliked = disliked + song)

    fun withBlockedArtist(name: String): DiscoveryFilters {
        val clean = name.trim()
        return if (clean.isEmpty() || blockedArtists.any { it.equals(clean, ignoreCase = true) }) {
            this
        } else {
            copy(blockedArtists = blockedArtists + clean)
        }
    }

    fun withoutHidden(id: String): DiscoveryFilters = copy(hidden = hidden.filterNot { it.id == id })
    fun withoutDisliked(id: String): DiscoveryFilters = copy(disliked = disliked.filterNot { it.id == id })
    fun withoutBlockedArtist(name: String): DiscoveryFilters =
        copy(blockedArtists = blockedArtists.filterNot { it.equals(name.trim(), ignoreCase = true) })

    fun blocks(id: String, artists: List<String>): Boolean =
        hidden.any { it.id == id } || disliked.any { it.id == id } ||
            artists.any { a -> blockedArtists.any { it.equals(a.trim(), ignoreCase = true) } }
}

/** Everything the filters remove from a candidate list, order preserved. */
fun <T> List<T>.withoutFiltered(
    filters: DiscoveryFilters,
    id: (T) -> String,
    artists: (T) -> List<String>,
): List<T> = filter { item -> !filters.blocks(id(item), artists(item)) }

/**
 * Local-history ranking for quick picks: songs by artists the listener actually plays
 * come first, the rest keep their feed order. The taste list is expected most-played
 * first, so earlier taste artists rank above later ones; ties keep feed order.
 */
fun <T> List<T>.rankedByTaste(
    tasteArtists: List<String>,
    artists: (T) -> List<String>,
): List<T> {
    val rank = tasteArtists.mapIndexed { i, name -> name.trim().lowercase() to i }.toMap()
    return mapIndexed { index, item ->
        val best = artists(item).mapNotNull { rank[it.trim().lowercase()] }.minOrNull()
        Triple(best ?: Int.MAX_VALUE, index, item)
    }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
}

/** The listener's discovery filters, persisted in their own preferences file. */
class DiscoveryFilterStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oris-filters", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(read())
    val filters: StateFlow<DiscoveryFilters> = flow

    fun hide(song: FilteredSong) = update(flow.value.withHidden(song))
    fun dislike(song: FilteredSong) = update(flow.value.withDisliked(song))
    fun blockArtist(name: String) = update(flow.value.withBlockedArtist(name))
    fun unhide(id: String) = update(flow.value.withoutHidden(id))
    fun undislike(id: String) = update(flow.value.withoutDisliked(id))
    fun unblockArtist(name: String) = update(flow.value.withoutBlockedArtist(name))

    private fun update(next: DiscoveryFilters) {
        flow.value = next
        prefs.edit()
            .putString("hidden", songsJson(next.hidden))
            .putString("disliked", songsJson(next.disliked))
            .putString("blocked", JSONArray(next.blockedArtists).toString())
            .apply()
    }

    private fun read(): DiscoveryFilters = runCatching {
        fun songs(key: String): List<FilteredSong> {
            val array = JSONArray(prefs.getString(key, "[]"))
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                FilteredSong(o.getString("id"), o.optString("title"), o.optString("artist"))
            }
        }
        val blocked = JSONArray(prefs.getString("blocked", "[]"))
        DiscoveryFilters(
            hidden = songs("hidden"),
            disliked = songs("disliked"),
            blockedArtists = (0 until blocked.length()).map { blocked.getString(it) },
        )
    }.getOrDefault(DiscoveryFilters())

    private fun songsJson(songs: List<FilteredSong>): String {
        val array = JSONArray()
        songs.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist))
        }
        return array.toString()
    }

    companion object {
        @Volatile private var instance: DiscoveryFilterStore? = null
        fun get(context: Context): DiscoveryFilterStore =
            instance ?: synchronized(this) { instance ?: DiscoveryFilterStore(context).also { instance = it } }
    }
}

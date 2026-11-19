package com.orismusic.client.library

import android.content.Context
import androidx.media3.common.MediaItem
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.models.MediaMetadata
import com.orismusic.client.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A song the user liked or played, kept small so it can live in plain preferences. */
data class SavedSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
)

const val HISTORY_LIMIT = 100

/** Liking something already liked removes it. Newest like goes first. */
fun List<SavedSong>.toggled(song: SavedSong): List<SavedSong> =
    if (any { it.id == song.id }) filterNot { it.id == song.id } else listOf(song) + this

/** Most recent play first, no repeats, bounded. */
fun List<SavedSong>.withPlay(song: SavedSong, limit: Int = HISTORY_LIMIT): List<SavedSong> =
    (listOf(song) + filterNot { it.id == song.id }).take(limit)

fun SavedSong.toPlayableItem(): MediaItem =
    MediaMetadata(
        id = id,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
        duration = -1,
        thumbnailUrl = thumbnail,
    ).toMediaItem()

fun MediaItem.toSavedSong(): SavedSong? {
    if (mediaId.isBlank()) return null
    val meta = mediaMetadata
    val title = meta.title?.toString().orEmpty()
    if (title.isBlank()) return null
    return SavedSong(
        id = mediaId,
        title = title,
        artist = meta.artist?.toString().orEmpty(),
        thumbnail = meta.artworkUri?.toString() ?: meta.extras?.getString("artwork_uri"),
    )
}

fun PlayerConnection.currentSavedSong(): SavedSong? =
    runCatching { player.currentMediaItem }.getOrNull()?.toSavedSong()

/** Liked songs and play history, saved on the phone. */
class LibraryStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oris-library", Context.MODE_PRIVATE)
    private val likedFlow = MutableStateFlow(read("liked"))
    private val historyFlow = MutableStateFlow(read("history"))

    private val countsFlow = MutableStateFlow(readCounts())
    /** How many times each song was started on this phone. Songs played before counting began are not in here. */
    val playCounts: StateFlow<Map<String, Int>> = countsFlow
    val liked: StateFlow<List<SavedSong>> = likedFlow
    val history: StateFlow<List<SavedSong>> = historyFlow

    fun toggleLike(song: SavedSong) {
        likedFlow.value = likedFlow.value.toggled(song)
        write("liked", likedFlow.value)
    }

    fun recordPlay(song: SavedSong) {
        if (historyFlow.value.firstOrNull()?.id == song.id) return
        countsFlow.value = countsFlow.value + (song.id to ((countsFlow.value[song.id] ?: 0) + 1))
        prefs.edit().putString("counts", JSONObject(countsFlow.value as Map<*, *>).toString()).apply()
        historyFlow.value = historyFlow.value.withPlay(song)
        write("history", historyFlow.value)
    }

    fun clearHistory() {
        historyFlow.value = emptyList()
        countsFlow.value = emptyMap()
        prefs.edit().remove("counts").apply()
        write("history", emptyList())
    }

    private fun readCounts(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString("counts", "{}"))
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun read(key: String): List<SavedSong> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            SavedSong(
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.optString("artist"),
                thumbnail = if (o.has("thumb")) o.getString("thumb") else null,
            )
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, songs: List<SavedSong>) {
        val array = JSONArray()
        songs.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                .apply { it.thumbnail?.let { t -> put("thumb", t) } })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    companion object {
        @Volatile private var instance: LibraryStore? = null
        fun get(context: Context): LibraryStore =
            instance ?: synchronized(this) { instance ?: LibraryStore(context).also { instance = it } }
    }
}

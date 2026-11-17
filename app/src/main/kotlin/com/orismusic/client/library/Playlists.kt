package com.orismusic.client.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A playlist the user made on this phone. */
data class UserPlaylist(
    val id: String,
    val name: String,
    val songs: List<SavedSong>,
)

fun List<UserPlaylist>.withNew(id: String, name: String): List<UserPlaylist> {
    val clean = name.trim()
    if (clean.isEmpty()) return this
    return this + UserPlaylist(id, clean, emptyList())
}

/** Adds to the end of the playlist, once. */
fun List<UserPlaylist>.withSong(playlistId: String, song: SavedSong): List<UserPlaylist> =
    map { p -> if (p.id == playlistId && p.songs.none { it.id == song.id }) p.copy(songs = p.songs + song) else p }

fun List<UserPlaylist>.withoutSong(playlistId: String, songId: String): List<UserPlaylist> =
    map { p -> if (p.id == playlistId) p.copy(songs = p.songs.filterNot { it.id == songId }) else p }

fun List<UserPlaylist>.without(playlistId: String): List<UserPlaylist> = filterNot { it.id == playlistId }

class PlaylistStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oris-library", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(read())
    val playlists: StateFlow<List<UserPlaylist>> = flow

    fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        update(flow.value.withNew(id, name))
        return id
    }

    fun add(playlistId: String, song: SavedSong) = update(flow.value.withSong(playlistId, song))
    fun remove(playlistId: String, songId: String) = update(flow.value.withoutSong(playlistId, songId))
    fun delete(playlistId: String) = update(flow.value.without(playlistId))

    private fun update(next: List<UserPlaylist>) {
        flow.value = next
        val array = JSONArray()
        next.forEach { p ->
            val songs = JSONArray()
            p.songs.forEach { s ->
                songs.put(JSONObject().put("id", s.id).put("title", s.title).put("artist", s.artist)
                    .apply { s.thumbnail?.let { t -> put("thumb", t) } })
            }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", songs))
        }
        prefs.edit().putString("playlists", array.toString()).apply()
    }

    private fun read(): List<UserPlaylist> = runCatching {
        val array = JSONArray(prefs.getString("playlists", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val songs = o.getJSONArray("songs")
            UserPlaylist(
                id = o.getString("id"),
                name = o.getString("name"),
                songs = (0 until songs.length()).map { j ->
                    val s = songs.getJSONObject(j)
                    SavedSong(
                        id = s.getString("id"),
                        title = s.getString("title"),
                        artist = s.optString("artist"),
                        thumbnail = if (s.has("thumb")) s.getString("thumb") else null,
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: PlaylistStore? = null
        fun get(context: Context): PlaylistStore =
            instance ?: synchronized(this) { instance ?: PlaylistStore(context).also { instance = it } }
    }
}

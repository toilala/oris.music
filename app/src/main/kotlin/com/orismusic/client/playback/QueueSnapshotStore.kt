package com.orismusic.client.playback

import android.content.Context
import com.orismusic.client.models.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Writes the current queue to a small JSON file so a cold start (or a killed process) can bring
 * it back, paused where it left off. Same idea as Echo and Mochy's persistent queue; storage
 * here is a plain file, no copied code.
 */
object QueueSnapshotStore {
    private const val FILE_NAME = "queue_snapshot.json"

    data class Snapshot(
        val title: String?,
        val items: List<MediaMetadata>,
        val index: Int,
        val positionMs: Long,
    )

    /** Call from the player's thread with already-read state; only the file write happens here. */
    fun save(context: Context, title: String?, items: List<MediaMetadata>, index: Int, positionMs: Long) {
        if (items.isEmpty()) return
        val arr = JSONArray()
        items.forEach { meta ->
            arr.put(JSONObject().apply {
                put("id", meta.id)
                put("title", meta.title)
                put("artists", JSONArray(meta.artists.map { it.name }))
                put("duration", meta.duration)
                if (meta.thumbnailUrl != null) put("thumb", meta.thumbnailUrl)
                if (meta.album != null) {
                    put("albumId", meta.album.id)
                    put("albumTitle", meta.album.title)
                }
            })
        }
        val root = JSONObject().apply {
            if (title != null) put("title", title)
            put("items", arr)
            put("index", index.coerceIn(0, items.size - 1))
            put("position", positionMs.coerceAtLeast(0L))
        }
        File(context.filesDir, FILE_NAME).writeText(root.toString())
    }

    fun load(context: Context): Snapshot? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val arr = root.getJSONArray("items")
        val items = mutableListOf<MediaMetadata>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val names = o.getJSONArray("artists")
            items += MediaMetadata(
                id = o.getString("id"),
                title = o.optString("title"),
                artists = (0 until names.length()).map { MediaMetadata.Artist(id = null, name = names.getString(it)) },
                duration = o.optInt("duration"),
                thumbnailUrl = if (o.has("thumb")) o.getString("thumb") else null,
                album = if (o.has("albumId")) MediaMetadata.Album(id = o.getString("albumId"), title = o.optString("albumTitle")) else null,
            )
        }
        if (items.isEmpty()) return null
        Snapshot(
            title = if (root.has("title")) root.getString("title") else null,
            items = items,
            index = root.optInt("index").coerceIn(0, items.size - 1),
            positionMs = root.optLong("position"),
        )
    }.getOrNull()
}

package com.orismusic.client.lyrics

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal fun queryPart(value: String) = URLEncoder.encode(value, "UTF-8")
internal fun String.lyricText() = takeUnless { it == "null" }.orEmpty()

/** Public provider response readers, written for Oris rather than copied from another client. */
internal object ExtraLyricsSources {
    suspend fun youly(title: String, artist: String, album: String, duration: Int, get: suspend (String) -> String): LyricsDocument? {
        val query = "title=${queryPart(title)}&artist=${queryPart(artist)}&album=${queryPart(album)}" +
            if (duration > 0) "&duration=$duration" else ""
        val json = JSONObject(get("https://lyricsplus.prjktla.my.id/v2/lyrics/get?$query"))
        val lines = decodeYouly(json)
        return lines.takeIf { it.isNotEmpty() }?.let { LyricsDocument("YouLyPlus", it) }
    }

    fun decodeYouly(json: JSONObject): List<LyricLine> {
        val synced = json.optString("syncedLyrics").lyricText()
        if (synced.isNotBlank()) return readLrc(synced)
        val rows = json.optJSONArray("lyrics") ?: return readLrc(json.optString("plainLyrics").lyricText())
        val timed = !json.optString("type").equals("none", true)
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            row.optString("text").lyricText().takeIf { it.isNotBlank() }?.let {
                LyricLine(if (timed && row.has("time")) row.optLong("time") else null, it)
            }
        }.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    suspend fun pax(title: String, artist: String, album: String, duration: Int, get: suspend (String) -> String): LyricsDocument? {
        val results = JSONArray(get("https://lyrics.paxsenix.org/apple-music/search?q=${queryPart("$title $artist")}"))
        val entries = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        val mapping = entries.associateBy { it.optString("id") }
        val candidates = entries.map { LyricsCandidate(it.optString("trackName").ifBlank { it.optString("songName") }, it.optString("artistName"),
            it.optString("albumName"), it.optDouble("duration", 0.0)/1000, it.optString("id"), "") }
        val match = chooseLyricsCandidate(candidates, title, artist, album, duration) ?: return null
        val id = match.synced
        if (id.isBlank() || mapping[id] == null) return null
        val response = JSONObject(get("https://lyrics.paxsenix.org/apple-music/lyrics?id=${queryPart(id)}"))
        val lines = decodePax(response)
        return lines.takeIf { it.isNotEmpty() }?.let { LyricsDocument("PaxSenix", it) }
    }

    fun decodePax(json: JSONObject): List<LyricLine> {
        val ttml = json.optString("ttmlContent").lyricText()
        if (ttml.isNotBlank()) return readTtml(ttml)
        for (field in listOf("elrcMultiPerson", "elrc", "plain")) {
            val value = json.optString(field).lyricText()
            if (value.isNotBlank()) return readLrc(value)
        }
        val rows = json.optJSONArray("content") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val text = row.optJSONArray("text") ?: return@mapNotNull null
            val pieces = (0 until text.length()).mapNotNull { text.optJSONObject(it)?.optString("text") }
            val joined = if (pieces.any { it.endsWith(" ") }) pieces.joinToString("") else pieces.joinToString(" ")
            joined.trim().takeIf { it.isNotBlank() }?.let { LyricLine(row.optLong("timestamp"), it) }
        }
    }

    suspend fun unison(id: String, title: String, artist: String, duration: Int, get: suspend (String) -> String): LyricsDocument? {
        val query = "v=${queryPart(id)}&song=${queryPart(title)}&artist=${queryPart(artist)}" +
            if (duration > 0) "&duration=$duration" else ""
        val envelope = JSONObject(get("https://unison.betterlyrics.org/lyrics?$query"))
        if (!envelope.optBoolean("success")) return null
        val data = envelope.optJSONObject("data") ?: return null
        return decodeUnison(data)
    }

    fun decodeUnison(data: JSONObject): LyricsDocument? {
        val text = data.optString("lyrics").lyricText()
        if (text.isBlank()) return null
        val video = data.optString("videoId").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
        val submitter = data.optJSONObject("submitter") ?: return null
        val name = submitter.optString("displayName").takeIf { it.isNotBlank() } ?: return null
        val key = submitter.optString("keyId").takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
        val lines = if (data.optString("format").equals("ttml", true)) readTtml(text) else readLrc(text)
        return LyricsDocument("Unison", lines, listOf(
            LyricCredit("Lyrics from Unison", "https://unison.betterlyrics.org/song/$video"),
            LyricCredit("Submitted by $name", "https://unison.betterlyrics.org/curator/$key"),
        ))
    }
}

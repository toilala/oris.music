package com.orismusic.client.music

import com.music.innertube.models.SongItem
import java.util.Locale

/** Candidate policy, not a claim of artist/channel verification. */
object MusicCandidates {
    private fun keyPart(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("\\s*[-–]\\s*topic$"), "")
        .replace(Regex("\\s*\\((?:official audio|official video|lyrics|lyric video)\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun trackKey(song: SongItem): String = keyPart(song.title) + "|" +
        song.artists.map { keyPart(it.name) }.sorted().joinToString("|")

    fun eligible(song: SongItem): Boolean {
        if (song.id.isBlank() || song.title.isBlank() || song.artists.isEmpty()) return false
        return when (song.musicVideoType) {
            "MUSIC_VIDEO_TYPE_PODCAST_EPISODE", "MUSIC_VIDEO_TYPE_UGC" -> false
            "MUSIC_VIDEO_TYPE_ATV" -> true
            "MUSIC_VIDEO_TYPE_OMV" -> song.artists.any { !it.id.isNullOrBlank() }
            null, "" -> song.album != null && song.artists.any { !it.id.isNullOrBlank() }
            else -> false
        }
    }

    /** Only reorder competing versions of one track. Keep the service's relevance order. */
    fun select(songs: List<SongItem>): List<SongItem> {
        val chosen = linkedMapOf<String, SongItem>()
        songs.filter(::eligible).distinctBy { it.id }.forEach { song ->
            val key = trackKey(song)
            val previous = chosen[key]
            if (previous == null || confidence(song) > confidence(previous)) chosen[key] = song
        }
        return chosen.values.toList()
    }

    private fun confidence(song: SongItem): Int = when (song.musicVideoType) {
        "MUSIC_VIDEO_TYPE_ATV" -> 3
        "MUSIC_VIDEO_TYPE_OMV" -> 2
        else -> 1
    }
}

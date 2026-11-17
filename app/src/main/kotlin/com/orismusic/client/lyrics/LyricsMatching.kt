package com.orismusic.client.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class LyricsCandidate(val title: String, val artist: String, val album: String,
    val duration: Double, val synced: String, val plain: String)

private fun words(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Ignore recording-master labels, but never collapse live, remix, cover or acoustic versions. */
private fun trackWords(text: String) = words(text
    .replace(Regex("\\s*[(\\[][^)\\]]*remaster[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s*[-–]\\s*(?:\\d{4}\\s*)?remaster(?:ed)?(?:\\s*\\d{4})?\\s*$", RegexOption.IGNORE_CASE), ""))

fun chooseLyricsCandidate(entries: List<LyricsCandidate>, title: String, artist: String, album: String, duration: Int): LyricsCandidate? {
    val wantedTitle = trackWords(title)
    val wantedArtist = words(artist)
    if (wantedTitle.isBlank() || wantedArtist.isBlank()) return null
    return entries.filter {
        trackWords(it.title) == wantedTitle && words(it.artist) == wantedArtist &&
            (it.synced.isNotBlank() || it.plain.isNotBlank()) &&
            (duration <= 0 || abs(it.duration - duration) <= 10)
    }.sortedWith(compareByDescending<LyricsCandidate> { it.synced.isNotBlank() }
        .thenByDescending { album.isNotBlank() && words(it.album) == words(album) }
        .thenBy { if (duration > 0) abs(it.duration-duration) else 0.0 }).firstOrNull()
}

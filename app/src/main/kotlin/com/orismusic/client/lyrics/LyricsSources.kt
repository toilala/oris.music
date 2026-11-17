package com.orismusic.client.lyrics

/** Stable IDs are persisted; display names can change without losing a user's order. */
data class LyricsSource(val id: String, val name: String, val website: String, val note: String = "", val available: Boolean = true, val defaultEnabled: Boolean = true)

object LyricsSources {
    val all = listOf(
        LyricsSource("lrclib", "LRCLIB", "https://lrclib.net"),
        LyricsSource("unison", "Unison", "https://unison.boidu.dev", "Community lyrics. Credit to the submitter is shown."),
        LyricsSource("better", "Better Lyrics", "https://github.com/better-lyrics/api", "Public cached lyrics; uncached requests can require an unavailable key."),
        LyricsSource("youly", "YouLyPlus", "https://github.com/ibratabian17/LyricsPlus", "Service returned 502 during validation; optional until it recovers.", defaultEnabled = false),
        LyricsSource("pax", "PaxSenix", "https://lyrics.paxsenix.org", "Service returned 403 to Oris during validation; no impersonation attempted.", defaultEnabled = false),
        LyricsSource("kugou", "KuGou via Better Lyrics", "https://docs.betterlyrics.org/reference/get-lyrics", "Keyless line-synced endpoint. Not a direct KuGou client."),
        LyricsSource("youtube_music", "YouTube Music", "https://music.youtube.com", "Uses this track's lyrics tab."),
        LyricsSource("youtube_subtitles", "YouTube Subtitle", "https://www.youtube.com", "Uploaded captions, when available. May not be song lyrics."),
        LyricsSource("simpmusic", "SimpMusic", "https://api-lyrics.simpmusic.org", "Not enabled: service explicitly restricts access to SimpMusic clients.", available = false, defaultEnabled = false),
    )
    fun find(id: String) = all.firstOrNull { it.id == id }
}

data class LyricsOptions(val order: List<String>, val disabled: Set<String>) {
    fun ordered() = order.mapNotNull(LyricsSources::find)
    fun enabled() = ordered().filter { it.available && it.id !in disabled }
    fun move(id: String, delta: Int): LyricsOptions {
        val index = order.indexOf(id)
        if (index < 0) return this
        val destination = (index + delta).coerceIn(0, order.lastIndex)
        return copy(order = order.toMutableList().apply { removeAt(index); add(destination, id) })
    }
    fun enable(id: String, enabled: Boolean) = copy(disabled = if (enabled) disabled-id else disabled+id)
}

fun restoreLyricsOptions(savedOrder: List<String>, savedDisabled: Set<String>?): LyricsOptions {
    val ids = LyricsSources.all.map { it.id }
    val order = savedOrder.filter { it in ids }.distinct().let { it + ids.filterNot(it::contains) }
    val disabled = savedDisabled?.filter { it in ids }?.toSet()
        ?: LyricsSources.all.filter { !it.defaultEnabled }.map { it.id }.toSet()
    return LyricsOptions(order, disabled)
}

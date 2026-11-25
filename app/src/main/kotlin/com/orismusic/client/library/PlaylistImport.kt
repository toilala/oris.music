package com.orismusic.client.library

/** One parsed song from pasted import text. videoId is present only when the
 *  source carried one; those entries can play directly. The rest are kept as
 *  metadata-only listings: the app cannot play them and has no matching for them. */
data class ImportedSong(val title: String, val artist: String, val videoId: String?)

enum class ImportFormat { TITLE_ARTIST_LINES, CSV }

/** What an import paste means, before anything is saved. songs keeps the source
 *  order with duplicates collapsed; skipped holds the lines that did not parse
 *  verbatim so the user can see exactly what was left out. unsupportedReason is
 *  set when the whole input is something this import cannot read (a link, an
 *  unknown structure); nothing is imported then. */
data class ImportPreview(
    val format: ImportFormat?,
    val songs: List<ImportedSong>,
    val duplicates: Int,
    val skipped: List<String>,
    val unsupportedReason: String?,
)

private val linkLine = Regex("https?://\\S+")
private val playlistLink = Regex("https?://\\S*(/playlist|/album|/track|spotify\\.link|/share)\\S*", RegexOption.IGNORE_CASE)

private fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

private fun identityOf(song: ImportedSong): String =
    song.videoId?.let { "v:$it" } ?: ("t:" + normalize(song.title) + "|" + normalize(song.artist))

private fun collect(entries: List<ImportedSong?>, format: ImportFormat, skipped: List<String>): ImportPreview {
    val seen = mutableSetOf<String>()
    var duplicates = 0
    val songs = mutableListOf<ImportedSong>()
    entries.filterNotNull().forEach { s ->
        if (seen.add(identityOf(s))) songs += s else duplicates++
    }
    return ImportPreview(format, songs, duplicates, skipped, null)
}

private fun unsupported(reason: String, skipped: List<String> = emptyList()) =
    ImportPreview(null, emptyList(), 0, skipped, reason)

/** Minimal CSV row splitter: commas outside double quotes, "" escapes a quote. */
private fun csvRow(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
            else -> cur.append(c)
        }
        i++
    }
    out += cur.toString()
    return out
}

private val videoIdShape = Regex("[A-Za-z0-9_-]{11}")
private val titleColumns = listOf("title", "track", "track name", "song", "song name", "name")
private val artistColumns = listOf("artist", "artists", "artist name", "artist name(s)", "artist names")
private val videoIdColumns = listOf("video id", "videoid", "video", "youtube id")

/** Parses pasted import text. Supported: "Title - Artist" lines, and CSV with a
 *  header row naming title and artist columns (an optional video id column is
 *  used when present). Playlist links and id-only exports are reported as
 *  unsupported rather than guessed at. */
fun parsePlaylistImport(text: String): ImportPreview {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return unsupported("Nothing to import yet.")
    if (lines.size == 1 && playlistLink.matches(lines[0])) {
        return unsupported("Playlist links can't be read offline. Export the playlist or paste its songs as Title - Artist, one per line.")
    }

    val header = csvRow(lines[0]).map { normalize(it) }
    val titleIdx = header.indexOfFirst { it in titleColumns }
    val artistIdx = header.indexOfFirst { it in artistColumns }
    val videoIdx = header.indexOfFirst { it in videoIdColumns }
    val looksLikeCsv = lines[0].contains(',') && (titleIdx >= 0 || artistIdx >= 0)

    if (looksLikeCsv) {
        if (titleIdx < 0 || artistIdx < 0) {
            return unsupported(
                "This file has no title and artist columns the import recognizes, so its songs can't be read here.",
                lines,
            )
        }
        val skipped = mutableListOf<String>()
        val entries = lines.drop(1).map { line ->
            val cols = csvRow(line)
            val title = cols.getOrNull(titleIdx)?.trim().orEmpty()
            val artist = cols.getOrNull(artistIdx)?.trim().orEmpty()
            val video = cols.getOrNull(videoIdx)?.trim()?.takeIf { it.matches(videoIdShape) }
            if (title.isEmpty()) { skipped += line; null } else ImportedSong(title, artist, video)
        }
        if (entries.all { it == null }) {
            return unsupported("None of the rows in this file could be read.", skipped)
        }
        return collect(entries, ImportFormat.CSV, skipped)
    }

    val skipped = mutableListOf<String>()
    val entries = lines.map { line ->
        when {
            playlistLink.matches(line) || (linkLine.matches(line) && !line.contains(" - ")) -> {
                skipped += line; null
            }
            else -> {
                val cut = line.indexOf(" - ")
                if (cut <= 0 || cut == line.length - 3) { skipped += line; null }
                else ImportedSong(line.substring(0, cut).trim(), line.substring(cut + 3).trim(), null)
            }
        }
    }
    if (entries.all { it == null }) {
        return unsupported("No readable songs found. Paste one song per line as Title - Artist.", skipped)
    }
    return collect(entries, ImportFormat.TITLE_ARTIST_LINES, skipped)
}

/** Imported songs join playlists with a stable identity: the video id when the
 *  source had one, otherwise a key from the normalized title and artist, so a
 *  repeated import of the same song lands on the same id and dedupes. Songs on
 *  an "import:" id are metadata only: not playable, and the app has no matching
 *  for them. */
fun ImportedSong.toSavedSong(): SavedSong = SavedSong(
    id = videoId ?: ("import:" + normalize(title) + "|" + normalize(artist)),
    title = title,
    artist = artist,
    thumbnail = null,
)

/** A library song is unmatched when it carries an "import:" placeholder id, or any
 *  id that cannot be a YouTube video id at all (including entries persisted before
 *  video id validation existed). Unmatched songs stay listed but must never enter
 *  a playback or download queue. A shape-valid id is only syntactically plausible:
 *  an unavailable video still fails through ordinary playback errors. */
fun SavedSong.isUnmatchedImport(): Boolean = id.startsWith("import:") || !id.matches(videoIdShape)

/** The playable subset of a library list, in the original order. Bulk actions
 *  (Play, Shuffle, search-result queues) run on this; callers surface the
 *  difference as metadata-only entries that can't play, so nothing is silently
 *  omitted. */
fun List<SavedSong>.matchedForPlayback(): List<SavedSong> = filter { !it.isUnmatchedImport() }

package com.orismusic.client.lyrics

data class LyricLine(val timeMs: Long?, val text: String)
data class LyricsDocument(val source: String, val lines: List<LyricLine>) {
    val timed: Boolean get() = lines.any { it.timeMs != null }
}

/** Parse ordinary and repeated LRC timestamps without discarding untimed lyrics. */
fun readLrc(value: String): List<LyricLine> {
    val clock = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val output = mutableListOf<LyricLine>()
    value.lineSequence().forEach { raw ->
        val stamps = clock.findAll(raw).toList()
        val text = raw.replace(clock, "").trim()
        if (stamps.isEmpty()) {
            if (text.isNotBlank() && !text.startsWith("[")) output += LyricLine(null, text)
        } else stamps.forEach { match ->
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val millis = match.groupValues[1].toLong()*60000 + match.groupValues[2].toLong()*1000 + fraction
            output += LyricLine((millis + offset).coerceAtLeast(0), text)
        }
    }
    return if (output.any { it.timeMs != null }) output.filter { it.timeMs != null }.sortedBy { it.timeMs } else output
}

fun activeLyric(lines: List<LyricLine>, positionMs: Long): Int =
    lines.indexOfLast { it.timeMs?.let { time -> time <= positionMs } == true }

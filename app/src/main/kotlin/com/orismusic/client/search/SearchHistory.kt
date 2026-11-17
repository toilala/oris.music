package com.orismusic.client.search

/** Most recent first, trimmed, distinct ignoring case, with a bounded local footprint. */
fun rememberQuery(previous: List<String>, query: String): List<String> {
    val clean = query.trim()
    if (clean.isEmpty()) return previous
    return (listOf(clean) + previous.filterNot { it.equals(clean, ignoreCase = true) }).take(12)
}

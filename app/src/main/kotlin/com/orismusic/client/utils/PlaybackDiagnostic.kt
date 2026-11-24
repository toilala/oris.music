package com.orismusic.client.utils

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A small on-device diagnostic, never cookies, tokens, stream URLs or account data. */
object PlaybackDiagnostic {
    private const val HISTORY = 20

    private val current = MutableStateFlow("Tap a song to test anonymous playback.")
    val message = current.asStateFlow()

    private val ring = ArrayDeque<String>()
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history = _history.asStateFlow()

    private val _activeFailure = MutableStateFlow<String?>(null)
    val activeFailure = _activeFailure.asStateFlow()

    fun report(text: String) {
        val clean = sanitize(text)
        current.value = clean
        synchronized(ring) {
            ring.addLast(clean)
            while (ring.size > HISTORY) ring.removeFirst()
            _history.value = ring.toList()
        }
    }

    fun reportFailure(text: String) {
        report(text)
        _activeFailure.value = sanitize(text)
    }

    /** Cleared only when the player itself reports real playback, never on a tap or prepare. */
    fun reportStarted() {
        _activeFailure.value = null
    }

    private fun sanitize(text: String): String = sanitizeDiagnostic(text)
}

/** One-line human version of a raw failure for the player surface. The raw chain stays in the diagnostic history. */
fun diagnosticSummary(raw: String): String {
    val t = raw.lowercase()
    return when {
        "network" in t || "unable to resolve host" in t || "eai_" in t || "cannot resolve" in t ->
            "No connection. Check your network and try again."
        "refused" in t -> "The music service refused this request. Try again shortly."
        "no playable stream" in t -> "This song can't play right now. Try another one."
        else -> "This song couldn't play. Try again."
    }
}

class GuestAccessRefused(message: String) : IOException(message)

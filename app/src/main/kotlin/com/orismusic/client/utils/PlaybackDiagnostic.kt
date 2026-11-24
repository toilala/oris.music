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

class GuestAccessRefused(message: String) : IOException(message)

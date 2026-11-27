package com.orismusic.client.utils

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** A small on-device diagnostic, never cookies, tokens, stream URLs or account data. */
object PlaybackDiagnostic {
    private const val HISTORY = 20
    private val resolveSeq = java.util.concurrent.atomic.AtomicInteger(0)
    private val latestAttempt = HashMap<String, Int>()
    private const val LOG_TAG = "OrisDiag"

    private val current = MutableStateFlow("Tap a song to test anonymous playback.")
    val message = current.asStateFlow()

    private val ring = ArrayDeque<String>()
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history = _history.asStateFlow()

    private val _activeFailure = MutableStateFlow<String?>(null)
    val activeFailure = _activeFailure.asStateFlow()

    // The media item the active failure belongs to; null means unattributed and always shown.
    // A failure owned by one track must not banner over another track that is playing fine.
    private val _activeFailureFor = MutableStateFlow<String?>(null)
    val activeFailureFor = _activeFailureFor.asStateFlow()

    fun report(text: String) {
        val clean = sanitize(text)
        // Mirror into logcat so CI evidence carries the same timeline as the dialog.
        // Only the sanitized text is ever logged: no URLs, cookies or account data.
        Timber.tag(LOG_TAG).i(clean)
        current.value = clean
        synchronized(ring) {
            ring.addLast(clean)
            while (ring.size > HISTORY) ring.removeFirst()
            _history.value = ring.toList()
        }
    }

    fun reportFailure(text: String, mediaId: String? = null) {
        report(text)
        Timber.tag(LOG_TAG).w("failure active: %s", sanitize(text))
        _activeFailure.value = sanitize(text)
        _activeFailureFor.value = mediaId
    }

    /** Cleared only when the player itself reports real playback, never on a tap or prepare. */
    fun reportStarted(mediaId: String? = null) {
        val attempt = mediaId?.let { synchronized(latestAttempt) { latestAttempt[it] } }
        Timber.tag(LOG_TAG).i(
            "playback started: %s%s",
            mediaId ?: "unknown",
            attempt?.let { " (resolve r$it)" } ?: "",
        )
        _activeFailure.value = null
        _activeFailureFor.value = null
    }

    /** Start of one resolve attempt. The sequence tags its result and any playback it yields,
     *  so retries of the same song stay distinguishable. */
    fun beginResolveAttempt(videoId: String): Int {
        val n = resolveSeq.incrementAndGet()
        synchronized(latestAttempt) { latestAttempt[videoId] = n }
        Timber.tag(LOG_TAG).i("resolve r%d start: %s", n, videoId)
        return n
    }

    fun endResolveAttempt(videoId: String, attempt: Int, outcome: String) {
        Timber.tag(LOG_TAG).i("resolve r%d %s: %s", attempt, sanitize(outcome), videoId)
    }

    private fun sanitize(text: String): String = sanitizeDiagnostic(text)
}

/** One-line human version of a raw failure for the player surface. The raw chain stays in the diagnostic history. */
fun diagnosticSummary(raw: String): String {
    val t = raw.lowercase()
    return when {
        "network" in t || "unable to resolve host" in t || "eai_" in t || "cannot resolve" in t ->
            "No connection. Check your network and try again."
        "timed out" in t -> "Taking too long. Check your connection and try again."
        "refused" in t -> "The music service refused this request. Try again shortly."
        "no playable stream" in t -> "This song can't play right now. Try another one."
        // The skip guard's pause message is already human wording; show it as-is.
        "failed in a row" in t -> raw
        // The terminal stop's after-retrying reason is human wording too; never flatten it.
        "failed to load after retrying" in t -> raw
        else -> "This song couldn't play. Try again."
    }
}

/** Very short failure label for the mini player pill, where even a first sentence clips. */
fun diagnosticShort(raw: String): String {
    val t = raw.lowercase()
    return when {
        "network" in t || "unable to resolve host" in t || "eai_" in t || "cannot resolve" in t -> "No connection"
        "timed out" in t -> "Taking too long"
        "refused" in t -> "Playback refused"
        "no playable stream" in t -> "Can't play this song"
        "failed in a row" in t -> "Paused after failures"
        "failed to load after retrying" in t -> "Stopped after retries"
        else -> "Couldn't play"
    }
}

/** True when any cause in the chain is a known guest refusal (bot check). */
fun hasGuestRefusal(error: Throwable?): Boolean {
    var cause = error
    repeat(12) {
        if (cause is GuestAccessRefused) return true
        if (cause?.suppressed?.any { it is GuestAccessRefused } == true) return true
        cause = cause?.cause
    }
    return false
}

class GuestAccessRefused(message: String) : IOException(message)

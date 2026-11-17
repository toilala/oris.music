package com.orismusic.client.utils

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A small on-device diagnostic, never cookies, tokens, stream URLs or account data. */
object PlaybackDiagnostic {
    private val current = MutableStateFlow("Tap a song to test anonymous playback.")
    val message = current.asStateFlow()
    fun report(text: String) { current.value = text }
}

class GuestAccessRefused(message: String) : IOException(message)

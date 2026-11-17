package com.orismusic.client.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Pauses playback after a chosen number of minutes. One timer at a time. */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val endsAtFlow = MutableStateFlow<Long?>(null)

    /** Uptime (SystemClock.elapsedRealtime) when the timer ends, or null when off. */
    val endsAt: StateFlow<Long?> = endsAtFlow

    fun start(minutes: Int, onFinish: () -> Unit) {
        cancel()
        val millis = minutes * 60_000L
        endsAtFlow.value = SystemClock.elapsedRealtime() + millis
        job = scope.launch {
            delay(millis)
            endsAtFlow.value = null
            runCatching(onFinish)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        endsAtFlow.value = null
    }
}

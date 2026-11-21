package com.orismusic.client.settings

import android.content.Context

/** Read synchronously at task removal so an old flow value cannot keep audio alive. */
class PlaybackPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("playback_behavior", Context.MODE_PRIVATE)

    var keepPlayingAfterTaskRemoval: Boolean
        get() = store.getBoolean("continue_after_task_removed", true)
        set(value) { store.edit().putBoolean("continue_after_task_removed", value).apply() }

    var skipSilence: Boolean
        get() = store.getBoolean("skip_silence", false)
        set(value) { store.edit().putBoolean("skip_silence", value).apply() }

    /** Whether the playback notification is fully shown (true) or minimized (false). */
    var playbackNotification: Boolean
        get() = store.getBoolean("playback_notification", true)
        set(value) { store.edit().putBoolean("playback_notification", value).apply() }

    /** When on, swiping the app away stops music only if it is already paused. */
    var stopOnlyWhenPaused: Boolean
        get() = store.getBoolean("stop_only_when_paused", false)
        set(value) { store.edit().putBoolean("stop_only_when_paused", value).apply() }

    /** When on, the queue is kept on disk and restored (paused) on the next cold start. */
    var persistentQueue: Boolean
        get() = store.getBoolean("persistent_queue", true)
        set(value) { store.edit().putBoolean("persistent_queue", value).apply() }

    var playbackSpeed: Float
        get() = store.getFloat("playback_speed", 1f).let { if (it in SPEEDS) it else 1f }
        set(value) { store.edit().putFloat("playback_speed", value).apply() }

    companion object {
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}

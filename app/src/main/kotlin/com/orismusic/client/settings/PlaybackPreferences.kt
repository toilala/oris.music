package com.orismusic.client.settings

import android.content.Context

/** Read synchronously at task removal so an old flow value cannot keep audio alive. */
class PlaybackPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("playback_behavior", Context.MODE_PRIVATE)

    var keepPlayingAfterTaskRemoval: Boolean
        get() = store.getBoolean("continue_after_task_removed", false)
        set(value) { store.edit().putBoolean("continue_after_task_removed", value).apply() }

    var skipSilence: Boolean
        get() = store.getBoolean("skip_silence", false)
        set(value) { store.edit().putBoolean("skip_silence", value).apply() }

    var playbackSpeed: Float
        get() = store.getFloat("playback_speed", 1f).let { if (it in SPEEDS) it else 1f }
        set(value) { store.edit().putFloat("playback_speed", value).apply() }

    companion object {
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}

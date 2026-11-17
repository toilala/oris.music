package com.orismusic.client.settings

import android.content.Context

/** Read synchronously at task removal so an old flow value cannot keep audio alive. */
class PlaybackPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("playback_behavior", Context.MODE_PRIVATE)

    var keepPlayingAfterTaskRemoval: Boolean
        get() = store.getBoolean("continue_after_task_removed", false)
        set(value) { store.edit().putBoolean("continue_after_task_removed", value).apply() }
}

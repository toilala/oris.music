package com.orismusic.client.settings

import android.content.Context
import com.orismusic.client.lyrics.LyricsOptions
import com.orismusic.client.lyrics.restoreLyricsOptions

class LyricsPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("lyrics_sources", Context.MODE_PRIVATE)
    fun read(): LyricsOptions = restoreLyricsOptions(
        store.getString("order", "").orEmpty().split(",").filter { it.isNotBlank() },
        if (store.contains("disabled")) store.getStringSet("disabled", emptySet())?.toSet() else null,
    )
    fun save(options: LyricsOptions) {
        store.edit().putString("order", options.order.joinToString(","))
            .putStringSet("disabled", options.disabled).apply()
    }
}

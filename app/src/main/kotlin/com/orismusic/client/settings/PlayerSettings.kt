package com.orismusic.client.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PlayKey(val label: String) { Glass("Glass"), Solid("Solid"), Plain("Plain") }
enum class PlaySize(val label: String, val play: Int, val skip: Int) { Compact("Compact", 76, 52), Regular("Regular", 92, 60), Large("Large", 104, 68) }
enum class SeekStyle(val label: String) { Wave("Wave"), Line("Line"), Bars("Bars") }
enum class LyricSize(val label: String) { Compact("Compact"), Regular("Regular"), Large("Large") }
enum class SkipIcons(val label: String) { Skip("Skip"), Seek("Seek") }

/** Where a tapped song starts playing. */
enum class TapOpen(val label: String) { Fullscreen("Full player"), Miniplayer("Miniplayer") }

/**
 * Player options. Compose state, saved in their own preferences file. To add an option:
 * add a field here, read and write it below, then add a row on the Player settings page.
 */
object PlayerSettings {
    private var store: android.content.SharedPreferences? = null

    var keys by mutableStateOf(PlayKey.Solid)
    var size by mutableStateOf(PlaySize.Regular)
    var icons by mutableStateOf(SkipIcons.Skip)
    var seek by mutableStateOf(SeekStyle.Wave)
    var remaining by mutableStateOf(false)
    var lyricSize by mutableStateOf(LyricSize.Regular)
    var lyricsCentered by mutableStateOf(false)
    var lyricsBlur by mutableStateOf(true)
    var morph by mutableStateOf(true)
    var titleMotion by mutableStateOf(true)
    var haptics by mutableStateOf(true)
    var upNext by mutableStateOf(true)
    var tapOpen by mutableStateOf(TapOpen.Miniplayer)

    private inline fun <reified E : Enum<E>> android.content.SharedPreferences.enum(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == getString(key, "") } ?: default

    fun init(context: Context) {
        if (store != null) return
        val p = context.applicationContext.getSharedPreferences("player_look", Context.MODE_PRIVATE)
        store = p
        keys = p.enum("keys", PlayKey.Solid)
        size = p.enum("size", PlaySize.Regular)
        icons = p.enum("icons", SkipIcons.Skip)
        seek = p.enum("seek", SeekStyle.Wave)
        remaining = p.getBoolean("remaining", false)
        lyricSize = p.enum("lyric_size", LyricSize.Regular)
        lyricsCentered = p.getBoolean("lyrics_centered", false)
        lyricsBlur = p.getBoolean("lyrics_blur", true)
        morph = p.getBoolean("morph", true)
        titleMotion = p.getBoolean("title_motion", true)
        haptics = p.getBoolean("haptics", true)
        upNext = p.getBoolean("up_next", true)
        tapOpen = p.enum("tap_open", TapOpen.Miniplayer)
    }

    fun save() {
        store?.edit()
            ?.putString("keys", keys.name)?.putString("size", size.name)?.putString("icons", icons.name)
            ?.putString("seek", seek.name)?.putBoolean("remaining", remaining)
            ?.putString("lyric_size", lyricSize.name)?.putBoolean("lyrics_centered", lyricsCentered)?.putBoolean("lyrics_blur", lyricsBlur)
            ?.putBoolean("morph", morph)?.putBoolean("title_motion", titleMotion)
            ?.putBoolean("haptics", haptics)?.putBoolean("up_next", upNext)
            ?.putString("tap_open", tapOpen.name)
            ?.apply()
    }

    fun reset() {
        keys = PlayKey.Solid; size = PlaySize.Regular; icons = SkipIcons.Skip
        seek = SeekStyle.Wave; remaining = false; lyricSize = LyricSize.Regular; lyricsCentered = false; lyricsBlur = true; morph = true; titleMotion = true; haptics = true; upNext = true; tapOpen = TapOpen.Miniplayer
        save()
    }
}

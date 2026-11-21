package com.orismusic.client.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PlayerBackgroundStyle(val label: String) { Gradient("Artwork gradient"), Blur("Blurred artwork"), Solid("Solid black") }
enum class PlayButtonStyle(val label: String) { Wavy("Wavy blob"), Squircle("Squircle") }
enum class PlayerSeekBar(val label: String) { Slim("Slim line"), Wave("Wave") }
enum class LyricSize(val label: String) { Compact("Small"), Regular("Regular"), Large("Large") }
enum class SwipeSensitivity(val label: String) { Low("Low"), Regular("Regular"), High("High") }

/** Where a tapped song starts playing. */
enum class TapOpen(val label: String) { Fullscreen("Full player"), Miniplayer("Miniplayer") }

/**
 * Player options. Compose state, saved in their own preferences file. To add an option:
 * add a field here, read and write it below, then add a row on the Player settings page.
 */
object PlayerSettings {
    private var store: android.content.SharedPreferences? = null

    var backgroundStyle by mutableStateOf(PlayerBackgroundStyle.Gradient)
    var playButton by mutableStateOf(PlayButtonStyle.Wavy)
    var seekBar by mutableStateOf(PlayerSeekBar.Slim)
    var remaining by mutableStateOf(false)
    var lyricSize by mutableStateOf(LyricSize.Regular)
    var lyricsCentered by mutableStateOf(false)
    var lyricsTapSeek by mutableStateOf(true)
    var lyricsAutoScroll by mutableStateOf(true)
    var swipeSensitivity by mutableStateOf(SwipeSensitivity.Regular)
    var haptics by mutableStateOf(true)
    var tapOpen by mutableStateOf(TapOpen.Miniplayer)

    private inline fun <reified E : Enum<E>> android.content.SharedPreferences.enum(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == getString(key, "") } ?: default

    fun init(context: Context) {
        if (store != null) return
        val p = context.applicationContext.getSharedPreferences("player_look", Context.MODE_PRIVATE)
        store = p
        backgroundStyle = p.enum("background_style", PlayerBackgroundStyle.Gradient)
        playButton = p.enum("play_button", PlayButtonStyle.Wavy)
        seekBar = p.enum("seek_bar", PlayerSeekBar.Slim)
        remaining = p.getBoolean("remaining", false)
        lyricSize = p.enum("lyric_size", LyricSize.Regular)
        lyricsCentered = p.getBoolean("lyrics_centered", false)
        lyricsTapSeek = p.getBoolean("lyrics_tap_seek", true)
        lyricsAutoScroll = p.getBoolean("lyrics_auto_scroll", true)
        swipeSensitivity = p.enum("swipe_sensitivity", SwipeSensitivity.Regular)
        haptics = p.getBoolean("haptics", true)
        tapOpen = p.enum("tap_open", TapOpen.Miniplayer)
        LyricTiming.init(context)
    }

    fun save() {
        store?.edit()
            ?.putString("background_style", backgroundStyle.name)
            ?.putString("play_button", playButton.name)
            ?.putString("seek_bar", seekBar.name)
            ?.putBoolean("remaining", remaining)
            ?.putString("lyric_size", lyricSize.name)?.putBoolean("lyrics_centered", lyricsCentered)
            ?.putBoolean("lyrics_tap_seek", lyricsTapSeek)?.putBoolean("lyrics_auto_scroll", lyricsAutoScroll)
            ?.putString("swipe_sensitivity", swipeSensitivity.name)
            ?.putBoolean("haptics", haptics)
            ?.putString("tap_open", tapOpen.name)
            ?.apply()
    }

    fun reset() {
        backgroundStyle = PlayerBackgroundStyle.Gradient; playButton = PlayButtonStyle.Wavy; seekBar = PlayerSeekBar.Slim
        remaining = false; lyricSize = LyricSize.Regular; lyricsCentered = false
        lyricsTapSeek = true; lyricsAutoScroll = true; swipeSensitivity = SwipeSensitivity.Regular
        haptics = true; tapOpen = TapOpen.Miniplayer
        save()
    }
}

/**
 * Per-song lyrics timing nudges, in milliseconds. A positive value hears the words earlier.
 * Kept in its own preferences file, one entry per song id.
 */
object LyricTiming {
    private var store: android.content.SharedPreferences? = null
    private val offsets = mutableStateMapOf<String, Int>()

    fun init(context: Context) {
        if (store != null) return
        val p = context.applicationContext.getSharedPreferences("lyrics_timing", Context.MODE_PRIVATE)
        store = p
        offsets.clear()
        p.all.forEach { (k, v) -> (v as? Int)?.let { offsets[k] = it } }
    }

    fun offset(songId: String): Int = offsets[songId] ?: 0

    fun set(songId: String, ms: Int) {
        if (ms == 0) offsets.remove(songId) else offsets[songId] = ms
        val e = store?.edit() ?: return
        if (ms == 0) e.remove(songId) else e.putInt(songId, ms)
        e.apply()
    }
}

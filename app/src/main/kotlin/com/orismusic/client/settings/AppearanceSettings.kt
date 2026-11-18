package com.orismusic.client.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How the player background follows the song. */
enum class BackgroundMode(val label: String) {
    Gradient("Gradient"),
    Blur("Blurred art"),
    Solid("Solid"),
}

/**
 * Look settings. Values are Compose state so the theme and the player react the moment
 * they change in Settings. Stored in a small private preferences file.
 */
object AppearanceSettings {
    private var store: android.content.SharedPreferences? = null

    /** Use the Android wallpaper (Material You) colours instead of the Oris palette. */
    var dynamicColor by mutableStateOf(false)
    var amoled by mutableStateOf(false)
    var adaptiveBackground by mutableStateOf(true)
    var backgroundMode by mutableStateOf(BackgroundMode.Gradient)
    /** Show the sleep timer button on the player. Off by default. */
    var sleepTimerButton by mutableStateOf(false)
    var intensity by mutableFloatStateOf(0.75f)
    /** Hide the Android status bar; a swipe from the top edge shows it briefly. */
    var hideStatusBar by mutableStateOf(false)

    fun init(context: Context) {
        if (store != null) return
        val prefs = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        store = prefs
        dynamicColor = prefs.getBoolean("dynamic", false)
        amoled = prefs.getBoolean("amoled", false)
        adaptiveBackground = prefs.getBoolean("adaptive", true)
        backgroundMode = BackgroundMode.entries.firstOrNull { it.name == prefs.getString("mode", "") } ?: BackgroundMode.Gradient
        sleepTimerButton = prefs.getBoolean("sleep_button", false)
        intensity = prefs.getFloat("intensity", 0.75f)
        hideStatusBar = prefs.getBoolean("hide_status", false)
    }

    fun save() {
        store?.edit()
            ?.putBoolean("dynamic", dynamicColor)
            ?.putBoolean("amoled", amoled)
            ?.putBoolean("adaptive", adaptiveBackground)
            ?.putString("mode", backgroundMode.name)
            ?.putBoolean("sleep_button", sleepTimerButton)
            ?.putFloat("intensity", intensity)
            ?.putBoolean("hide_status", hideStatusBar)
            ?.apply()
    }
}

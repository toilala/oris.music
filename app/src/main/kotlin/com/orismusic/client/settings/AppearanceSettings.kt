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
    Ambient("Ambient"),
    Solid("Solid"),
}

/** Accent colour for the Oris palette (the play key, nav highlight, section labels). Ignored under Material You. */
enum class AccentSeed(val label: String, val swatch: androidx.compose.ui.graphics.Color,
                      val accent: androidx.compose.ui.graphics.Color, val onAccent: androidx.compose.ui.graphics.Color,
                      val container: androidx.compose.ui.graphics.Color, val onContainer: androidx.compose.ui.graphics.Color) {
    Rose("Rose", androidx.compose.ui.graphics.Color(0xFFD9A7A0), androidx.compose.ui.graphics.Color(0xFFD9A7A0), androidx.compose.ui.graphics.Color(0xFF2A1210), androidx.compose.ui.graphics.Color(0xFF3B2B29), androidx.compose.ui.graphics.Color(0xFFF3DCD8)),
    Amber("Amber", androidx.compose.ui.graphics.Color(0xFFE3B87C), androidx.compose.ui.graphics.Color(0xFFE3B87C), androidx.compose.ui.graphics.Color(0xFF2A1A08), androidx.compose.ui.graphics.Color(0xFF3B2E1C), androidx.compose.ui.graphics.Color(0xFFF3E2C8)),
    Sage("Sage", androidx.compose.ui.graphics.Color(0xFFA8C2A0), androidx.compose.ui.graphics.Color(0xFFA8C2A0), androidx.compose.ui.graphics.Color(0xFF101F0E), androidx.compose.ui.graphics.Color(0xFF28321F), androidx.compose.ui.graphics.Color(0xFFD8E6CF)),
    Sky("Sky", androidx.compose.ui.graphics.Color(0xFF9FBFD8), androidx.compose.ui.graphics.Color(0xFF9FBFD8), androidx.compose.ui.graphics.Color(0xFF0E1B26), androidx.compose.ui.graphics.Color(0xFF223140), androidx.compose.ui.graphics.Color(0xFFD5E6F3)),
    Violet("Violet", androidx.compose.ui.graphics.Color(0xFFC4AEDB), androidx.compose.ui.graphics.Color(0xFFC4AEDB), androidx.compose.ui.graphics.Color(0xFF1D1226), androidx.compose.ui.graphics.Color(0xFF2E2438), androidx.compose.ui.graphics.Color(0xFFE8DCF3)),
    Stone("Stone", androidx.compose.ui.graphics.Color(0xFFD0CCC6), androidx.compose.ui.graphics.Color(0xFFD0CCC6), androidx.compose.ui.graphics.Color(0xFF161514), androidx.compose.ui.graphics.Color(0xFF2B2A29), androidx.compose.ui.graphics.Color(0xFFEDE9E2)),
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
    /** Show the tab names under the bottom bar icons. */
    var navLabels by mutableStateOf(true)
    var accent by mutableStateOf(AccentSeed.Rose)

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
        accent = AccentSeed.entries.firstOrNull { it.name == prefs.getString("accent", "") } ?: AccentSeed.Rose
        NavSettings.init(context)
        HomeSettings.init(context)
        PlayerSettings.init(context)
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
            ?.putString("accent", accent.name)
            ?.putBoolean("nav_labels", navLabels)
            ?.apply()
    }
}

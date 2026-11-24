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

/** Light, dark, or following the phone. Follow-phone is the default. */
enum class ThemeMode(val label: String) {
    System("Follow phone"),
    Light("Light"),
    Dark("Dark"),
}

/** Accent colour for the Oris palette (the play key, nav highlight, section labels). Ignored under Material You. */
enum class AccentSeed(val label: String, val swatch: androidx.compose.ui.graphics.Color,
                      val accent: androidx.compose.ui.graphics.Color, val onAccent: androidx.compose.ui.graphics.Color,
                      val container: androidx.compose.ui.graphics.Color, val onContainer: androidx.compose.ui.graphics.Color,
                      val lightAccent: androidx.compose.ui.graphics.Color, val lightOnAccent: androidx.compose.ui.graphics.Color,
                      val lightContainer: androidx.compose.ui.graphics.Color, val lightOnContainer: androidx.compose.ui.graphics.Color) {
    Rose("Rose", androidx.compose.ui.graphics.Color(0xFFD9A7A0), androidx.compose.ui.graphics.Color(0xFFD9A7A0), androidx.compose.ui.graphics.Color(0xFF2A1210), androidx.compose.ui.graphics.Color(0xFF3B2B29), androidx.compose.ui.graphics.Color(0xFFF3DCD8),
        androidx.compose.ui.graphics.Color(0xFFA15B53), androidx.compose.ui.graphics.Color(0xFFFFFAF7), androidx.compose.ui.graphics.Color(0xFFF4D9D3), androidx.compose.ui.graphics.Color(0xFF59302B)),
    Amber("Amber", androidx.compose.ui.graphics.Color(0xFFE3B87C), androidx.compose.ui.graphics.Color(0xFFE3B87C), androidx.compose.ui.graphics.Color(0xFF2A1A08), androidx.compose.ui.graphics.Color(0xFF3B2E1C), androidx.compose.ui.graphics.Color(0xFFF3E2C8),
        androidx.compose.ui.graphics.Color(0xFF8F5E17), androidx.compose.ui.graphics.Color(0xFFFFFAF2), androidx.compose.ui.graphics.Color(0xFFF2E0BE), androidx.compose.ui.graphics.Color(0xFF46300A)),
    Sage("Sage", androidx.compose.ui.graphics.Color(0xFFA8C2A0), androidx.compose.ui.graphics.Color(0xFFA8C2A0), androidx.compose.ui.graphics.Color(0xFF101F0E), androidx.compose.ui.graphics.Color(0xFF28321F), androidx.compose.ui.graphics.Color(0xFFD8E6CF),
        androidx.compose.ui.graphics.Color(0xFF57764A), androidx.compose.ui.graphics.Color(0xFFF7FBF3), androidx.compose.ui.graphics.Color(0xFFDCE7D2), androidx.compose.ui.graphics.Color(0xFF2A3C22)),
    Sky("Sky", androidx.compose.ui.graphics.Color(0xFF9FBFD8), androidx.compose.ui.graphics.Color(0xFF9FBFD8), androidx.compose.ui.graphics.Color(0xFF0E1B26), androidx.compose.ui.graphics.Color(0xFF223140), androidx.compose.ui.graphics.Color(0xFFD5E6F3),
        androidx.compose.ui.graphics.Color(0xFF48708F), androidx.compose.ui.graphics.Color(0xFFF5FAFE), androidx.compose.ui.graphics.Color(0xFFD6E4EF), androidx.compose.ui.graphics.Color(0xFF1E3647)),
    Violet("Violet", androidx.compose.ui.graphics.Color(0xFFC4AEDB), androidx.compose.ui.graphics.Color(0xFFC4AEDB), androidx.compose.ui.graphics.Color(0xFF1D1226), androidx.compose.ui.graphics.Color(0xFF2E2438), androidx.compose.ui.graphics.Color(0xFFE8DCF3),
        androidx.compose.ui.graphics.Color(0xFF755796), androidx.compose.ui.graphics.Color(0xFFFAF6FE), androidx.compose.ui.graphics.Color(0xFFE6DAF0), androidx.compose.ui.graphics.Color(0xFF37254A)),
    Stone("Stone", androidx.compose.ui.graphics.Color(0xFFD0CCC6), androidx.compose.ui.graphics.Color(0xFFD0CCC6), androidx.compose.ui.graphics.Color(0xFF161514), androidx.compose.ui.graphics.Color(0xFF2B2A29), androidx.compose.ui.graphics.Color(0xFFEDE9E2),
        androidx.compose.ui.graphics.Color(0xFF615A52), androidx.compose.ui.graphics.Color(0xFFFAF7F2), androidx.compose.ui.graphics.Color(0xFFE3DED6), androidx.compose.ui.graphics.Color(0xFF2F2B26)),
}

/**
 * Look settings. Values are Compose state so the theme and the player react the moment
 * they change in Settings. Stored in a small private preferences file.
 */
object AppearanceSettings {
    private var store: android.content.SharedPreferences? = null

    /** Light, dark or following the phone. */
    var themeMode by mutableStateOf(ThemeMode.System)
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
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", "") } ?: ThemeMode.System
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
            ?.putString("theme", themeMode.name)
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

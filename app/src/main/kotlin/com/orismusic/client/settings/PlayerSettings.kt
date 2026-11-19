package com.orismusic.client.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PlayKey(val label: String) { Glass("Glass"), Solid("Solid"), Plain("Plain") }
enum class PlaySize(val label: String, val play: Int, val skip: Int) { Compact("Compact", 76, 56), Regular("Regular", 88, 64), Large("Large", 100, 72) }
enum class SkipIcons(val label: String) { Skip("Skip"), Seek("Seek") }

/**
 * Player options. Compose state, saved in their own preferences file. To add an option:
 * add a field here, read and write it below, then add a row on the Player settings page.
 */
object PlayerSettings {
    private var store: android.content.SharedPreferences? = null

    var keys by mutableStateOf(PlayKey.Glass)
    var size by mutableStateOf(PlaySize.Regular)
    var icons by mutableStateOf(SkipIcons.Skip)
    var morph by mutableStateOf(true)
    var titleMotion by mutableStateOf(true)
    var haptics by mutableStateOf(true)
    var upNext by mutableStateOf(true)

    private inline fun <reified E : Enum<E>> android.content.SharedPreferences.enum(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == getString(key, "") } ?: default

    fun init(context: Context) {
        if (store != null) return
        val p = context.applicationContext.getSharedPreferences("player_look", Context.MODE_PRIVATE)
        store = p
        keys = p.enum("keys", PlayKey.Glass)
        size = p.enum("size", PlaySize.Regular)
        icons = p.enum("icons", SkipIcons.Skip)
        morph = p.getBoolean("morph", true)
        titleMotion = p.getBoolean("title_motion", true)
        haptics = p.getBoolean("haptics", true)
        upNext = p.getBoolean("up_next", true)
    }

    fun save() {
        store?.edit()
            ?.putString("keys", keys.name)?.putString("size", size.name)?.putString("icons", icons.name)
            ?.putBoolean("morph", morph)?.putBoolean("title_motion", titleMotion)
            ?.putBoolean("haptics", haptics)?.putBoolean("up_next", upNext)
            ?.apply()
    }

    fun reset() {
        keys = PlayKey.Glass; size = PlaySize.Regular; icons = SkipIcons.Skip
        morph = true; titleMotion = true; haptics = true; upNext = true
        save()
    }
}

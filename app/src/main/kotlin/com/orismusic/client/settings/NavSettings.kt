package com.orismusic.client.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NavStyle(val label: String) { Glass("Glass"), Solid("Solid"), Clear("Clear") }
enum class NavEffect(val label: String) { Stretch("Stretch"), Slide("Slide"), Dot("Dot") }
enum class NavAccent(val label: String) { Warm("Warm"), Light("Light"), Primary("Primary") }
enum class NavSize(val label: String, val icon: Int, val extra: Int) { Small("Small", 22, -4), Medium("Medium", 24, 0), Large("Large", 28, 6) }
enum class NavWidth(val label: String, val extra: Int) { Compact("Compact", 0), Roomy("Roomy", 20) }
enum class NavLift(val label: String, val bottom: Int) { Snug("Snug", 2), Normal("Normal", 8), Raised("Raised", 22) }

/**
 * Bottom bar options. Compose state, saved in their own preferences file. To add an option:
 * add a field here, read and write it below, then add a row on the Navigation settings page.
 */
object NavSettings {
    private var store: android.content.SharedPreferences? = null

    /** Tab names are gone for good: the bar is icon-only. Kept as a field so the bar code stays simple. */
    var labels by mutableStateOf(false)
    var style by mutableStateOf(NavStyle.Glass)
    var effect by mutableStateOf(NavEffect.Stretch)
    var accent by mutableStateOf(NavAccent.Warm)
    var size by mutableStateOf(NavSize.Medium)
    var width by mutableStateOf(NavWidth.Compact)
    var lift by mutableStateOf(NavLift.Normal)
    var hideOnScroll by mutableStateOf(false)
    var haptics by mutableStateOf(false)

    private inline fun <reified E : Enum<E>> android.content.SharedPreferences.enum(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == getString(key, "") } ?: default

    fun init(context: Context) {
        if (store != null) return
        val prefs = context.applicationContext.getSharedPreferences("navigation", Context.MODE_PRIVATE)
        store = prefs
        // v1.3.1 kept the label switch with the look settings; carry that choice over once.
        val old = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        labels = false
        style = prefs.enum("style", NavStyle.Glass)
        effect = prefs.enum("effect", NavEffect.Stretch)
        accent = prefs.enum("accent", NavAccent.Warm)
        size = prefs.enum("size", NavSize.Medium)
        width = prefs.enum("width", NavWidth.Compact)
        lift = prefs.enum("lift", NavLift.Normal)
        hideOnScroll = prefs.getBoolean("hide_on_scroll", false)
        haptics = prefs.getBoolean("haptics", false)
    }

    fun save() {
        store?.edit()
            ?.putBoolean("labels", labels)
            ?.putString("style", style.name)
            ?.putString("effect", effect.name)
            ?.putString("accent", accent.name)
            ?.putString("size", size.name)
            ?.putString("width", width.name)
            ?.putString("lift", lift.name)
            ?.putBoolean("hide_on_scroll", hideOnScroll)
            ?.putBoolean("haptics", haptics)
            ?.apply()
    }

    fun reset() {
        labels = false; style = NavStyle.Glass; effect = NavEffect.Stretch; accent = NavAccent.Warm
        size = NavSize.Medium; width = NavWidth.Compact; lift = NavLift.Normal
        hideOnScroll = false; haptics = false
        save()
    }
}

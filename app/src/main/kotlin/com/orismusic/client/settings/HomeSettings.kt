package com.orismusic.client.settings

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf

enum class HomeDensity(val label: String, val gap: Int) { Airy("Airy", 26), Cozy("Cozy", 18), Tight("Tight", 10) }
enum class HomeCorners(val label: String, val k: Float) {
    Sharp("Sharp", 0.3f), Soft("Soft", 1f), Round("Round", 1.8f);
    fun shape(base: Int) = androidx.compose.foundation.shape.RoundedCornerShape((base * k).dp)
}
enum class HomeCoverSize(val label: String, val k: Float) {
    Small("Small", 0.85f), Medium("Medium", 1f), Large("Large", 1.2f);
    fun dp(base: Int) = (base * k).dp
}
enum class HeroStyle(val label: String) { Deck("Deck"), Banner("Banner") }
enum class ShelfStyle(val label: String) { Row("Row"), Scatter("Scatter") }
enum class JumpStyle(val label: String) { Tiles("Tiles"), Strip("Strip") }

/** How much entrance and swipe motion Home shows. */
enum class HomeMotion(val label: String) { Calm("Calm"), Lively("Lively") }

/** Home sections that can be hidden or moved. The YouTube shelves always come last. */
enum class HomeSection(val id: String, val title: String) {
    Jump("jump", "Continue listening"),
    Recent("recent", "Recently played"),
    Most("most", "Most played"),
    Because("because", "Because you listen to"),
    History("history", "History"),
    FromArtists("from-artists", "New from your artists"),
    Artists("artist-list", "Your artists"),
}

/**
 * Home options. Compose state, saved in their own preferences file. To add an option:
 * add a field here, read and write it below, then add a row on the Home settings page.
 */
object HomeSettings {
    private var store: android.content.SharedPreferences? = null

    var minimal by mutableStateOf(true)
    var hero by mutableStateOf(true)
    var youtube by mutableStateOf(true)
    var heroStyle by mutableStateOf(HeroStyle.Deck)
    var shelfStyle by mutableStateOf(ShelfStyle.Scatter)
    var density by mutableStateOf(HomeDensity.Cozy)
    var corners by mutableStateOf(HomeCorners.Soft)
    var coverSize by mutableStateOf(HomeCoverSize.Medium)
    var jumpStyle by mutableStateOf(JumpStyle.Tiles)
    var motion by mutableStateOf(HomeMotion.Lively)
    var becauseCount by mutableStateOf(3)
    val order = mutableStateListOf<HomeSection>().apply { addAll(HomeSection.entries) }
    val hidden = mutableStateListOf<HomeSection>()

    private inline fun <reified E : Enum<E>> android.content.SharedPreferences.enum(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == getString(key, "") } ?: default

    fun init(context: Context) {
        if (store != null) return
        val p = context.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)
        store = p
        minimal = p.getBoolean("minimal", true)
        hero = p.getBoolean("hero", true)
        youtube = p.getBoolean("youtube", true)
        heroStyle = p.enum("hero_style", HeroStyle.Deck)
        shelfStyle = p.enum("shelf_style", ShelfStyle.Scatter)
        density = p.enum("density", HomeDensity.Cozy)
        corners = p.enum("corners", HomeCorners.Soft)
        coverSize = p.enum("cover_size", HomeCoverSize.Medium)
        jumpStyle = p.enum("jump_style", JumpStyle.Tiles)
        motion = p.enum("motion", HomeMotion.Lively)
        becauseCount = p.getInt("because_count", 3).coerceIn(1, 5)
        val saved = (p.getString("order", "") ?: "").split(",").mapNotNull { n -> HomeSection.entries.firstOrNull { it.name == n } }
        order.clear(); order.addAll(saved + HomeSection.entries.filter { it !in saved })
        hidden.clear()
        hidden.addAll((p.getString("hidden", "") ?: "").split(",").mapNotNull { n -> HomeSection.entries.firstOrNull { it.name == n } })
    }

    fun visible(s: HomeSection) = s !in hidden

    fun setVisible(s: HomeSection, show: Boolean) {
        if (show) hidden.remove(s) else if (s !in hidden) hidden.add(s)
        save()
    }

    fun move(s: HomeSection, by: Int) {
        val i = order.indexOf(s)
        val j = (i + by).coerceIn(0, order.lastIndex)
        if (i < 0 || i == j) return
        order.removeAt(i); order.add(j, s)
        save()
    }

    fun save() {
        store?.edit()
            ?.putBoolean("minimal", minimal)
            ?.putBoolean("hero", hero)
            ?.putBoolean("youtube", youtube)
            ?.putString("hero_style", heroStyle.name)
            ?.putString("shelf_style", shelfStyle.name)
            ?.putString("density", density.name)
            ?.putString("corners", corners.name)
            ?.putString("cover_size", coverSize.name)
            ?.putString("jump_style", jumpStyle.name)
            ?.putString("motion", motion.name)
            ?.putInt("because_count", becauseCount)
            ?.putString("order", order.joinToString(",") { it.name })
            ?.putString("hidden", hidden.joinToString(",") { it.name })
            ?.apply()
    }

    fun reset() {
        minimal = true
        shelfStyle = ShelfStyle.Scatter; hero = true; heroStyle = HeroStyle.Deck; youtube = true; density = HomeDensity.Cozy; corners = HomeCorners.Soft
        coverSize = HomeCoverSize.Medium; jumpStyle = JumpStyle.Tiles; motion = HomeMotion.Lively; becauseCount = 3
        order.clear(); order.addAll(HomeSection.entries); hidden.clear()
        save()
    }
}

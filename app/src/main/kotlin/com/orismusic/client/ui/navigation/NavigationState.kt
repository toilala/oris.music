package com.orismusic.client.ui.navigation

import java.io.Serializable

/** One root and a small tab history, with the player above the current screen. */
data class NavigationState(
    val tabs: List<Int> = listOf(0),
    val playerOpen: Boolean = false,
) : Serializable {
    val currentTab: Int get() = tabs.last()
    val canGoBack: Boolean get() = playerOpen || tabs.size > 1
    fun select(tab: Int): NavigationState {
        require(tab in 0..3)
        val existing = tabs.indexOf(tab)
        return copy(tabs = if (existing >= 0) tabs.take(existing + 1) else tabs + tab,
            playerOpen = false)
    }
    fun openPlayer() = copy(playerOpen = true)
    fun closePlayer() = copy(playerOpen = false)
    fun back(): NavigationState = when {
        playerOpen -> closePlayer()
        tabs.size > 1 -> copy(tabs = tabs.dropLast(1))
        else -> this
    }
}

/**
 * Song taps ask here whether the full player should open. MainActivity installs the
 * opener; screens stay free of navigation plumbing. Follows the player's TapOpen setting.
 */
object PlayerLaunch {
    var request: (() -> Unit)? = null

    /** Set by MainActivity when the activity was recreated after process death. The first
     *  NowPlayingScreen composition consumes it: only that restored open may auto-collapse
     *  when the queue comes back empty; a player the user opened by hand stays open. */
    var activityRecreated = false
    fun consumeRecreatedFlag(): Boolean { val v = activityRecreated; activityRecreated = false; return v }

    fun maybeOpen() {
        if (com.orismusic.client.settings.PlayerSettings.tapOpen == com.orismusic.client.settings.TapOpen.Fullscreen) request?.invoke()
    }
}

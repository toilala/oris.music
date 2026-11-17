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

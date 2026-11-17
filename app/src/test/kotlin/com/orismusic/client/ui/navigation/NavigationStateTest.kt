package com.orismusic.client.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationStateTest {
    @Test fun rootAllowsSystemExit() { assertFalse(NavigationState().canGoBack) }
    @Test fun queueBackReturnsHome() {
        val state = NavigationState().select(3)
        assertTrue(state.canGoBack)
        assertEquals(NavigationState(), state.back())
    }
    @Test fun playerBackKeepsUnderlyingQueue() {
        val queue = NavigationState().select(3)
        assertEquals(queue, queue.openPlayer().back())
    }
    @Test fun nestedTabsUnwindInOrder() {
        val state = NavigationState().select(1).select(3).openPlayer()
        assertEquals(3, state.back().currentTab)
        assertEquals(1, state.back().back().currentTab)
        assertEquals(0, state.back().back().back().currentTab)
    }
    @Test fun reselectingEarlierTabPopsRatherThanLoops() {
        assertEquals(listOf(0, 1), NavigationState().select(1).select(3).select(1).tabs)
    }
    @Test fun selectingHomeClearsHistory() {
        assertFalse(NavigationState().select(1).select(3).select(0).canGoBack)
    }
    @Test fun repeatedBackAtRootIsStable() {
        assertEquals(NavigationState(), NavigationState().back().back())
    }
}

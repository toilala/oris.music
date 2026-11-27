package com.orismusic.client.ui

import com.orismusic.client.ui.components.EntranceSeen
import org.junit.Assert.*
import org.junit.Test

class EntranceSeenTest {
    @Test fun markedRowsAreRemembered() {
        val seen = EntranceSeen()
        seen.mark(7, seen.epoch)
        assertTrue(seen.has(7))
        assertFalse(seen.has(8))
    }

    @Test fun clearingForgetsRows() {
        val seen = EntranceSeen()
        seen.mark(7, seen.epoch)
        seen.clear()
        assertFalse(seen.has(7))
    }

    @Test fun staleMarkAfterClearIsDropped() {
        val seen = EntranceSeen()
        val before = seen.epoch
        seen.clear()
        seen.mark(7, before)
        assertFalse(seen.has(7))
    }

    @Test fun newScopeStartsEmptyAndSameScopeKeepsRows() {
        val seen = EntranceSeen()
        seen.enter("a")
        seen.mark(1, seen.epoch)
        seen.enter("a")
        assertTrue(seen.has(1))
        seen.enter("b")
        assertFalse(seen.has(1))
    }
}

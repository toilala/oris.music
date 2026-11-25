package com.orismusic.client.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The skip-walk guard pauses a dying queue at the limit and starts clean after a
 *  success or any explicit user action. */
class ConsecutiveFailureGuardTest {

    @Test
    fun `streak reaches the limit exactly at the limit`() {
        val guard = ConsecutiveFailureGuard(limit = 3)
        assertFalse(guard.onSongGaveUp())
        assertFalse(guard.onSongGaveUp())
        assertTrue(guard.onSongGaveUp())
        assertEquals(3, guard.streak)
    }

    @Test
    fun `successful playback resets the streak`() {
        val guard = ConsecutiveFailureGuard(limit = 3)
        guard.onSongGaveUp()
        guard.onSongGaveUp()
        guard.onPlaybackStarted()
        assertEquals(0, guard.streak)
        assertFalse(guard.onSongGaveUp())
        assertFalse(guard.onSongGaveUp())
        assertTrue(guard.onSongGaveUp())
    }

    @Test
    fun `user action resets the streak so a fresh queue gets the full allowance`() {
        val guard = ConsecutiveFailureGuard(limit = 3)
        repeat(3) { guard.onSongGaveUp() }
        guard.onUserAction()
        assertEquals(0, guard.streak)
        assertFalse(guard.onSongGaveUp())
        assertFalse(guard.onSongGaveUp())
        assertTrue(guard.onSongGaveUp())
    }

    @Test
    fun `streak keeps counting past the limit until something resets it`() {
        val guard = ConsecutiveFailureGuard(limit = 2)
        assertTrue(guard.onSongGaveUp() || guard.onSongGaveUp())
        assertTrue(guard.streak >= 2)
        assertTrue(guard.onSongGaveUp())
    }

    @Test
    fun `a queue with somewhere left to go walks and feeds the streak`() {
        val guard = ConsecutiveFailureGuard(limit = 3)
        assertEquals(RetriesExhausted.SkipToNext, retriesExhausted(pinned = false, hasNext = true, guard = guard))
        assertEquals(RetriesExhausted.SkipToNext, retriesExhausted(pinned = false, hasNext = true, guard = guard))
        assertEquals(RetriesExhausted.PauseForStreak, retriesExhausted(pinned = false, hasNext = true, guard = guard))
        assertEquals(3, guard.streak)
    }

    @Test
    fun `a trailing song stops in place without touching the streak`() {
        val guard = ConsecutiveFailureGuard(limit = 3)
        assertEquals(RetriesExhausted.StopInPlace, retriesExhausted(pinned = false, hasNext = false, guard = guard))
        assertEquals(0, guard.streak)
    }

    @Test
    fun `the pinned first song stops in place without touching the streak`() {
        val guard = ConsecutiveFailureGuard(limit = 3)
        assertEquals(RetriesExhausted.StopInPlace, retriesExhausted(pinned = true, hasNext = true, guard = guard))
        assertEquals(0, guard.streak)
    }

    @Test
    fun `the terminal stop message says retrying and never claims a streak or a count`() {
        val message = terminalStopMessage()
        assertTrue(message.contains("after retrying"))
        assertTrue(message.contains("Tap play"))
        assertFalse(message.contains("in a row"))
        assertFalse(message.any { it.isDigit() })
    }

    @Test
    fun `the streak pause message keeps its own wording`() {
        assertEquals("Playback paused: 3 songs failed in a row. Tap play to try again.", streakPauseMessage(3))
    }
}

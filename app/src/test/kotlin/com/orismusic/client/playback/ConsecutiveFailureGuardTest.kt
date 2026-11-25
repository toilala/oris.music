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
}

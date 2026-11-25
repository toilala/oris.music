package com.orismusic.client.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheStatusTest {

    @Test
    fun `unset content length is unknown`() {
        assertEquals(OfflineStatus.UNKNOWN, cacheStatusFor(-1L, 0L))
    }

    @Test
    fun `zero content length is unknown`() {
        assertEquals(OfflineStatus.UNKNOWN, cacheStatusFor(0L, 0L))
    }

    @Test
    fun `negative length stays unknown even with cached bytes`() {
        assertEquals(OfflineStatus.UNKNOWN, cacheStatusFor(-1L, 5000L))
    }

    @Test
    fun `no cached bytes is partial, never ready`() {
        assertEquals(OfflineStatus.PARTIAL, cacheStatusFor(1000L, 0L))
    }

    @Test
    fun `one byte short is still partial`() {
        assertEquals(OfflineStatus.PARTIAL, cacheStatusFor(1000L, 999L))
    }

    @Test
    fun `exact coverage is ready`() {
        assertEquals(OfflineStatus.READY, cacheStatusFor(1000L, 1000L))
    }

    @Test
    fun `overcoverage counts as ready`() {
        assertEquals(OfflineStatus.READY, cacheStatusFor(1000L, 1001L))
    }
}

package com.orismusic.client.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineStatusTest {

    @Test
    fun `downloading wins over every other state`() {
        assertEquals(OfflineStatus.DOWNLOADING,
            offlineRowStatus("a", saved = true, busy = setOf("a"), failed = setOf("a"),
                validated = mapOf("a" to OfflineStatus.READY)))
    }

    @Test
    fun `failed shows when a finished attempt failed`() {
        assertEquals(OfflineStatus.FAILED,
            offlineRowStatus("a", saved = false, busy = emptySet(), failed = setOf("a"),
                validated = emptyMap()))
    }

    @Test
    fun `not saved and not active means no status`() {
        assertNull(offlineRowStatus("a", saved = false, busy = emptySet(), failed = emptySet(),
            validated = mapOf("a" to OfflineStatus.READY)))
    }

    @Test
    fun `saved but not yet validated is unknown, never ready`() {
        assertEquals(OfflineStatus.UNKNOWN,
            offlineRowStatus("a", saved = true, busy = emptySet(), failed = emptySet(),
                validated = emptyMap()))
    }

    @Test
    fun `validated states pass through`() {
        OfflineStatus.entries.forEach { state ->
            assertEquals(state,
                offlineRowStatus("a", saved = true, busy = emptySet(), failed = emptySet(),
                    validated = mapOf("a" to state)))
        }
    }

    @Test
    fun `partial content is never reported ready`() {
        // The row helper trusts the validation map; this guards the wiring that feeds it:
        // a PARTIAL validation must survive to the row untouched.
        assertEquals(OfflineStatus.PARTIAL,
            offlineRowStatus("a", saved = true, busy = emptySet(), failed = emptySet(),
                validated = mapOf("a" to OfflineStatus.PARTIAL)))
    }
}

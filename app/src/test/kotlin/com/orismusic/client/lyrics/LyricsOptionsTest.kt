package com.orismusic.client.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsOptionsTest {
    @Test fun registryHasOnlyEchosNineSources() {
        assertEquals(setOf("youly", "pax", "better", "unison", "simpmusic", "lrclib", "kugou", "youtube_subtitles", "youtube_music"), LyricsSources.all.map { it.id }.toSet())
    }
    @Test fun savedOrderWinsAndNewProvidersAreAppended() {
        val restored = restoreLyricsOptions(listOf("better", "lrclib"), emptySet())
        assertEquals(listOf("better", "lrclib"), restored.order.take(2))
        assertEquals(LyricsSources.all.size, restored.order.size)
    }
    @Test fun unknownAndDuplicateIdsNeverBecomeRequests() {
        val restored = restoreLyricsOptions(listOf("bogus", "lrclib", "lrclib"), emptySet())
        assertEquals("lrclib", restored.order.first())
        assertFalse("bogus" in restored.order)
        assertEquals(restored.order.size, restored.order.distinct().size)
    }
    @Test fun disabledProviderIsNotAnAutomaticOrManualCandidate() {
        val restored = restoreLyricsOptions(emptyList(), setOf("lrclib"))
        assertFalse(restored.enabled().any { it.id == "lrclib" })
        assertFalse(restored.enabled().any { it.id == "simpmusic" })
    }
    @Test fun explicitlyEmptyDisabledSetIsNotReplacedWithDefaults() {
        assertTrue(restoreLyricsOptions(emptyList(), emptySet()).enabled().any { it.id == "pax" })
    }
    @Test fun movingAndTogglingPreserveOtherChoices() {
        val initial = restoreLyricsOptions(emptyList(), null)
        val next = initial.move("better", -1).enable("lrclib", false)
        assertEquals(initial.order.indexOf("better")-1, next.order.indexOf("better"))
        assertTrue("lrclib" in next.disabled)
        assertEquals(initial.disabled, next.enable("lrclib", true).disabled)
    }
}

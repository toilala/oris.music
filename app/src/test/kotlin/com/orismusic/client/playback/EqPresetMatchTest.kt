package com.orismusic.client.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Preset recognition derives the selected chip from the current band values, so the UI
 *  never trusts a stored preset name; a mix that matches nothing reads as Custom. */
class EqPresetMatchTest {

    private val presets = EqualizerController.presets

    @Test
    fun `applied preset levels are recognized as that preset`() {
        for ((name, shape) in presets) {
            val levels = presetLevels(shape, 5, -1500, 1500)
            assertEquals(name, eqPresetNameFor(presets, levels, -1500, 1500))
        }
    }

    @Test
    fun `bass boost on five bands matches the levels the device persists`() {
        assertEquals(listOf(1500, 1050, 0, 0, 0), presetLevels(presets.getValue("Bass boost"), 5, -1500, 1500))
    }

    @Test
    fun `preset shapes stretch across a different band count`() {
        val levels = presetLevels(presets.getValue("Bass boost"), 3, -1500, 1500)
        assertEquals("Bass boost", eqPresetNameFor(presets, levels, -1500, 1500))
    }

    @Test
    fun `zero levels read as flat`() {
        assertEquals("Flat", eqPresetNameFor(presets, List(5) { 0 }, -1500, 1500))
    }

    @Test
    fun `a hand-tweaked mix matches nothing and becomes custom`() {
        assertNull(eqPresetNameFor(presets, listOf(1500, 1050, 0, 0, 1), -1500, 1500))
    }

    @Test
    fun `empty levels match nothing`() {
        assertNull(eqPresetNameFor(presets, emptyList(), -1500, 1500))
    }

    @Test
    fun `negative levels respect the lower range`() {
        val vocal = presetLevels(presets.getValue("Vocal"), 5, -1500, 1500)
        assertEquals(-600, vocal[0])
        assertEquals("Vocal", eqPresetNameFor(presets, vocal, -1500, 1500))
    }
}

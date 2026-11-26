package com.orismusic.client.playback

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the production write/read coordination in EqualizerController through the
 * EqPlatform seam: intended settings live in prefs, published state is only what the
 * platform verified on read-back, and a failed read-back keeps the last verified
 * values explicitly stale instead of republishing intent as fact. Robolectric backs
 * android.util.Log, which the refusal/clamp recording paths write to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class EqualizerControllerTest {

    /** Scripted platform equalizer: writable flags decide which writes get refused,
     *  whether reads throw, and whether writes come back clamped. */
    private class FakeEqPlatform(
        override val bandCount: Int = 5,
        override val minLevel: Int = -1500,
        override val maxLevel: Int = 1500,
    ) : EqPlatform {
        var enabled = false
        val levels = IntArray(bandCount)
        var refuseEnabledWrites = false
        /** Per-band clamp floor: a write below the band's floor comes back clamped,
         *  like the emulator's audio effect (observed on-device: 1050 -> 1100). */
        var clampFloors: Map<Int, Int> = emptyMap()
        /** Per-band clamp cap: a write above the band's cap comes back clamped. */
        var clampCaps: Map<Int, Int> = emptyMap()
        var throwOnReads = false
        var released = false

        override fun centerFreq(band: Int) = 60000 * (band + 1)
        override fun readEnabled(): Boolean {
            if (throwOnReads) throw RuntimeException("platform read failed")
            return enabled
        }
        override fun writeEnabled(on: Boolean): Boolean {
            if (refuseEnabledWrites) return false
            enabled = on
            return true
        }
        override fun readBandLevel(band: Int): Int {
            if (throwOnReads) throw RuntimeException("platform read failed")
            return levels[band]
        }
        override fun writeBandLevel(band: Int, level: Int) {
            val floor = clampFloors[band]
            val cap = clampCaps[band]
            levels[band] = when {
                floor != null && level < floor -> floor
                cap != null && level > cap -> cap
                else -> level
            }
        }
        override fun release() { released = true }
    }

    /** Minimal disk-free SharedPreferences; unused members throw, so the test proves
     *  exactly which calls the controller makes. */
    private class FakePrefs : SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defValue: String?): String? =
            data[key] as? String ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                data[key] = value; return this
            }
            override fun apply() {}
            override fun commit(): Boolean = true
            override fun putStringSet(p0: String, p1: MutableSet<String>?): SharedPreferences.Editor = TODO()
            override fun putInt(p0: String, p1: Int): SharedPreferences.Editor = TODO()
            override fun putLong(p0: String, p1: Long): SharedPreferences.Editor = TODO()
            override fun putFloat(p0: String, p1: Float): SharedPreferences.Editor = TODO()
            override fun remove(p0: String): SharedPreferences.Editor = TODO()
            override fun clear(): SharedPreferences.Editor = TODO()
        }
        override fun getAll(): MutableMap<String, *> = TODO()
        override fun getStringSet(p0: String, p1: MutableSet<String>?): MutableSet<String>? = TODO()
        override fun getInt(p0: String, p1: Int): Int = TODO()
        override fun getLong(p0: String, p1: Long): Long = TODO()
        override fun getFloat(p0: String, p1: Float): Float = TODO()
        override fun contains(p0: String): Boolean = TODO()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = TODO()
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = TODO()
    }

    private lateinit var platform: FakeEqPlatform
    private lateinit var prefs: FakePrefs

    @Before
    fun setUp() {
        platform = FakeEqPlatform()
        prefs = FakePrefs()
        val created = platform
        EqualizerController.platformFactory = { session -> if (session == 7) created else null }
        EqualizerController.prefs = prefs
        // Clean slate: session 0 releases any previous effect and publishes unavailable.
        EqualizerController.attach(0)
    }

    @Test
    fun `attach with no session reports unavailable and never touches saved intent`() {
        prefs.data["enabled"] = true
        prefs.data["levels"] = "1500,0,0,0,0"
        EqualizerController.attach(0)
        val s = EqualizerController.state.value
        assertFalse(s.available)
        assertEquals(EqUnavailable.NoSession, s.unavailableReason)
        assertEquals(true, prefs.data["enabled"])
        assertEquals("1500,0,0,0,0", prefs.data["levels"])
    }

    @Test
    fun `attach applies saved intent and publishes platform-verified state`() {
        prefs.data["enabled"] = true
        prefs.data["levels"] = "1500,1050,0,0,0"
        EqualizerController.attach(7)
        val s = EqualizerController.state.value
        assertTrue(s.available)
        assertTrue(s.enabled)
        assertEquals(listOf(1500, 1050, 0, 0, 0), s.levels)
        assertFalse(s.stale)
    }

    @Test
    fun `enabled write refusal never publishes the refused intent and prefs keep the user intent`() {
        EqualizerController.attach(7)
        assertFalse(EqualizerController.state.value.enabled)
        platform.refuseEnabledWrites = true
        EqualizerController.setEnabled(true)
        val s = EqualizerController.state.value
        // The platform's fresh read-back says off: published as verified-off, and the
        // refused intent (on) appears nowhere in state. The refusal itself is logged.
        assertFalse(s.enabled)
        assertFalse(s.stale)
        assertEquals(true, prefs.data["enabled"]) // the user's intent is still saved
        assertFalse(platform.enabled) // the write genuinely did not apply
    }

    @Test
    fun `band clamp publishes the observed clamped level while prefs keep the intent`() {
        EqualizerController.attach(7)
        platform.clampFloors = mapOf(0 to 1000)
        EqualizerController.setLevel(0, 500)
        val s = EqualizerController.state.value
        assertEquals(1000, s.levels[0]) // observed clamp, not the intended 500
        assertFalse(s.stale)
        assertEquals("500,0,0,0,0", prefs.data["levels"]) // intent preserved
    }

    @Test
    fun `read exception retains last verified values marked stale`() {
        prefs.data["enabled"] = false
        EqualizerController.attach(7)
        platform.throwOnReads = true
        EqualizerController.setEnabled(true)
        val s = EqualizerController.state.value
        assertFalse(s.enabled) // last verified, not the intended new value
        assertTrue(s.stale)
    }

    @Test
    fun `attach read failure reports unavailable instead of publishing intent`() {
        prefs.data["enabled"] = true
        prefs.data["levels"] = "1500,0,0,0,0"
        platform.throwOnReads = true
        EqualizerController.attach(7)
        val s = EqualizerController.state.value
        assertFalse(s.available)
        assertEquals(EqUnavailable.InitFailed, s.unavailableReason)
    }

    @Test
    fun `preset loop never folds a clamped band back into the saved intent`() {
        // The runtime repro (run 45719436428): Bass boost with the platform clamping
        // band 1 (1050 -> 1100) must still persist the full intended vector while the
        // published state stays the platform's observed answer.
        platform.clampFloors = mapOf(1 to 1100)
        EqualizerController.attach(7)
        EqualizerController.applyPreset(EqualizerController.presets.getValue("Bass boost"))
        val s = EqualizerController.state.value
        assertEquals("1500,1050,0,0,0", prefs.data["levels"])
        assertEquals(listOf(1500, 1100, 0, 0, 0), s.levels)
        assertEquals(1100, platform.levels[1]) // the clamp genuinely happened
        assertFalse(s.stale)
    }

    @Test
    fun `preset loop with two clamped bands persists every intended level`() {
        platform.clampCaps = mapOf(0 to 1400)
        platform.clampFloors = mapOf(1 to 1100)
        EqualizerController.attach(7)
        EqualizerController.applyPreset(EqualizerController.presets.getValue("Bass boost"))
        val s = EqualizerController.state.value
        // The clamps genuinely happened at the platform, on the intended writes.
        assertEquals(1400, platform.levels[0])
        assertEquals(1100, platform.levels[1])
        assertEquals("1500,1050,0,0,0", prefs.data["levels"])
        assertEquals(listOf(1400, 1100, 0, 0, 0), s.levels)
    }

    @Test
    fun `slider change after a clamp does not leak the neighbor's observed level into intent`() {
        prefs.data["levels"] = "1500,1050,0,0,0"
        platform.clampFloors = mapOf(1 to 1100)
        EqualizerController.attach(7)
        assertEquals(1100, EqualizerController.state.value.levels[1]) // observed clamp published
        EqualizerController.setLevel(0, 1200)
        assertEquals("1200,1050,0,0,0", prefs.data["levels"])
    }

    @Test
    fun `enable alone persists no levels key, absent default is not a persistence failure`() {
        EqualizerController.attach(7)
        EqualizerController.setEnabled(true)
        assertEquals(true, prefs.data["enabled"])
        assertFalse(prefs.data.containsKey("levels"))
        assertTrue(EqualizerController.state.value.enabled)
    }

    @Test
    fun `first edit with no saved levels builds on the zero intent, not a platform-altered default`() {
        // The device cannot represent zero on band 0, so read-back publishes its floor.
        // A first slider change must not adopt that observation as neighboring intent.
        platform.clampFloors = mapOf(0 to 300)
        EqualizerController.attach(7)
        assertEquals(300, EqualizerController.state.value.levels[0])
        assertFalse(prefs.data.containsKey("levels"))
        EqualizerController.setLevel(2, 500)
        assertEquals("0,0,500,0,0", prefs.data["levels"])
    }

    @Test
    fun `malformed and short saved vectors read with the same defaults attach applies`() {
        // An unparseable entry drops out exactly as attach's read treats it.
        prefs.data["levels"] = "1500,bogus"
        EqualizerController.attach(7)
        assertEquals(listOf(1500, 0, 0, 0, 0), EqualizerController.state.value.levels)
        EqualizerController.setLevel(1, 700)
        assertEquals("1500,700,0,0,0", prefs.data["levels"])
        // A short vector defaults the missing bands, again exactly as attach.
        prefs.data["levels"] = "1500,1050"
        EqualizerController.setLevel(3, 200)
        assertEquals("1500,1050,0,200,0", prefs.data["levels"])
    }

    @Test
    fun `band reads stay inside the platform count and range`() {
        platform = FakeEqPlatform(bandCount = 3)
        val created = platform
        EqualizerController.platformFactory = { created }
        prefs.data["levels"] = "1500,1050,700,400,200" // five saved, three on device
        EqualizerController.attach(7)
        val s = EqualizerController.state.value
        assertTrue(s.available)
        assertEquals(3, s.levels.size)
        assertEquals(listOf(1500, 1050, 700), s.levels)
        // A platform answer outside the supported range is coerced, never published raw.
        platform.levels[1] = 9999
        EqualizerController.setLevel(0, 100)
        assertEquals(1500, EqualizerController.state.value.levels[1])
    }
}

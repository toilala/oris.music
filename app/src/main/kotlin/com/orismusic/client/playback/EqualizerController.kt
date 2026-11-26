package com.orismusic.client.playback

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the equalizer cannot be used right now. Only distinctions the attach path can
 *  actually establish: there is no usable audio session, or the effect failed to start
 *  (which can be transient, so it is never reported as unsupported hardware). An effect
 *  that opens but never answers a read-back also lands on InitFailed. */
enum class EqUnavailable { NoSession, InitFailed }

fun eqUnavailableReason(session: Int, effectCreated: Boolean): EqUnavailable? =
    if (session == 0) EqUnavailable.NoSession
    else if (!effectCreated) EqUnavailable.InitFailed
    else null

/** What the settings screen needs to draw the equalizer. [enabled] and [levels] are
 *  platform-VERIFIED values: they were read back from the effect after the last write,
 *  never the intended values republished. When the latest read-back failed, [stale] is
 *  true and the values are the last verified ones, explicitly not current. */
data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val centerHz: List<Int> = emptyList(),
    val levels: List<Int> = emptyList(),
    val unavailableReason: EqUnavailable? = null,
    val stale: Boolean = false,
)

/** Levels a preset shape produces for a device band layout, stretched across however many
 *  bands the device has. Used both to apply a preset and to recognize it afterwards. */
fun presetLevels(shape: List<Float>, bandCount: Int, minLevel: Int, maxLevel: Int): List<Int> =
    List(bandCount) { i ->
        val pos = if (bandCount == 1) 0f else i * (shape.size - 1f) / (bandCount - 1f)
        val lo = pos.toInt().coerceAtMost(shape.size - 1)
        val hi = (lo + 1).coerceAtMost(shape.size - 1)
        val v = shape[lo] + (shape[hi] - shape[lo]) * (pos - lo)
        if (v >= 0) (v * maxLevel).toInt() else (-v * minLevel).toInt()
    }

/** The preset whose computed levels match the current ones exactly, or null when the mix
 *  no longer matches any preset (a custom mix). */
fun eqPresetNameFor(presets: Map<String, List<Float>>, levels: List<Int>, minLevel: Int, maxLevel: Int): String? =
    if (levels.isEmpty()) null
    else presets.entries.firstOrNull { (_, shape) ->
        presetLevels(shape, levels.size, minLevel, maxLevel) == levels
    }?.key

/** Thin seam over the platform equalizer so the write/read coordination is testable
 *  without a device: every write reports its return code, every observed value comes
 *  from an explicit read, and a failed read throws instead of guessing. */
internal interface EqPlatform {
    val bandCount: Int
    val minLevel: Int
    val maxLevel: Int
    fun centerFreq(band: Int): Int
    fun readEnabled(): Boolean
    /** The platform return code: true only when the effect accepted the write. */
    fun writeEnabled(on: Boolean): Boolean
    fun readBandLevel(band: Int): Int
    /** This SDK exposes no return code on band writes: an exception is the refusal
     *  signal, and the read-back is the acceptance check. */
    fun writeBandLevel(band: Int, level: Int)
    fun release()
}

/** The real android.media.audiofx.Equalizer behind the seam. Return codes are checked,
 *  never assumed: a rejected write reads as rejected, not as success. */
private class AndroidEqPlatform(private val effect: Equalizer) : EqPlatform {
    override val bandCount get() = effect.numberOfBands.toInt()
    override val minLevel get() = effect.bandLevelRange[0].toInt()
    override val maxLevel get() = effect.bandLevelRange[1].toInt()
    override fun centerFreq(band: Int) = effect.getCenterFreq(band.toShort())
    override fun readEnabled() = effect.enabled
    override fun writeEnabled(on: Boolean) = effect.setEnabled(on) == AudioEffect.SUCCESS
    override fun readBandLevel(band: Int) = effect.getBandLevel(band.toShort()).toInt()
    override fun writeBandLevel(band: Int, level: Int) =
        effect.setBandLevel(band.toShort(), level.toShort())
    override fun release() = effect.release()
}

/** Wraps the system equalizer on the player's audio session and remembers the settings.
 *  Saved preferences are the user's INTENT: they change on user actions only, never
 *  because a device rejected a write or had no session. Published state is the
 *  platform's OBSERVED answer: every value in it came from a read-back. */
object EqualizerController {
    private var eq: EqPlatform? = null
    internal var prefs: android.content.SharedPreferences? = null
    internal var platformFactory: (Int) -> EqPlatform? = { session ->
        runCatching { AndroidEqPlatform(Equalizer(0, session)) }
            .onFailure { android.util.Log.w("OrisEQ", "equalizer init failed on session $session", it) }
            .getOrNull()
    }
    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    val presets: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass boost" to listOf(1f, 0.7f, 0f, 0f, 0f),
        "Treble boost" to listOf(0f, 0f, 0f, 0.6f, 1f),
        "Vocal" to listOf(-0.4f, 0f, 0.7f, 0.5f, -0.2f),
        "Rock" to listOf(0.7f, 0.3f, -0.3f, 0.4f, 0.8f),
    )

    /** Hooks the equalizer to the player now and whenever its audio session changes. */
    fun watch(context: Context, player: Player, exo: androidx.media3.exoplayer.ExoPlayer) {
        prefs = context.applicationContext.getSharedPreferences("oris-eq", Context.MODE_PRIVATE)
        attach(exo.audioSessionId)
        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = attach(audioSessionId)
        })
    }

    internal fun attach(session: Int) {
        runCatching { eq?.release() }
        eq = null
        if (session == 0) {
            _state.value = EqState(unavailableReason = EqUnavailable.NoSession)
            return
        }
        val created = platformFactory(session)
        if (created == null) {
            _state.value = EqState(unavailableReason = EqUnavailable.InitFailed)
            return
        }
        // The platform's own layout, read defensively: nothing here is trusted to succeed.
        val layout = runCatching { Triple(created.bandCount, created.minLevel, created.maxLevel) }.getOrNull()
        if (layout == null || layout.first <= 0) {
            android.util.Log.w("OrisEQ", "equalizer layout unreadable on session $session")
            runCatching { created.release() }
            _state.value = EqState(unavailableReason = EqUnavailable.InitFailed)
            return
        }
        val (count, min, max) = layout
        eq = created
        // Saved intent is re-applied to the device, checked per write; the device's
        // answer never rewrites the intent.
        val wantLevels = savedIntent(count, min, max)
        val wantOn = prefs?.getBoolean("enabled", false) ?: false
        wantLevels.forEachIndexed { i, level -> applyBandWrite(created, i, level) }
        applyEnabledWrite(created, wantOn)
        val verified = readBack(created, count, min, max)
        if (verified == null) {
            // The effect opened but never answered a read: nothing verified to publish.
            android.util.Log.w("OrisEQ", "equalizer on session $session never answered a read-back")
            _state.value = EqState(unavailableReason = EqUnavailable.InitFailed)
            return
        }
        _state.value = EqState(
            available = true, enabled = verified.first,
            minLevel = min, maxLevel = max,
            centerHz = List(count) { i -> runCatching { created.centerFreq(i) / 1000 }.getOrDefault(0) },
            levels = verified.second,
        )
    }

    /** The saved band intent for `count` bands, parsed and defaulted the same way
     *  everywhere: a missing key, unparseable entries and a short vector all fall back
     *  to the zero/default intent; every value coerces into the platform range. */
    private fun savedIntent(count: Int, min: Int, max: Int): List<Int> {
        val saved = prefs?.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return List(count) { i -> (saved.getOrNull(i) ?: 0).coerceIn(min, max) }
    }

    /** One intent write, checked where the SDK allows checking: setEnabled returns a
     *  status code, setBandLevel returns nothing, so a band write is checked by the
     *  exception it throws and the read-back that follows. Rejected writes and clamped
     *  values are logged with their actual numbers; what state later publishes is the
     *  platform's answer, never the intent. */
    private fun applyBandWrite(e: EqPlatform, band: Int, level: Int) {
        val wrote = runCatching { e.writeBandLevel(band, level) }
        if (wrote.isFailure) {
            android.util.Log.w("OrisEQ", "band $band level $level rejected by the platform", wrote.exceptionOrNull())
            return
        }
        val observed = runCatching { e.readBandLevel(band) }.getOrNull() ?: return
        if (observed != level) android.util.Log.w("OrisEQ", "band $band clamped by the platform: $level -> $observed")
    }

    private fun applyEnabledWrite(e: EqPlatform, on: Boolean) {
        val accepted = runCatching { e.writeEnabled(on) }.getOrDefault(false)
        if (!accepted) {
            android.util.Log.w("OrisEQ", "enabled=$on rejected by the platform")
            return
        }
        val observed = runCatching { e.readEnabled() }.getOrNull() ?: return
        if (observed != on) android.util.Log.w("OrisEQ", "enabled clamped by the platform: $on -> $observed")
    }

    /** Verified platform state: enabled plus every band, each read against the supported
     *  count and coerced into the supported range. Null when any read fails, because a
     *  half-read snapshot is not verified state. */
    private fun readBack(e: EqPlatform, count: Int, min: Int, max: Int): Pair<Boolean, List<Int>>? =
        runCatching {
            val on = e.readEnabled()
            val levels = List(count) { i -> e.readBandLevel(i).coerceIn(min, max) }
            on to levels
        }.getOrNull()

    fun setEnabled(on: Boolean) {
        // Intent follows the user's action; it is not the device's answer.
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        val s = _state.value
        val e = eq ?: return
        if (!s.available) return
        applyEnabledWrite(e, on)
        val verified = readBack(e, s.levels.size, s.minLevel, s.maxLevel)
        _state.value = if (verified != null) {
            s.copy(enabled = verified.first, levels = verified.second, stale = false)
        } else {
            // Last verified values stay published, explicitly marked not current.
            s.copy(stale = true)
        }
    }

    fun setLevel(band: Int, level: Int) {
        val s = _state.value
        if (band !in s.levels.indices) return
        // Intent extends the saved intent, never the published observed state: a
        // clamped neighbor is the platform's answer, not the user's request.
        val intent = savedIntent(s.levels.size, s.minLevel, s.maxLevel).toMutableList().also { it[band] = level }
        prefs?.edit()?.putString("levels", intent.joinToString(","))?.apply()
        val e = eq ?: return
        if (!s.available) return
        applyBandWrite(e, band, level)
        val verified = readBack(e, s.levels.size, s.minLevel, s.maxLevel)
        _state.value = if (verified != null) {
            s.copy(enabled = verified.first, levels = verified.second, stale = false)
        } else {
            s.copy(stale = true)
        }
    }

    /** Preset shapes are stretched across however many bands the device has. */
    fun applyPreset(shape: List<Float>) {
        val s = _state.value
        if (!s.available || s.levels.isEmpty()) return
        presetLevels(shape, s.levels.size, s.minLevel, s.maxLevel).forEachIndexed { i, level ->
            setLevel(i, level)
        }
    }
}

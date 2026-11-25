package com.orismusic.client.playback

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the equalizer cannot be used right now. Only distinctions the attach path can
 *  actually establish: there is no usable audio session, or the effect failed to start
 *  (which can be transient, so it is never reported as unsupported hardware). */
enum class EqUnavailable { NoSession, InitFailed }

fun eqUnavailableReason(session: Int, effectCreated: Boolean): EqUnavailable? =
    if (session == 0) EqUnavailable.NoSession
    else if (!effectCreated) EqUnavailable.InitFailed
    else null

/** What the settings screen needs to draw the equalizer. */
data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val centerHz: List<Int> = emptyList(),
    val levels: List<Int> = emptyList(),
    val unavailableReason: EqUnavailable? = null,
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

/** Wraps the system equalizer on the player's audio session and remembers the settings. */
object EqualizerController {
    private var eq: Equalizer? = null
    private var prefs: android.content.SharedPreferences? = null
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

    private fun attach(session: Int) {
        runCatching { eq?.release() }
        eq = null
        val created = if (session == 0) null else runCatching { Equalizer(0, session) }
            .onFailure { android.util.Log.w("OrisEQ", "equalizer init failed on session $session", it) }
            .getOrNull()
        val reason = eqUnavailableReason(session, created != null)
        if (reason != null || created == null) {
            _state.value = EqState(unavailableReason = reason ?: EqUnavailable.InitFailed)
            return
        }
        val e = created
        eq = e
        val range = e.bandLevelRange
        val count = e.numberOfBands.toInt()
        val saved = prefs?.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
        val levels = List(count) { i -> (saved.getOrNull(i) ?: 0).coerceIn(range[0].toInt(), range[1].toInt()) }
        val on = prefs?.getBoolean("enabled", false) ?: false
        runCatching {
            levels.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.toShort()) }
            e.enabled = on
        }
        _state.value = EqState(
            available = true, enabled = on,
            minLevel = range[0].toInt(), maxLevel = range[1].toInt(),
            centerHz = List(count) { e.getCenterFreq(it.toShort()) / 1000 },
            levels = levels,
        )
    }

    fun setEnabled(on: Boolean) {
        runCatching { eq?.enabled = on }
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        _state.value = _state.value.copy(enabled = on)
    }

    fun setLevel(band: Int, level: Int) {
        val s = _state.value
        if (band !in s.levels.indices) return
        runCatching { eq?.setBandLevel(band.toShort(), level.toShort()) }
        val next = s.levels.toMutableList().also { it[band] = level }
        prefs?.edit()?.putString("levels", next.joinToString(","))?.apply()
        _state.value = s.copy(levels = next)
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

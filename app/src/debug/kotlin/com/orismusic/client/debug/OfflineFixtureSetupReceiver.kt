package com.orismusic.client.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orismusic.client.playback.OfflineAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Explicit one-shot fixture setup for the offline runtime check. A broadcast is a
 * single delivered event: nothing retains or replays it, and no activity or task
 * restoration can re-execute it, unlike extras on a displayed activity's intent,
 * which Android may redeliver when a task is restored after process death. Debug
 * builds only; invoked by the harness before any cache fill. */
class OfflineFixtureSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val seconds = intent.getIntExtra("fixture_seconds", 0)
        if (seconds <= 0) return
        val file = java.io.File(context.cacheDir, "offline-fixture-tone.wav")
        file.writeBytes(fixtureWav(seconds))
        OfflineAudio.fixtureUri = file.toURI().toString()
    }
}

/** The generated fixture tone (440Hz, 8kHz, 16-bit mono), served from file:// so
 * the real CacheWriter and download cache do their normal work without any
 * network. The harness mirrors these parameters for deterministic restores; the
 * two byte streams are never asserted identical. */
internal fun fixtureWav(seconds: Int): ByteArray {
    val rate = 8000
    val samples = rate * seconds
    val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
    bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
    bytes.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
    bytes.put("data".toByteArray()).putInt(samples * 2)
    repeat(samples) { bytes.putShort((sin(it * 2.0 * Math.PI * 440 / rate) * 2000).toInt().toShort()) }
    return bytes.array()
}

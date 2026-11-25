package com.orismusic.client.debug

import android.app.Activity
import android.content.*
import android.os.*
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.orismusic.client.playback.OrisMediaService
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Drives the real service through a deterministic failure sequence on local files, no
 *  network: two failures advance the queue, a good tone proves a successful start resets
 *  the streak, three more failures make the skip-walk guard pause instead of reaching the
 *  last item. With --es queue single it queues one bad item only, for the terminal-stop
 *  check. The status line mirrors the player so the harness can follow along. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SkipGuardCheckActivity : Activity() {
    private lateinit var status: TextView
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val player = (binder as OrisMediaService.MusicBinder).service.player
            val rate = 8000
            val samples = rate * 10
            val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            bytes.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            bytes.put("data".toByteArray()).putInt(samples * 2)
            repeat(samples) { bytes.putShort((sin(it * 2.0 * Math.PI * 220 / rate) * 2000).toInt().toShort()) }
            val tone = java.io.File(cacheDir, "skip-guard-tone.wav").apply { writeBytes(bytes.array()) }
            fun bad(n: Int) = MediaItem.Builder()
                .setUri(java.io.File(cacheDir, "missing-$n.wav").toURI().toString())
                .setMediaId("sg-bad-$n")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("SkipGuard bad $n").setArtist("Oris test").build())
                .build()
            fun good(n: Int) = MediaItem.Builder()
                .setUri(tone.toURI().toString())
                .setMediaId("sg-good-$n")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("SkipGuard good $n").setArtist("Oris test").build())
                .build()
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = render(player)
                override fun onIsPlayingChanged(isPlaying: Boolean) = render(player)
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = render(player)
            })
            // --es queue single: one bad item only, for the terminal-stop check.
            val single = intent?.getStringExtra("queue") == "single"
            player.setMediaItems(
                if (single) listOf(bad(6))
                else listOf(bad(1), bad(2), good(1), bad(3), bad(4), bad(5), good(2)),
            )
            player.prepare()
            player.play()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    private fun render(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: "none"
        status.text = "item=$id playing=${player.isPlaying} pwr=${player.playWhenReady}"
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status = TextView(this).apply { text = "Preparing skip-guard sequence"; textSize = 20f }
        val remove = Button(this).apply {
            text = "Remove this task"
            isAllCaps = false
            setOnClickListener { finishAndRemoveTask() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 72, 24, 24)
            addView(status)
            addView(remove)
        })
        startService(Intent(this, OrisMediaService::class.java))
        bound = bindService(Intent(this, OrisMediaService::class.java), connection, Context.BIND_AUTO_CREATE)
    }
    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}

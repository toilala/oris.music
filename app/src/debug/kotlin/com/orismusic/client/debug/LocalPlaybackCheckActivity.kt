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

/** A generated tone exercises the real service/ExoPlayer without YouTube or a test server. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalPlaybackCheckActivity : Activity() {
    private lateinit var status: TextView
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val player = (binder as OrisMediaService.MusicBinder).service.player
            val rate = 8000
            val samples = rate * 2
            val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            bytes.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            bytes.put("data".toByteArray()).putInt(samples * 2)
            repeat(samples) { bytes.putShort((sin(it * 2.0 * Math.PI * 220 / rate) * 2000).toInt().toShort()) }
            val file = java.io.File(cacheDir, "runtime-tone.wav").apply { writeBytes(bytes.array()) }
            val cover = java.io.File(cacheDir, "runtime-cover.png")
            val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.shader = android.graphics.LinearGradient(0f, 0f, 512f, 512f, 0xFFD94F70.toInt(), 0xFF2B6CB0.toInt(), android.graphics.Shader.TileMode.CLAMP)
                drawRect(0f, 0f, 512f, 512f, paint)
                paint.shader = null
                paint.color = 0xFFFFD166.toInt()
                drawCircle(150f, 150f, 90f, paint)
                paint.color = 0xFFFFFFFF.toInt()
                drawRect(300f, 330f, 480f, 360f, paint)
                drawRect(300f, 390f, 420f, 420f, paint)
            }
            cover.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    status.text = if (isPlaying) "Local audio playing" else "Local audio stopped"
                }
            })
            player.setMediaItem(MediaItem.Builder().setUri(file.toURI().toString())
                .setMediaId("runtime-local-tone")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Runtime local tone").setArtist("Oris test").setArtworkUri(android.net.Uri.fromFile(cover)).build()).build())
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.play()
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status = TextView(this).apply { text = "Preparing local audio"; textSize = 24f }
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

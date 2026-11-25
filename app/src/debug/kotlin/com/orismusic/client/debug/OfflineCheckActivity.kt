package com.orismusic.client.debug

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.orismusic.client.library.SavedSong
import com.orismusic.client.library.toPlayableItem
import com.orismusic.client.playback.OfflineAudio
import com.orismusic.client.playback.offlineRowStatus
import androidx.media3.common.Player
import com.orismusic.client.playback.OrisMediaService

/** Exercises the real save-for-offline pipeline against a generated local tone instead of
 * YouTube: real cache write, state flows, restart validation, offline playback from cache and
 * removal. Debug builds only; evidence from this activity is local-fixture evidence, never
 * proof that a YouTube download works. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineCheckActivity : Activity() {
    private lateinit var status: TextView
    private var bound = false
    private val song = SavedSong("offline-fixture-tone", "Offline fixture tone", "Oris test", null)
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val state = offlineRowStatus(song.id,
                saved = OfflineAudio.downloaded.value.any { it.id == song.id },
                busy = OfflineAudio.inProgress.value,
                failed = OfflineAudio.failed.value,
                validated = OfflineAudio.status.value)
            val label = "Offline state: " + (state?.name ?: "NOT SAVED")
            // Rewrite only on real transitions: a 400ms unconditional setText keeps
            // accessibility events flowing, so uiautomator never reaches idle and
            // every hierarchy dump comes back empty.
            if (status.text.toString() != label) status.text = label
            handler.postDelayed(this, 400)
        }
    }
    private var player: Player? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val p = (binder as OrisMediaService.MusicBinder).service.player
            player = p
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) status.text = "Offline state: PLAYING FROM CACHE"
                }
            })
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Recreation diagnosis: every onCreate is recorded so the harness can
        // correlate recreations with its own actions. Recreation restores the
        // fixture URI but NEVER rewrites the source bytes: this activity carries
        // no generation path at all. Only OfflineFixtureSetupReceiver writes the
        // bytes, and a broadcast is a one-shot event no task restoration can replay.
        java.io.File(filesDir, "offline-check-recreations.log").appendText(
            "pid=" + android.os.Process.myPid() +
                " elapsed=" + android.os.SystemClock.elapsedRealtime() +
                " extras=" + (intent?.extras != null) + "\n")
        val file = java.io.File(cacheDir, "offline-fixture-tone.wav")
        OfflineAudio.fixtureUri = file.toURI().toString()
        OfflineAudio.load(this)

        status = TextView(this).apply { text = "Offline state: NOT SAVED"; textSize = 20f }
        fun action(label: String, run: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { run() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 72, 24, 24)
            addView(status)
            addView(action("Download fixture") { OfflineAudio.download(this@OfflineCheckActivity, song) })
            addView(action("Cancel download") { OfflineAudio.cancel(this@OfflineCheckActivity, song.id) })
            addView(action("Play offline") {
                startService(Intent(this@OfflineCheckActivity, OrisMediaService::class.java))
                if (!bound) bound = bindService(Intent(this@OfflineCheckActivity, OrisMediaService::class.java),
                    connection, Context.BIND_AUTO_CREATE)
                // The player reference lands asynchronously; retry until the bind completes.
                handler.postDelayed(object : Runnable {
                    override fun run() {
                        val p = player
                        if (p == null) {
                            handler.postDelayed(this, 300)
                            return
                        }
                        p.setMediaItem(song.toPlayableItem())
                        p.prepare()
                        p.play()
                    }
                }, 300)
            })
            addView(action("Remove fixture") { OfflineAudio.remove(this@OfflineCheckActivity, song.id) })
        })
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}

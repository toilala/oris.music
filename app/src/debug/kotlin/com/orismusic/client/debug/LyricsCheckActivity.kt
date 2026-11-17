package com.orismusic.client.debug

import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.orismusic.client.di.MusicDatabase
import com.orismusic.client.playback.OrisMediaService
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.ui.player.LyricsPane
import com.orismusic.client.ui.theme.OrisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Metadata-only lyrics check: deliberately never prepares/resolves streamed audio. */
@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LyricsCheckActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    private var playerConnection: PlayerConnection? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as OrisMediaService.MusicBinder
            local.service.player.setMediaItem(MediaItem.Builder().setUri("file:///metadata-only-not-prepared.wav").setMediaId("wXTJBr9tt8Q")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Yesterday").setArtist("The Beatles").setAlbumTitle("Help!").build()).build())
            val connection = PlayerConnection(this@LyricsCheckActivity, local, database, lifecycleScope)
            playerConnection = connection
            setContent { OrisTheme { LyricsPane(connection, onClose = { finishAndRemoveTask() }) } }
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Runs with Android ICU, not the desktop JVM regex engine.
        check(com.orismusic.client.lyrics.readLrc("[00:01.00]{agent:1}Hello {bg}world").single().text == "Hello world")
        android.util.Log.i("OrisLyrics", "Android LRC parser regression passed")
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        startService(Intent(this, OrisMediaService::class.java))
        bound = bindService(Intent(this, OrisMediaService::class.java), connection, Context.BIND_AUTO_CREATE)
    }
    override fun onDestroy() {
        playerConnection?.dispose()
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}

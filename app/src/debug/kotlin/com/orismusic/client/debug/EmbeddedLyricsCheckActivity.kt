package com.orismusic.client.debug

import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** The player's own lyrics view: metadata only, same real providers, over a plain dark backdrop. */
@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EmbeddedLyricsCheckActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    private var playerConnection: PlayerConnection? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as OrisMediaService.MusicBinder
            local.service.player.setMediaItem(MediaItem.Builder().setUri("file:///metadata-only-not-prepared.wav").setMediaId("wXTJBr9tt8Q")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Yesterday").setArtist("The Beatles").setAlbumTitle("Help!").build()).build())
            val connection = PlayerConnection(this@EmbeddedLyricsCheckActivity, local, database, lifecycleScope)
            playerConnection = connection
            setContent {
                OrisTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF2A2430), Color(0xFF121016))
                                )
                            )
                    ) {
                        LyricsPane(connection, onClose = { finishAndRemoveTask() }, embedded = true)
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
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

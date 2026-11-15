package com.orismusic.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.orismusic.client.playback.OrisMediaService
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.ListQueue
import com.orismusic.client.ui.screens.HomeScreen
import com.orismusic.client.ui.theme.OrisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var database: com.orismusic.client.di.MusicDatabase

    private var playerConnection: PlayerConnection? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is OrisMediaService.MusicBinder) {
                try {
                    connectTo(service)
                    Timber.d("PlayerConnection created successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create PlayerConnection")
                    lifecycleScope.launch {
                        // Retry after a short delay
                        delay(500)
                        try {
                            connectTo(service)
                        } catch (e2: Exception) {
                            Timber.e(e2, "Failed to create PlayerConnection on retry")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    private fun connectTo(binder: OrisMediaService.MusicBinder) {
        val previous = playerConnection
        playerConnection = PlayerConnection(this, binder, database, lifecycleScope).also { connection ->
            lifecycleScope.launch {
                connection.isPlayerInitialized.collect { ready ->
                    if (ready) {
                        playDemoTrack(connection)
                    }
                }
            }
        }
        previous?.dispose()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OrisTheme {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        playerConnection = playerConnection,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, OrisMediaService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        isBound = true
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerConnection?.dispose()
        playerConnection = null
    }

    private fun playDemoTrack(connection: PlayerConnection) {
        val mediaItem = androidx.media3.common.MediaItem.fromUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
            .buildUpon()
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Demo Track")
                    .setArtist("SoundHelix")
                    .build()
            )
            .build()

        val queue = ListQueue(
            title = "Demo",
            mediaItems = listOf(mediaItem)
        )
        connection.playQueue(queue)
    }
}
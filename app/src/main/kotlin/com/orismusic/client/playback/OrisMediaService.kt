package com.orismusic.client.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import com.orismusic.client.MainActivity

@UnstableApi
@AndroidEntryPoint
class OrisMediaService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private var isSessionReleased = false

    // For PlayerConnection to observe player initialization
    val isPlayerReady = MutableStateFlow(false)

    inner class MusicBinder : Binder() {
        val service: OrisMediaService
            get() = this@OrisMediaService
    }

    // Expose player for PlayerConnection
    fun getPlayer(): ExoPlayer = player

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_playback",
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this))
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        createNotificationChannel()

        player = createExoPlayer()

        val callback = MediaLibrarySessionCallback()

        mediaSession =
            MediaLibrarySession.Builder(this, player, callback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()

        val sessionToken = SessionToken(this, ComponentName(this, OrisMediaService::class.java))
        val controllerFuture = androidx.media3.session.MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Timber.d("Playback state changed: $state")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Timber.e(error, "Player error")
            }
        })

        isPlayerReady.value = true
        Timber.d("OrisMediaService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return if (::mediaSession.isInitialized && !isSessionReleased) {
            mediaSession
        } else {
            null
        }
    }

    override fun onDestroy() {
        if (::mediaSession.isInitialized && !isSessionReleased) {
            isSessionReleased = true
            mediaSession.release()
        }
        player.release()
        Timber.d("OrisMediaService destroyed")
        super.onDestroy()
    }
}
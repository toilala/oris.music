package com.orismusic.client.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.exoplayer.ExoPlayer
import com.orismusic.client.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class PlayerConnection(
    val context: Context,
    private val binder: OrisMediaService.MusicBinder,
    parentScope: CoroutineScope,
) : Listener {

    val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady

    private fun getPlayerSafe(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.w("Player accessed before service initialization complete")
            }
            service.getPlayer()
        } catch (e: Exception) {
            Timber.e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("OrisMediaService.player not initialized", e)
        }
    }

    val player: ExoPlayer
        get() = getPlayerSafe()

    private val _isPlayerInitialized = MutableStateFlow(playerReadinessFlow.value)
    val isPlayerInitialized: StateFlow<Boolean> = _isPlayerInitialized

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady

    val isPlaying: StateFlow<Boolean> = combine(_playbackState, _playWhenReady) { state, ready ->
        ready && state != Player.STATE_ENDED
    }.stateIn(scope, SharingStarted.Lazily, false)

    private val _currentMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentMetadata: StateFlow<MediaMetadata?> = _currentMetadata

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    val canSkipNext = MutableStateFlow(true)
    val canSkipPrevious = MutableStateFlow(true)
    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val error = MutableStateFlow<androidx.media3.common.PlaybackException?>(null)

    private var attachedPlayer: Player? = null

    init {
        scope.launch {
            playerReadinessFlow.collect { ready ->
                _isPlayerInitialized.value = ready
                if (ready && attachedPlayer == null) {
                    updateAttachedPlayer(player)
                }
            }
        }

        if (service.isPlayerReady.value) {
            updateAttachedPlayer(player)
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)

        _playbackState.value = newPlayer.playbackState
        _playWhenReady.value = newPlayer.playWhenReady
        _currentMetadata.value = newPlayer.mediaMetadata
        _currentMediaItem.value = newPlayer.currentMediaItem
        _currentPosition.value = newPlayer.currentPosition
        _duration.value = newPlayer.duration
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        updateCanSkipNextPrevious()
    }

    private fun updateCanSkipNextPrevious() {
        val timeline = player.currentTimeline
        if (!timeline.isEmpty) {
            canSkipNext.value = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            canSkipPrevious.value = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        } else {
            canSkipNext.value = false
            canSkipPrevious.value = false
        }
    }

    fun play() {
        try { player.play() } catch (e: Exception) { Timber.e(e) }
    }

    fun pause() {
        try { player.pause() } catch (e: Exception) { Timber.e(e) }
    }

    fun seekTo(position: Long) {
        try { player.seekTo(position) } catch (e: Exception) { Timber.e(e) }
    }

    fun seekToNext() {
        try { player.seekToNext() } catch (e: Exception) { Timber.e(e) }
    }

    fun seekToPrevious() {
        try { player.seekToPrevious() } catch (e: Exception) { Timber.e(e) }
    }

    fun setMediaItem(mediaItem: MediaItem) {
        try {
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) { Timber.e(e) }
    }

    override fun onPlaybackStateChanged(state: Int) {
        _playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(newPlayWhenReady: Boolean, reason: Int) {
        _playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        _currentMediaItem.value = mediaItem
        _currentMetadata.value = mediaItem?.mediaMetadata
        _currentPosition.value = player.currentPosition
        _duration.value = player.duration
        updateCanSkipNextPrevious()
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        updateCanSkipNextPrevious()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        updateCanSkipNextPrevious()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipNextPrevious()
    }

    override fun onPlayerErrorChanged(playbackError: androidx.media3.common.PlaybackException?) {
        error.value = playbackError
        if (playbackError != null) {
            Timber.e(playbackError, "Player error")
        }
    }

    fun dispose() {
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
        scope.cancel()
    }
}
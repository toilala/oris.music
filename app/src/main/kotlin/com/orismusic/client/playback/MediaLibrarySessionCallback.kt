package com.orismusic.client.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.orismusic.client.constants.MediaSessionConstants
import com.orismusic.client.playback.queues.ListQueue

class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

    var service: OrisMediaService? = null
    var toggleLike: (() -> Unit)? = null
    var toggleStartRadio: (() -> Unit)? = null
    var toggleLibrary: (() -> Unit)? = null
    var onAutomotiveConnected: (() -> Unit)? = null

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        if (controller.packageName in AUTOMOTIVE_PACKAGES) {
            onAutomotiveConnected?.invoke()
        }
        return super.onConnect(session, controller)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val command = customCommand.customAction
        when (command) {
            MediaSessionConstants.CommandToggleLike -> {
                toggleLike?.invoke()
            }
            MediaSessionConstants.CommandToggleRepeatMode -> {
                service?.player?.repeatMode = when (service?.player?.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            MediaSessionConstants.CommandToggleShuffle -> {
                service?.player?.shuffleModeEnabled = !(service?.player?.shuffleModeEnabled ?: false)
            }
            MediaSessionConstants.CommandToggleStartRadio -> {
                toggleStartRadio?.invoke()
            }
            MediaSessionConstants.CommandItemLike -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.toggleLikeForMediaId(it) }
            }
            MediaSessionConstants.CommandItemPlayNext -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.playNextMediaId(it) }
            }
            MediaSessionConstants.CommandItemDismiss -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.dismissFromQueue(it) }
            }
            MediaSessionConstants.CommandItemFollow -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.followArtist(it) }
            }
            MediaSessionConstants.CommandItemUnfollow -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.unfollowArtist(it) }
            }
            MediaSessionConstants.CommandEpisodeSkipBack -> {
                val player = service?.player
                if (player != null) {
                    val current = player.currentPosition
                    player.seekTo((current - 15000).coerceAtLeast(0L))
                }
            }
            MediaSessionConstants.CommandEpisodeSkipForward -> {
                val player = service?.player
                if (player != null) {
                    val current = player.currentPosition
                    player.seekTo(current + 30000)
                }
            }
            MediaSessionConstants.CommandEpisodeSpeed -> {
                val speed = args.getFloat("speed", 1f)
                service?.player?.playbackParameters = PlaybackParameters(speed, 1f)
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        service?.addToQueue(mediaItems)
        return super.onAddMediaItems(mediaSession, controller, mediaItems)
    }

    @UnstableApi
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val queue = ListQueue(
            title = "External Queue",
            items = mediaItems,
            startIndex = startIndex,
            position = startPositionMs
        )
        service?.playQueue(queue)
        return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }

    companion object {
        private val AUTOMOTIVE_PACKAGES = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.apps.automotive",
            "com.android.car",
        )
    }
}

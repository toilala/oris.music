package com.orismusic.client.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibrarySession
import androidx.media3.session.MediaLibrarySessionCallback
import androidx.media3.session.MediaSession
import androidx.media3.session.controller.ControllerInfo
import androidx.media3.session.MediaSession.ResultCallback
import com.orismusic.client.constants.MediaSessionConstants

class MediaLibrarySessionCallback : MediaLibrarySessionCallback() {

    var service: OrisMediaService? = null
    var toggleLike: (() -> Unit)? = null
    var toggleStartRadio: (() -> Unit)? = null
    var toggleLibrary: (() -> Unit)? = null
    var onAutomotiveConnected: (() -> Unit)? = null

    override fun onConnect(controllerInfo: ControllerInfo, session: MediaLibrarySession): MediaLibrarySession.Callback {
        if (controllerInfo.packageName in AUTOMOTIVE_PACKAGES) {
            onAutomotiveConnected?.invoke()
        }
        return super.onConnect(controllerInfo, session)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: ControllerInfo,
        customCommand: MediaSession.Command,
        args: Bundle,
        callback: ResultCallback,
    ) {
        val command = customCommand.customAction
        when (command) {
            MediaSessionConstants.CommandToggleLike -> {
                toggleLike?.invoke()
                callback.onResult(null)
            }
            MediaSessionConstants.CommandToggleRepeatMode -> {
                service?.player?.repeatMode = when (service?.player?.repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ONE
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                }
                callback.onResult(null)
            }
            MediaSessionConstants.CommandToggleShuffle -> {
                service?.player?.shuffleModeEnabled = !(service?.player?.shuffleModeEnabled ?: false)
                callback.onResult(null)
            }
            MediaSessionConstants.CommandToggleStartRadio -> {
                toggleStartRadio?.invoke()
                callback.onResult(null)
            }
            MediaSessionConstants.CommandItemLike -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.toggleLikeForMediaId(it) }
                callback.onResult(null)
            }
            MediaSessionConstants.CommandItemPlayNext -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.playNextMediaId(it) }
                callback.onResult(null)
            }
            MediaSessionConstants.CommandItemDismiss -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.dismissFromQueue(it) }
                callback.onResult(null)
            }
            MediaSessionConstants.CommandItemFollow -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.followArtist(it) }
                callback.onResult(null)
            }
            MediaSessionConstants.CommandItemUnfollow -> {
                val mediaId = args.getString("mediaId")
                mediaId?.let { service?.unfollowArtist(it) }
                callback.onResult(null)
            }
            MediaSessionConstants.CommandEpisodeSkipBack -> {
                service?.player?.seekToRelative(-15000)
                callback.onResult(null)
            }
            MediaSessionConstants.CommandEpisodeSkipForward -> {
                service?.player?.seekToRelative(30000)
                callback.onResult(null)
            }
            MediaSessionConstants.CommandEpisodeSpeed -> {
                val speed = args.getFloat("speed", 1f)
                service?.player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed, 1f)
                callback.onResult(null)
            }
        }
    }

    override fun onAddMediaItems(
        session: MediaLibrarySession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        index: Int,
    ) {
        service?.addToQueue(mediaItems)
    }

    override fun onSetMediaItems(
        session: MediaLibrarySession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        val queue = OrisMediaService.ListQueue(
            title = "External Queue",
            items = mediaItems,
            startIndex = startIndex,
            startPosition = startPositionMs
        )
        service?.playQueue(queue)
    }

    companion object {
        private const val AUTOMOTIVE_PACKAGES = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.apps.automotive",
            "com.android.car",
        )
    }
}
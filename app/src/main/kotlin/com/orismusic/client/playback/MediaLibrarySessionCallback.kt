package com.orismusic.client.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibrarySession
import androidx.media3.session.MediaLibrarySessionCallback
import androidx.media3.session.MediaSession
import com.orismusic.client.utils.YTPlayerUtils
import com.orismusic.client.utils.YTPlayerUtils.PlaybackData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MediaLibrarySessionCallback : MediaLibrarySessionCallback() {
    // var service: OrisMediaService? = null
    // var toggleLike: ((String, Boolean) -> Unit)? = null
    // var toggleStartRadio: ((String) -> Unit)? = null
    // var toggleLibrary: ((String) -> Unit)? = null
    // var onAutomotiveConnected: (() -> Unit)? = null

    override fun onAddMediaItems(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<MediaLibrarySessionCallback.MediaItemsResult> {
        Timber.d("onAddMediaItems: ${mediaItems.size} items")
        // Handle adding media items to queue
        return com.google.common.util.concurrent.Futures.immediateFuture(
            MediaLibrarySessionCallback.MediaItemsResult(MediaLibrarySessionCallback.RESULT_SUCCESS)
        )
    }

    override fun onRemoveMediaItems(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaIds: List<String>
    ): ListenableFuture<MediaLibrarySessionCallback.MediaItemsResult> {
        Timber.d("onRemoveMediaItems: $mediaIds")
        return com.google.common.util.concurrent.Futures.immediateFuture(
            MediaLibrarySessionCallback.MediaItemsResult(MediaLibrarySessionCallback.RESULT_SUCCESS)
        )
    }

    override fun onSetMediaItems(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaLibrarySessionCallback.MediaItemsResult> {
        Timber.d("onSetMediaItems: ${mediaItems.size} items, startIndex: $startIndex")
        // Handle setting the queue
        return com.google.common.util.concurrent.Futures.immediateFuture(
            MediaLibrarySessionCallback.MediaItemsResult(MediaLibrarySessionCallback.RESULT_SUCCESS)
        )
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        params: MediaLibrarySession.LibraryParams
    ): MediaLibrarySession.LibraryResult<MediaItem> {
        // Return root library item
        val root = MediaItem.fromUri("android-app://com.orismusic.client/library")
        return MediaLibrarySession.LibraryResult(MediaLibrarySession.LibraryResult.RESULT_SUCCESS, root)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        parentMediaId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibrarySession.LibraryParams
    ): MediaLibrarySession.LibraryResult<MediaLibrarySession.Children> {
        // Return children for library browsing
        return MediaLibrarySession.LibraryResult(MediaLibrarySession.LibraryResult.RESULT_SUCCESS, MediaLibrarySession.Children(emptyList()))
    }

    override fun onGetSearchResults(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibrarySession.LibraryParams
    ): MediaLibrarySession.LibraryResult<MediaLibrarySession.SearchResults> {
        // Handle search
        return MediaLibrarySession.LibraryResult(MediaLibrarySession.LibraryResult.RESULT_SUCCESS, MediaLibrarySession.SearchResults(emptyList()))
    }

    override fun onCustomCommand(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        customCommand: MediaSession.CustomCommand,
        args: Bundle
    ): ListenableFuture<MediaSession.SessionResult> {
        // Handle custom commands
        return com.google.common.util.concurrent.Futures.immediateFuture(
            MediaSession.SessionResult(MediaSession.SessionResult.RESULT_SUCCESS)
        )
    }
}

import com.google.common.util.concurrent.ListenableFuture
import android.os.Bundle
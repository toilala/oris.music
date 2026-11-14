package com.orismusic.client.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibrarySession
import androidx.media3.session.MediaLibrarySessionCallback
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

class MediaLibrarySessionCallback : MediaLibrarySessionCallback() {
    override fun onAddMediaItems(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<MediaLibrarySessionCallback.MediaItemsResult> {
        Timber.d("onAddMediaItems: ${mediaItems.size} items")
        return Futures.immediateFuture(
            MediaLibrarySessionCallback.MediaItemsResult(MediaLibrarySessionCallback.RESULT_SUCCESS)
        )
    }

    override fun onRemoveMediaItems(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaIds: List<String>
    ): ListenableFuture<MediaLibrarySessionCallback.MediaItemsResult> {
        Timber.d("onRemoveMediaItems: $mediaIds")
        return Futures.immediateFuture(
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
        return Futures.immediateFuture(
            MediaLibrarySessionCallback.MediaItemsResult(MediaLibrarySessionCallback.RESULT_SUCCESS)
        )
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        params: MediaLibrarySession.LibraryParams
    ): MediaLibrarySession.LibraryResult<MediaItem> {
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
        return MediaLibrarySession.LibraryResult(MediaLibrarySession.LibraryResult.RESULT_SUCCESS, MediaLibrarySession.Children(emptyList()))
    }

    override fun onGetSearchResults(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibrarySession.LibraryParams
    ): MediaLibrarySession.LibraryResult<MediaLibrarySession.SearchResults> {
        return MediaLibrarySession.LibraryResult(MediaLibrarySession.LibraryResult.RESULT_SUCCESS, MediaLibrarySession.SearchResults(emptyList()))
    }

    override fun onCustomCommand(
        session: MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        customCommand: MediaSession.CustomCommand,
        args: Bundle
    ): ListenableFuture<MediaSession.SessionResult> {
        return Futures.immediateFuture(
            MediaSession.SessionResult(MediaSession.SessionResult.RESULT_SUCCESS)
        )
    }
}
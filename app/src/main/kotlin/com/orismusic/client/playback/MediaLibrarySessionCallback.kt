package com.orismusic.client.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

class MediaLibrarySessionCallback : MediaLibraryService.MediaLibrarySession.Callback() {
    var service: OrisMediaService? = null
    var toggleLike: ((String, Boolean) -> Unit)? = null
    var toggleStartRadio: ((String) -> Unit)? = null
    var toggleLibrary: ((String) -> Unit)? = null
    var onAutomotiveConnected: (() -> Unit)? = null

    override fun onAddMediaItems(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<MediaLibraryService.MediaItemsResult> {
        Timber.d("onAddMediaItems: ${mediaItems.size} items")
        return Futures.immediateFuture(
            MediaLibraryService.MediaItemsResult(MediaLibraryService.MediaItemsResult.RESULT_SUCCESS)
        )
    }

    override fun onRemoveMediaItems(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaIds: List<String>
    ): ListenableFuture<MediaLibraryService.MediaItemsResult> {
        Timber.d("onRemoveMediaItems: $mediaIds")
        return Futures.immediateFuture(
            MediaLibraryService.MediaItemsResult(MediaLibraryService.MediaItemsResult.RESULT_SUCCESS)
        )
    }

    override fun onSetMediaItems(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaLibraryService.MediaItemsResult> {
        Timber.d("onSetMediaItems: ${mediaItems.size} items, startIndex: $startIndex")
        return Futures.immediateFuture(
            MediaLibraryService.MediaItemsResult(MediaLibraryService.MediaItemsResult.RESULT_SUCCESS)
        )
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams
    ): MediaLibraryService.LibraryResult<MediaItem> {
        val root = MediaItem.fromUri("android-app://com.orismusic.client/library")
        return MediaLibraryService.LibraryResult(MediaLibraryService.LibraryResult.RESULT_SUCCESS, root)
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        parentMediaId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams
    ): MediaLibraryService.LibraryResult<MediaLibraryService.Children> {
        return MediaLibraryService.LibraryResult(MediaLibraryService.LibraryResult.RESULT_SUCCESS, MediaLibraryService.Children(emptyList()))
    }

    override fun onGetSearchResults(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams
    ): MediaLibraryService.LibraryResult<MediaLibraryService.SearchResults> {
        return MediaLibraryService.LibraryResult(MediaLibraryService.LibraryResult.RESULT_SUCCESS, MediaLibraryService.SearchResults(emptyList()))
    }

    override fun onCustomCommand(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        customCommand: MediaSession.CustomCommand,
        args: Bundle
    ): ListenableFuture<MediaSession.SessionResult> {
        return Futures.immediateFuture(
            MediaSession.SessionResult(MediaSession.SessionResult.RESULT_SUCCESS)
        )
    }
}
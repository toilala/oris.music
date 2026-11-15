package com.orismusic.client.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.orismusic.client.constants.MediaSessionConstants
import com.orismusic.client.utils.ReportException.reportException
import com.orismusic.client.utils.dataStore
import com.orismusic.client.utils.get
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MediaLibrarySessionCallback
@Inject
constructor(
  @ApplicationContext val context: Context,
) : MediaLibrarySession.Callback {
  private val scope = CoroutineScope(Dispatchers.Main) + Job()
  lateinit var service: OrisMediaService
  var toggleLike: () -> Unit = {}
  var toggleStartRadio: () -> Unit = {}
  var toggleLibrary: () -> Unit = {}

  /**
   * Invoked when an automotive controller connects. Used to embed the artwork of
   * the item already playing, which otherwise never goes through a media-item
   * transition while the car is connected.
   */
  var onAutomotiveConnected: () -> Unit = {}

  /** The session Auto browses, kept to tell it when the Home snapshot was refreshed. */
  @Volatile private var librarySession: MediaLibrarySession? = null
  private val liveUpdatesStarted = java.util.concurrent.atomic.AtomicBoolean(false)

  /**
   * Tells Auto when lists that change with listening do: Auto keeps the children it loaded
   * and re-reads a list only when notified, so History stayed as it was when first opened.
   */
  private fun startLiveUpdates() {
    scope.launch {
      // TODO: Implement database events flow when database is available
      // database
      //   .events()
      //   .map { events -> events.take(HISTORY_SIZE).map { it.song.id } }
      //   .distinctUntilChanged()
      //   .drop(1)
      //   .debounce(LIVE_UPDATE_DEBOUNCE_MS)
      //   .collect { notifyChildrenChanged(OrisMediaService.HISTORY) }
    }
  }

  private fun notifyChildrenChanged(parentId: String) {
    val session = librarySession ?: return
    scope.launch(Dispatchers.Main) {
      runCatching { session.notifyChildrenChanged(parentId, Int.MAX_VALUE, null) }
    }
  }

  /**
   * Package names [MediaSession.isAutomotiveController] and
   * [MediaSession.isAutoCompanionController] treat as automotive/companion surfaces.
   */
  private val trustedAutomotivePackages =
    setOf(
      "com.google.android.projection.gearhead", // Android Auto
      "com.google.android.gms.car", // Android Auto (legacy package)
      "com.android.systemui", // Android Automotive OS system UI
    )

  /**
   * True only if [controller] is both claiming a trusted automotive/companion package name *and* is
   * actually running as that package's UID.
   */
  private fun isVerifiedAutomotiveController(controller: MediaSession.ControllerInfo): Boolean {
    val claimedPackage = controller.packageName
    if (claimedPackage !in trustedAutomotivePackages) return false
    val actualUid =
      try {
        context.packageManager.getPackageUid(claimedPackage, 0)
      } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        return false
      }
    return actualUid == controller.uid
  }

  /**
   * Whether [controller] is a surface allowed to mutate playback state via the toggle custom
   * commands.
   */
  private fun isAuthorizedController(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
  ): Boolean =
    controller.isTrusted ||
      session.isMediaNotificationController(controller) ||
      isVerifiedAutomotiveController(controller)

  private fun isAutomotiveBrowser(browser: MediaSession.ControllerInfo): Boolean =
    browser.packageName == "com.google.android.projection.gearhead" ||
      browser.packageName == "com.google.android.gms.car" ||
      browser.packageName == "com.android.systemui"

  /**
   * Negotiates connection capabilities for an incoming controller.
   */
  override fun onConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
  ): MediaSession.ConnectionResult {
    val connectionResult = super.onConnect(session, controller)
    val availableSessionCommands =
      if (isAuthorizedController(session, controller)) {
        connectionResult.availableSessionCommands
          .buildUpon()
          .add(MediaSessionConstants.CommandToggleLike)
          .add(MediaSessionConstants.CommandToggleStartRadio)
          .add(MediaSessionConstants.CommandToggleLibrary)
          .add(MediaSessionConstants.CommandToggleShuffle)
          .add(MediaSessionConstants.CommandToggleRepeatMode)
          .add(MediaSessionConstants.CommandItemLike)
          .add(MediaSessionConstants.CommandItemPlayNext)
          .add(MediaSessionConstants.CommandItemDismiss)
          .add(MediaSessionConstants.CommandItemFollow)
          .add(MediaSessionConstants.CommandItemUnfollow)
          .add(MediaSessionConstants.CommandEpisodeSkipBack)
          .add(MediaSessionConstants.CommandEpisodeSkipForward)
          .add(MediaSessionConstants.CommandEpisodeSpeed)
          .build()
      } else {
        connectionResult.availableSessionCommands
      }

    if (isVerifiedAutomotiveController(controller)) {
      onAutomotiveConnected()
    }
    if (isAutomotiveBrowser(controller)) {
      // prefetchHome() - TODO: implement when YouTube integration is ready
    }

    val availablePlayerCommands =
      if (session.isMediaNotificationController(controller)) {
        connectionResult.availablePlayerCommands
          .buildUpon()
          .remove(androidx.media3.common.Player.COMMAND_GET_TIMELINE)
          .build()
      } else {
        connectionResult.availablePlayerCommands
      }

    return MediaSession.ConnectionResult.accept(
      availableSessionCommands,
      availablePlayerCommands,
    )
  }

  /**
   * Handles custom session commands such as toggle-like, toggle-shuffle and toggle-repeat.
   */
  override fun onCustomCommand(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle,
  ): ListenableFuture<SessionResult> {
    val isToggleCommand =
      customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_LIKE ||
        customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_START_RADIO ||
        customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_LIBRARY ||
        customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_SHUFFLE ||
        customCommand.customAction == MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE ||
        customCommand.customAction == MediaSessionConstants.ACTION_EPISODE_SKIP_BACK ||
        customCommand.customAction == MediaSessionConstants.ACTION_EPISODE_SKIP_FORWARD ||
        customCommand.customAction == MediaSessionConstants.ACTION_EPISODE_SPEED
    val isItemCommand =
      customCommand.customAction == MediaSessionConstants.ACTION_ITEM_LIKE ||
        customCommand.customAction == MediaSessionConstants.ACTION_ITEM_PLAY_NEXT ||
        customCommand.customAction == MediaSessionConstants.ACTION_ITEM_DISMISS ||
        customCommand.customAction == MediaSessionConstants.ACTION_ITEM_FOLLOW ||
        customCommand.customAction == MediaSessionConstants.ACTION_ITEM_UNFOLLOW
    val authorized = isAuthorizedController(session, controller)
    if ((isToggleCommand || isItemCommand) && !authorized) {
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    if (isItemCommand) {
      val mediaId =
        args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)
          ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
      return scope.future {
        // TODO: Implement item commands when database is available
        val handled = false
        SessionResult(if (handled) SessionResult.RESULT_SUCCESS else SessionError.ERROR_BAD_VALUE)
      }
    }
    when (customCommand.customAction) {
      MediaSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike()
      MediaSessionConstants.ACTION_TOGGLE_START_RADIO -> toggleStartRadio()
      MediaSessionConstants.ACTION_TOGGLE_LIBRARY -> toggleLibrary()
      MediaSessionConstants.ACTION_TOGGLE_SHUFFLE ->
        session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
      MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> session.player.toggleRepeatMode()
      MediaSessionConstants.ACTION_EPISODE_SKIP_BACK ->
        session.player.seekTo((session.player.currentPosition - 15000).coerceAtLeast(0))
      MediaSessionConstants.ACTION_EPISODE_SKIP_FORWARD -> {
        val target = session.player.currentPosition + 30000
        val duration = session.player.duration
        session.player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
      }
      MediaSessionConstants.ACTION_EPISODE_SPEED -> {
        // TODO: Implement podcast speed cycling
      }
    }
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
  }

  /**
   * Provides the library root and browsing hints for Automotive and other browsers.
   */
  override fun onGetLibraryRoot(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    params: MediaLibraryService.LibraryParams?,
  ): ListenableFuture<LibraryResult<MediaItem>> {
    val isAutomotive = isAutomotiveBrowser(browser)

    val rootExtras =
      Bundle().apply {
        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
        putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        if (isAutomotive) {
          params?.extras?.let { extras ->
            if (extras.containsKey(MediaConstants.EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS)) {
              putInt(
                MediaConstants.EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS,
                extras.getInt(MediaConstants.EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS)
              )
            }
          }
        }
      }

    val rootParams =
      MediaLibraryService.LibraryParams.Builder()
        .setOffline(params?.isOffline ?: false)
        .setRecent(params?.isRecent ?: false)
        .setSuggested(params?.isSuggested ?: false)
        .setExtras(rootExtras)
        .build()

    return Futures.immediateFuture(
      LibraryResult.ofItem(
        rootMediaItem(),
        rootParams.withContentStyleHints(isAutomotive),
      ),
    )
  }

  /**
   * Returns children for a given browsable parent.
   */
  override fun onGetChildren(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?,
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    librarySession = session
    if (liveUpdatesStarted.compareAndSet(false, true)) startLiveUpdates()
    val isAutomotive = isAutomotiveBrowser(browser)
    return scope.future(Dispatchers.IO) {
      // TODO: Implement proper children loading when database and YouTube integration are available
      val children = emptyList<MediaItem>()
      val paged = children.paginate(page, pageSize)
      LibraryResult.ofItemList(
        paged,
        params.withContentStyleHints(isAutomotive),
      )
    }
  }

  /**
   * Handles media search requests.
   */
  override fun onSearch(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    params: MediaLibraryService.LibraryParams?,
  ): ListenableFuture<LibraryResult<Void>> {
    session.notifySearchResultChanged(browser, query, 1, params)
    return Futures.immediateFuture(LibraryResult.ofVoid())
  }

  /**
   * Returns search results matching [query].
   */
  override fun onGetSearchResult(
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?,
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    val isAutomotive = isAutomotiveBrowser(browser)
    return scope.future(Dispatchers.IO) {
      if (query.isEmpty()) {
        return@future LibraryResult.ofItemList(
          emptyList(),
          params.withContentStyleHints(isAutomotive)
        )
      }
      // TODO: Implement search when database and YouTube integration are available
      LibraryResult.ofItemList(emptyList(), params.withContentStyleHints(isAutomotive))
    }
  }

  /**
   * Handles setting media items for playback.
   */
  override fun onSetMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: MutableList<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
  ): ListenableFuture<MediaItemsWithStartPosition> =
    scope.future(Dispatchers.IO) {
      // TODO: Implement queue loading from media items
      val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
      defaultResult
    }

  private fun drawableUri(
    @androidx.annotation.DrawableRes id: Int,
  ): Uri =
    Uri.Builder()
      .scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
      .authority(context.resources.getResourcePackageName(id))
      .appendPath(context.resources.getResourceTypeName(id))
      .appendPath(context.resources.getResourceEntryName(id))
      .build()

  private fun rootMediaItem(): MediaItem =
    MediaItem.Builder()
      .setMediaId(OrisMediaService.ROOT)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(context.getString(com.orismusic.client.R.string.app_name))
          .setIsPlayable(false)
          .setIsBrowsable(true)
          .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
          .build(),
      )
      .build()

  private fun browsableMediaItem(
    id: String,
    title: String,
    subtitle: String?,
    iconUri: Uri?,
    mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
    singleItemStyle: Int = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
    browsableStyle: Int = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
  ): MediaItem =
    MediaItem.Builder()
      .setMediaId(id)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(title)
          .setSubtitle(subtitle)
          .setArtist(subtitle)
          .setArtworkUri(iconUri)
          .setIsPlayable(false)
          .setIsBrowsable(true)
          .setMediaType(mediaType)
          .setExtras(
            contentStyleExtras(
              browsableStyle = browsableStyle,
              playableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
              singleItemStyle = singleItemStyle,
            )
          )
          .build(),
      )
      .build()

  private fun contentStyleExtras(
    browsableStyle: Int? = null,
    playableStyle: Int? = null,
    singleItemStyle: Int? = null,
  ): Bundle =
    Bundle().apply {
      browsableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it) }
      playableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it) }
      singleItemStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, it) }
    }

  private fun MediaLibraryService.LibraryParams?.withContentStyleHints(
    isAutomotive: Boolean
  ): MediaLibraryService.LibraryParams {
    val extras =
      Bundle(this?.extras ?: Bundle()).apply {
        if (isAutomotive) {
          putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
          )
          putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
          )
        }
      }

    return MediaLibraryService.LibraryParams.Builder()
      .setOffline(this?.isOffline ?: false)
      .setRecent(this?.isRecent ?: false)
      .setSuggested(this?.isSuggested ?: false)
      .setExtras(extras)
      .build()
  }

  private fun <T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize < 1 || isEmpty()) return emptyList()
    if (pageSize == Int.MAX_VALUE) return this

    val fromIndex = page.toLong() * pageSize
    if (fromIndex >= size) return emptyList()

    return subList(fromIndex.toInt(), minOf(fromIndex.toInt() + pageSize, size))
  }

  private companion object {
    const val HISTORY_SIZE = 50
    const val LIVE_UPDATE_DEBOUNCE_MS = 2_000L
  }
}
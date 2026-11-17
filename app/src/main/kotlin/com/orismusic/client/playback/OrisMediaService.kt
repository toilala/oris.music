package com.orismusic.client.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes as AndroidAudioAttributes
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.orismusic.client.utils.GuestAccessRefused
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.orismusic.client.MainActivity
import com.orismusic.client.R
import com.orismusic.client.constants.AudioNormalizationKey
import com.orismusic.client.constants.AudioOffload
import com.orismusic.client.constants.AudioQualityKey
import com.orismusic.client.constants.CrossfadeDurationKey
import com.orismusic.client.constants.CrossfadeEnabledKey
import com.orismusic.client.constants.CrossfadeGaplessKey
import com.orismusic.client.constants.HideExplicitKey
import com.orismusic.client.constants.HideVideoSongsKey
import com.orismusic.client.constants.InfiniteQueueKey
import com.orismusic.client.constants.MixWithOtherAppsKey
import com.orismusic.client.constants.PauseOnMute
import com.orismusic.client.constants.PersistentQueueKey
import com.orismusic.client.constants.PlayerVolumeKey
import com.orismusic.client.constants.RepeatModeKey
import com.orismusic.client.constants.ResumeOnBluetoothConnectKey
import com.orismusic.client.constants.ShuffleModeKey
import com.orismusic.client.constants.ShufflePlaylistFirstKey
import com.orismusic.client.constants.SkipSilenceInstantKey
import com.orismusic.client.constants.SkipSilenceKey
import com.orismusic.client.constants.StopPlaybackOnTaskRemovedKey
import com.orismusic.client.constants.IpVersionKey
import com.orismusic.client.constants.transitionModeOf
import com.orismusic.client.di.AudioFxService
import com.orismusic.client.di.AudioLabStore
import com.orismusic.client.di.AudioSpectrum
import com.orismusic.client.di.CarLyrics
import com.orismusic.client.di.CastConnectionHandler
import com.orismusic.client.di.CrossmixTransitionAudioProcessor
import com.orismusic.client.di.CustomEqualizerAudioProcessor
import com.orismusic.client.di.CustomStreamResult
import com.orismusic.client.di.DiscordPresence
import com.orismusic.client.di.DownloadCache
import com.orismusic.client.di.EqualizerService
import com.orismusic.client.di.ForcedRoute
import com.orismusic.client.di.HapticAudioProcessor
import com.orismusic.client.di.LoudnessEnhancer
import com.orismusic.client.di.LyricsHelper
import com.orismusic.client.di.MochyWidgetManager
import com.orismusic.client.di.MusicDatabase
import com.orismusic.client.di.NetworkConnectivityObserver
import com.orismusic.client.di.PlayerCache
import com.orismusic.client.di.ReplayGainTags
import com.orismusic.client.di.Scrobbler
import com.orismusic.client.di.SilenceDetectorAudioProcessor
import com.orismusic.client.di.SleepTimer
import com.orismusic.client.di.SongTuning
import com.orismusic.client.di.SourceModules
import com.orismusic.client.di.SyncUtils
import com.orismusic.client.di.WatchEndpoint
import com.orismusic.client.playback.queues.EmptyQueue
import com.orismusic.client.playback.queues.ListQueue
import com.orismusic.client.playback.queues.Queue
import com.orismusic.client.utils.YTPlayerUtils
import com.orismusic.client.utils.YTPlayerUtils.PlaybackData
import com.orismusic.client.utils.dataStore
import com.orismusic.client.utils.dataStoreGet
import com.orismusic.client.utils.get
import com.orismusic.client.utils.reportException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.plus
import okhttp3.OkHttpClient
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L
private const val SpeedApplyGapMs = 60L
private const val PositionWakeUs = 100_000L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
/** A stream that failed at [startedAt] (elapsedRealtime) and skips to the next track [timeoutMs] later. */
data class StreamFailure(val startedAt: Long, val timeoutMs: Long)

@AndroidEntryPoint
class OrisMediaService : MediaLibraryService(), Player.Listener, PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var audioLab: AudioLabStore

    @Inject
    lateinit var audioFx: AudioFxService

    @Inject
    lateinit var songTuning: SongTuning

    @Inject
    lateinit var scrobbler: Scrobbler

    @Inject
    lateinit var discordPresence: DiscordPresence

    @Inject
    lateinit var sourceModules: SourceModules

    @Inject
    lateinit var widgetManager: MochyWidgetManager

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var reentrantFocusGain = false
    private var mixWithOtherApps = false
    private var wasPlayingBeforeCall = false
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    var preferredDeviceId: Int? = null
        private set

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossmixMode = CrossmixMode.OFF
    private var crossfadeTriggerJob: Job? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag("OrisMediaService").e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer?.let { failed ->
                failed.removeListener(this)
                playerCrossmixProcessors.remove(failed)
                playerSilenceProcessors.remove(failed)
                failed.release()
            }
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: OrisMediaService
            get() = this@OrisMediaService
    }

    // MainActivity binds with an explicit intent (no action); media session
    // controllers always use MediaSessionService's interface action. Route
    // the local binding to MusicBinder, keep the session contract for the rest.
    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == null) binder else super.onBind(intent)

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    val streamFailure = MutableStateFlow<StreamFailure?>(null)
    private var failureJob: Job? = null
    private val isNetworkConnected = MutableStateFlow(false)

    private lateinit var audioQuality: AudioQuality
    private lateinit var ipVersion: Any // IpVersion placeholder

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    private var queueGeneration = 0
    private var initialQueueLoading = false
    private var runwayJob: Job? = null

    private var infiniteSource: Queue? = null
    private val usedInfiniteSeeds = java.util.HashSet<String>()
    private val failedInfiniteSeeds = java.util.HashSet<String>()

    private var runwayRetryJob: Job? = null
    private var runwayRetries = 0

    private var pendingInfiniteQueue: Boolean? = null

    private var autoplayPreviewRetryAt = 0L

    val currentMediaMetadata = MutableStateFlow<androidx.media3.common.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                kotlinx.coroutines.flow.flow { emit(null) }
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            kotlinx.coroutines.flow.flow { emit(null) }
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState

        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted

        player.volume = if (muted) 0f else playerVolume.value
    }

    fun setPreferredAudioDevice(deviceId: Int?) {
        preferredDeviceId = deviceId
    }

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    private val resolvedStreamSources = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val failedSources = java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>>()
    private val failedSourcesAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val moduleStreamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private val resolvedSourceIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val forcedRoutes = java.util.concurrent.ConcurrentHashMap<String, ForcedRoute>()
    private val _playingFrom = MutableStateFlow<Map<String, CustomStreamResult>>(emptyMap())
    val playingFrom = _playingFrom.asStateFlow()
    val streamFacts = MutableStateFlow<Map<String, CustomStreamResult>>(emptyMap())
    private data class DecoderNotice(val text: String, val from: String?)
    private val decoderNotices = java.util.concurrent.ConcurrentHashMap<String, DecoderNotice>()
    private var lastAnnouncedSourceMediaId: String? = null

    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    private var isCrossfading = false
    private var crossfadeJob: Job? = null

    @Volatile private var skipBlend = false
    @Volatile private var blendAhead = 1
    private var prefetchJob: Job? = null

    @Volatile private var studioForVideos = false
    private var fadingLoudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
    private var promotingCrossmix = false

    // Minimal TransportTransitions
    val transport = object {
        val host = object {}
    }

    val crossmixMixing = MutableStateFlow(false)
    val crossmixStatus = MutableStateFlow("")

    fun testCrossmix() {
        // Crossmix test
    }

    private val playingStreams = java.util.concurrent.ConcurrentHashMap<String, Any>() // CustomStreamResult placeholder
    private val listeningOnlyStreams: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // Minimal VideoAudioSync (as regular class)
    private class VideoAudioSync {
        inner class Source(
            val uri: android.net.Uri,
            val key: String,
            val listeningOnly: Boolean
        )
        inner class SongVideoMap(
            val songStartMs: Long,
            val videoStartMs: Long,
            val confidence: Float
        )
        suspend fun match(source1: Source, source2: Source): SongVideoMap? = null
    }
    private val videoAudioSync = VideoAudioSync()

    suspend fun matchSongToVideo(songId: String, videoId: String): Any? {
        return null
    }

    private fun lyricsAudioSource(item: MediaItem): Pair<android.net.Uri, String> {
        val stream = playingStreams[item.mediaId]
        if (stream != null) {
            // placeholder for stream.streamUrl and stream.cacheKey
        }
        return (item.localConfiguration?.uri ?: item.mediaId.toUri()) to item.mediaId
    }

    // Minimal LyricsAutoSync
    private val lyricsAutoSync = object {
        suspend fun start(
            songId: String,
            uri: android.net.Uri,
            key: String,
            durationMs: Long,
            lines: List<Any>,
            currentOffsetMs: Int
        ) {}
    }

    fun autoSyncLyrics(lines: List<Any>, currentOffsetMs: Int) {
        // placeholder
    }

    private fun announceLyricsSync(state: Any) {
        // Handle lyrics sync result
    }

    private data class CrossfadeConfig(
        val mode: CrossmixMode,
        val enabled: Boolean,
        val durationSeconds: Float,
        val disableForGaplessAlbums: Boolean,
    )

    private lateinit var mediaSession: MediaLibrarySession

    private var carLyrics: CarLyrics? = null

    val platformSessionToken: android.media.session.MediaSession.Token
        get() = mediaSession.platformToken

    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: StateFlow<Boolean> = playerInitialized.asStateFlow()

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = java.util.HashMap<Player, SilenceDetectorAudioProcessor>()
    private val playerReplayGain = java.util.HashMap<Player, ReplayGainTags>()
    private val playerEqProcessors = java.util.HashMap<Player, CustomEqualizerAudioProcessor>()
    private val playerCrossmixProcessors = java.util.HashMap<Player, CrossmixTransitionAudioProcessor>()
    private val playerSpectra = java.util.WeakHashMap<Player, AudioSpectrum>()

    fun spectrumOf(player: Player): AudioSpectrum? = playerSpectra[player]

    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var hapticAudioProcessor: HapticAudioProcessor? = null

    // Minimal ScrobbleTimer
    private val scrobbleTimer = object {
        val scopeProvider: () -> CoroutineScope = { scope }
        val scrobbler: Any by lazy { this@OrisMediaService.scrobbler }
    }

    // Minimal YouTubePlayReporter
    private val playReporter = object {
        val scopeProvider: () -> CoroutineScope = { scope }
        val getCurrentPosition: () -> Long = { player.currentPosition }
    }

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    private val songUrlCache = java.util.HashMap<String, Pair<String, Long>>()

    private val songStreamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    private val musicVideoAudioSongs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var lastMusicVideoNoticeId: String? = null

    private val bypassCacheForQualityChange = java.util.HashSet<String>()

    private var currentMediaIdRetryCount = java.util.HashMap<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L
    private val STREAM_RETRY_INTERVAL_MS = 5000L

    private val recentlyFailedSongs = java.util.HashSet<String>()
    private var failedSongsClearJob: Job? = null

    var castConnectionHandler: CastConnectionHandler? = null
        private set

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true

            if (hasBluetooth) {
                if (dataStoreGet(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        playerInitialized.value = false
        com.orismusic.client.utils.InnerTubeXResolver.resumeBackgroundWork()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.music_player),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val pending = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag("OrisMediaService").e(e, "Failed to create foreground notification")
            reportException(e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
        )
        player = createExoPlayer()
        _playerFlow.value = player
        player.addListener(this)
        EqualizerController.watch(this, player, player)
        playerInitialized.value = true
        Timber.d("OrisMediaService created")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocus()

        mediaLibrarySessionCallback.apply {
            service = this@OrisMediaService
        }
        mediaSession =
            MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()


        connectivityManager = getSystemService(ConnectivityManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }

        loadPreferences()
        applyAudioQualitySettings()
    }

    private fun loadPreferences() {
        scope.launch {
            dataStore.data.collect { prefs ->
                crossfadeEnabled = prefs[CrossfadeEnabledKey] ?: false
                crossfadeDuration = (prefs[CrossfadeDurationKey] ?: 5f) * 1000
                crossfadeGapless = prefs[CrossfadeGaplessKey] ?: true
                mixWithOtherApps = prefs[MixWithOtherAppsKey] ?: false
            }
        }
    }

    private fun applyAudioQualitySettings() {
        // Apply audio quality settings
    }

    private fun setupAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AndroidAudioAttributes.Builder()
                        .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                        .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(
                    { focusChange -> onAudioFocusChange(focusChange) },
                    Handler(Looper.getMainLooper())
                )
                .setWillPauseWhenDucked(true)
                .build()
        } else {
            legacyFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                onAudioFocusChange(focusChange)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.requestAudioFocus(legacyFocusListener!!, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            audioManager.abandonAudioFocus(legacyFocusListener!!)
        }
        hasAudioFocus = false
    }

    private fun onAudioFocusChange(focusChange: Int) {
        lastAudioFocusState = focusChange
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (wasPlayingBeforeAudioFocusLoss) {
                    wasPlayingBeforeAudioFocusLoss = false
                    if (!reentrantFocusGain) {
                        player.play()
                    }
                }
                reentrantFocusGain = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower volume temporarily
            }
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioSink = DefaultAudioSink.Builder()
            .setAudioProcessors(arrayOf(
                SonicAudioProcessor(),
            ))
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                500,
                500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory())
                .setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
                    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                        var cause: Throwable? = info.exception
                        repeat(12) {
                            if (cause is GuestAccessRefused) return C.TIME_UNSET
                            cause = cause?.cause
                        }
                        return super.getRetryDelayMsFor(info)
                    }
                }))
            .build()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createDataSourceFactory(): DataSource.Factory {
        val resolving = createResolvingFactory(this)
        if (!::downloadCache.isInitialized) return resolving
        // Songs saved for offline play come from the download cache; everything else streams.
        return androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(resolving)
    }

    private fun createLegacyResolvingFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("User-Agent" to "OrisMusic/1.0"))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        val baseFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        return ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val uri = dataSpec.uri
            val host = uri.host?.lowercase()
            val isYouTubeWatch = (host == "music.youtube.com" || host == "www.youtube.com" || host == "youtube.com") &&
                uri.path?.startsWith("/watch") == true
            if (!isYouTubeWatch) {
                dataSpec
            } else {
                val videoId = uri.getQueryParameter("v")
                if (videoId == null) {
                    dataSpec
                } else {
                    val data = runBlocking {
                        YTPlayerUtils.getPlaybackData(
                            videoId,
                            com.orismusic.client.constants.AudioQuality.AUTO,
                            this@OrisMediaService
                        )
                    }
                    dataSpec.buildUpon()
                        .setUri(data.streamUrl)
                        .setHttpRequestHeaders(dataSpec.httpRequestHeaders + YTPlayerUtils.headersFor(data.streamUrl))
                        .build()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return if (!resourcesReleased && ::mediaSession.isInitialized) {
            mediaSession
        } else {
            null
        }
    }

    private var resourcesReleased = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Do not resurrect an idle playback service after Android reclaims it.
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (com.orismusic.client.settings.PlaybackPreferences(this).keepPlayingAfterTaskRemoval) {
            Timber.i("Task removed: background playback retained by preference")
            return
        }
        releasePlaybackResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        stopSelf()
        Timber.i("Task removed: playback shutdown requested")
    }

    private fun releasePlaybackResources() {
        if (resourcesReleased) return
        resourcesReleased = true
        isRunning = false
        playerInitialized.value = false
        queueGeneration++
        scope.cancel()
        if (::player.isInitialized) player.removeListener(this)
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        secondaryPlayer?.release()
        secondaryPlayer = null
        fadingPlayer?.release()
        fadingPlayer = null
        abandonAudioFocus()
        if (::audioManager.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        com.orismusic.client.utils.InnerTubeXResolver.stopBackgroundWork()
        Timber.i("Playback resources released; service work cancelled")
    }

    override fun onDestroy() {
        releasePlaybackResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        Timber.i("OrisMediaService destroyed")
        super.onDestroy()
    }

    // Player.Listener callbacks
    override fun onPlaybackStateChanged(state: Int) {
        Timber.d("Playback state changed: $state")
        if (state == Player.STATE_READY || state == Player.STATE_ENDED) fillUpcomingMusic()
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.e(error, "Player error")
        handlePlaybackError(error)
    }

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        // Handle playback stats
    }

    private fun handlePlaybackError(error: PlaybackException) {
        // Handle playback errors, retry logic, etc.
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        currentMediaMetadata.value = mediaItem?.mediaMetadata
        fillUpcomingMusic()
    }

    private fun fillUpcomingMusic() {
        if (resourcesReleased || initialQueueLoading || runwayJob?.isActive == true || !currentQueue.hasNextPage()) return
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return
        val source = currentQueue
        val generation = queueGeneration
        runwayJob = scope.launch {
            try {
                val additions = source.nextPage()
                if (generation != queueGeneration || source !== currentQueue) return@launch
                val wasEnded = player.playbackState == Player.STATE_ENDED
                val firstAdded = player.mediaItemCount
                if (additions.isNotEmpty()) {
                    player.addMediaItems(additions)
                    if (wasEnded) { player.seekTo(firstAdded, 0L); player.prepare() }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.w(failure, "Could not extend music queue; existing songs remain playable")
            }
        }
    }

    // Queue methods for PlayerConnection
    fun playQueue(queue: Queue, playWhenReady: Boolean = true) {
        queueGeneration++
        val generation = queueGeneration
        runwayJob?.cancel()
        scope.launch {
            initialQueueLoading = true
            try {
                val status = queue.getInitialStatus()
                if (generation != queueGeneration) return@launch
                currentQueue = queue
                queueTitle = status.title
                if (status.items.isNotEmpty()) {
                    player.setMediaItems(status.items, status.mediaItemIndex, status.position)
                    player.prepare()
                    player.playWhenReady = playWhenReady
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "Could not load selected queue")
            } finally {
                if (generation == queueGeneration) { initialQueueLoading = false; fillUpcomingMusic() }
            }
        }
    }

    fun startRadioSeamlessly(jumpToFirst: Boolean = false) {
        Timber.d("startRadioSeamlessly called")
    }

    fun playNext(items: List<MediaItem>) {
        Timber.d("playNext called with ${items.size} items")
        val insertIndex = player.currentMediaItemIndex + 1
        player.addMediaItems(insertIndex, items)
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        Timber.d("addToQueue called with ${items.size} items")
        player.addMediaItems(items)
        player.prepare()
    }

    data class ClearedQueueState(
        val removedItems: List<MediaItem>,
        val startIndex: Int,
        val previousQueue: Queue,
        val previousQueueTitle: String?,
        val originalQueueSize: Int,
        val currentMediaId: String?,
        val queueGeneration: Long,
        val wasShuffled: Boolean,
        val preservedPlaybackOrder: List<Int>?,
    )

    fun clearQueue(): ClearedQueueState? {
        if (player.mediaItemCount == 0) return null
        val currentIndex = player.currentMediaItemIndex
        val upcomingItems = mutableListOf<MediaItem>()
        for (i in currentIndex + 1 until player.mediaItemCount) {
            upcomingItems.add(player.getMediaItemAt(i))
        }
        if (upcomingItems.isEmpty()) return null

        player.removeMediaItems(currentIndex + 1, player.mediaItemCount)

        return ClearedQueueState(
            removedItems = upcomingItems,
            startIndex = currentIndex + 1,
            previousQueue = currentQueue,
            previousQueueTitle = queueTitle,
            originalQueueSize = originalQueueSize,
            currentMediaId = player.currentMediaItem?.mediaId,
            queueGeneration = queueGeneration.toLong(),
            wasShuffled = player.shuffleModeEnabled,
            preservedPlaybackOrder = null,
        )
    }

    fun restoreQueue(state: ClearedQueueState) {
        player.addMediaItems(state.startIndex, state.removedItems)
        currentQueue = state.previousQueue
        queueTitle = state.previousQueueTitle
        originalQueueSize = state.originalQueueSize
    }

    fun toggleLike() {
        Timber.d("toggleLike called")
    }

    fun toggleLibrary() {
        Timber.d("toggleLibrary called")
    }

    fun toggleLikeForMediaId(mediaId: String) {
        Timber.d("toggleLikeForMediaId: $mediaId")
    }

    fun playNextMediaId(mediaId: String) {
        Timber.d("playNextMediaId: $mediaId")
    }

    fun dismissFromQueue(mediaId: String) {
        Timber.d("dismissFromQueue: $mediaId")
    }

    fun followArtist(mediaId: String) {
        Timber.d("followArtist: $mediaId")
    }

    fun unfollowArtist(mediaId: String) {
        Timber.d("unfollowArtist: $mediaId")
    }

    companion object {
        const val CHANNEL_ID = "media_playback"
        const val NOTIFICATION_ID = 1
        var isRunning = false
    }

    // AudioQuality enum for settings
    enum class AudioQuality {
        LOW, MEDIUM, HIGH, LOSSLESS, AUTO
    }

    // CrossmixMode enum
    enum class CrossmixMode {
        OFF, CROSSFADE, AUTOMIX
    }
}
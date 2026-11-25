package com.orismusic.client.constants

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.Player

// Playback preferences
val AudioQualityKey = stringPreferencesKey("audioQuality")

enum class AudioQuality {
    AUTO,
    LOW,
    MEDIUM,
    HIGH,
    LOSSLESS,
    HI_RES_LOSSLESS,
    DOLBY_ATMOS;

    val permitsHigh: Boolean
        get() = this != LOW && this != MEDIUM

    val maxKbps: Int
        get() = when (this) {
            LOW -> 64
            MEDIUM -> 128
            HIGH -> 256
            LOSSLESS -> 1411
            HI_RES_LOSSLESS -> 9216
            DOLBY_ATMOS -> 768
            AUTO -> Int.MAX_VALUE
        }

    companion object {
        fun fromString(value: String?): AudioQuality {
            return value?.let { AudioQuality.entries.find { it.name == value } } ?: AUTO
        }
    }
}

// Audio focus and behavior
val MixWithOtherAppsKey = booleanPreferencesKey("mixWithOtherApps")
val PauseOnMute = booleanPreferencesKey("pauseOnMute")
val ResumeOnBluetoothConnectKey = booleanPreferencesKey("resumeOnBluetoothConnect")
val StopPlaybackOnTaskRemovedKey = booleanPreferencesKey("stopPlaybackOnTaskRemoved")

// Queue preferences
val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PersistentShuffleAcrossQueuesKey = booleanPreferencesKey("persistentShuffleAcrossQueues")
val RememberShuffleAndRepeatKey = booleanPreferencesKey("rememberShuffleAndRepeat")
val ShuffleModeKey = booleanPreferencesKey("shuffleMode")
val RepeatModeKey = intPreferencesKey("repeatMode")
val ShufflePlaylistFirstKey = booleanPreferencesKey("shufflePlaylistFirst")
val PreventDuplicateTracksInQueueKey = booleanPreferencesKey("preventDuplicateTracksInQueue")
val InfiniteQueueKey = booleanPreferencesKey("infiniteQueue")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")

// Audio quality per network
val AudioQualityWifiKey = stringPreferencesKey("audioQualityWifi")
val AudioQualityCellularKey = stringPreferencesKey("audioQualityCellular")
val DataSaverEnabledKey = booleanPreferencesKey("dataSaverEnabled")

// Stream error handling
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val DontSkipOnFailureKey = booleanPreferencesKey("dontSkipOnFailure")
val StreamFailureTimeoutKey = intPreferencesKey("streamFailureTimeout")
val ForcePlaybackKey = booleanPreferencesKey("forcePlayback")

// Silence skipping
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val SkipSilenceInstantKey = booleanPreferencesKey("skipSilenceInstant")

// Audio normalization
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")


// Volume
val PlayerVolumeKey = floatPreferencesKey("playerVolume")

// Explicit content
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")

// IP Version
val IpVersionKey = stringPreferencesKey("ipVersion")

// Offline mode
val OfflineModeKey = booleanPreferencesKey("offlineMode")

// Scrobbling
val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")

// History
val HistoryDuration = floatPreferencesKey("historyDuration")
val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")

// Studio for videos
val StudioForVideosKey = booleanPreferencesKey("studioForVideos")

// Transport fades
val TransportFadeEnabledKey = booleanPreferencesKey("transportFadeEnabled")
val TransportFadeSecondsKey = floatPreferencesKey("transportFadeSeconds")
val TransportEffectKey = stringPreferencesKey("transportEffect")

const val TransportFadeDefaultSeconds = 2f
const val TransportFadeMinSeconds = 0.5f
const val TransportFadeMaxSeconds = 10f

enum class TransportEffect(val label: String) {
    OFF("None"),
    ECHO("Echo"),
    REWIND("Rewind"),
    TAPE_STOP("Tape stop"),
    FAR_AWAY("Fade to far"),
    REVERB("Reverb wash"),
    FILTER("Filter sweep"),
}

// Skip blend
val SkipBlendKey = booleanPreferencesKey("skipBlend")
val SkipBlendAheadKey = intPreferencesKey("skipBlendAhead")
const val SkipBlendAheadMax = 4

// Show stream source toast
val ShowStreamSourceToastKey = booleanPreferencesKey("showStreamSourceToast")

// YouTube sync
val YtReportPlaysKey = booleanPreferencesKey("ytReportPlays")

// Audio offload
val AudioOffload = booleanPreferencesKey("enableOffload")

// Similar content
val SimilarContent = booleanPreferencesKey("similarContent")

// Auto download on like
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")

// Download quality
val DownloadQualityKey = stringPreferencesKey("downloadQuality")

enum class DownloadQuality(val title: String, val description: String) {
    HIGH("High", "The best YouTube has: ~160 kbps Opus"),
    MEDIUM("Medium", "Around 128 kbps, half the space"),
    LOW("Low", "The smallest files, ~50–70 kbps"),
}

// YouTube session
val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")

// Show lyrics
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val PreloadLyricsEnabledKey = booleanPreferencesKey("preloadLyricsEnabled")
val PreloadNextSongEnabledKey = booleanPreferencesKey("preloadNextSongEnabled")
val PreloadNextSongLimitKey = intPreferencesKey("preloadNextSongLimit")

// Force Opus
val ForceOpusKey = booleanPreferencesKey("forceOpus")

// USB DAC
val EnableBitPerfectUsbDacKey = booleanPreferencesKey("enableBitPerfectUsbDac")
val PreferExtensionAudioKey = booleanPreferencesKey("preferExtensionAudio")

// Spatial audio
val SpatialAudioKey = booleanPreferencesKey("spatialAudioKey")

// Discord
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")
val DiscordTokenKey = stringPreferencesKey("discordToken")

// Last.fm
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val LastFMUseSendLikes = booleanPreferencesKey("lastfmUseSendLikes")

// Default values
const val DEFAULT_PLAYER_VOLUME = 1f
const val DEFAULT_REPEAT_MODE = Player.REPEAT_MODE_OFF
const val DEFAULT_SCRUBBLE_DELAY_PERCENT = 0.5f
const val DEFAULT_SCRUBBLE_DELAY_SECONDS = 30
const val DEFAULT_SCRUBBLE_MIN_SONG_DURATION = 30
const val DEFAULT_AUDIO_QUALITY = "AUTO"
const val DEFAULT_IP_VERSION = "AUTO"
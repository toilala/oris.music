package com.orismusic.client.constants

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.Player

object AudioNormalizationKey : booleanPreferencesKey("audio_normalization")
object AudioOffload : booleanPreferencesKey("audio_offload")
object AudioQualityKey : stringPreferencesKey("audio_quality")
object AutoDownloadOnLikeKey : booleanPreferencesKey("auto_download_on_like")
object AutoLoadMoreKey : booleanPreferencesKey("auto_load_more")
object AutoSkipNextOnErrorKey : booleanPreferencesKey("auto_skip_next_on_error")
object CrossfadeDurationKey : floatPreferencesKey("crossfade_duration")
object CrossfadeEnabledKey : booleanPreferencesKey("crossfade_enabled")
object CrossfadeGaplessKey : booleanPreferencesKey("crossfade_gapless")
object DisableLoadMoreWhenRepeatAllKey : booleanPreferencesKey("disable_load_more_when_repeat_all")
object HideExplicitKey : booleanPreferencesKey("hide_explicit")
object HideVideoSongsKey : booleanPreferencesKey("hide_video_songs")
object InfiniteQueueKey : booleanPreferencesKey("infinite_queue")
object IpVersionKey : stringPreferencesKey("ip_version")
object MixWithOtherAppsKey : booleanPreferencesKey("mix_with_other_apps")
object PauseListenHistoryKey : booleanPreferencesKey("pause_listen_history")
object PauseOnMute : booleanPreferencesKey("pause_on_mute")
object PersistentQueueKey : booleanPreferencesKey("persistent_queue")
object PersistentShuffleAcrossQueuesKey : booleanPreferencesKey("persistent_shuffle_across_queues")
object PlayerVolumeKey : floatPreferencesKey("player_volume")
object PreventDuplicateTracksInQueueKey : booleanPreferencesKey("prevent_duplicate_tracks_in_queue")
object RememberShuffleAndRepeatKey : booleanPreferencesKey("remember_shuffle_and_repeat")
object RepeatModeKey : intPreferencesKey("repeat_mode")
object ResumeOnBluetoothConnectKey : booleanPreferencesKey("resume_on_bluetooth_connect")
object ScrobbleDelayPercentKey : floatPreferencesKey("scrobble_delay_percent")
object ScrobbleDelaySecondsKey : intPreferencesKey("scrobble_delay_seconds")
object ScrobbleMinSongDurationKey : intPreferencesKey("scrobble_min_song_duration")
object ShowLyricsKey : booleanPreferencesKey("show_lyrics")
object ShuffleModeKey : booleanPreferencesKey("shuffle_mode")
object ShufflePlaylistFirstKey : booleanPreferencesKey("shuffle_playlist_first")
object SkipSilenceInstantKey : booleanPreferencesKey("skip_silence_instant")
object SkipSilenceKey : booleanPreferencesKey("skip_silence")
object StopPlaybackOnTaskRemovedKey : booleanPreferencesKey("stop_playback_on_task_removed")

// Media Session Command Constants
object MediaSessionConstants {
    object CommandToggleLike : intPreferencesKey("command_toggle_like")
    object CommandToggleRepeatMode : intPreferencesKey("command_toggle_repeat_mode")
    object CommandToggleShuffle : intPreferencesKey("command_toggle_shuffle")
    object CommandToggleStartRadio : intPreferencesKey("command_toggle_start_radio")
    object CommandItemLike : intPreferencesKey("command_item_like")
    object CommandItemPlayNext : intPreferencesKey("command_item_play_next")
    object CommandItemDismiss : intPreferencesKey("command_item_dismiss")
    object CommandItemFollow : intPreferencesKey("command_item_follow")
    object CommandItemUnfollow : intPreferencesKey("command_item_unfollow")
    object CommandEpisodeSkipBack : intPreferencesKey("command_episode_skip_back")
    object CommandEpisodeSkipForward : intPreferencesKey("command_episode_skip_forward")
    object CommandEpisodeSpeed : intPreferencesKey("command_episode_speed")
}

// Default values
const val DEFAULT_CROSSFADE_DURATION = 5f
const val DEFAULT_PLAYER_VOLUME = 1f
const val DEFAULT_REPEAT_MODE = Player.REPEAT_MODE_OFF
const val DEFAULT_SCRUBBLE_DELAY_PERCENT = 0.5f
const val DEFAULT_SCRUBBLE_DELAY_SECONDS = 30
const val DEFAULT_SCRUBBLE_MIN_SONG_DURATION = 30
const val DEFAULT_AUDIO_QUALITY = "AUTO"
const val DEFAULT_IP_VERSION = "AUTO"
package com.orismusic.client.playback

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.orismusic.client.library.SavedSong
import com.orismusic.client.library.toSavedSong

/** Records listening history from the player's own event batches.
 *
 *  Attached to the ExoPlayer in OrisMediaService, not to a UI-side controller: history
 *  keeps recording while no Activity exists, and only the session-side timeline carries
 *  real window uids (Timeline.Window.toBundle omits uid, so every controller-side copy
 *  holds a fake per-copy uid object).
 *
 *  All evaluation happens inside onEvents: Media3 guarantees that getter combinations
 *  (current item, timeline, playing state) are only consistent there, and that one
 *  Looper iteration's changes arrive in a single batch. An entry is written only when
 *  that coherent snapshot shows an item genuinely playing: STATE_READY, isPlaying, no
 *  player error, and a known window uid that differs from the last recorded one.
 *
 *  The uid is per queue insertion (each media source holder gets a fresh object), so a
 *  queue replaced with the same song records again, a repeat-one loop keeps the same
 *  uid and does not duplicate, and pause/resume of one instance records once. A
 *  restored-but-paused queue never records, because isPlaying stays false until the
 *  user actually resumes.
 */
class PlaybackHistoryRecorder(
    private val record: (SavedSong) -> Unit,
) : Player.Listener {

    /** One consistent player snapshot, taken inside onEvents. */
    internal data class Snapshot(
        val isPlaying: Boolean,
        val playbackState: Int,
        val hasError: Boolean,
        val itemUid: Any?,
        val song: SavedSong?,
    )

    private var lastRecordedUid: Any? = null

    override fun onEvents(player: Player, events: Player.Events) {
        if (!shouldEvaluate(events)) return
        evaluate(snapshotOf(player))
    }

    /** The batches worth one eligibility check: item, playing, state, timeline, and
     *  error changes (an error clearing can make an item eligible again). */
    internal fun shouldEvaluate(events: Player.Events): Boolean =
        events.containsAny(
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_PLAYER_ERROR,
        )

    internal fun evaluate(snap: Snapshot) {
        if (snap.hasError) return
        if (snap.playbackState != Player.STATE_READY) return
        if (!snap.isPlaying) return
        val uid = snap.itemUid ?: return
        val song = snap.song ?: return
        if (uid == lastRecordedUid) return
        lastRecordedUid = uid
        record(song)
    }

    /** Maps player getters to a snapshot. Unreadable or unsupported items map to null
     *  fields and never produce a record, so a broken item cannot consume the dedupe
     *  uid ahead of a valid one. */
    internal fun snapshot(
        timeline: Timeline,
        index: Int,
        isPlaying: Boolean,
        playbackState: Int,
        hasError: Boolean,
        item: androidx.media3.common.MediaItem?,
    ): Snapshot {
        val window = Timeline.Window()
        val uid = if (!timeline.isEmpty && index >= 0 && index < timeline.windowCount) {
            timeline.getWindow(index, window).uid
        } else {
            null
        }
        return Snapshot(
            isPlaying = isPlaying,
            playbackState = playbackState,
            hasError = hasError,
            itemUid = uid,
            song = item?.toSavedSong(),
        )
    }

    private fun snapshotOf(player: Player): Snapshot = runCatching {
        snapshot(
            timeline = player.currentTimeline,
            index = player.currentMediaItemIndex,
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
            hasError = player.playerError != null,
            item = player.currentMediaItem,
        )
    }.getOrElse {
        Snapshot(isPlaying = false, playbackState = Player.STATE_IDLE, hasError = true,
            itemUid = null, song = null)
    }
}

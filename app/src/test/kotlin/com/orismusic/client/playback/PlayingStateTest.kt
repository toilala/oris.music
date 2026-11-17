package com.orismusic.client.playback

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Local Media3 contract check. No service, internet, or audio source needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PlayingStateTest {
    private class ControlledPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var snapshot = State.Builder().build()
        override fun getState(): State = snapshot
        fun transition(playback: Int, ready: Boolean, suppressed: Boolean = false) {
            snapshot = State.Builder()
                .setPlaylist(listOf(MediaItemData.Builder("local-contract-test").build()))
                .setPlaybackState(playback)
                .setPlayWhenReady(ready, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackSuppressionReason(if (suppressed)
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                    else Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                .build()
            invalidateState()
        }
    }
    @Test fun actualPlaybackTracksReadinessAndSuppressionNotIntent() {
        val player = ControlledPlayer()
        assertFalse(player.isPlaying)
        player.transition(Player.STATE_IDLE, true)
        assertFalse(player.isPlaying)
        player.transition(Player.STATE_BUFFERING, true)
        assertFalse(player.isPlaying)
        player.transition(Player.STATE_READY, true)
        assertTrue(player.isPlaying)
        player.transition(Player.STATE_READY, true, suppressed = true)
        assertFalse(player.isPlaying)
        player.transition(Player.STATE_READY, false)
        assertFalse(player.isPlaying)
        player.transition(Player.STATE_ENDED, true)
        assertFalse(player.isPlaying)
        player.transition(Player.STATE_IDLE, false)
        assertFalse(player.isPlaying)
        player.release()
    }
}

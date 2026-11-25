package com.orismusic.client.playback

/** Counts songs that gave up in a row, so a dying queue pauses and says why instead of
 *  skipping forever. A real playback start clears the streak, and so does any explicit
 *  user action (picking a song or queue, or pressing play again after the pause), so a
 *  fresh attempt never inherits an exhausted streak from an unrelated queue. */
class ConsecutiveFailureGuard(val limit: Int = 3) {
    var streak = 0
        private set

    /** One more song exhausted its retries. True once the streak reaches the limit. */
    fun onSongGaveUp(): Boolean {
        streak++
        return streak >= limit
    }

    /** The player itself reported real playback. */
    fun onPlaybackStarted() {
        streak = 0
    }

    /** The user selected something or pressed play. */
    fun onUserAction() {
        streak = 0
    }
}

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

/** What happens when a song's retries run out. */
enum class RetriesExhausted { SkipToNext, PauseForStreak, StopInPlace }

/** Decides the end of the retry path. Only a queue with somewhere left to go feeds the
 *  streak guard; a trailing or only song (or the pinned first song) stops in place with
 *  its own reason, so a one-song queue never invents a streak. */
fun retriesExhausted(pinned: Boolean, hasNext: Boolean, guard: ConsecutiveFailureGuard): RetriesExhausted =
    if (!pinned && hasNext) {
        if (guard.onSongGaveUp()) RetriesExhausted.PauseForStreak else RetriesExhausted.SkipToNext
    } else {
        RetriesExhausted.StopInPlace
    }

/** Why playback paused after the guard's streak. */
fun streakPauseMessage(limit: Int): String =
    "Playback paused: $limit songs failed in a row. Tap play to try again."

/** Why playback stopped when there was no next song to walk to. No try count: each app
 *  retry wraps the loader's own attempts, so any single number would be wrong. */
fun terminalStopMessage(): String =
    "Playback stopped: this song failed to load after retrying. Tap play to try again."

package com.orismusic.client.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.orismusic.client.library.SavedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the history recording rule against batched onEvents snapshots: an entry exists
 * only when one coherent snapshot shows the item genuinely playing (READY + isPlaying
 * + no error), deduped by the queue-insertion uid. Snapshots model what the player
 * getters report inside a single batch, not an invented event order. The adapter
 * tests run the production event filter and the production timeline/item-to-song
 * mapping; only the six player getter calls themselves are outside the seam.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaybackHistoryRecorderTest {

    private val songA = SavedSong(id = "a", title = "Song A", artist = "Artist", thumbnail = null)
    private val songB = SavedSong(id = "b", title = "Song B", artist = "Artist", thumbnail = null)

    private class Sink {
        val recorded = mutableListOf<SavedSong>()
    }

    private fun snap(
        uid: Any?,
        song: SavedSong?,
        isPlaying: Boolean = true,
        state: Int = Player.STATE_READY,
        hasError: Boolean = false,
    ) = PlaybackHistoryRecorder.Snapshot(isPlaying, state, hasError, uid, song)

    private fun recorderWith(sink: Sink) = PlaybackHistoryRecorder { sink.recorded.add(it) }

    @Test
    fun `a genuinely playing item records exactly once across repeated batches`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uid = Any()
        r.evaluate(snap(uid, songA))
        // The same state re-evaluated (a later unrelated batch, a second evaluation of
        // the same iteration) never duplicates.
        r.evaluate(snap(uid, songA))
        r.evaluate(snap(uid, songA))
        assertEquals(listOf(songA), sink.recorded)
    }

    @Test
    fun `seamless next track records with no isPlaying edge`() {
        val sink = Sink()
        val r = recorderWith(sink)
        r.evaluate(snap(Any(), songA))
        // Auto-advance that stayed READY and playing: one snapshot, new uid.
        r.evaluate(snap(Any(), songB))
        assertEquals(listOf(songA, songB), sink.recorded)
    }

    @Test
    fun `restored or skipped item while paused never records until actually played`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uid = Any()
        // Restored paused queue / paused skip: READY but not playing.
        r.evaluate(snap(uid, songA, isPlaying = false))
        assertEquals(emptyList<SavedSong>(), sink.recorded)
        // The user resumes: same instance now genuinely playing.
        r.evaluate(snap(uid, songA))
        assertEquals(listOf(songA), sink.recorded)
    }

    @Test
    fun `buffer then failure never records`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uid = Any()
        r.evaluate(snap(uid, songA, isPlaying = false, state = Player.STATE_BUFFERING))
        r.evaluate(snap(uid, songA, isPlaying = false, state = Player.STATE_IDLE, hasError = true))
        r.evaluate(snap(uid, songA, isPlaying = false, state = Player.STATE_IDLE))
        assertEquals(emptyList<SavedSong>(), sink.recorded)
    }

    @Test
    fun `an errored snapshot is rejected even when it claims playing`() {
        val sink = Sink()
        val r = recorderWith(sink)
        r.evaluate(snap(Any(), songA, isPlaying = true, hasError = true))
        assertEquals(emptyList<SavedSong>(), sink.recorded)
    }

    @Test
    fun `a stall and recovery of the same instance does not duplicate`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uid = Any()
        r.evaluate(snap(uid, songA))
        r.evaluate(snap(uid, songA, isPlaying = false, state = Player.STATE_BUFFERING))
        r.evaluate(snap(uid, songA))
        assertEquals(listOf(songA), sink.recorded)
    }

    @Test
    fun `queue replaced with the same song is a new instance and records again`() {
        val sink = Sink()
        val r = recorderWith(sink)
        r.evaluate(snap(Any(), songA))
        r.evaluate(snap(Any(), songA)) // same song, new queue insertion uid
        assertEquals(listOf(songA, songA), sink.recorded)
    }

    @Test
    fun `returning to a prior song after another plays records it again`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uidA = Any()
        r.evaluate(snap(uidA, songA))
        r.evaluate(snap(Any(), songB))
        r.evaluate(snap(uidA, songA))
        assertEquals(listOf(songA, songB, songA), sink.recorded)
    }

    @Test
    fun `repeat-one replay keeps the same uid and does not duplicate`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uid = Any()
        r.evaluate(snap(uid, songA))
        // REPEAT transition: same uid, still playing.
        r.evaluate(snap(uid, songA))
        assertEquals(listOf(songA), sink.recorded)
    }

    @Test
    fun `unknown identity never records`() {
        val sink = Sink()
        val r = recorderWith(sink)
        r.evaluate(snap(null, songA))
        r.evaluate(snap(Any(), null))
        assertEquals(emptyList<SavedSong>(), sink.recorded)
    }

    // --- Adapter tests: the production event filter and snapshot mapping. ---

    private fun eventsOf(vararg events: Int): Player.Events =
        Player.Events(androidx.media3.common.FlagSet.Builder().addAll(*events).build())

    @Test
    fun `only item, playing, state, timeline and error batches trigger evaluation`() {
        val r = recorderWith(Sink())
        assertFalse(r.shouldEvaluate(eventsOf(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)))
        assertFalse(r.shouldEvaluate(eventsOf(Player.EVENT_VOLUME_CHANGED)))
        assertTrue(r.shouldEvaluate(eventsOf(Player.EVENT_MEDIA_ITEM_TRANSITION)))
        assertTrue(r.shouldEvaluate(eventsOf(Player.EVENT_IS_PLAYING_CHANGED)))
        assertTrue(r.shouldEvaluate(eventsOf(Player.EVENT_PLAYBACK_STATE_CHANGED)))
        assertTrue(r.shouldEvaluate(eventsOf(Player.EVENT_TIMELINE_CHANGED)))
        assertTrue(r.shouldEvaluate(eventsOf(Player.EVENT_PLAYER_ERROR)))
    }

    /** Minimal timeline whose window uids are the given identity objects. */
    private class FakeTimeline(private val uids: List<Any>) : Timeline() {
        override fun getWindowCount(): Int = uids.size
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.set(
                uids[windowIndex], MediaItem.EMPTY, null,
                0L, 0L, 0L, true, false, null, 0L, 60_000L, 0, 0, 0L,
            )
        override fun getPeriodCount(): Int = uids.size
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
            period.set(periodIndex, uids[periodIndex], periodIndex, 60_000L, 0L)
        override fun getIndexOfPeriod(uid: Any): Int = uids.indexOf(uid)
        override fun getUidOfPeriod(periodIndex: Int): Any = uids[periodIndex]
    }

    private fun item(id: String, title: String, artist: String, artwork: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artwork?.let(android.net.Uri::parse))
                    .build()
            )
            .build()

    @Test
    fun `snapshot maps the full song identity through the real timeline and item`() {
        val r = recorderWith(Sink())
        val uid = Any()
        val s = r.snapshot(
            timeline = FakeTimeline(listOf(uid)),
            index = 0,
            isPlaying = true,
            playbackState = Player.STATE_READY,
            hasError = false,
            item = item("a", "Song A", "Artist", "file:///cover.jpg"),
        )
        assertSame(uid, s.itemUid)
        assertEquals(
            SavedSong(id = "a", title = "Song A", artist = "Artist", thumbnail = "file:///cover.jpg"),
            s.song,
        )
    }

    @Test
    fun `unsupported metadata maps to no song and never consumes the dedupe uid`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val uid = Any()
        val timeline = FakeTimeline(listOf(uid))
        // Blank media id: toSavedSong refuses it.
        val broken = r.snapshot(timeline, 0, isPlaying = true,
            playbackState = Player.STATE_READY, hasError = false, item = item("", "Song A", "Artist"))
        assertNull(broken.song)
        r.evaluate(broken)
        assertEquals(emptyList<SavedSong>(), sink.recorded)
        // The same uid with a valid item still records: nothing was consumed.
        val fixed = r.snapshot(timeline, 0, isPlaying = true,
            playbackState = Player.STATE_READY, hasError = false, item = item("a", "Song A", "Artist"))
        r.evaluate(fixed)
        assertEquals(listOf(songA), sink.recorded)
    }

    @Test
    fun `invalid index yields no identity and no record`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val s = r.snapshot(FakeTimeline(listOf(Any())), 7, isPlaying = true,
            playbackState = Player.STATE_READY, hasError = false, item = item("a", "Song A", "Artist"))
        assertNull(s.itemUid)
        r.evaluate(s)
        assertEquals(emptyList<SavedSong>(), sink.recorded)
    }

    @Test
    fun `errored snapshot through the adapter never records`() {
        val sink = Sink()
        val r = recorderWith(sink)
        r.evaluate(r.snapshot(FakeTimeline(listOf(Any())), 0, isPlaying = true,
            playbackState = Player.STATE_READY, hasError = true, item = item("a", "Song A", "Artist")))
        assertEquals(emptyList<SavedSong>(), sink.recorded)
    }

    @Test
    fun `restored paused item then resume, through the production snapshot mapping`() {
        val sink = Sink()
        val r = recorderWith(sink)
        val timeline = FakeTimeline(listOf(Any()))
        r.evaluate(r.snapshot(timeline, 0, isPlaying = false,
            playbackState = Player.STATE_READY, hasError = false, item = item("a", "Song A", "Artist")))
        assertEquals(emptyList<SavedSong>(), sink.recorded)
        r.evaluate(r.snapshot(timeline, 0, isPlaying = true,
            playbackState = Player.STATE_READY, hasError = false, item = item("a", "Song A", "Artist")))
        assertEquals(listOf(songA), sink.recorded)
    }
}

package com.orismusic.client.playback

import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.orismusic.client.constants.RememberShuffleAndRepeatKey
import com.orismusic.client.constants.RepeatModeKey
import com.orismusic.client.constants.ShuffleModeKey
import com.orismusic.client.utils.dataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class) // Channel.isEmpty in the barrier test
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class ShuffleRepeatMemoryTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var testScope: CoroutineScope
    private lateinit var callScope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var memory: ShuffleRepeatMemory

    @Before
    fun setUp() {
        runBlocking { context.dataStore.edit { it.clear() } }
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // Separate scope for ack-awaiting setRemember calls, so tearing the
        // memory's scope down mid-test does not cancel the callers themselves.
        callScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        player = ExoPlayer.Builder(context).build()
        // Consumer PAUSED until each test calls startConsumer(): tests queue a
        // known command sequence and then release it - deterministic ordering,
        // no timing luck.
        memory = ShuffleRepeatMemory(context, testScope, player, autoStart = false)
        player.addListener(memory)
    }

    @After
    fun tearDown() {
        // Stop the consumer and wait for any in-flight transaction BEFORE the
        // player is released, so no test can leak a write into the next test's
        // freshly cleared store.
        testScope.cancel()
        runBlocking { testScope.coroutineContext.job.join() }
        callScope.cancel()
        runBlocking { callScope.coroutineContext.job.join() }
        player.release()
        runBlocking { context.dataStore.edit { it.clear() } }
    }

    private fun awaitTrue(timeoutMs: Long = 5000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runBlocking { condition() }) return
            Thread.sleep(25)
        }
        error("condition not met within ${timeoutMs}ms")
    }

    private suspend fun storedModes(): Pair<Boolean?, Int?> {
        val prefs = context.dataStore.data.first()
        return prefs[ShuffleModeKey] to prefs[RepeatModeKey]
    }

    @Test
    fun `restart restores the saved modes`() {
        runBlocking {
            context.dataStore.edit {
                it[ShuffleModeKey] = true
                it[RepeatModeKey] = Player.REPEAT_MODE_ONE
            }
        }
        assertEquals(true to Player.REPEAT_MODE_ONE, memory.savedModes())
    }

    @Test
    fun `remember off restores nothing`() {
        runBlocking {
            context.dataStore.edit {
                it[RememberShuffleAndRepeatKey] = false
                it[ShuffleModeKey] = true
                it[RepeatModeKey] = Player.REPEAT_MODE_ALL
            }
        }
        assertNull(memory.savedModes())
    }

    @Test
    fun `mode change on the player persists`() {
        memory.beginPersisting()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ONE
        memory.startConsumer()
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_ONE) }
    }

    @Test
    fun `switching modes back off persists the off state`() {
        memory.beginPersisting()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true
        memory.startConsumer()
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_ALL) }
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        awaitTrue { storedModes() == (false to Player.REPEAT_MODE_OFF) }
    }

    @Test
    fun `rapid changes end with the final state on disk`() {
        memory.beginPersisting()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        player.shuffleModeEnabled = true
        memory.startConsumer()
        // All six events were queued in order before the consumer ever ran.
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_OFF) }
        assertEquals(true, player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    @Test
    fun `events before beginPersisting never reach the channel`() {
        // Dropped at enqueue, synchronously, on this thread: nothing queued.
        player.shuffleModeEnabled = true
        assertTrue(memory.commands.isEmpty)
        memory.beginPersisting()
        player.repeatMode = Player.REPEAT_MODE_ONE
        memory.startConsumer()
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_ONE) }
    }

    @Test
    fun `queued pre-off snapshot held through off and on leaves no trace`() {
        memory.beginPersisting()
        player.repeatMode = Player.REPEAT_MODE_ONE // queued, consumer paused
        // Toggles enqueued asynchronously behind it, acks awaited after release.
        val offAck = CompletableDeferred<Boolean>()
        memory.commands.trySend(ShuffleRepeatMemory.Command.Remember(false, offAck))
        val onAck = CompletableDeferred<Boolean>()
        memory.commands.trySend(ShuffleRepeatMemory.Command.Remember(true, onAck))
        player.repeatMode = Player.REPEAT_MODE_ALL // queued behind both
        memory.startConsumer()
        // FIFO: snapshot(ONE) -> off -> on -> snapshot(ALL). Whether the first
        // snapshot writes-then-clears or is rejected, its REPEAT_MODE_ONE must
        // not survive; a FIFO violation (it processed last) would fail this.
        assertTrue(runBlocking { offAck.await() })
        assertTrue(runBlocking { onAck.await() })
        awaitTrue { storedModes() == (false to Player.REPEAT_MODE_ALL) }
    }

    @Test
    fun `remember off clears a persisted write atomically`() {
        memory.beginPersisting()
        player.shuffleModeEnabled = true
        memory.startConsumer()
        awaitTrue { storedModes().first == true } // snapshot persisted (positive)
        runBlocking { ShuffleRepeatMemory.setRemember(context, false) } // live consumer: ack awaits commit
        // The ack only completes after the transaction committed, so the clear
        // is already durable - no polling needed.
        assertEquals(null to null, runBlocking { storedModes() })
        assertNull(memory.savedModes())
    }

    @Test
    fun `rapid remember off on then a fresh change persists only the fresh state`() {
        memory.beginPersisting()
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ONE
        memory.startConsumer()
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_ONE) }
        runBlocking { ShuffleRepeatMemory.setRemember(context, false) }
        runBlocking { ShuffleRepeatMemory.setRemember(context, true) }
        // Off's ack means the clear committed; on's ack means remember=true is
        // durable; nothing pre-off can resurrect because savedModes needs keys.
        assertNull(memory.savedModes())
        player.repeatMode = Player.REPEAT_MODE_ALL
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_ALL) }
    }

    @Test
    fun `toggle without a live consumer writes the store directly`() {
        memory.startConsumer()
        // Kill the consumer; teardown deregisters the instance and closes the
        // channel, so the toggle falls back to a direct write.
        testScope.cancel()
        runBlocking { testScope.coroutineContext.job.join() }
        runBlocking { ShuffleRepeatMemory.setRemember(context, false) }
        val prefs = runBlocking { context.dataStore.data.first() }
        assertEquals(false, prefs[RememberShuffleAndRepeatKey])
        assertNull(memory.savedModes())
    }

    @Test
    fun `toggle queued on a consumer that never starts falls back and commits`() {
        // Enqueue asynchronously while the consumer is held (paused), then kill
        // the scope before startConsumer: teardown must ack false, and the call
        // must fall back to a direct write rather than vanish or hang.
        val call = callScope.async { ShuffleRepeatMemory.setRemember(context, false) }
        testScope.cancel()
        runBlocking { testScope.coroutineContext.job.join() }
        runBlocking { call.await() }
        val prefs = runBlocking { context.dataStore.data.first() }
        assertEquals(false, prefs[RememberShuffleAndRepeatKey])
    }

    @Test
    fun `capture runs on the player looper and the store transform off it`() {
        memory.beginPersisting()
        player.shuffleModeEnabled = true
        memory.startConsumer()
        awaitTrue { storedModes().first == true }
        assertEquals(Looper.getMainLooper().thread, memory.lastCaptureThread)
        val transformThread = memory.lastTransformThread
        assertTrue(transformThread != null && transformThread != Looper.getMainLooper().thread)
    }

    @Test
    fun `damaged repeat value falls back to off`() {
        runBlocking {
            context.dataStore.edit {
                it[ShuffleModeKey] = true
                it[RepeatModeKey] = 99
            }
        }
        assertEquals(true to Player.REPEAT_MODE_OFF, memory.savedModes())
    }

    @Test
    fun `repeat values are validated`() {
        assertEquals(Player.REPEAT_MODE_OFF, ShuffleRepeatMemory.normalizedRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ALL, ShuffleRepeatMemory.normalizedRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_ONE, ShuffleRepeatMemory.normalizedRepeatMode(Player.REPEAT_MODE_ONE))
        assertEquals(Player.REPEAT_MODE_OFF, ShuffleRepeatMemory.normalizedRepeatMode(7))
        assertEquals(Player.REPEAT_MODE_OFF, ShuffleRepeatMemory.normalizedRepeatMode(null))
    }

    @Test
    fun `replacing the queue keeps the current modes`() {
        // The restore path sets modes before the items go on the player; this pins
        // that setMediaItems neither resets them nor makes the listener rewrite them.
        memory.beginPersisting()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.shuffleModeEnabled = true
        memory.startConsumer()
        player.setMediaItems(
            listOf(
                MediaItem.fromUri("file:///a"),
                MediaItem.fromUri("file:///b"),
            ),
            1,
            0L,
        )
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
        assertTrue(player.shuffleModeEnabled)
        awaitTrue { storedModes() == (true to Player.REPEAT_MODE_ONE) }
    }
}

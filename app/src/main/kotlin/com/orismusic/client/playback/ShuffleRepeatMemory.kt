package com.orismusic.client.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import com.orismusic.client.constants.RememberShuffleAndRepeatKey
import com.orismusic.client.constants.RepeatModeKey
import com.orismusic.client.constants.ShuffleModeKey
import com.orismusic.client.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Remembers shuffle and repeat across restarts, as part of the persistent-queue
 * "back where you left off" behavior. The service owns this: it lives with the
 * playback session, so one Player.Listener here catches mode changes from
 * anywhere - the tray controls, the notification, or any other MediaController -
 * without each caller persisting on its own.
 *
 * ORDERING INVARIANT (the whole design). Every mode capture and every
 * remember-setting toggle enters ONE FIFO channel from the SAME thread - player
 * listener callbacks fire on the player's application looper (the main thread),
 * and the settings toggle posts from the main thread too - so channel order IS
 * the true action order. A single consumer coroutine applies the commands
 * serially and is the only writer of the three keys while it lives. Therefore:
 *  - a snapshot queued before a remember-off either writes and is then cleared
 *    by the off command's own transaction, or is skipped; it can never land
 *    AFTER the off, because the off precedes it in the channel;
 *  - the remember-off command's transaction atomically sets remember=false and
 *    removes both mode keys - one commit, no separate async clear that could
 *    run late and erase fresh state;
 *  - a snapshot queued after a remember-on persists normally;
 *  - no epochs or cached flags exist anywhere, so nothing can be mis-tagged.
 *
 * Lifecycle: routing, registration and close share one lock. [setRemember] is
 * completion-aware: it only reports success after the transaction actually
 * committed, and a command that can no longer be processed (channel closed,
 * consumer dead, cancellation) is acked as failed so the caller writes the
 * store directly instead - a toggle can never vanish, and nothing is replayed
 * into a newer instance.
 *
 * The listener is fully nonblocking: a capture is one immutable data class on
 * the looper plus trySend; all store I/O happens in the consumer. Nothing is
 * enqueued until the service calls [beginPersisting] after the startup restore
 * decision, so a restore read can never race a startup write and the restore's
 * own mode-set is never re-persisted.
 *
 * The Library "Shuffle" buttons are separate: they deal a shuffled list into a
 * fresh queue and do not touch the player's shuffle mode.
 */
class ShuffleRepeatMemory(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: Player,
    autoStart: Boolean = true,
) : Player.Listener {

    internal sealed interface Command {
        /** Immutable modes captured on the player's looper at event time. */
        data class Snapshot(val shuffle: Boolean, val repeat: Int) : Command

        /** [ack] completes true only after the transaction committed; false when
         *  the command could not be processed (teardown/cancellation), so the
         *  caller falls back to a direct write. */
        data class Remember(val on: Boolean, val ack: CompletableDeferred<Boolean>) : Command
    }

    internal val commands = Channel<Command>(Channel.UNLIMITED)

    /** Test seam: consumer starts only when told, so a test can queue a known
     *  command sequence and then release it - deterministic, no timing luck. */
    private var consumerStarted = false

    /** Thread evidence for tests: capture thread (must be the player looper) and
     *  the thread the store transform actually ran on (instrumented inside). */
    @Volatile internal var lastCaptureThread: Thread? = null
    @Volatile internal var lastTransformThread: Thread? = null

    /** Startup barrier: set once by the service after the restore decision.
     *  Checked at enqueue time (on the player's looper, in event order), so a
     *  pre-restore event never enters the channel at all. */
    @Volatile
    private var persistEnabled = false

    init {
        synchronized(routingLock) { active = this }
        // Covers every death: consumer end, scope cancellation, and scope
        // cancellation before the consumer ever started (autoStart = false).
        scope.coroutineContext.job.invokeOnCompletion { teardown() }
        if (autoStart) startConsumer()
    }

    internal fun startConsumer() {
        synchronized(routingLock) {
            if (consumerStarted) return
            consumerStarted = true
        }
        scope.launch {
            for (command in commands) when (command) {
                is Command.Snapshot -> context.dataStore.edit { prefs ->
                    lastTransformThread = Thread.currentThread()
                    // Recheck inside the transaction: a store writer outside
                    // this class (there is none today) could not be ordered
                    // by the channel, so the store gets the final word.
                    if (prefs[RememberShuffleAndRepeatKey] != false) {
                        prefs[ShuffleModeKey] = command.shuffle
                        prefs[RepeatModeKey] = command.repeat
                    }
                }
                is Command.Remember -> {
                    try {
                        context.dataStore.edit { prefs ->
                            lastTransformThread = Thread.currentThread()
                            prefs[RememberShuffleAndRepeatKey] = command.on
                            if (!command.on) {
                                prefs.remove(ShuffleModeKey)
                                prefs.remove(RepeatModeKey)
                            }
                        }
                        command.ack.complete(true)
                    } catch (t: Throwable) {
                        command.ack.complete(false)
                        if (t is CancellationException) throw t
                    }
                }
            }
        }
    }

    /** Deregister + close + fail every queued ack, linearized against routing. */
    private fun teardown() {
        synchronized(routingLock) {
            if (active === this) active = null
            commands.close()
        }
        while (true) {
            when (val queued = commands.tryReceive().getOrNull()) {
                null -> return
                is Command.Remember -> queued.ack.complete(false)
                is Command.Snapshot -> Unit // mode state dies with the service
            }
        }
    }

    /** Called by the service once the startup restore decision is made (queue
     *  snapshot applied or intentionally skipped). Mode events before this point
     *  are dropped at enqueue: the only mode-set in that window is the restore
     *  itself, whose values already came from the store. */
    fun beginPersisting() {
        persistEnabled = true
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = onModesChanged()

    override fun onRepeatModeChanged(repeatMode: Int) = onModesChanged()

    /** Runs on the player's application looper (listener delivery). Capture only:
     *  no store I/O on this thread, ever. */
    private fun onModesChanged() {
        if (!persistEnabled) return
        lastCaptureThread = Thread.currentThread()
        commands.trySend(Command.Snapshot(player.shuffleModeEnabled, player.repeatMode))
    }

    /** Modes to put on the player when a queue snapshot restored, or null when
     *  remembering is off, nothing valid is stored, or the store cannot be read
     *  promptly (player keeps defaults; persistence still starts). Called once
     *  during service init, before [beginPersisting], when no writes exist.
     *  Time-bounded so a stalled store can never wedge service startup. */
    fun savedModes(): Pair<Boolean, Int>? = runBlocking {
        try {
            withTimeout(3000) {
                val prefs = context.dataStore.data.first()
                if (prefs[RememberShuffleAndRepeatKey] == false) return@withTimeout null
                val shuffle = prefs[ShuffleModeKey] ?: return@withTimeout null
                val repeat = normalizedRepeatMode(prefs[RepeatModeKey] ?: return@withTimeout null)
                shuffle to repeat
            }
        } catch (t: TimeoutCancellationException) {
            null
        }
    }

    companion object {
        private val routingLock = Any()

        /** The live instance; lets the settings toggle reach the channel without
         *  holding a service reference. Set/cleared under [routingLock]. */
        @Volatile
        private var active: ShuffleRepeatMemory? = null

        /** The one entry point for the remember toggle. Suspends until the
         *  transaction is durably committed - via the live instance's channel
         *  (true action order, main-thread send) or, when no live instance can
         *  accept it, via a direct write here (safe: no player exists to race).
         *  An enqueued command whose consumer dies before processing is acked
         *  false and falls through to the same direct write. */
        suspend fun setRemember(context: Context, on: Boolean) {
            val ack = synchronized(routingLock) {
                val memory = active
                if (memory == null) null
                else {
                    val ack = CompletableDeferred<Boolean>()
                    if (memory.commands.trySend(Command.Remember(on, ack)).isSuccess) ack else null
                }
            }
            if (ack != null && ack.await()) return
            context.dataStore.edit { prefs ->
                prefs[RememberShuffleAndRepeatKey] = on
                if (!on) {
                    prefs.remove(ShuffleModeKey)
                    prefs.remove(RepeatModeKey)
                }
            }
        }

        /** Anything outside the three real modes means a damaged store, not a choice. */
        fun normalizedRepeatMode(raw: Int?): Int = when (raw) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_OFF
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

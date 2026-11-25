package com.orismusic.client.playback

import java.io.InterruptedIOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real DownloadRegistry that OfflineAudio delegates to: a blocking fake
 * write stands in for CacheWriter.cache(), cooperating with cancelWrite the way
 * CacheWriter does (throwIfCanceled between reads), and latches pin the interleavings.
 */
class DownloadRegistryTest {

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val events = Collections.synchronizedList(mutableListOf<String>())
    private var dropThrows = false

    private val registry = DownloadRegistry<String, String>(
        scope = scope,
        dropSpans = { id ->
            if (dropThrows) throw RuntimeException("cache busy")
            events.add("drop:$id")
        },
        onCommit = { id, _ -> events.add("commit:$id") },
        onFailed = { id, _ -> events.add("failed:$id") },
        onBusyChange = { id, busy -> events.add("busy:$busy") },
        onCleanupFailed = { id, _ -> events.add("cleanupFailed:$id") },
        onRemove = { id -> events.add("remove:$id") },
    )

    @After
    fun tearDown() {
        dispatcher.close()
    }

    /** A write that blocks until released, then aborts like CacheWriter when cancelled. */
    private fun blockingWrite(entered: CountDownLatch, release: CountDownLatch)
            : DownloadRegistry<String, String>.Record.() -> Unit = {
        cancelWrite = { events.add("cancelWrite") }
        entered.countDown()
        release.await(5, TimeUnit.SECONDS)
        if (cancelRequested) throw InterruptedIOException()
    }

    private fun instantWrite(): DownloadRegistry<String, String>.Record.() -> Unit = {
        cancelWrite = {}
    }

    private fun awaitActive(id: String, expected: Boolean) =
        awaitActive(registry, id, expected)

    private fun awaitActive(reg: DownloadRegistry<String, String>, id: String, expected: Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (reg.isActive(id) != expected && System.nanoTime() < end) Thread.sleep(10)
        assertEquals("isActive($id)", expected, reg.isActive(id))
    }

    private fun CountDownLatch.awaitLatched() =
        assertTrue("latch timed out", await(5, TimeUnit.SECONDS))

    @Test
    fun `cancel during write aborts commit and the owner drops spans on unwind`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p", blockingWrite(entered, release)))
        entered.awaitLatched()

        assertTrue(registry.cancel("a"))
        // The key stays owned and visibly busy until the writer has unwound.
        assertTrue(registry.isActive("a"))
        assertEquals(listOf("busy:true", "cancelWrite"), events.toList())

        release.countDown()
        awaitActive("a", false)
        assertEquals(
            listOf("busy:true", "cancelWrite", "drop:a", "busy:false"),
            events.toList())
        assertFalse(events.contains("commit:a"))
        assertFalse(events.contains("failed:a"))
    }

    @Test
    fun `cancel with no owner is a no-op`() {
        assertFalse(registry.cancel("ghost"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `commit retires ownership atomically so a later cancel cannot erase the download`() {
        assertTrue(registry.start("a", "p", instantWrite()))
        awaitActive("a", false)
        assertEquals(listOf("busy:true", "commit:a", "busy:false"), events.toList())

        assertFalse(registry.cancel("a"))
        assertFalse(events.contains("drop:a"))
        assertFalse(events.contains("cancelWrite"))
    }

    @Test
    fun `cancel requested as the write finishes still denies the commit`() {
        assertTrue(registry.start("a", "p") {
            cancelWrite = {}
            // The cancel lands after the write's work but before the commit point.
            registry.cancel("a")
        })
        awaitActive("a", false)
        assertEquals(listOf("busy:true", "drop:a", "busy:false"), events.toList())
        assertFalse(events.contains("commit:a"))
    }

    @Test
    fun `retry is refused while the cancelled write unwinds and proceeds after settlement`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p", blockingWrite(entered, release)))
        entered.awaitLatched()
        assertTrue(registry.cancel("a"))

        // Same key is still owned during cleanup: a retry is refused, not overlapped.
        assertFalse(registry.start("a", "p2", instantWrite()))

        release.countDown()
        awaitActive("a", false)
        assertTrue(registry.start("a", "p2", instantWrite()))
        awaitActive("a", false)
        assertEquals(1, events.count { it == "commit:a" })
        assertEquals(1, events.count { it == "drop:a" })
        assertTrue(events.indexOf("drop:a") < events.lastIndexOf("busy:true"))
    }

    @Test
    fun `a cancelled job's abort is not failure but a fresh attempt's error is`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p", blockingWrite(entered, release)))
        entered.awaitLatched()
        assertTrue(registry.cancel("a"))
        release.countDown()
        awaitActive("a", false)
        assertFalse(events.contains("failed:a"))

        assertTrue(registry.start("a", "p") { throw RuntimeException("boom") })
        awaitActive("a", false)
        assertEquals(1, events.count { it == "failed:a" })
        // A plain failure keeps partial spans for a later resume; the only drop is the
        // cancelled first job's.
        assertEquals(1, events.count { it == "drop:a" })
    }

    @Test
    fun `evict with no owner drops spans immediately and runs removal under the ownership lock`() {
        registry.evict("a")
        assertEquals(listOf("drop:a", "remove:a"), events.toList())
    }

    @Test
    fun `evict during flight defers span cleanup to the owning teardown`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p", blockingWrite(entered, release)))
        entered.awaitLatched()

        registry.evict("a")
        assertFalse(events.contains("drop:a"))
        assertTrue(registry.isActive("a"))

        release.countDown()
        awaitActive("a", false)
        assertEquals(1, events.count { it == "drop:a" })
        assertFalse(events.contains("commit:a"))
    }

    @Test
    fun `evict cannot delete a successor's spans and settles removal before the successor commits`() {
        registry.evict("a")
        assertTrue(registry.start("a", "p", instantWrite()))
        awaitActive("a", false)
        // The successor is a clean new lifecycle: its commit lands strictly after the
        // removal's side effects, and its spans are never dropped by the removal.
        assertEquals(
            listOf("drop:a", "remove:a", "busy:true", "commit:a", "busy:false"),
            events.toList())
        assertEquals(1, events.count { it == "drop:a" })
    }

    @Test
    fun `span cleanup failure is reported and never treated as proven removal`() {
        dropThrows = true
        registry.evict("bad")
        assertEquals(listOf("cleanupFailed:bad", "remove:bad"), events.toList())

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("bad", "p", blockingWrite(entered, release)))
        entered.awaitLatched()
        assertTrue(registry.cancel("bad"))
        release.countDown()
        awaitActive("bad", false)
        // Cleanup failed, yet teardown still settled honestly: owner retired, busy cleared.
        assertEquals(2, events.count { it == "cleanupFailed:bad" })
        assertFalse(registry.isActive("bad"))
        assertEquals("busy:false", events.last())
    }

    @Test
    fun `evict pinned at the pre-commit boundary runs removal atomically and suppresses the commit`() {
        val atBoundary = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p") {
            cancelWrite = { events.add("cancelWrite") }
            // The write's work is done; the job is pinned before the commit section.
            atBoundary.countDown()
            release.await(5, TimeUnit.SECONDS)
        })
        atBoundary.awaitLatched()

        // Removal lands while the job sits at the exact post-write/pre-commit boundary:
        // its side effects run atomically with the eviction intent and the commit is
        // suppressed, so a removed entry can never be resurrected by this job.
        registry.evict("a")
        assertEquals(listOf("busy:true", "remove:a", "cancelWrite"), events.toList())

        release.countDown()
        awaitActive("a", false)
        assertEquals(
            listOf("busy:true", "remove:a", "cancelWrite", "drop:a", "busy:false"),
            events.toList())
        assertFalse(events.contains("commit:a"))
    }

    @Test
    fun `commit before evict commits then removes in one ownership order`() {
        assertTrue(registry.start("a", "p", instantWrite()))
        awaitActive("a", false)

        // The commit already retired the owner: removal still clears the saved state
        // and drops the spans, strictly after the commit it undoes.
        registry.evict("a")
        assertEquals(
            listOf("busy:true", "commit:a", "busy:false", "drop:a", "remove:a"),
            events.toList())
    }

    @Test
    fun `a successor start is refused while the evicted owner unwinds then commits after settlement`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p", blockingWrite(entered, release)))
        entered.awaitLatched()

        registry.evict("a")
        // The evicted key stays owned until its teardown finishes: the successor is
        // refused, never overlapped, so removal cannot erase a successor's state.
        assertFalse(registry.start("a", "p2", instantWrite()))

        release.countDown()
        awaitActive("a", false)
        assertTrue(registry.start("a", "p2", instantWrite()))
        awaitActive("a", false)
        assertEquals(
            listOf("busy:true", "remove:a", "cancelWrite", "drop:a", "busy:false",
                "busy:true", "commit:a", "busy:false"),
            events.toList())
    }

    @Test
    fun `a second cancel while unwinding keeps a single owner and one cleanup`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p", blockingWrite(entered, release)))
        entered.awaitLatched()
        assertTrue(registry.cancel("a"))
        assertTrue(registry.cancel("a"))

        release.countDown()
        awaitActive("a", false)
        assertEquals(1, events.count { it == "drop:a" })
        assertEquals(1, events.count { it == "busy:false" })
    }

    @Test
    fun `a failure after a recorded cancel is teardown-decided as cancel, never FAILED`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p") {
            cancelWrite = { events.add("cancelWrite") }
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw RuntimeException("boom") // a genuine write failure, not the cancel abort
        })
        entered.awaitLatched()

        // The cancel is fully recorded while the writer is still blocked; the write
        // then fails. Eligibility is decided at teardown under the ownership lock, so
        // the recorded cancel wins: spans drop and no FAILED marker lands.
        assertTrue(registry.cancel("a"))
        release.countDown()
        awaitActive("a", false)
        assertEquals(
            listOf("busy:true", "cancelWrite", "drop:a", "busy:false"),
            events.toList())
        assertFalse(events.contains("failed:a"))
    }

    @Test
    fun `a failure after a recorded eviction suppresses the marker and settles removal`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p") {
            cancelWrite = { events.add("cancelWrite") }
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw RuntimeException("boom")
        })
        entered.awaitLatched()

        // Removal (eviction intent + saved-state edits) is fully recorded while the
        // writer is still blocked; the write then fails. Teardown sees the intent and
        // drops the spans; no FAILED marker lands on the removed entry.
        registry.evict("a")
        release.countDown()
        awaitActive("a", false)
        assertEquals(
            listOf("busy:true", "remove:a", "cancelWrite", "drop:a", "busy:false"),
            events.toList())
        assertFalse(events.contains("failed:a"))
        assertFalse(events.contains("commit:a"))
    }

    @Test
    fun `a settled failure is marked once and a later removal clears it in order`() {
        assertTrue(registry.start("a", "p") { throw RuntimeException("boom") })
        awaitActive("a", false)
        // A plain failure keeps partial spans for a later resume; removal then drops
        // them and clears the saved state strictly after the marker was set.
        assertEquals(listOf("busy:true", "failed:a", "busy:false"), events.toList())

        registry.evict("a")
        assertEquals(
            listOf("busy:true", "failed:a", "busy:false", "drop:a", "remove:a"),
            events.toList())
    }

    @Test
    fun `failure marking runs inside the ownership transition, not before it`() {
        val localEvents = Collections.synchronizedList(mutableListOf<String>())
        val inFailed = CountDownLatch(1)
        val leaveFailed = CountDownLatch(1)
        val local = DownloadRegistry<String, String>(
            scope = scope,
            dropSpans = { id -> localEvents.add("drop:$id") },
            onCommit = { id, _ -> localEvents.add("commit:$id") },
            onFailed = { id, _ ->
                localEvents.add("failed:$id")
                inFailed.countDown()
                leaveFailed.await(5, TimeUnit.SECONDS)
            },
            onBusyChange = { _, busy -> localEvents.add("busy:$busy") },
            onCleanupFailed = { id, _ -> localEvents.add("cleanupFailed:$id") },
            onRemove = { id -> localEvents.add("remove:$id") },
        )
        assertTrue(local.start("a", "p") { throw RuntimeException("boom") })
        // The job is parked inside onFailed. If the callback ran outside the
        // ownership lock, a concurrent eviction would complete its removal edits now.
        inFailed.awaitLatched()
        val evictor = thread { local.evict("a") }
        Thread.sleep(300)
        assertFalse(localEvents.contains("remove:a"))

        leaveFailed.countDown()
        evictor.join(5000)
        // The failure transition settles completely before the removal runs.
        assertEquals(
            listOf("busy:true", "failed:a", "busy:false", "drop:a", "remove:a"),
            localEvents.toList())
    }

    @Test
    fun `cancel before the writer ever starts still settles ownership`() {
        // A dedicated single-thread scope whose only thread is occupied: the launch
        // queues without entering the body, which is exactly the leak shape.
        val gateDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val gateScope = CoroutineScope(SupervisorJob() + gateDispatcher)
        try {
            val localEvents = Collections.synchronizedList(mutableListOf<String>())
            val local = DownloadRegistry<String, String>(
                scope = gateScope,
                dropSpans = { id -> localEvents.add("drop:$id") },
                onCommit = { id, _ -> localEvents.add("commit:$id") },
                onFailed = { id, _ -> localEvents.add("failed:$id") },
                onBusyChange = { _, busy -> localEvents.add("busy:$busy") },
                onCleanupFailed = { id, _ -> localEvents.add("cleanupFailed:$id") },
                onRemove = { id -> localEvents.add("remove:$id") },
            )
            val occupy = CountDownLatch(1)
            val enteredBody = CountDownLatch(1)
            gateScope.launch { occupy.await(5, TimeUnit.SECONDS) }
            assertTrue(local.start("a", "p") {
                enteredBody.countDown()
                cancelWrite = {}
            })

            // The cancel lands while the job is still queued: the body never runs.
            assertTrue(local.cancel("a"))
            occupy.countDown()
            awaitActive(local, "a", false)

            // Ownership and busy settle via completion, with spans dropped per the
            // recorded intent; a skipped body is ordinary cancellation, never FAILED.
            assertEquals(1L, enteredBody.count)
            assertEquals(
                listOf("busy:true", "drop:a", "busy:false"),
                localEvents.toList())

            // The next start proceeds on a clean lifecycle and commits.
            assertTrue(local.start("a", "p2") { cancelWrite = {} })
            awaitActive(local, "a", false)
            assertEquals(
                listOf("busy:true", "drop:a", "busy:false",
                    "busy:true", "commit:a", "busy:false"),
                localEvents.toList())
        } finally {
            gateDispatcher.close()
        }
    }

    @Test
    fun `cancel after writer entry settles only after the blocked writer unwinds`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        assertTrue(registry.start("a", "p") {
            cancelWrite = { events.add("cancelWrite") }
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            // The cancelled writer unwinds by returning, not by throwing.
        })
        entered.awaitLatched()

        assertTrue(registry.cancel("a"))
        // While the writer is still blocked, completion settlement must not run:
        // the key stays owned and no spans are dropped.
        Thread.sleep(300)
        assertTrue(registry.isActive("a"))
        assertEquals(listOf("busy:true", "cancelWrite"), events.toList())

        release.countDown()
        awaitActive("a", false)
        // Exactly one settlement: the owning teardown's, never a second from completion.
        assertEquals(
            listOf("busy:true", "cancelWrite", "drop:a", "busy:false"),
            events.toList())
        assertEquals(1, events.count { it == "drop:a" })
    }

    @Test
    fun `a launch that completes instantly is settled once and never left registered`() {
        // Samples the completion-before-handler-registration interleaving: an instant
        // write can finish before invokeOnCompletion is attached; settlement must be
        // exactly-once either way and the record must not stay registered.
        repeat(50) {
            val id = "k$it"
            assertTrue(registry.start(id, "p", instantWrite()))
            awaitActive(id, false)
        }
        assertEquals(50, events.count { it.startsWith("commit:") })
        assertEquals(50, events.count { it == "busy:false" })
    }
}

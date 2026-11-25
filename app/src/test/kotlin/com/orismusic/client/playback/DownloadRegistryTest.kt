package com.orismusic.client.playback

import java.io.InterruptedIOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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

    private fun awaitActive(id: String, expected: Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (registry.isActive(id) != expected && System.nanoTime() < end) Thread.sleep(10)
        assertEquals("isActive($id)", expected, registry.isActive(id))
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
}

package com.orismusic.client.playback

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the lifecycle of one in-flight download per key: who may write the cache key,
 * when a completed write may commit, and what cancel or removal are allowed to touch.
 *
 * The rules this enforces:
 * - A key has at most one owner, from start until teardown finishes; a retry while the
 *   previous writer is still unwinding is refused, never overlapped.
 * - Commit is the terminal transition and retires ownership atomically: a cancel that
 *   lands afterwards finds no record and is a no-op, so a completed download is never
 *   marked cancelled or erased by a late cancel.
 * - A cancel requested before the commit point aborts the commit; the owning job drops
 *   the key's spans itself, after the writer has unwound, never concurrently with a
 *   write and never touching a successor's spans.
 * - Cancel with no owner is a no-op: no state residue, no deletion of completed work.
 * - Busy clears only inside the owner's teardown, so the key stays visibly owned until
 *   cleanup has settled.
 * - Removal is atomic: the caller's saved-state and persisted-record edits (onRemove)
 *   run inside the same critical section that records the eviction intent, so no
 *   commit and no successor start can interleave between span handling and those
 *   edits. A start after removal is a clean new lifecycle whose commit lands strictly
 *   after onRemove, never erased by it.
 * - Failure marking is part of the owner's teardown transition: eligibility is decided
 *   and the failure callback runs under the ownership lock, so a cancel or removal
 *   recorded before the write unwinds suppresses the marker, and a late failure can
 *   never restore one onto a cancelled or removed download.
 * - Settlement never depends on the job body having entered: a job cancelled before
 *   its first dispatch completes without running, so a completion handler runs the
 *   same guarded teardown for a never-entered body. An ordinary cancellation is
 *   never marked failed; only a genuine (non-cancellation) completion cause can be.
 */
class DownloadRegistry<ID : Any, P>(
    private val scope: CoroutineScope,
    private val dropSpans: (ID) -> Unit,
    private val onCommit: (ID, P) -> Unit,
    private val onFailed: (ID, P) -> Unit,
    private val onBusyChange: (ID, Boolean) -> Unit,
    private val onCleanupFailed: (ID, Exception) -> Unit,
    private val onRemove: (ID) -> Unit = {},
) {

    inner class Record internal constructor(val id: ID, val payload: P) {
        @Volatile var cancelRequested: Boolean = false
            internal set

        /** Aborts the blocking write; the write body sets this before it starts blocking. */
        @Volatile var cancelWrite: () -> Unit = {}

        /** Set when the job body actually entered; a skipped body settles via completion. */
        @Volatile internal var entered: Boolean = false

        internal var job: Job? = null
    }

    private val active = ConcurrentHashMap<ID, Record>()
    private val lock = Any()

    /** True while a record owns this key, including its cleanup window. */
    fun isActive(id: ID): Boolean = active.containsKey(id)

    /**
     * Starts a download for [id] unless the key is already owned (in flight or still
     * unwinding); returns false when refused. [write] runs on the registry's scope and
     * must be blocking work that aborts promptly once Record.cancelWrite is invoked.
     */
    fun start(id: ID, payload: P, write: Record.() -> Unit): Boolean {
        val record: Record
        synchronized(lock) {
            if (active.containsKey(id)) return false
            record = Record(id, payload)
            active[id] = record
            onBusyChange(id, true)
        }
        val job = scope.launch { run(record, write) }
        record.job = job
        // Settlement must not depend on the body having entered: a job cancelled
        // before its first dispatch completes without running run(), so ownership
        // would otherwise never settle. The handler runs the same identity-guarded
        // teardown for a never-entered body; a body that did run has already settled
        // and the guard returns. Registered after launch, it still fires for an
        // already-completed job, so a fast completion cannot stay registered either.
        job.invokeOnCompletion { cause ->
            if (!record.entered) {
                // An ordinary cancellation is never a failure; only a genuine
                // (non-cancellation) completion cause may be marked failed.
                teardown(record, if (cause is CancellationException) null else cause)
            }
        }
        return true
    }

    /**
     * Requests cancellation of the key's current owner; returns false, a no-op, when the
     * key is unowned, so completed or never-started downloads are untouched. Busy state
     * and span cleanup belong to the owner's teardown, not to this call.
     */
    fun cancel(id: ID): Boolean {
        val record: Record
        synchronized(lock) {
            record = active[id] ?: return false
            record.cancelRequested = true
        }
        record.cancelWrite.invoke()
        record.job?.cancel()
        return true
    }

    /**
     * Removes the key completely and atomically. [onRemove] (the caller's saved-state
     * and persisted-record edits) runs inside the same critical section that records
     * the eviction intent, so no commit and no successor start can interleave between
     * span handling and those edits. With an owner in flight the intent is a
     * cancellation: the owning teardown drops the spans once the writer has unwound,
     * and the key stays owned (starts are refused) until then. With no owner the spans
     * are dropped now, under the ownership lock. The eviction intent is recorded before
     * [onRemove] runs, so a failing removal callback cannot leave the key unmarked.
     */
    fun evict(id: ID) {
        val record = synchronized(lock) {
            val r = active[id]
            if (r == null) {
                try {
                    dropSpans(id)
                } catch (e: Exception) {
                    onCleanupFailed(id, e)
                }
            } else {
                r.cancelRequested = true
            }
            onRemove(id)
            r
        } ?: return
        record.cancelWrite.invoke()
        record.job?.cancel()
    }

    private fun run(record: Record, write: Record.() -> Unit) {
        record.entered = true
        var failure: Throwable? = null
        try {
            record.write()
            synchronized(lock) {
                // Terminal transition: commit and retire in the same critical section, so
                // no cancel can land between a completed write and ownership ending.
                if (!record.cancelRequested && active[record.id] === record) {
                    onCommit(record.id, record.payload)
                    active.remove(record.id)
                    onBusyChange(record.id, false)
                    return
                }
            }
        } catch (e: Exception) {
            failure = e
        } finally {
            // Every path out of the write settles here, so cleanup never depends on
            // which way the job exited. After a commit the record is already retired
            // and teardown returns immediately.
            teardown(record, failure)
        }
    }

    /**
     * Span cleanup, failure marking and deregistration, run only by the owner after
     * the write unwound. Failure eligibility is decided and the failure callback runs
     * inside this same ownership transition: a cancel or removal recorded before the
     * unwind wins, so a late failure can never re-mark a cancelled or removed download.
     */
    private fun teardown(record: Record, failure: Throwable?) {
        synchronized(lock) {
            if (active[record.id] !== record) return
            if (record.cancelRequested) {
                try {
                    dropSpans(record.id)
                } catch (e: Exception) {
                    onCleanupFailed(record.id, e)
                }
            } else if (failure != null) {
                onFailed(record.id, record.payload)
            }
            active.remove(record.id)
            onBusyChange(record.id, false)
        }
    }
}

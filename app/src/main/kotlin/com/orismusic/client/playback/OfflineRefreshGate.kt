package com.orismusic.client.playback

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Serializes the offline-status snapshot refresh against every mutation that can
 * invalidate one. One short lock owns the version, the refresh sequencing and the
 * status publication itself: a mutation's version increment and its flow edits are
 * one critical section, and a refresh's validity check and its status assignment are
 * one critical section, so a competing mutation can never interleave between check
 * and publication. Cache and SharedPreferences work never run inside this lock, and
 * this gate never acquires any other lock.
 */
internal class OfflineRefreshGate(
    private val statuses: MutableStateFlow<Map<String, OfflineStatus>>,
    private val busy: MutableStateFlow<Set<String>>,
) {
    private val lock = Any()
    private var version = 0L
    private var startedSeq = 0L
    private var publishedSeq = 0L

    /** Test-only seam: runs inside the publish critical section so a test can park a
     * publication mid-section and prove a competing mutation blocks behind the lock. */
    internal var publishProbe: (() -> Unit)? = null

    /** A refresh in flight: the validity snapshot captured by [begin]. */
    data class Ticket internal constructor(
        internal val startVersion: Long,
        internal val seq: Long,
        internal val savedIds: Set<String>,
        internal val busyIds: Set<String>,
    )

    /** A state mutation: the version increment and the caller's flow edits land in
     * the same critical section, so a refresh either sees the whole mutation or is
     * rejected on its version check. */
    fun <T> mutate(block: () -> T): T = synchronized(lock) {
        version++
        block()
    }

    /** Begins a refresh: version, sequence and the immutable saved/busy membership
     * the refresh reads from are captured in one critical section. [savedIds] is
     * evaluated inside the lock so the snapshot can never predate the version. */
    fun begin(savedIds: () -> Set<String>): Ticket = synchronized(lock) {
        Ticket(version, ++startedSeq, savedIds(), busy.value.toSet())
    }

    /** Publishes a computed snapshot: the validity check, the sequencing and the
     * status assignment are the same critical section. Ids busy at publication keep
     * their current entries (their spans are mid-write, so a refresh read of them is
     * untrustworthy); busy and current status are read live inside this section, not
     * passed in stale. Returns false when a mutation invalidated the snapshot or a
     * newer refresh already published; the caller may then re-begin. */
    fun publish(ticket: Ticket, computed: Map<String, OfflineStatus>): Boolean =
        synchronized(lock) {
            if (ticket.startVersion != version || ticket.seq <= publishedSeq) return false
            publishProbe?.invoke()
            publishedSeq = ticket.seq
            val current = statuses.value
            statuses.value = computed + busy.value.mapNotNull { id ->
                current[id]?.let { id to it }
            }
            true
        }
}

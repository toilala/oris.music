package com.orismusic.client.playback

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the OfflineRefreshGate that OfflineAudio delegates to: publication validity,
 * refresh sequencing and the mutation/publication critical sections are exercised
 * against the real gate with latched competing threads, never counter-only checks.
 */
class OfflineRefreshGateTest {

    private fun gate(
        statuses: Map<String, OfflineStatus> = emptyMap(),
        busy: Set<String> = emptySet(),
    ): Triple<OfflineRefreshGate, MutableStateFlow<Map<String, OfflineStatus>>, MutableStateFlow<Set<String>>> {
        val statusFlow = MutableStateFlow(statuses)
        val busyFlow = MutableStateFlow(busy)
        return Triple(OfflineRefreshGate(statusFlow, busyFlow), statusFlow, busyFlow)
    }

    @Test
    fun validPublishAppliesComputedSnapshot() {
        val (gate, statusFlow, _) = gate(mapOf("a" to OfflineStatus.READY))
        val ticket = gate.begin { setOf("a") }
        assertTrue(gate.publish(ticket, mapOf("a" to OfflineStatus.PARTIAL)))
        assertEquals(mapOf("a" to OfflineStatus.PARTIAL), statusFlow.value)
    }

    @Test
    fun mutationBetweenBeginAndPublishRejectsTheSnapshot() {
        val (gate, statusFlow, _) = gate(mapOf("a" to OfflineStatus.READY))
        val ticket = gate.begin { setOf("a") }
        gate.mutate { statusFlow.value = statusFlow.value - "a" }
        assertFalse(gate.publish(ticket, mapOf("a" to OfflineStatus.PARTIAL)))
        assertEquals(emptyMap<String, OfflineStatus>(), statusFlow.value)
    }

    @Test
    fun competingMutationCannotInterleaveWithPublication() {
        val (gate, statusFlow, _) = gate(mapOf("a" to OfflineStatus.READY))
        val ticket = gate.begin { setOf("a") }
        val publishing = CountDownLatch(1)
        val release = CountDownLatch(1)
        gate.publishProbe = {
            publishing.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val publisher = thread {
            assertTrue(gate.publish(ticket, mapOf("a" to OfflineStatus.PARTIAL)))
        }
        assertTrue(publishing.await(5, TimeUnit.SECONDS))
        val mutator = thread {
            gate.mutate { statusFlow.value = statusFlow.value + ("b" to OfflineStatus.FAILED) }
        }
        // The mutation must still be blocked behind the parked publication: the
        // version check and the assignment are one critical section.
        Thread.sleep(300)
        assertTrue(mutator.isAlive)
        release.countDown()
        publisher.join(5000)
        mutator.join(5000)
        gate.publishProbe = null
        assertEquals(OfflineStatus.PARTIAL, statusFlow.value["a"])
        assertEquals(OfflineStatus.FAILED, statusFlow.value["b"])
    }

    @Test
    fun olderRefreshCannotOverwriteNewerPublication() {
        val (gate, statusFlow, _) = gate(mapOf("a" to OfflineStatus.READY))
        val older = gate.begin { setOf("a") }
        val newer = gate.begin { setOf("a") }
        assertTrue(gate.publish(newer, mapOf("a" to OfflineStatus.PARTIAL)))
        assertFalse(gate.publish(older, mapOf("a" to OfflineStatus.UNKNOWN)))
        assertEquals(OfflineStatus.PARTIAL, statusFlow.value["a"])
    }

    @Test
    fun busyIdKeepsItsCurrentEntryAtPublication() {
        val (gate, statusFlow, _) = gate(
            mapOf("a" to OfflineStatus.READY, "b" to OfflineStatus.UNKNOWN),
            busy = setOf("b"),
        )
        val ticket = gate.begin { setOf("a", "b") }
        assertTrue(gate.publish(ticket, mapOf("a" to OfflineStatus.PARTIAL, "b" to OfflineStatus.PARTIAL)))
        assertEquals(OfflineStatus.PARTIAL, statusFlow.value["a"])
        assertEquals(OfflineStatus.UNKNOWN, statusFlow.value["b"])
    }

    @Test
    fun removedIdIsNotResurrectedByARefreshThatBeganBeforeRemoval() {
        val (gate, statusFlow, _) = gate(mapOf("a" to OfflineStatus.READY, "b" to OfflineStatus.READY))
        val ticket = gate.begin { setOf("a", "b") }
        gate.mutate { statusFlow.value = statusFlow.value - "b" }
        assertFalse(gate.publish(ticket, mapOf("a" to OfflineStatus.READY, "b" to OfflineStatus.READY)))
        assertEquals(mapOf("a" to OfflineStatus.READY), statusFlow.value)
    }

    @Test
    fun concurrentFailedSetUpdatesLoseNothing() {
        // The failedFlow update{} shape OfflineAudio now uses at every site, pinned
        // with a start-gated burst of racing add/remove pairs on one real flow.
        val failed = MutableStateFlow(setOf("gone1", "gone2"))
        val start = CountDownLatch(1)
        val workers = (1..8).map { i ->
            thread {
                start.await(5, TimeUnit.SECONDS)
                failed.update { it + "id$i" }
                failed.update { it - "gone$i" }
            }
        }
        start.countDown()
        workers.forEach { it.join(5000) }
        assertEquals((1..8).map { n -> "id$n" }.toSet(), failed.value)
    }
}

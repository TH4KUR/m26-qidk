package com.megathon.incident.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewSchedulerTest {
    private suspend fun until(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) yield() }

    @Test
    fun extractionStartsOnlyOnceEnoughNewWordsArrive() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        val s = PreviewScheduler(this, minNewWords = 3) { seen += it }
        s.offer("one two")
        yield()
        assertTrue(seen.isEmpty())
        s.offer("one two three")
        until { seen.size == 1 }
        assertEquals("one two three", seen.single())
        s.offer("one two three four")
        yield()
        assertEquals(1, seen.size, "one new word is not worth a model call")
    }

    @Test
    fun whileOneRunsOnlyTheLatestSnapshotWaitsAndOlderOnesAreDropped() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val seen = CopyOnWriteArrayList<String>()
        var running = 0
        var most = 0
        val s = PreviewScheduler(this, minNewWords = 3) {
            running++; most = maxOf(most, running)
            if (seen.isEmpty()) { seen += it; gate.await() } else seen += it
            running--
        }
        s.offer("a b c")
        until { seen.size == 1 }
        s.offer("a b c d e f")
        s.offer("a b c d e f g h i")
        gate.complete(Unit)
        until { seen.size == 2 }
        yield()
        assertEquals(listOf("a b c", "a b c d e f g h i"), seen.toList())
        assertEquals(1, most)
    }

    @Test
    fun stopDropsTheWaitingSnapshotButLetsTheRunningOneFinish() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val seen = CopyOnWriteArrayList<String>()
        val done = CopyOnWriteArrayList<String>()
        val s = PreviewScheduler(this, minNewWords = 3) { seen += it; gate.await(); done += it }
        s.offer("a b c")
        until { seen.size == 1 }
        s.offer("a b c d e f")
        s.stop()
        gate.complete(Unit)
        until { done.size == 1 }
        yield(); yield()
        assertEquals(listOf("a b c"), seen.toList(), "nothing speculative starts after Stop")
        s.offer("a b c d e f g h i")
        yield()
        assertEquals(1, seen.size)
    }

    @Test
    fun aFailedExtractionDoesNotStopLaterOnes() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        val s = PreviewScheduler(this, minNewWords = 2) {
            seen += it
            if (seen.size == 1) error("model failed")
        }
        s.offer("a b")
        until { seen.size == 1 }
        yield(); yield()
        s.offer("a b c d")
        until { seen.size == 2 }
        assertEquals("a b c d", seen.last())
    }

    @Test
    fun anIdenticalSnapshotIsNeverExtractedTwice() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        val s = PreviewScheduler(this, minNewWords = 1) { seen += it }
        s.offer("a b")
        until { seen.size == 1 }
        yield(); yield()
        s.offer("a b")
        yield(); yield()
        assertEquals(1, seen.size)
    }
}

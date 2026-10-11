package com.megathon.incident.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AcceleratorLaneTest {
    @Test
    fun workRunsOneAtATime() = runBlocking {
        val lane = AcceleratorLane()
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = async { lane.run { order += "first start"; gate.await(); order += "first end" } }
        withTimeout(5000) { while (order.isEmpty()) yield() }
        val second = async { lane.run { order += "second" } }
        yield(); yield()
        assertEquals(listOf("first start"), order)
        gate.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("first start", "first end", "second"), order)
    }

    @Test
    fun speculativeWorkQueuedBeforeStopIsSkippedWhenItsTurnComes() = runBlocking {
        val lane = AcceleratorLane()
        val gate = CompletableDeferred<Unit>()
        var ran = false
        val running = async { lane.run { gate.await() } }
        yield()
        val speculative = async { withContext(Speculative) { lane.run { ran = true } } }
        yield(); yield()
        lane.stopSpeculation()
        gate.complete(Unit)
        running.await()
        assertFailsWith<CancellationException> { speculative.await() }
        assertTrue(!ran, "a speculative call must not start after Stop")
    }

    @Test
    fun finalWorkStillRunsAfterStop() = runBlocking {
        val lane = AcceleratorLane()
        lane.stopSpeculation()
        assertEquals("ok", lane.run { "ok" })
    }

    @Test
    fun aNewRecordingAllowsSpeculationAgain() = runBlocking {
        val lane = AcceleratorLane()
        lane.stopSpeculation()
        lane.startSpeculation()
        assertEquals("ok", withContext(Speculative) { lane.run { "ok" } })
    }
}

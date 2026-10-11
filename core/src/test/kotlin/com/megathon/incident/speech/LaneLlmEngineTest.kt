package com.megathon.incident.speech

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.testing.Compute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LaneLlmEngineTest {
    private class Inner(val gate: CompletableDeferred<Unit>? = null) : LlmEngine {
        override val name = "inner"
        var calls = 0
        override suspend fun complete(request: LlmRequest): LlmResult {
            calls++
            gate?.await()
            return LlmResult("{}", 1, Compute.SIMULATED)
        }
    }

    private val request = LlmRequest("s", "u")

    @Test
    fun itKeepsTheInnerNameAndResult() = runBlocking {
        val engine = LaneLlmEngine(Inner(), AcceleratorLane())
        assertEquals("inner", engine.name)
        assertEquals("{}", engine.complete(request).text)
    }

    @Test
    fun aSpeculativeCallQueuedBehindOtherWorkIsSkippedAfterStop() = runBlocking {
        val lane = AcceleratorLane()
        val gate = CompletableDeferred<Unit>()
        val inner = Inner()
        val engine = LaneLlmEngine(inner, lane)
        val busy = async { lane.run { gate.await() } }
        yield()
        val preview = async { withContext(Speculative) { engine.complete(request) } }
        yield(); yield()
        lane.stopSpeculation()
        gate.complete(Unit)
        busy.await()
        assertFailsWith<CancellationException> { preview.await() }
        assertEquals(0, inner.calls)
        assertEquals("{}", engine.complete(request).text, "the final extraction still runs")
        assertEquals(1, inner.calls)
    }
}

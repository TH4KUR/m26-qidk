package com.megathon.incident.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelPreloaderTest {
    @Test fun nothingToLoadIsAnEmptyResult() = runBlocking {
        assertTrue(ModelPreloader().run(emptyList()).isEmpty())
    }

    @Test fun reportsTheLoadTimeTheModelGave() = runBlocking {
        val out = ModelPreloader().run(listOf("llm" to Warmable { 9_100L }))
        assertEquals(listOf<PreloadOutcome>(PreloadOutcome.Loaded("llm", 9_100L)), out)
    }

    @Test fun anAlreadyLoadedModelIsReportedAsLoadedInZeroMs() = runBlocking {
        val out = ModelPreloader().run(listOf("llm" to Warmable { 0L }))
        assertEquals(PreloadOutcome.Loaded("llm", 0L), out.single())
    }

    @Test fun stepsRunOneAfterTheOtherNeverInParallel() = runBlocking {
        val events = mutableListOf<String>()
        fun step(name: String) = name to Warmable {
            events += "$name start"
            delay(40)
            events += "$name end"
            1L
        }
        ModelPreloader().run(listOf(step("speech"), step("llm")))
        assertEquals(listOf("speech start", "speech end", "llm start", "llm end"), events, "the second load must wait for the first")
    }

    @Test fun aFailedLoadIsReportedWithItsReasonAndDoesNotStopTheNextOne() = runBlocking {
        val out = ModelPreloader().run(
            listOf(
                "llm" to Warmable { throw IllegalStateException("the language model 'x' is not in this app's model store") },
                "other" to Warmable { 5L },
            ),
        )
        val failed = assertIs<PreloadOutcome.Failed>(out[0])
        assertEquals("llm", failed.name)
        assertTrue(failed.reason.contains("not in this app's model store"), failed.reason)
        assertEquals(PreloadOutcome.Loaded("other", 5L), out[1])
    }

    @Test fun aFailedLoadIsTriedExactlyOnceNotInALoop() = runBlocking {
        var calls = 0
        ModelPreloader().run(listOf("llm" to Warmable { calls++; error("boom") }))
        assertEquals(1, calls)
    }

    @Test fun aLaterRunTriesAgainAfterAFailureSoTheNextCallIsFreeToRetry() = runBlocking {
        var calls = 0
        val warm = Warmable { if (calls++ == 0) error("not ready") else 7L }
        val first = ModelPreloader().run(listOf("llm" to warm))
        val second = ModelPreloader().run(listOf("llm" to warm))
        assertIs<PreloadOutcome.Failed>(first.single())
        assertEquals(PreloadOutcome.Loaded("llm", 7L), second.single())
        assertEquals(2, calls)
    }

    @Test fun cancellationIsNotSwallowedAsAFailure() {
        assertFailsWith<CancellationException> {
            runBlocking { ModelPreloader().run(listOf("llm" to Warmable { throw CancellationException("screen closed") })) }
        }
    }

    @Test fun aFailureWithNoMessageStillHasAReadableReason() = runBlocking {
        val out = ModelPreloader().run(listOf("llm" to Warmable { throw IllegalStateException() }))
        val failed = assertIs<PreloadOutcome.Failed>(out.single())
        assertTrue(failed.reason.isNotBlank(), "a blank reason cannot be shown or logged usefully")
    }
}

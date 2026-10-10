package com.megathon.incident.llm

import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.testing.FakeLlmEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * One contract suite for every [LlmEngine]: run against the fake now and against the real GenieX
 * adapter later, so the two cannot drift (handoff section 8, rule 4).
 */
abstract class LlmEngineContract {
    protected abstract fun engine(): LlmEngine

    /** Real engines may not know this input; a fake is built to know it. */
    protected abstract val knowsTheDeckExample: Boolean

    private val deck = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."

    private fun request(text: String) = ExtractionRequest(text, text, IncidentReport.empty("r", 0))

    @Test fun declaresWhereItRuns() {
        assertTrue(engine().compute in Compute.entries)
    }

    @Test fun neverThrowsOnAnEmptyUtteranceAndReturnsText() = runBlocking {
        val out = engine().generate(request(""))
        assertTrue(out.isNotBlank(), "an engine must return something the parser can reject or accept")
    }

    @Test fun theDeckExampleComesBackAsAParseableUpdateReport() = runBlocking {
        if (!knowsTheDeckExample) return@runBlocking
        val parsed = ToolCallParser.parse(engine().generate(request(deck)))
        val ok = assertIs<ParseResult.Ok>(parsed, "raw output was not a valid update_report")
        assertEquals("Conference Room 2", ok.payload.location)
    }

    @Test fun anUnknownInputIsStillParseable() = runBlocking {
        val out = engine().generate(request("zzz qqq no such utterance"))
        assertIs<ParseResult.Ok>(ToolCallParser.parse(out), "fallback must be a valid no-op update_report: $out")
    }

    @Test fun aSimulatedEngineIsRefusedWhereLiveIsRequired() {
        val e = engine()
        if (e.compute == Compute.SIMULATED) {
            assertFailsWith<IllegalStateException> { e.requireLive() }
        } else {
            assertEquals(e, e.requireLive())
        }
    }
}

class FakeLlmEngineContractTest : LlmEngineContract() {
    override val knowsTheDeckExample = true

    override fun engine(): LlmEngine = FakeLlmEngine.withDeckExample()

    @Test fun theFakeIsAlwaysLabelledSimulated() = assertEquals(Compute.SIMULATED, engine().compute)

    @Test fun cannedAnswersAreReturnedPerUtterance() = runBlocking {
        val e = FakeLlmEngine(mapOf("hello" to """{"tool":"update_report","arguments":{"location":"Lab"}}"""))
        val ok = assertIs<ParseResult.Ok>(
            ToolCallParser.parse(e.generate(ExtractionRequest("hello", "hello", IncidentReport.empty("r", 0)))),
        )
        assertEquals("Lab", ok.payload.location)
    }
}

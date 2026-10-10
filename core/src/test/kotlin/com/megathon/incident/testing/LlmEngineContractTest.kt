package com.megathon.incident.testing

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.ToolCallParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract every [LlmEngine] must meet. Run it against the fake now and against `GenieXLlmEngine` later
 * (same tests, so fake and real cannot drift). Subclass and implement [create].
 */
abstract class LlmEngineContractTest {
    protected abstract fun create(): LlmEngine

    /** Engines that really run on hardware override this; fakes must say SIMULATED. */
    protected open val expectedCompute: Compute = Compute.SIMULATED

    private fun ask(engine: LlmEngine, user: String) =
        runBlocking { engine.complete(LlmRequest(system = "Extract an incident report.", user = user)) }

    @Test
    fun hasAName() = assertTrue(create().name.isNotBlank())

    @Test
    fun reportsWhereItRan() = assertEquals(expectedCompute, ask(create(), FakeLlmEngine.DECK_TRANSCRIPT).compute)

    @Test
    fun replyToTheDeckExampleIsAValidUpdateReport() {
        val reply = ask(create(), FakeLlmEngine.DECK_TRANSCRIPT).text
        val parsed = assertIs<ParseResult.Ok>(ToolCallParser.parse(reply), "unparsable reply: $reply")
        assertEquals("Conference Room 2", parsed.payload.location)
    }

    @Test
    fun replyToAnUnknownInputIsStillAValidEnvelope() {
        val reply = ask(create(), "zzz nothing the fixtures know about zzz").text
        assertIs<ParseResult.Ok>(ToolCallParser.parse(reply), "unparsable reply: $reply")
    }

    @Test
    fun elapsedTimeIsNotNegative() = assertTrue(ask(create(), FakeLlmEngine.DECK_TRANSCRIPT).elapsedMs >= 0)
}

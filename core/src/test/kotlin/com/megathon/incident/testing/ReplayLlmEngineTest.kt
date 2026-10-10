package com.megathon.incident.testing

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.ToolCallParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun fixtureText(): String =
    ReplayLlmEngine::class.java.getResource("/fixtures/llm_replay_deck.jsonl")!!.readText()

class ReplayLlmEngineContractTest : LlmEngineContractTest() {
    override fun create(): LlmEngine = ReplayLlmEngine.fromJsonLines(fixtureText())
}

class ReplayLlmEngineTest {
    private val engine = ReplayLlmEngine.fromJsonLines(fixtureText())

    private fun reply(user: String) = runBlocking { engine.complete(LlmRequest("", user)) }

    @Test
    fun secondFixtureIsMatchedByItsOwnKey() {
        val parsed = assertIs<ParseResult.Ok>(ToolCallParser.parse(reply("Smoke in the server room").text))
        assertEquals("fire_smoke", parsed.payload.incident_type)
    }

    @Test
    fun commentLinesAndBlankLinesAreIgnored() {
        assertEquals(2, ReplayLlmEngine.fromJsonLines("# c\n\n" + fixtureText()).let { e ->
            listOf("water leak", "smoke").count { runBlocking { e.complete(LlmRequest("", it)) }.text != FakeLlmEngine.EMPTY_REPLY }
        })
    }

    @Test
    fun replayIsAlwaysSimulated() = assertEquals(Compute.SIMULATED, reply("water leak").compute)
}

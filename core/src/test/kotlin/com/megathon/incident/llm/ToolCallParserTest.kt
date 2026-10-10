package com.megathon.incident.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolCallParserTest {
    private fun ok(raw: String) = assertIs<ParseResult.Ok>(ToolCallParser.parse(raw)).payload
    private fun fail(raw: String) = assertIs<ParseResult.Failure>(ToolCallParser.parse(raw))

    @Test
    fun parsesJsonEnvelope() {
        val p = ok("""{"tool":"update_report","arguments":{"location":"Conference Room 2","evidence":{"location":"near Conference Room 2"}}}""")
        assertEquals("Conference Room 2", p.location)
        assertEquals("near Conference Room 2", p.evidence["location"])
    }

    @Test
    fun parsesQwenNativeToolCallEnvelope() {
        val raw = "<tool_call>\n{\"name\": \"update_report\", \"arguments\": {\"incident_type\": \"water_leak_flood\"}}\n</tool_call>"
        assertEquals("water_leak_flood", ok(raw).incident_type)
    }

    @Test
    fun stripsThinkBlocksMarkdownFencesAndLeadingProse() {
        val raw = "<think>the user mentions a leak</think>\nSure, here it is:\n```json\n{\"tool\":\"update_report\",\"arguments\":{\"location\":\"Lab 3\"}}\n```"
        assertEquals("Lab 3", ok(raw).location)
    }

    @Test
    fun toleratesTrailingCommas() {
        assertEquals(listOf("Ravi"), ok("""{"tool":"update_report","arguments":{"witnesses":["Ravi",],}}""").witnesses)
    }

    @Test
    fun braceInsideAStringDoesNotBreakExtraction() {
        assertEquals("Room {B}", ok("""{"tool":"update_report","arguments":{"location":"Room {B}"}}""").location)
    }

    @Test
    fun literalNullStringBecomesNull() {
        assertNull(ok("""{"tool":"update_report","arguments":{"location":"null","severity":"None"}}""").location)
    }

    @Test
    fun keepsHindiText() {
        assertEquals("रसोई", ok("""{"tool":"update_report","arguments":{"location":"रसोई"}}""").location)
    }

    @Test
    fun proseWithoutJsonFails() {
        assertEquals(FailureCode.NO_ENVELOPE, fail("Sure! There was a leak in the lobby.").code)
    }

    @Test
    fun bareArgumentsObjectIsRejected() {
        assertEquals(FailureCode.NO_ENVELOPE, fail("""{"location":"Lobby"}""").code)
    }

    @Test
    fun wrongToolNameFails() {
        assertEquals(FailureCode.UNKNOWN_TOOL, fail("""{"tool":"submit_report","arguments":{}}""").code)
    }

    @Test
    fun twoEnvelopesFail() {
        val raw = """{"tool":"update_report","arguments":{"location":"A"}} {"tool":"update_report","arguments":{"location":"B"}}"""
        assertEquals(FailureCode.MULTIPLE_ENVELOPES, fail(raw).code)
    }

    @Test
    fun truncatedOutputFailsInsteadOfSalvaging() {
        // A token cap hit mid-object must never yield a half-filled payload.
        assertEquals(FailureCode.TRUNCATED, fail("""{"tool":"update_report","arguments":{"location":"Conference Ro""").code)
    }

    @Test
    fun wrongValueTypeFails() {
        assertEquals(FailureCode.INVALID_ARGUMENTS, fail("""{"tool":"update_report","arguments":{"witnesses":"Ravi"}}""").code)
    }

    @Test
    fun unknownKeysAreIgnored() {
        assertEquals("Lab 3", ok("""{"tool":"update_report","arguments":{"location":"Lab 3","mood":"calm"}}""").location)
    }

    @Test
    fun failureKeepsRawOutputForTheLog() {
        val f = fail("nonsense")
        assertTrue(f.raw.contains("nonsense"))
    }
}

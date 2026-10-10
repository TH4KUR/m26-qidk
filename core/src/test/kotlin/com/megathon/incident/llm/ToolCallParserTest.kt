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

    // Deliberate change (recall patch review, 2026-10-11): a model that writes a list field as one string, or writes
    // evidence as a list, used to lose the whole call. The names and quotes are still checked against the transcript later,
    // so a recoverable shape is coerced. A scalar field with the wrong shape still fails.

    @Test
    fun aListFieldWrittenAsOneStringIsCoercedToItsNames() {
        assertEquals(listOf("Ravi"), ok("""{"tool":"update_report","arguments":{"witnesses":"Ravi"}}""").witnesses)
        assertEquals(listOf("Ravi", "Meena"), ok("""{"tool":"update_report","arguments":{"witnesses":"Ravi and Meena"}}""").witnesses)
    }

    @Test
    fun aListFieldWithANumberOrAnObjectIsDroppedNotInvented() {
        assertEquals(emptyList(), ok("""{"tool":"update_report","arguments":{"witnesses":5,"location":"Lab 3"}}""").witnesses)
        assertEquals(emptyList(), ok("""{"tool":"update_report","arguments":{"witnesses":{"a":"b"}}}""").witnesses)
    }

    @Test
    fun evidenceWrittenAsListsOrNullsIsCoercedAndTheCallSurvives() {
        val p = ok("""{"tool":"update_report","arguments":{"location":"Lab 3","evidence":{"location":["in Lab 3","near the door"],"severity":null,"x":[]}}}""")
        assertEquals("Lab 3", p.location)
        assertEquals("in Lab 3, near the door", p.evidence["location"])
        assertEquals(null, p.evidence["severity"])
    }

    @Test
    fun aRepeatedEvidenceKeyIsToleratedButARepeatedFieldStillFails() {
        ok("""{"tool":"update_report","arguments":{"location":"Lab 3","evidence":{"location":"Lab 3"},"evidence":{"location":"Lab 3"}}}""")
        assertEquals(
            FailureCode.DUPLICATE_KEY,
            fail("""{"tool":"update_report","arguments":{"location":"Lab 3","location":"Lab 4"}}""").code,
        )
    }

    @Test
    fun aWrongShapeForAScalarFieldStillFails() {
        assertEquals(FailureCode.INVALID_ARGUMENTS, fail("""{"tool":"update_report","arguments":{"location":["Lab 3"]}}""").code)
        assertEquals(FailureCode.INVALID_ARGUMENTS, fail("""{"tool":"update_report","arguments":{"incident_type":{"a":1}}}""").code)
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

    // ---- optional urgency hints (Q6) -----------------------------------------------------------

    @Test fun parsesTheOptionalUrgencyHints() {
        val p = ok("""{"tool":"update_report","arguments":{"location":"Lab","impact_hint":4,"ongoing":true}}""")
        assertEquals(4, p.impact_hint)
        assertEquals(true, p.ongoing)
    }

    @Test fun aNumericStringHintIsAccepted() =
        assertEquals(4, ok("""{"tool":"update_report","arguments":{"impact_hint":"4"}}""").impact_hint)

    @Test fun anUnparseableHintIsDroppedWithoutFailingTheReport() {
        val p = ok("""{"tool":"update_report","arguments":{"location":"Lab","impact_hint":"high","ongoing":"maybe"}}""")
        assertEquals("Lab", p.location)
        assertNull(p.impact_hint)
        assertNull(p.ongoing)
    }

    @Test fun hintsAreOptional() = assertNull(ok("""{"tool":"update_report","arguments":{"location":"Lab"}}""").impact_hint)

    // ---- duplicate writes (codex rule: never last-write-wins) -----------------------------------

    @Test fun aDuplicateScalarKeyIsRejected() =
        assertEquals(FailureCode.DUPLICATE_KEY, fail("""{"tool":"update_report","arguments":{"location":"A","location":"B"}}""").code)

    @Test fun aDuplicateKeyInsideEvidenceIsRejected() = assertEquals(
        FailureCode.DUPLICATE_KEY,
        fail("""{"tool":"update_report","arguments":{"evidence":{"location":"a","location":"b"}}}""").code,
    )

    @Test fun theSameNameInDifferentObjectsIsNotADuplicate() {
        val p = ok("""{"tool":"update_report","arguments":{"location":"Lab","evidence":{"location":"in the Lab"}}}""")
        assertEquals("Lab", p.location)
    }

    @Test fun aKeyNameInsideAStringValueIsNotAKey() {
        val p = ok("""{"tool":"update_report","arguments":{"location":"room \"location\": 2","witnesses":["a"]}}""")
        assertEquals("room \"location\": 2", p.location)
    }
}


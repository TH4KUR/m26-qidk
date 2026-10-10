package com.megathon.incident

import com.megathon.incident.domain.Aspect
import com.megathon.incident.domain.Modality
import com.megathon.incident.llm.Envelope
import com.megathon.incident.llm.FailureCode
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.ToolCallParser
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared fixtures under `core/src/test/resources/fixtures/` (handoff section 7a). D, E and A reuse
 * them; these tests pin what the real components do with them so the fixtures cannot drift.
 */
class FixturesTest {
    private fun fixture(path: String) =
        javaClass.getResourceAsStream("/fixtures/$path")!!.bufferedReader(Charsets.UTF_8).readText()

    // ---- raw model outputs ----------------------------------------------------------------------

    private fun parse(name: String) = ToolCallParser.parse(fixture("llm/$name"))

    @Test fun validOutputParses() {
        val ok = assertIs<ParseResult.Ok>(parse("valid.txt"))
        assertEquals("Conference Room 2", ok.payload.location)
        assertEquals(Envelope.JSON, ok.envelope)
    }

    @Test fun fencedOutputWithProseAndTrailingCommaParses() =
        assertEquals("Lab 3", assertIs<ParseResult.Ok>(parse("fenced.txt")).payload.location)

    @Test fun truncatedOutputIsRejectedNotSalvaged() =
        assertEquals(FailureCode.TRUNCATED, assertIs<ParseResult.Failure>(parse("truncated.txt")).code)

    @Test fun wrongToolIsRejected() =
        assertEquals(FailureCode.UNKNOWN_TOOL, assertIs<ParseResult.Failure>(parse("wrong_tool.txt")).code)

    @Test fun extraPropertiesAreIgnored() =
        assertEquals("Lab 3", assertIs<ParseResult.Ok>(parse("extra_properties.txt")).payload.location)

    @Test fun duplicateScalarWritesAreRejectedNotLastWriteWins() =
        assertEquals(FailureCode.DUPLICATE_KEY, assertIs<ParseResult.Failure>(parse("duplicate_scalar_writes.txt")).code)

    @Test fun proseOnlyIsRejected() =
        assertEquals(FailureCode.NO_ENVELOPE, assertIs<ParseResult.Failure>(parse("prose_only.txt")).code)

    @Test fun nativeToolCallEnvelopeParses() {
        val ok = assertIs<ParseResult.Ok>(parse("native_envelope.txt"))
        assertEquals("pantry", ok.payload.location)
        assertEquals(Envelope.NATIVE, ok.envelope)
    }

    @Test fun twoEnvelopesAreRejected() =
        assertEquals(FailureCode.MULTIPLE_ENVELOPES, assertIs<ParseResult.Failure>(parse("two_envelopes.txt")).code)

    @Test fun anEmptyArgumentsObjectIsAValidNoOp() {
        val p = assertIs<ParseResult.Ok>(parse("no_op.txt")).payload
        assertNull(p.location)
        assertNull(p.incident_type)
    }

    @Test fun aGarbageHintIsDroppedAndTheReportSurvives() {
        val p = assertIs<ParseResult.Ok>(parse("garbage_hint.txt")).payload
        assertEquals("Lab", p.location)
        assertNull(p.impact_hint)
    }

    @Test fun aValidHintIsKept() {
        val p = assertIs<ParseResult.Ok>(parse("with_hint.txt")).payload
        assertEquals(5, p.impact_hint)
        assertEquals(true, p.ongoing)
    }

    @Test fun evidenceKeysMayRepeatTheFieldNamesWithoutBeingDuplicates() {
        // "location" appears in arguments and again inside evidence: different objects.
        assertIs<ParseResult.Ok>(parse("valid.txt"))
    }

    // ---- transcripts ----------------------------------------------------------------------------

    @Serializable
    private data class Expect(
        val completed: Boolean? = null, val ongoing: Boolean? = null, val negated: Boolean? = null,
        val hedged: Boolean? = null, val planned: Boolean? = null,
    )

    @Serializable
    private data class Line(val id: String, val text: String, val tags: List<String> = emptyList(), val expect: Expect = Expect())

    private val tam = TamDetector(TamCueLists.loadDefault())

    private val lines: List<Line> = fixture("transcripts/transcripts.jsonl").lines().filter { it.isNotBlank() }
        .map { Json.decodeFromString(Line.serializer(), it) }

    @Test fun everyTranscriptFixtureYieldsClausesAndMeetsItsExpectations() {
        assertTrue(lines.size >= 12, "only ${lines.size} transcript fixtures")
        for (l in lines) {
            val clauses = tam.analyze(l.text)
            assertTrue(clauses.isNotEmpty(), "${l.id}: no clauses")
            val completed = clauses.any { it.tam.aspect == Aspect.COMPLETED }
            val ongoing = clauses.any { it.tam.aspect == Aspect.ONGOING }
            val negated = clauses.any { it.tam.modality == Modality.NEGATED }
            val hedged = clauses.any { it.hedged }
            val planned = clauses.any { it.tam.modality == Modality.PLANNED }
            l.expect.completed?.let { assertEquals(it, completed, "${l.id}: completed") }
            l.expect.ongoing?.let { assertEquals(it, ongoing, "${l.id}: ongoing") }
            l.expect.negated?.let { assertEquals(it, negated, "${l.id}: negated") }
            l.expect.hedged?.let { assertEquals(it, hedged, "${l.id}: hedged") }
            l.expect.planned?.let { assertEquals(it, planned, "${l.id}: planned") }
        }
    }

    @Test fun hindiFixturesAreMarkedUnreviewed() {
        val hindi = lines.filter { "hindi" in it.tags || "hinglish" in it.tags }
        assertTrue(hindi.isNotEmpty())
        assertTrue(hindi.all { "UNREVIEWED" in it.tags }, "native-speaker review is still pending")
    }
}

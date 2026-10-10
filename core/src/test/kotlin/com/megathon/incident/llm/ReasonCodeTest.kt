package com.megathon.incident.llm

import com.megathon.incident.domain.ExtractionPayload
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every proposal that is not a plain "heard" and every rejection says why in a stable code (E's per-field scoring). */
class ReasonCodeTest {
    private val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), TemporalResolver(ZoneId.of("Asia/Kolkata")))
    private val at = Instant.parse("2026-10-11T09:30:00Z")

    private fun run(transcript: String, p: ExtractionPayload) = validator.validate(p, transcript, at)

    private fun ValidationResult.proposal(f: Field) = proposals.single { it.field == f }

    private fun ValidationResult.rejection(f: Field) = rejections.single { it.field == f }

    @Test fun aPlainHeardValueHasNoCode() {
        val r = run("There is a leak in Room 4.", ExtractionPayload(location = "Room 4", evidence = mapOf("location" to "Room 4")))
        assertNull(r.proposal(Field.LOCATION).reasonCode)
    }

    @Test fun hedgedValueIsSuggestedWithHedged() {
        val r = run("I think the leak is in Room 4.", ExtractionPayload(location = "Room 4", evidence = mapOf("location" to "Room 4")))
        val p = r.proposal(Field.LOCATION)
        assertEquals(ValueStatus.SUGGESTED, p.value.status)
        assertEquals(ReasonCode.HEDGED, p.reasonCode)
    }

    @Test fun aValueNotInTheTranscriptIsUngrounded() {
        val r = run("There is a puddle in the corridor.", ExtractionPayload(location = "Server Room", evidence = mapOf("location" to "Server Room")))
        assertEquals(ReasonCode.UNGROUNDED, r.proposal(Field.LOCATION).reasonCode)
    }

    @Test fun aSoundAlikeIsNearMiss() {
        val r = run("there is water near conference room to", ExtractionPayload(location = "Conference Room 2", evidence = mapOf("location" to "Conference Room 2")))
        assertEquals(ReasonCode.NEAR_MISS, r.proposal(Field.LOCATION).reasonCode)
    }

    @Test fun aNegatedPlaceIsRejectedAsNegated() {
        val r = run("The leak is not in Room 2.", ExtractionPayload(location = "Room 2", evidence = mapOf("location" to "Room 2")))
        assertEquals(ReasonCode.NEGATED, r.rejection(Field.LOCATION).reasonCode)
    }

    @Test fun aPlannedActionIsRejectedAndMovedToFollowUpAsPlanned() {
        val r = run(
            "We will call the plumber tomorrow.",
            ExtractionPayload(immediate_actions = "call the plumber", evidence = mapOf("immediate_actions" to "We will call the plumber")),
        )
        assertEquals(ReasonCode.PLANNED, r.rejection(Field.IMMEDIATE_ACTIONS).reasonCode)
        assertEquals(ReasonCode.PLANNED, r.proposal(Field.RECOMMENDED_FOLLOW_UP).reasonCode)
    }

    @Test fun anObservationUnderEvidenceIsNotAnAction() {
        val r = run("I saw it at 8:10 PM. The glass is on the floor.", ExtractionPayload(evidence = mapOf("immediate_actions" to "I saw it at 8:10 PM")))
        assertEquals(ReasonCode.NOT_AN_ACTION, r.rejection(Field.IMMEDIATE_ACTIONS).reasonCode)
    }

    @Test fun aRoleIsNotAName() {
        val r = run("A staff member fell.", ExtractionPayload(persons_involved = listOf("staff member")))
        assertEquals(ReasonCode.NOT_A_NAME, r.rejection(Field.PERSONS_INVOLVED).reasonCode)
    }

    @Test fun aNameThatWasNeverSaidIsNotStated() {
        val r = run("A pipe burst in Room 4.", ExtractionPayload(witnesses = listOf("Ravi")))
        assertEquals(ReasonCode.NOT_STATED, r.rejection(Field.WITNESSES).reasonCode)
    }

    @Test fun aSeverityNobodySaidIsNotStated() {
        val r = run("A pipe burst in Room 4.", ExtractionPayload(severity = "severe", evidence = mapOf("severity" to "very bad")))
        assertEquals(ReasonCode.NOT_STATED, r.rejection(Field.SEVERITY).reasonCode)
    }

    @Test fun aTimeNobodySaidIsNotStated() {
        val r = run("A pipe burst in Room 4.", ExtractionPayload(date_time_text = "last week", evidence = mapOf("date_time_text" to "last week")))
        assertEquals(ReasonCode.NOT_STATED, r.rejection(Field.DATE_TIME).reasonCode)
    }

    @Test fun anUnknownTypeIsUnknownValue() {
        val r = run("there is a banana", ExtractionPayload(incident_type = "banana", evidence = mapOf("incident_type" to "banana")))
        assertEquals(ReasonCode.UNKNOWN_VALUE, r.rejection(Field.INCIDENT_TYPE).reasonCode)
    }
}

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayloadValidatorTest {
    private val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), TemporalResolver(ZoneId.of("Asia/Kolkata")))
    private val capturedAt = Instant.parse("2026-10-11T09:30:00Z") // 15:00 IST

    private val slide = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."

    private fun validate(transcript: String, payload: ExtractionPayload) = validator.validate(payload, transcript, capturedAt)

    private fun ValidationResult.proposal(f: Field) = proposals.singleOrNull { it.field == f }

    private fun ValidationResult.rejected(f: Field) = rejections.filter { it.field == f }

    private val slidePayload = ExtractionPayload(
        incident_type = "Water Leak", location = "Conference Room 2", date_time_text = "around 10 AM",
        immediate_actions = "isolated the area",
        evidence = mapOf(
            "incident_type" to "water leak", "location" to "near Conference Room 2",
            "date_time_text" to "around 10 AM", "immediate_actions" to "already isolated the area",
        ),
    )

    // ---- the problem-statement example ---------------------------------------------------------

    @Test fun slideExampleFillsFourFieldsAndLeavesSeverityAndDateToBeAsked() {
        val r = validate(slide, slidePayload)
        assertEquals("water_leak_flood", r.proposal(Field.INCIDENT_TYPE)!!.value.text)
        assertEquals(ValueStatus.REPORTED, r.proposal(Field.INCIDENT_TYPE)!!.value.status)
        assertEquals("Conference Room 2", r.proposal(Field.LOCATION)!!.value.text)
        assertEquals("isolated the area", r.proposal(Field.IMMEDIATE_ACTIONS)!!.value.text)
        assertNull(r.proposal(Field.SEVERITY), "severity was never stated")
        val time = r.proposal(Field.DATE_TIME)!!
        assertTrue("10:00" in time.value.text!!, time.value.text)
        assertTrue("date missing" in time.value.text!!, time.value.text)
        assertTrue("≈" in time.value.text!!, "approximate time must be marked: ${time.value.text}")
    }

    // ---- enums ---------------------------------------------------------------------------------

    @Test fun unknownIncidentTypeIsRejectedNotCoercedToOther() {
        val r = validate("there is a banana", ExtractionPayload(incident_type = "banana", evidence = mapOf("incident_type" to "banana")))
        assertNull(r.proposal(Field.INCIDENT_TYPE))
        assertEquals(1, r.rejected(Field.INCIDENT_TYPE).size)
    }

    @Test fun severityTheReporterNeverStatedIsRejectedNotSuggested() {
        val r = validate(slide, ExtractionPayload(severity = "severe", evidence = mapOf("severity" to "it is severe")))
        assertNull(r.proposal(Field.SEVERITY))
        assertEquals(1, r.rejected(Field.SEVERITY).size)
    }

    @Test fun statedSeverityIsAccepted() {
        val r = validate("it is a minor leak", ExtractionPayload(severity = "minor", evidence = mapOf("severity" to "minor leak")))
        assertEquals("minor", r.proposal(Field.SEVERITY)!!.value.text)
        assertEquals(ValueStatus.REPORTED, r.proposal(Field.SEVERITY)!!.value.status)
    }

    @Test fun aHazardTheReporterDeniedIsRejected() {
        val r = validate("there is no fire, only smoke from the toaster",
            ExtractionPayload(incident_type = "fire_smoke", evidence = mapOf("incident_type" to "no fire")))
        assertNull(r.proposal(Field.INCIDENT_TYPE))
        assertTrue(r.rejected(Field.INCIDENT_TYPE).single().reason.contains("negated", ignoreCase = true))
    }

    // ---- grounding -----------------------------------------------------------------------------

    @Test fun anUngroundedLocationBecomesASuggestionNotAFill() {
        val r = validate(slide, ExtractionPayload(location = "Main Auditorium", evidence = mapOf("location" to "Main Auditorium")))
        val p = r.proposal(Field.LOCATION)!!
        assertEquals(ValueStatus.SUGGESTED, p.value.status)
        assertNotNull(p.note)
    }

    @Test fun hallucinatedWitnessIsDroppedAndNothingIsProposed() {
        val r = validate(slide, ExtractionPayload(witnesses = listOf("John"), evidence = mapOf("witnesses" to "John saw it")))
        assertNull(r.proposal(Field.WITNESSES))
        assertEquals(1, r.rejected(Field.WITNESSES).size)
    }

    @Test fun groundedPeopleAreJoinedAndUngroundedOnesDropped() {
        val r = validate("Ravi slipped near the stairs and Priya saw it",
            ExtractionPayload(persons_involved = listOf("Ravi", "Anil"), witnesses = listOf("Priya"),
                evidence = mapOf("persons_involved" to "Ravi slipped", "witnesses" to "Priya saw it")))
        assertEquals("Ravi", r.proposal(Field.PERSONS_INVOLVED)!!.value.text)
        assertEquals("Priya", r.proposal(Field.WITNESSES)!!.value.text)
        assertEquals(1, r.rejected(Field.PERSONS_INVOLVED).size)
    }

    @Test fun hindiLocationIsGrounded() {
        val r = validate("कॉन्फ़्रेंस रूम 2 के पास पानी का रिसाव है",
            ExtractionPayload(location = "कॉन्फ़्रेंस रूम 2", evidence = mapOf("location" to "रूम 2 के पास")))
        assertEquals(ValueStatus.REPORTED, r.proposal(Field.LOCATION)!!.value.status)
    }

    // ---- tense / aspect / modality effects ------------------------------------------------------

    @Test fun aPlannedActionIsNotAnImmediateActionAndBecomesAFollowUpSuggestion() {
        val r = validate("there is a leak and we will call maintenance",
            ExtractionPayload(immediate_actions = "call maintenance", evidence = mapOf("immediate_actions" to "we will call maintenance")))
        assertNull(r.proposal(Field.IMMEDIATE_ACTIONS))
        assertTrue(r.rejected(Field.IMMEDIATE_ACTIONS).single().reason.contains("planned", ignoreCase = true))
        val followUp = r.proposal(Field.RECOMMENDED_FOLLOW_UP)!!
        assertEquals(ValueStatus.SUGGESTED, followUp.value.status)
        assertTrue("call maintenance" in followUp.value.text!!)
    }

    @Test fun aNegatedActionIsNotRecordedAsDone() {
        val r = validate("we did not isolate the area",
            ExtractionPayload(immediate_actions = "isolated the area", evidence = mapOf("immediate_actions" to "did not isolate the area")))
        assertNull(r.proposal(Field.IMMEDIATE_ACTIONS))
    }

    @Test fun aHedgedValueIsOnlyASuggestion() {
        val r = validate("I think it is near conference room 2",
            ExtractionPayload(location = "conference room 2", evidence = mapOf("location" to "near conference room 2")))
        assertEquals(ValueStatus.SUGGESTED, r.proposal(Field.LOCATION)!!.value.status)
    }

    @Test fun nobodySawItIsAnExplicitNoneForWitnesses() {
        val r = validate("there was a leak, nobody saw it", ExtractionPayload(evidence = emptyMap()))
        val w = r.proposal(Field.WITNESSES)!!
        assertEquals(ValueStatus.EXPLICIT_NONE, w.value.status)
        assertNull(w.value.text)
    }

    @Test fun nobodyWasHurtIsAnExplicitNoneForPersonsInvolved() {
        val r = validate("there was a leak, nobody was hurt", ExtractionPayload())
        assertEquals(ValueStatus.EXPLICIT_NONE, r.proposal(Field.PERSONS_INVOLVED)!!.value.status)
    }

    @Test fun aSpecificPersonWhoDidNotSeeItIsNeverAWitness() {
        val r = validate("Ravi did not see it",
            ExtractionPayload(witnesses = listOf("Ravi"), evidence = mapOf("witnesses" to "Ravi did not see it")))
        assertNull(r.proposal(Field.WITNESSES), "must not become a witness, nor 'none'")
        assertTrue(r.rejected(Field.WITNESSES).single().reason.contains("negated", ignoreCase = true))
    }

    @Test fun hindiNobodyIsHurtIsAnExplicitNone() {
        val r = validate("koi ghayal nahi hua", ExtractionPayload())
        assertEquals(ValueStatus.EXPLICIT_NONE, r.proposal(Field.PERSONS_INVOLVED)!!.value.status)
    }

    // ---- time ----------------------------------------------------------------------------------

    @Test fun kalIsFlaggedAmbiguousAndKeepsTheOriginalWords() {
        val r = validate("kal raat paani leak hua", ExtractionPayload(date_time_text = "kal raat", evidence = mapOf("date_time_text" to "kal raat")))
        val p = r.proposal(Field.DATE_TIME)!!
        assertTrue(p.note!!.contains("ambiguous", ignoreCase = true), p.note)
        assertTrue("kal raat" in p.value.text!!)
    }

    @Test fun anUnresolvableTimeKeepsTheOriginalTextAndSaysItNeedsAsking() {
        val r = validate("it happened at lunch", ExtractionPayload(date_time_text = "at lunch", evidence = mapOf("date_time_text" to "at lunch")))
        val p = r.proposal(Field.DATE_TIME)!!
        assertEquals("at lunch", p.value.text)
        assertTrue(p.note!!.contains("could not", ignoreCase = true), p.note)
    }

    @Test fun resolvedRelativeTimeUsesTheCaptureInstant() {
        val r = validate("it happened 2 hours ago", ExtractionPayload(date_time_text = "2 hours ago", evidence = mapOf("date_time_text" to "2 hours ago")))
        assertTrue("2026-10-11" in r.proposal(Field.DATE_TIME)!!.value.text!!)
        assertTrue("13:00" in r.proposal(Field.DATE_TIME)!!.value.text!!)
    }
}

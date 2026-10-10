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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Review of the recall patch (`lead/recall-salvage`): each rule it adds must keep a wrong value from becoming a
 * REPORTED one. Every case here is a real reply of a small model that the replay on E's dev split showed going wrong
 * (see `docs/experiments/C-20261011-recall-patch-review.md`).
 */
class RecallRulesTest {
    private val resolver = TemporalResolver(ZoneId.of("Asia/Kolkata"))
    private val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), resolver)
    private val capturedAt = Instant.parse("2026-10-11T09:30:00Z")

    private fun run(transcript: String, payload: ExtractionPayload) = validator.validate(payload, transcript, capturedAt)

    private fun ValidationResult.proposal(f: Field) = proposals.singleOrNull { it.field == f }

    // ---- what the patch is for -----------------------------------------------------------------------------------

    @Test fun aQuoteWrittenOnlyUnderEvidenceFillsTheFieldWhenItIsInTheTranscript() {
        val r = run(
            "We gave her an ice pack. It happened outside the washroom.",
            ExtractionPayload(evidence = mapOf("immediate_actions" to "We gave her an ice pack", "location" to "outside the washroom")),
        )
        assertEquals("outside the washroom", r.proposal(Field.LOCATION)!!.value.text)
        assertEquals(ValueStatus.REPORTED, r.proposal(Field.LOCATION)!!.value.status)
        assertNotNull(r.proposal(Field.IMMEDIATE_ACTIONS))
    }

    @Test fun anEvidenceQuoteThatIsNotInTheTranscriptNeverFillsAField() {
        val r = run("The pump is leaking.", ExtractionPayload(evidence = mapOf("location" to "Server Room 9")))
        assertNull(r.proposal(Field.LOCATION)?.takeIf { it.value.status == ValueStatus.REPORTED })
    }

    @Test fun noPowerInTheClauseDoesNotRemoveTheLocation() {
        val r = run(
            "The whole third floor of South Block has had no power since 9 PM yesterday.",
            ExtractionPayload(location = "South Block", evidence = mapOf("location" to "South Block")),
        )
        assertEquals(ValueStatus.REPORTED, r.proposal(Field.LOCATION)!!.value.status)
    }

    // ---- a negation right at the quoted place still removes it -------------------------------------------------

    @Test fun aLocationThatIsNegatedAtTheQuoteIsStillRejected() {
        val r = run(
            "The leak is not in Room 2, it is in Room 4.",
            ExtractionPayload(location = "Room 2", evidence = mapOf("location" to "Room 2")),
        )
        assertNull(r.proposal(Field.LOCATION))
        assertEquals(1, r.rejections.count { it.field == Field.LOCATION })
    }

    @Test fun theCorrectedLocationInThatSentenceIsKept() {
        val r = run(
            "The leak is not in Room 2, it is in Room 4.",
            ExtractionPayload(location = "Room 4", evidence = mapOf("location" to "Room 4")),
        )
        assertEquals("Room 4", r.proposal(Field.LOCATION)!!.value.text)
    }

    // ---- people must be names ----------------------------------------------------------------------------------

    @Test fun aStaffMemberIsARoleNotAName() {
        val r = run(
            "A staff member fell near the counter of the stationery shop.",
            ExtractionPayload(persons_involved = listOf("staff member"), evidence = mapOf("persons_involved" to "staff member")),
        )
        assertNull(r.proposal(Field.PERSONS_INVOLVED))
    }

    @Test fun aNamedPersonWithARoleIsKept() {
        val r = run(
            "The cook Suresh put it out with the fire blanket.",
            ExtractionPayload(persons_involved = listOf("Suresh"), evidence = mapOf("persons_involved" to "The cook Suresh")),
        )
        assertEquals("Suresh", r.proposal(Field.PERSONS_INVOLVED)!!.value.text)
    }

    @Test fun someoneWhoWasThereButDidNotSeeIsNotAWitness() {
        val r = run(
            "A student slipped on the wet floor near the Auditorium. Rahul was there but he didn't see her fall.",
            ExtractionPayload(witnesses = listOf("Rahul"), evidence = mapOf("witnesses" to "Rahul")),
        )
        assertNull(r.proposal(Field.WITNESSES))
    }

    // ---- a promoted quote must be an action --------------------------------------------------------------------

    @Test fun anObservationUnderEvidenceIsNotAnImmediateAction() {
        for (quote in listOf("I saw it at 8:10 PM", "water is all over the floor", "fell over")) {
            val r = run(
                "Someone smashed the glass door. $quote. The glass is still on the floor.",
                ExtractionPayload(evidence = mapOf("immediate_actions" to quote)),
            )
            assertNull(r.proposal(Field.IMMEDIATE_ACTIONS), "'$quote' is not something the reporter did")
        }
    }

    @Test fun anActionUnderEvidenceIsPromoted() {
        for (quote in listOf("put a cone next to it", "I've informed the estate office", "We cleaned the wound with the first aid kit")) {
            val r = run("A branch fell on a car. $quote.", ExtractionPayload(evidence = mapOf("immediate_actions" to quote)))
            assertNotNull(r.proposal(Field.IMMEDIATE_ACTIONS), "'$quote' is an action")
        }
    }

    // ---- an incident type is not a job title -------------------------------------------------------------------

    @Test fun aSecurityGuardIsNotASecurityIncident() {
        val r = run(
            "A delivery van reversed without looking and nearly hit a security guard near the main gate.",
            ExtractionPayload(incident_type = "security", evidence = mapOf("incident_type" to "security")),
        )
        assertNotEquals(ValueStatus.REPORTED, r.proposal(Field.INCIDENT_TYPE)?.value?.status)
    }

    @Test fun aSecurityIncidentStillCounts() {
        val r = run(
            "Someone broke into the store room, a security issue.",
            ExtractionPayload(incident_type = "security", evidence = mapOf("incident_type" to "security issue")),
        )
        assertEquals(ValueStatus.REPORTED, r.proposal(Field.INCIDENT_TYPE)!!.value.status)
    }

    // ---- times -------------------------------------------------------------------------------------------------

    @Test fun nightGuardsAreNotATime() {
        assertNull(resolver.findTimePhrase("There were 2 night guards on duty and 3 morning shift workers."))
    }

    @Test fun elevenThisMorningIsATime() = assertEquals("at 11 this morning", resolver.findTimePhrase("It happened at 11 this morning."))

    @Test fun aCorrectionInALaterSentenceWins() =
        assertEquals("10 PM", resolver.findTimePhrase("It started at 9 PM. No wait, 10 PM."))

    @Test fun aTimeInAPlannedClauseIsNotTakenAsWhenItHappened() {
        val r = run(
            "The leak is in Room 4. The plumber will come at 5 PM.",
            ExtractionPayload(location = "Room 4", evidence = mapOf("location" to "Room 4")),
        )
        assertNull(r.proposal(Field.DATE_TIME))
    }

    // ---- a cue word may stand in for a quote only when it really names the type --------------------------------

    @Test fun aWeakWordDoesNotStandInForAQuote() {
        val r = run(
            "The water cooler on the second floor is making a loud noise.",
            ExtractionPayload(incident_type = "water_leak_flood"),
        )
        assertEquals(ValueStatus.SUGGESTED, r.proposal(Field.INCIDENT_TYPE)!!.value.status)
    }

    @Test fun aStrongCueWordIsTakenAsTheQuote() {
        val r = run("There is a leak in the pantry.", ExtractionPayload(incident_type = "water_leak_flood"))
        val p = r.proposal(Field.INCIDENT_TYPE)!!
        assertEquals(ValueStatus.REPORTED, p.value.status)
        assertEquals("leak", p.value.evidence)
    }

    @Test fun aStolenBicycleIsASecurityIncidentEvenThoughTheGuardWasTold() {
        val r = run(
            "My bicycle was stolen from the cycle stand last night. I've told the security guard at the main gate.",
            ExtractionPayload(incident_type = "security", evidence = mapOf("incident_type" to "security")),
        )
        val p = r.proposal(Field.INCIDENT_TYPE)!!
        assertEquals(ValueStatus.REPORTED, p.value.status)
        assertEquals("stolen", p.value.evidence)
    }
}

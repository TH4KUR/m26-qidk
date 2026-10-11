package com.megathon.incident.llm

import com.megathon.incident.domain.ExtractionPayload
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A stated severity of "none" / "no harm" inside a one-clause fragment (no punctuation) must not negate the actions,
 * witnesses and places in the same fragment, and "without an injury" must not negate the event that came without one.
 * Found by auditing B's 800 synthetic labels through the real validator (62 rejected, 29 after the name fix).
 */
class NegationScopeTest {
    private val tam = TamDetector(TamCueLists.loadDefault())
    private val validator = PayloadValidator(tam, TemporalResolver(ZoneId.of("Asia/Kolkata")))
    private val at = Instant.parse("2026-10-11T05:30:00Z")

    private fun modality(text: String) = tam.analyze(text).first().tam.modality

    @Test fun aStatedNoHarmDoesNotNegateTheFragment() {
        assertEquals(
            Modality.ASSERTED,
            modality("uh water is dripping from the ceiling pipe outside Lab A4 um we closed the inlet valve Sonal saw the incident I rate this as no harm"),
        )
        assertEquals(Modality.ASSERTED, modality("the shelf tipped over we moved it I rate the severity none"))
        assertEquals(Modality.ASSERTED, modality("it is not serious we closed the valve"))
    }

    @Test fun otherNegationsStillNegate() {
        assertEquals(Modality.NEGATED, modality("none of them were hurt"))
        assertEquals(Modality.NEGATED, modality("we did not close the valve"))
        assertEquals(Modality.NEGATED, modality("there was no fire"))
    }

    @Test fun anActionNextToAStatedNoHarmIsReported() {
        val r = validator.validate(
            ExtractionPayload(
                immediate_actions = "closed the inlet valve", witnesses = listOf("Sonal"),
                evidence = mapOf("immediate_actions" to "We closed the inlet valve", "witnesses" to "Sonal saw the incident"),
            ),
            "uh Water is dripping from the ceiling pipe outside Lab A4 um We closed the inlet valve Sonal saw the incident I rate this as no harm",
            at,
        )
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.IMMEDIATE_ACTIONS }.value.status)
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.WITNESSES }.value.status)
    }

    @Test fun anEventThatCameWithoutAnInjuryIsStillThatEvent() {
        val r = validator.validate(
            ExtractionPayload(
                incident_type = "illness", location = "near the Sports Ground pavilion",
                evidence = mapOf("incident_type" to "Vikram lost consciousness without an injury", "location" to "near the Sports Ground pavilion"),
            ),
            "Vikram lost consciousness without an injury near the Sports Ground pavilion. It happened three days ago at about 3 PM.",
            at,
        )
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.INCIDENT_TYPE }.value.status)
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.LOCATION }.value.status)
    }

    @Test fun aTypeNegatedAtItsOwnCueIsStillRejected() {
        val r = validator.validate(
            ExtractionPayload(incident_type = "fire_smoke", evidence = mapOf("incident_type" to "no fire")),
            "There was no fire in the lab, only a bad smell.",
            at,
        )
        assertNull(r.proposals.firstOrNull { it.field == Field.INCIDENT_TYPE })
    }

    @Test fun aFailureStateIsNotANegationOfTheReport() {
        assertEquals(Modality.ASSERTED, modality("The door access reader is not working at the Research Block entrance"))
        assertEquals(Modality.ASSERTED, modality("the printer isn't responding and the lift won't start"))
        assertEquals(Modality.NEGATED, modality("nobody reported the printer"))
    }

    @Test fun anAbsenceOfInjuryDoesNotNegateThePersonWhoCollapsed() {
        val r = validator.validate(
            ExtractionPayload(persons_involved = listOf("Vikram"), evidence = mapOf("persons_involved" to "Vikram lost consciousness without an injury")),
            "Vikram lost consciousness without an injury near the Sports Ground pavilion. We called an ambulance.",
            at,
        )
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.PERSONS_INVOLVED }.value.status)
    }

    @Test fun aReaderThatIsNotWorkingKeepsItsPlaceAndItsName() {
        val r = validator.validate(
            ExtractionPayload(
                location = "at the Research Block entrance", equipment_asset = "door access reader",
                evidence = mapOf("location" to "at the Research Block entrance", "equipment_asset" to "The door access reader is not working"),
            ),
            "The door access reader is not working at the Research Block entrance. It happened a couple of hours ago.",
            at,
        )
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.LOCATION }.value.status)
        assertEquals(ValueStatus.REPORTED, r.proposals.single { it.field == Field.EQUIPMENT_ASSET }.value.status)
    }
}

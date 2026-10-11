package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "My friend Rahul was with me when it happened" names a witness without the word "saw". The model often files such a
 * person as "involved" (or forgets them); the rules offer the witness chip (report first, suggest after, spec D16).
 */
class PresenceWitnessChipsTest {
    private val engine = RuleSuggestionEngine(
        SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
        Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
    )

    private fun state(vararg fields: Pair<Field, FieldValue>): ReportState {
        var s = ReportState.empty("r1", 0)
        for ((f, v) in fields) s = s.copy(report = s.report.with(f, v))
        return s
    }

    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private fun rule(s: ReportState, transcript: String) = engine.suggest(s, transcript).filter { it.source == Source.RULE }

    private val said = "There was a water leak near Conference Room 2 around 10 AM. My friend Rahul was with me when it happened."

    // ---- a person the model filed as involved ------------------------------------------------------------------

    @Test fun aPersonWhoWasWithTheSpeakerMovesToWitnesses() {
        val s = state(Field.PERSONS_INVOLVED to heard("Rahul"))
        val chips = rule(s, said).filter { it.field == Field.WITNESSES }
        assertEquals(1, chips.size, chips.toString())
        assertEquals("Rahul", chips.single().proposed)
        assertTrue("Rahul" in chips.single().reason, chips.single().reason)
    }

    @Test fun acceptingTheChipsMovesThePerson() {
        var s = state(Field.PERSONS_INVOLVED to heard("Rahul, Priya"))
        val transcript = "Priya slipped near the pantry. My friend Rahul was with me when it happened."
        for (chip in rule(s, transcript)) s = engine.accept(s, chip)
        assertEquals("Priya", s.report.value(Field.PERSONS_INVOLVED).text)
        assertEquals("Rahul", s.report.value(Field.WITNESSES).text)
    }

    @Test fun otherPresenceWordingsCount() {
        for (sentence in listOf(
            "Rahul was there when it happened.",
            "Rahul was standing next to me when the pipe burst.",
            "Rahul was present at the time.",
            "Rahul was nearby when it started.",
            "Rahul was in the room when the fire started.",
        )) {
            val s = state(Field.PERSONS_INVOLVED to heard("Rahul"))
            assertTrue(rule(s, sentence).any { it.field == Field.WITNESSES && it.proposed == "Rahul" }, sentence)
        }
    }

    @Test fun aPersonWhoWasAlsoHurtStaysInvolved() {
        val s = state(Field.PERSONS_INVOLVED to heard("Rahul"))
        assertTrue(rule(s, "Rahul was with me when he slipped and hurt his arm.").none { it.field == Field.WITNESSES })
    }

    @Test fun aNegatedPresenceIsNotWitnessing() {
        val s = state(Field.PERSONS_INVOLVED to heard("Rahul"))
        assertTrue(rule(s, "Rahul was not there when it happened.").none { it.field == Field.WITNESSES })
    }

    // ---- a person the model did not emit at all ----------------------------------------------------------------

    @Test fun anUnlistedPersonWhoWasWithTheSpeakerIsOfferedAsAWitness() {
        val s = state(Field.LOCATION to heard("Conference Room 2"))
        val chip = rule(s, said).single { it.field == Field.WITNESSES }
        assertEquals(Suggestion(Field.WITNESSES, "", "Rahul", chip.reason, Source.RULE), chip)
    }

    @Test fun twoNamesAreOfferedTogether() {
        val s = state()
        val chip = rule(s, "Rahul and Priya were with me when it happened.").single { it.field == Field.WITNESSES }
        assertEquals("Rahul, Priya", chip.proposed)
    }

    @Test fun hinglishPresenceWorks() {
        val s = state()
        assertTrue(rule(s, "Rahul mere saath tha jab yeh hua.").any { it.field == Field.WITNESSES && it.proposed == "Rahul" })
    }

    @Test fun anAlreadyFilledWitnessFieldGetsNoRecoveryChip() {
        val s = state(Field.WITNESSES to heard("Priya"))
        assertTrue(rule(s, said).none { it.field == Field.WITNESSES && it.original.isEmpty() })
    }

    @Test fun theSpeakerAndCommonWordsAreNotNames() {
        val s = state()
        for (sentence in listOf("I was with the supervisor.", "There was a water leak.", "It was there when I came.", "The guard was there.")) {
            assertTrue(rule(s, sentence).none { it.field == Field.WITNESSES }, sentence)
        }
    }

    @Test fun aPlaceNamedInTheLocationIsNotAPerson() {
        val s = state(Field.LOCATION to heard("Maple Hostel"))
        assertTrue(rule(s, "Maple was there on the corner.").none { it.field == Field.WITNESSES })
    }
}

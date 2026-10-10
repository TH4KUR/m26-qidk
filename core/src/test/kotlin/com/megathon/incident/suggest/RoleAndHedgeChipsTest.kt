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

class RoleAndHedgeChipsTest {

    private val engine = RuleSuggestionEngine(
        SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
        Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
    )

    private fun state(vararg fields: Pair<Field, FieldValue>): ReportState {
        var s = ReportState.empty("r1", 0)
        for ((f, v) in fields) s = s.copy(report = s.report.with(f, v))
        return s
    }

    private fun heard(text: String, evidence: String? = text) = FieldValue(text, ValueStatus.REPORTED, evidence)

    private fun chipsFor(field: Field, s: ReportState, transcript: String) = engine.suggest(s, transcript).filter { it.field == field }

    private fun roleChips(s: ReportState, transcript: String) = engine.suggest(s, transcript).filter { it.source == Source.RULE }

    // ---- witness vs involved -------------------------------------------------------------------

    private val slip = "Ravi slipped near the pantry, Priya saw it."
    private val witnessReason = "Priya only saw it, so Priya is a witness, not involved."

    @Test
    fun aPersonWhoOnlySawItIsOfferedAsAWitness() {
        val s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"))
        assertEquals(
            listOf(
                Suggestion(Field.PERSONS_INVOLVED, "Ravi, Priya", "Ravi", witnessReason, Source.RULE),
                Suggestion(Field.WITNESSES, "", "Priya", witnessReason, Source.RULE),
            ),
            roleChips(s, slip),
        )
    }

    @Test
    fun acceptingBothRoleChipsMovesThePerson() {
        var s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"))
        for (chip in roleChips(s, slip)) s = engine.accept(s, chip)
        assertEquals("Ravi", s.report.value(Field.PERSONS_INVOLVED).text)
        assertEquals("Priya", s.report.value(Field.WITNESSES).text)
        assertEquals(ValueStatus.USER_CONFIRMED, s.report.value(Field.WITNESSES).status)
    }

    @Test
    fun anExistingWitnessListIsExtended() {
        val s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"), Field.WITNESSES to heard("Suresh"))
        assertEquals("Suresh, Priya", chipsFor(Field.WITNESSES, s, "$slip Suresh saw it too.").single().proposed)
    }

    @Test
    fun someoneWhoWasAlsoHurtStaysInvolved() {
        val s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"))
        assertTrue(roleChips(s, "Priya saw Ravi slip. Then Priya slipped too.").isEmpty())
    }

    @Test
    fun aNegatedSightingIsNotAWitness() {
        val s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"))
        assertTrue(roleChips(s, "Ravi slipped, Priya did not see it.").isEmpty())
    }

    @Test
    fun neverSawIsNotASighting() {
        val s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"))
        assertTrue(roleChips(s, "Ravi slipped, Priya never saw it.").isEmpty())
    }

    @Test
    fun someoneHurtBeforeTheSightingStaysInvolved() {
        val s = state(Field.PERSONS_INVOLVED to heard("Ravi, Priya"))
        assertTrue(roleChips(s, "Ravi slipped. Priya fell down the stairs where she saw the leak.").isEmpty())
    }

    @Test
    fun theOnlyPersonInvolvedIsNotRemovedLeavingTheFieldEmpty() {
        val s = state(Field.PERSONS_INVOLVED to heard("Priya"))
        assertEquals(listOf(Field.WITNESSES), roleChips(s, "Priya saw the leak.").map { it.field })
    }

    // ---- "nobody hurt" against the injury type -------------------------------------------------

    @Test
    fun injuryTypeWithNobodyHurtGetsAChip() {
        val s = state(Field.INCIDENT_TYPE to heard("injury", "slipped"))
        val chip = chipsFor(Field.INCIDENT_TYPE, s, "Ravi slipped on the wet floor. Nobody was hurt.").single()
        assertEquals("injury", chip.original)
        assertEquals("near_miss", chip.proposed)
        assertEquals(Source.RULE, chip.source)
        assertTrue("Nobody was hurt" in chip.reason, chip.reason)
    }

    @Test
    fun injuryTypeWithSomeoneHurtGetsNoChip() {
        val s = state(Field.INCIDENT_TYPE to heard("injury", "hurt"))
        assertTrue(chipsFor(Field.INCIDENT_TYPE, s, "Ravi slipped and was hurt.").isEmpty())
    }

    // ---- hedges --------------------------------------------------------------------------------

    @Test
    fun aHedgedValueGetsAConfirmChip() {
        val s = state(Field.LOCATION to FieldValue("Room 2", ValueStatus.SUGGESTED, "Room 2"))
        assertEquals(
            listOf(Suggestion(Field.LOCATION, "Room 2", "Room 2", "The speaker said \"I think\"; confirm the location.", Source.TEXT_CUE)),
            chipsFor(Field.LOCATION, s, "I think it was Room 2."),
        )
    }

    @Test
    fun aPlainStatementGetsNoHedgeChip() =
        assertTrue(chipsFor(Field.LOCATION, state(Field.LOCATION to heard("Room 2")), "It was Room 2.").isEmpty())

    @Test
    fun aHedgeInAnotherSentenceDoesNotCount() =
        assertTrue(chipsFor(Field.LOCATION, state(Field.LOCATION to heard("Room 2")), "I think it started at 10. The leak is in Room 2.").isEmpty())

    @Test
    fun aValueTheUserConfirmedIsNotQuestionedAgain() {
        val s = state(Field.LOCATION to FieldValue("Room 2", ValueStatus.USER_CONFIRMED, "Room 2"))
        assertTrue(chipsFor(Field.LOCATION, s, "I think it was Room 2.").isEmpty())
    }
}

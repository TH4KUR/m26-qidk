package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.suggest.Homophones
import com.megathon.incident.suggest.RuleSuggestionEngine
import com.megathon.incident.suggest.SiteDirectory
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Replies with one location and the words as evidence, whatever is said. */
private class LocationLlm(private val location: String, private val evidence: String = location) : LlmEngine {
    override val name = "SCRIPTED"
    override suspend fun complete(request: LlmRequest) = LlmResult(
        """{"tool":"update_report","arguments":{"incident_type":"fire","location":"$location","evidence":{"incident_type":"fire","location":"$evidence"}}}""",
        1, Compute.SIMULATED,
    )
}

/** D's rule engine, wired into the session: chips are suggestions the reporter accepts or dismisses. */
class SuggestionsInSessionTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val engine = RuleSuggestionEngine(SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()), clock)

    private fun session(llm: LlmEngine, withEngine: Boolean = true) =
        Pipeline.newSession(llm, InMemoryReportStore(), clock, suggestions = if (withEngine) engine else null)

    private fun ReportSession.say(text: String) = runBlocking { onUtterance(text) }
    private fun ReportSession.location() = ui.value.fields.first { it.field == Field.LOCATION }
    private fun ReportSession.chips() = ui.value.suggestions

    private fun heardAt(spoken: String, sentence: String = "There's a massive fire in $spoken right now."): ReportSession =
        session(LocationLlm(spoken)).also { it.say(sentence) }

    @Test fun aHeardASh2GetsAChipForTheCanonicalSiteNameAndTheFieldIsUntouched() {
        val s = heardAt("a sh2")
        val chip = s.chips().single { it.field == Field.LOCATION }
        assertEquals("Seminar Hall 2", chip.proposed)
        assertEquals("a sh2", chip.original)
        assertEquals(Source.GAZETTEER, chip.source)
        assertEquals("a sh2", s.location().value, "the report keeps what was heard until the reporter taps")
        assertEquals(UiStatus.HEARD, s.location().status)
    }

    @Test fun shTwoAndSHDashTwoGetTheSameChip() {
        for (spoken in listOf("sh two", "SH-2")) {
            val s = heardAt(spoken, "Huge fire in $spoken today morning.")
            assertEquals("Seminar Hall 2", s.chips().single { it.field == Field.LOCATION }.proposed, spoken)
        }
    }

    @Test fun withoutAnEngineThereAreNoChips() {
        assertTrue(session(LocationLlm("a sh2"), withEngine = false).also { it.say("fire in a sh2") }.chips().isEmpty())
    }

    @Test fun acceptingTheChipSetsTheFieldConfirmedAndUndoBringsTheHeardValueAndTheChipBack() {
        val s = heardAt("a sh2")
        val chip = s.chips().single { it.field == Field.LOCATION }
        s.acceptSuggestion(chip)
        assertEquals("Seminar Hall 2", s.location().value)
        assertEquals(UiStatus.CONFIRMED, s.location().status)
        assertEquals(ValueStatus.USER_CONFIRMED, s.ui.value.report.value(Field.LOCATION).status)
        assertTrue(s.chips().none { it.field == Field.LOCATION }, "an accepted chip is gone")
        val j = s.ui.value.journal.last { it.field == Field.LOCATION }
        assertEquals("a sh2", j.before)
        assertEquals("Seminar Hall 2", j.after)
        assertTrue(s.ui.value.canUndo)

        s.undo()
        assertEquals("a sh2", s.location().value)
        assertEquals(UiStatus.HEARD, s.location().status)
        assertEquals(chip, s.chips().single { it.field == Field.LOCATION }, "after undo the chip is offered again")
    }

    @Test fun rejectingTheChipRemovesItKeepsTheFieldAndItIsNotOfferedAgain() {
        val s = heardAt("a sh2")
        val chip = s.chips().single { it.field == Field.LOCATION }
        s.rejectSuggestion(chip)
        assertTrue(s.chips().none { it.field == Field.LOCATION })
        assertEquals("a sh2", s.location().value)
        assertEquals(UiStatus.HEARD, s.location().status)
        s.say("It is still burning in a sh2.") // the same words heard again: the dismissed chip stays dismissed
        assertTrue(s.chips().none { it.field == Field.LOCATION })
    }

    @Test fun aLocationTheReporterConfirmedOrTypedIsNeverReplacedByAChip() {
        val typed = heardAt("a sh2").also { it.edit(Field.LOCATION, "a sh2") }
        assertTrue(typed.chips().none { it.field == Field.LOCATION }, "typed values get no chip")

        val s = heardAt("a sh2")
        val chip = s.chips().single { it.field == Field.LOCATION }
        s.edit(Field.LOCATION, "Lab Block")
        s.acceptSuggestion(chip) // a stale chip, made for a value that is no longer there
        assertEquals("Lab Block", s.location().value)
        assertEquals(ValueStatus.USER_EDITED, s.ui.value.report.value(Field.LOCATION).status)
    }

    @Test fun aCanonicalNameAndTheSeveritySuggestionGetNoChip() {
        val s = session(FakeLlmEngine())
        s.say(FakeLlmEngine.DECK_TRANSCRIPT)
        assertEquals("Conference Room 2", s.location().value)
        assertTrue(s.chips().isEmpty(), "no chip: ${s.chips()}")
    }

    @Test fun theExistingSeverityAcceptStillWorksWithAnEngine() {
        val s = session(FakeLlmEngine())
        s.say(FakeLlmEngine.DECK_TRANSCRIPT)
        s.acceptSuggestion(Field.SEVERITY)
        assertEquals(UiStatus.CONFIRMED, s.ui.value.fields.first { it.field == Field.SEVERITY }.status)
    }

    @Test fun resetClearsDecisionsAndChips() {
        val s = heardAt("a sh2")
        s.rejectSuggestion(s.chips().single { it.field == Field.LOCATION })
        s.reset()
        assertTrue(s.chips().isEmpty())
        s.say("There's a massive fire in a sh2 right now.")
        assertNotEquals(0, s.chips().size, "a new report starts with no dismissals")
    }

    @Test fun aHedgedValueGetsAConfirmChipAndAcceptingConfirmsIt() {
        val s = session(LocationLlm("Seminar Hall 2"))
        s.say("I think it was Seminar Hall 2.")
        val chip = s.chips().single { it.field == Field.LOCATION }
        assertEquals(Source.TEXT_CUE, chip.source)
        assertEquals(chip.original, chip.proposed)
        s.acceptSuggestion(chip)
        assertEquals(UiStatus.CONFIRMED, s.location().status)
        assertTrue(s.chips().none { it.field == Field.LOCATION })
    }

    @Test fun aLiftIsNotAPlaceSoNearTheLiftGetsNoLocationChip() {
        val s = heardAt("near the lift", "There was a fire near the lift.")
        assertTrue(s.chips().none { it.field == Field.LOCATION }, "${s.chips()}")
    }
}

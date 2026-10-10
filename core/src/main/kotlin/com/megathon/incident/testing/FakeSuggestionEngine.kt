package com.megathon.incident.testing

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.llm.Compute
import com.megathon.incident.suggest.ChipDecisions
import com.megathon.incident.suggest.SuggestionEngine
import java.time.Clock

/**
 * Scripted stand-in for the real engine so A can build the chips UI now. It knows exactly one chip:
 * a heard location of "seminar hall to" gets "Seminar Hall 2" (the deck example on our campus, same
 * as `fixtures/suggestions/deck_example.json`). Accept and reject are the real bookkeeping.
 * Labelled SIMULATED: never show its chips as live suggestions.
 */
class FakeSuggestionEngine(clock: Clock) : SuggestionEngine {

    private val decisions = ChipDecisions(clock)

    override val compute = Compute.SIMULATED

    override fun suggest(state: ReportState, transcript: String): List<Suggestion> {
        val heard = state.report.value(Field.LOCATION).text ?: return emptyList()
        if (heard.trim().lowercase() != "seminar hall to") return emptyList()
        val chip = Suggestion(Field.LOCATION, heard, "Seminar Hall 2", "Heard \"hall to\"; the site has Seminar Hall 2.", Source.GAZETTEER)
        return listOf(chip).filterNot { state.hasDecided(it) }
    }

    override fun accept(state: ReportState, s: Suggestion): ReportState = decisions.accept(state, s)

    override fun reject(state: ReportState, s: Suggestion): ReportState = decisions.reject(state, s)
}

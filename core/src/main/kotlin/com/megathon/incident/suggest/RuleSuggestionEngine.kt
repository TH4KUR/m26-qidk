package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.Compute
import java.time.Clock

/**
 * The real suggestion engine (spec D16): rules on the CPU, no model. It runs after the LLM on the
 * values already in the report, and only proposes; the report keeps what was heard until the user taps.
 * Values the user already confirmed or typed are never second-guessed.
 */
class RuleSuggestionEngine(site: SiteDirectory, homophones: HomophoneTable, clock: Clock) : SuggestionEngine {

    private val snapper = GazetteerSnapper(site, homophones)
    private val decisions = ChipDecisions(clock)

    override val compute = Compute.CPU

    override fun suggest(state: ReportState, transcript: String): List<Suggestion> {
        val chips = mutableListOf<Suggestion>()
        for ((field, kind) in SNAPPED_FIELDS) {
            val value = state.report.value(field)
            if (value.status !in MACHINE_HEARD) continue
            val text = value.text ?: continue
            val snap = snapper.snap(text, kind) ?: continue
            chips += Suggestion(field, text, snap.entry.name, snap.reason, Source.GAZETTEER)
        }
        return chips.filterNot { state.hasDecided(it) }
    }

    override fun accept(state: ReportState, s: Suggestion): ReportState = decisions.accept(state, s)

    override fun reject(state: ReportState, s: Suggestion): ReportState = decisions.reject(state, s)

    private companion object {
        val SNAPPED_FIELDS = listOf(Field.LOCATION to EntryKind.PLACE, Field.EQUIPMENT_ASSET to EntryKind.ASSET)
        val MACHINE_HEARD = setOf(ValueStatus.REPORTED, ValueStatus.SUGGESTED)
    }
}

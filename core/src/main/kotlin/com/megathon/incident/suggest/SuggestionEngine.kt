package com.megathon.incident.suggest

import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.JournalEntry
import com.megathon.incident.domain.JournalKind
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.testing.Compute
import java.time.Clock

/**
 * Report first, suggest after (spec D16). The report keeps exactly what was heard; an engine only
 * proposes chips, and only the user's tap changes a field. Submit never waits on pending chips,
 * because chips are not stored: [suggest] is recomputed from the state each time.
 */
interface SuggestionEngine {
    /** Shown in the proof strip; a fake is [Compute.SIMULATED]. */
    val compute: Compute

    /** Pure and deterministic; never throws; skips chips the journal says were already decided. */
    fun suggest(state: ReportState, transcript: String): List<Suggestion>

    /** The field becomes the proposal as USER_CONFIRMED; what was heard stays in the journal. */
    fun accept(state: ReportState, s: Suggestion): ReportState

    /** The field is untouched; the decision is journaled so the chip is not offered again. */
    fun reject(state: ReportState, s: Suggestion): ReportState
}

/** Accept/reject bookkeeping shared by every engine, so the fake and the real one record decisions identically. */
class ChipDecisions(private val clock: Clock) {

    fun accept(state: ReportState, s: Suggestion): ReportState {
        val current = state.report.value(s.field)
        if (!applies(state, s, current)) return state
        val after = FieldValue(s.proposed, ValueStatus.USER_CONFIRMED, current.evidence)
        return state.copy(report = state.report.with(s.field, after))
            .record(JournalEntry(clock.millis(), s.field, JournalKind.SUGGESTION_ACCEPTED, current, after, s))
    }

    fun reject(state: ReportState, s: Suggestion): ReportState {
        val current = state.report.value(s.field)
        if (!applies(state, s, current)) return state
        return state.record(JournalEntry(clock.millis(), s.field, JournalKind.SUGGESTION_REJECTED, current, current, s))
    }

    /** A chip counts once, and only against the value it was made for: a stale chip never overwrites a later value. */
    private fun applies(state: ReportState, s: Suggestion, current: FieldValue): Boolean =
        !state.hasDecided(s) && (current.text ?: "") == s.original
}

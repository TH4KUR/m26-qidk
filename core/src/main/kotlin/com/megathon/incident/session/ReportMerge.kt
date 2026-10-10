package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.FieldProposal
import com.megathon.incident.llm.ValidationResult
import java.time.Instant
import java.time.ZoneId

data class MergeContext(val latestUtterance: String, val capturedAt: Instant, val zone: ZoneId, val atMs: Long)

data class MergeOutcome(
    val report: IncidentReport,
    val journal: List<JournalEntry>,
    /** Fields whose value needs a question (for example an ambiguous or future day). */
    val clarify: Set<Field>,
)

/**
 * Interim merge (the suggestion/merge owner's version replaces it): applies validated proposals to a report.
 *  - Filling an empty field is not a change of mind; replacing a value is journaled.
 *  - A model repeating itself, or guessing, never overwrites a value; only words **said in this utterance** can.
 *  - A suggestion only ever fills an empty field.
 *  - A time with no date is never completed: the value stays as the validator produced it and the field is flagged
 *    [MergeOutcome.clarify], so the planner asks which day.
 */
object ReportMerge {
    fun apply(report: IncidentReport, result: ValidationResult, ctx: MergeContext): MergeOutcome {
        var current = report
        val journal = mutableListOf<JournalEntry>()
        val clarify = mutableSetOf<Field>()

        for (p in result.proposals) {
            val cur = current.value(p.field)
            val replaced = decide(cur, p.value, ctx.latestUtterance) ?: continue
            current = current.with(p.field, replaced.value)
            if (replaced.journal) {
                journal += JournalEntry(p.field, cur.text, replaced.value.text, "heard a correction", ctx.atMs)
            }
            if (needsDay(p)) clarify += p.field
        }
        return MergeOutcome(current, journal, clarify)
    }

    private data class Decision(val value: FieldValue, val journal: Boolean)

    private fun decide(cur: FieldValue, new: FieldValue, utterance: String): Decision? {
        val newStatus = new.status
        if (newStatus == ValueStatus.SUGGESTED) {
            return if (cur.status == ValueStatus.MISSING) Decision(new, false) else null
        }
        return when (cur.status) {
            ValueStatus.MISSING, ValueStatus.UNKNOWN, ValueStatus.SUGGESTED, ValueStatus.EXPLICIT_NONE -> Decision(new, false)
            ValueStatus.REPORTED, ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED -> when {
                cur.text == new.text -> null
                saidNow(new, utterance) -> Decision(new.copy(status = ValueStatus.REPORTED), true)
                else -> null
            }
        }
    }

    /** The proposal's evidence (or value) appears in what was just said, so it is a deliberate correction. */
    private fun saidNow(v: FieldValue, utterance: String): Boolean {
        val probe = (v.evidence ?: v.text)?.trim().orEmpty()
        return probe.isNotEmpty() && utterance.contains(probe, ignoreCase = true)
    }

    /**
     * Ask which day only when the day really is unknown: no date at all (a bare time, a week), an ambiguous word ("kal"),
     * or a phrase the resolver could not read. A known date with no clock time ("yesterday morning") is a usable answer.
     */
    private fun needsDay(p: FieldProposal): Boolean {
        if (p.field != Field.DATE_TIME || p.value.text == null) return false
        val t = p.temporal
        if (t != null) return t.date == null || t.ambiguous
        val note = p.note.orEmpty() // a proposal without the structured time (only the note says what was wrong)
        return note.startsWith("ambiguous") || note.startsWith("could not resolve") || note.startsWith("needs clarification")
    }
}

package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.FieldProposal
import com.megathon.incident.llm.ValidationResult
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class MergeContext(val latestUtterance: String, val capturedAt: Instant, val zone: ZoneId, val atMs: Long)

data class MergeOutcome(
    val report: IncidentReport,
    val journal: List<JournalEntry>,
    /** Fields whose value needs a question (for example an ambiguous or future day). */
    val clarify: Set<Field>,
    /** Fields the merge completed by assumption (a time already past today gets today's date). Shown as assumed. */
    val assumed: Set<Field>,
)

/**
 * Interim merge (the suggestion/merge owner's version replaces it): applies validated proposals to a report.
 *  - Filling an empty field is not a change of mind; replacing a value is journaled.
 *  - A model repeating itself, or guessing, never overwrites a value; only words **said in this utterance** can.
 *  - A suggestion only ever fills an empty field.
 */
object ReportMerge {
    fun apply(report: IncidentReport, result: ValidationResult, ctx: MergeContext): MergeOutcome {
        var current = report
        val journal = mutableListOf<JournalEntry>()
        val clarify = mutableSetOf<Field>()
        val assumed = mutableSetOf<Field>()

        for (p in result.proposals) {
            val (value, flag) = completeDate(p, ctx)
            val cur = current.value(p.field)
            val replaced = decide(cur, value, ctx.latestUtterance) ?: continue
            current = current.with(p.field, replaced.value)
            if (replaced.journal) {
                journal += JournalEntry(p.field, cur.text, replaced.value.text, "heard a correction", ctx.atMs)
            }
            when (flag) {
                Flag.CLARIFY -> clarify += p.field
                Flag.ASSUMED -> assumed += p.field
                null -> Unit
            }
        }
        return MergeOutcome(current, journal, clarify, assumed)
    }

    private data class Decision(val value: FieldValue, val journal: Boolean)
    private enum class Flag { CLARIFY, ASSUMED }

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

    private fun completeDate(p: FieldProposal, ctx: MergeContext): Pair<FieldValue, Flag?> {
        if (p.field != Field.DATE_TIME) return p.value to null
        val note = p.note.orEmpty()
        val text = p.value.text ?: return p.value to null
        if (note.startsWith("ambiguous") || note.startsWith("could not resolve")) return p.value to Flag.CLARIFY
        if (!note.startsWith("needs clarification")) return p.value to null

        val parts = DateTimeDisplay.parse(text) ?: return p.value to Flag.CLARIFY
        if (parts.date != null) return p.value to Flag.CLARIFY
        val now = ctx.capturedAt.atZone(ctx.zone)
        // An incident report is about something that already happened, so a time of day with no date means its most
        // recent past occurrence: today if that time has passed, otherwise yesterday. Always shown as assumed.
        val passedToday = parts.clock?.let { LocalTime.parse(it) <= now.toLocalTime() } ?: true
        val date = if (passedToday) now.toLocalDate() else now.toLocalDate().minusDays(1)
        return p.value.copy(text = DateTimeDisplay.withDate(text, date)) to Flag.ASSUMED
    }
}

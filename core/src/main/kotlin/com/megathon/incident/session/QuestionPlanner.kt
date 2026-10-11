package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.TriageLane
import com.megathon.incident.domain.ValueStatus

/**
 * Interim follow-up planner (the planner owner's version replaces it). Deterministic, not LLM discretion (spec D6):
 * asks only about the **four mandatory fields**, in policy order, **one question at a time** (the lane's cap is never
 * exceeded, and a worker is never handed two questions in one breath). A field already in `askedBefore` is not asked again:
 * the session clears that history whenever the open fields change (an answer made progress), so a field is asked again
 * only after progress, and when a whole round gets nowhere the report goes to review with the gap flagged.
 * Once every mandatory field is answered, [OptionalAsks] may ask up to two more, in plain words, about tools or items,
 * people and witnesses (never in an urgent lane).
 */
class QuestionPlanner(private val askOptional: Boolean = true) {
    /** The mandatory fields the lane cares about that are still unanswered or need clarifying, in policy order. */
    fun open(report: IncidentReport, clarify: Set<Field>, lane: TriageLane): List<Field> = lane.mandatoryFields
        .filter { it in MANDATORY }
        .filter { f -> f in clarify || !report.isAnswered(f) }

    /**
     * [heard] is everything the reporter has said and [candidates] a tool or item (or other value) the chips already found:
     * both only shape the optional questions, which come after the four mandatory answers.
     */
    fun next(
        report: IncidentReport, clarify: Set<Field>, lane: TriageLane, askedBefore: List<Set<Field>>,
        heard: String = "", candidates: Map<Field, String> = emptyMap(),
    ): Question? {
        val open = open(report, clarify, lane)
        if (open.isEmpty()) {
            if (!askOptional || lane.level >= Priority.HIGH) return null
            return OptionalAsks.next(report, askedBefore, heard, candidates)
        }
        val asked = askedBefore.flatten().toSet()
        val needed = open.filter { it !in asked }.take(minOf(1, lane.maxQuestionsPerTurn))
        if (needed.isEmpty()) return null

        val urgent = lane.level >= Priority.HIGH
        val asks = needed.map { ask(it, report, clarify, urgent) }
        return Question(asks, asks.joinToString(" ") { it.prompt })
    }

    private fun ask(field: Field, report: IncidentReport, clarify: Set<Field>, urgent: Boolean): Ask = when (field) {
        Field.INCIDENT_TYPE -> Ask(field, "What kind of incident is it?", TYPE_CHOICES.map { (label, type) -> QuickReply(label, field, type.wire) })
        Field.LOCATION -> Ask(field, if (urgent) "Where is it?" else "Where did it happen?", emptyList())
        Field.DATE_TIME -> if (field in clarify) {
            // A time of day with no date is never assumed: ask, with "Today" preselected (one tap, never applied alone).
            // A value that already names a day (an ambiguous "kal") is asked as a plain "which day", nothing preselected.
            val noDate = report.value(field).text?.let { DateTimeDisplay.parse(it)?.date == null } ?: false
            val today = QuickReply("Today", field, "today", suggested = noDate)
            val yesterday = QuickReply("Yesterday", field, "yesterday")
            Ask(field, if (noDate) "Was that today?" else "Which day was that?", listOf(today, yesterday))
        } else {
            Ask(field, "When was that?", listOf(QuickReply("Just now", field, "now")))
        }
        Field.SEVERITY -> {
            val suggested = report.value(field).takeIf { it.status == ValueStatus.SUGGESTED }?.text
            Ask(field, "How bad is it?", SEVERITY_CHOICES.map { (label, s) -> QuickReply(label, field, s.wire, suggested = s.wire == suggested) })
        }
        else -> Ask(field, "Can you tell me more?", emptyList())
    }

    companion object {
        val MANDATORY: Set<Field> = setOf(Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY)

        val TYPE_CHOICES: List<Pair<String, IncidentType>> = listOf(
            "Water leak" to IncidentType.WATER_LEAK_FLOOD,
            "Fire or smoke" to IncidentType.FIRE_SMOKE,
            "Injury" to IncidentType.INJURY,
            "Electrical" to IncidentType.ELECTRICAL,
            "Spill" to IncidentType.SPILL_CHEMICAL,
            "Something else" to IncidentType.OTHER,
        )

        val SEVERITY_CHOICES: List<Pair<String, Severity>> = listOf(
            "Minor" to Severity.MINOR,
            "Moderate" to Severity.MODERATE,
            "Severe" to Severity.SEVERE,
            "Critical" to Severity.CRITICAL,
            "No harm" to Severity.NONE,
        )
    }
}

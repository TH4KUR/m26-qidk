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
 * asks only about the **four mandatory fields**, in policy order, at most the lane's cap per turn, and never the
 * same set twice in a row (then the report goes to review with the gap flagged).
 * The lane's extra optional fields (LOW) are deliberately not asked: the spec's demo is exactly one follow-up.
 */
class QuestionPlanner {
    fun next(report: IncidentReport, clarify: Set<Field>, lane: TriageLane, askedBefore: List<Set<Field>>): Question? {
        val needed = lane.mandatoryFields
            .filter { it in MANDATORY }
            .filter { f -> f in clarify || !report.isAnswered(f) }
            .take(lane.maxQuestionsPerTurn)
        if (needed.isEmpty()) return null
        if (askedBefore.lastOrNull() == needed.toSet()) return null

        val urgent = lane.level >= Priority.HIGH
        val asks = needed.map { ask(it, report, clarify, urgent) }
        return Question(asks, asks.joinToString(" ") { it.prompt })
    }

    private fun ask(field: Field, report: IncidentReport, clarify: Set<Field>, urgent: Boolean): Ask = when (field) {
        Field.INCIDENT_TYPE -> Ask(field, "What kind of incident is it?", TYPE_CHOICES.map { (label, type) -> QuickReply(label, field, type.wire) })
        Field.LOCATION -> Ask(field, if (urgent) "Where is it?" else "Where did it happen?", emptyList())
        Field.DATE_TIME -> if (field in clarify) {
            Ask(field, "Which day was that?", listOf(QuickReply("Today", field, "today"), QuickReply("Yesterday", field, "yesterday")))
        } else {
            Ask(field, "When did it happen?", listOf(QuickReply("Just now", field, "now")))
        }
        Field.SEVERITY -> {
            val suggested = report.value(field).takeIf { it.status == ValueStatus.SUGGESTED }?.text
            Ask(field, "How severe is it?", SEVERITY_CHOICES.map { (label, s) -> QuickReply(label, field, s.wire, suggested = s.wire == suggested) })
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

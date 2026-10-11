package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.ValueStatus

/**
 * Cross-questioning about the optional fields the model is weakest on: tools or items, people involved, witnesses.
 * Runs only after the four mandatory answers are in, never in an urgent lane (the caller checks), one question at a
 * time, at most [MAX_PER_REPORT] per report, and a field is never asked twice. Every question has a one-tap "Not sure" (the dock's own "Skip" means check and send, so this one is not called Skip).
 * What gets asked depends on what was said: an injury asks who was hurt, a "colleague" is asked for by role, a tool or item the
 * chips already found becomes a yes or no. Deterministic and cheap: no model call, so it cannot slow a turn or invent a name.
 */
internal object OptionalAsks {
    val ASKABLE = setOf(Field.PERSONS_INVOLVED, Field.EQUIPMENT_ASSET, Field.WITNESSES)
    private const val MAX_PER_REPORT = 2

    private val ROLE = Regex(
        """\b(my|our|a|an|the)\s+(colleague|coworker|co-worker|student|visitor|patient|guest|customer|worker|staff member|professor|teacher|driver|technician|cleaner|person)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val SOMEONE = Regex("""\b(someone|somebody)\b""", RegexOption.IGNORE_CASE)
    private val ARTICLE = Regex("""^(?:the|a|an|my|our)\s+""", RegexOption.IGNORE_CASE)

    fun next(report: IncidentReport, askedBefore: List<Set<Field>>, heard: String, candidates: Map<Field, String>): Question? {
        val already = askedBefore.flatten().filter { it in ASKABLE }.toSet()
        if (already.size >= MAX_PER_REPORT) return null
        val type = IncidentType.fromWire(report.value(Field.INCIDENT_TYPE).text)
        val order = when (type) {
            IncidentType.INJURY -> listOf(Field.PERSONS_INVOLVED, Field.WITNESSES, Field.EQUIPMENT_ASSET)
            IncidentType.ILLNESS -> listOf(Field.PERSONS_INVOLVED, Field.WITNESSES)
            IncidentType.ENVIRONMENTAL -> listOf(Field.WITNESSES)
            else -> listOf(Field.EQUIPMENT_ASSET, Field.WITNESSES)
        }
        val field = order.firstOrNull { it !in already && !report.isAnswered(it) } ?: return null
        val suggested = report.value(field).takeIf { it.status == ValueStatus.SUGGESTED }?.text
        val ask = ask(field, type, heard, candidates[field] ?: suggested)
        return Question(listOf(ask), ask.prompt)
    }

    private fun ask(field: Field, type: IncidentType?, heard: String, candidate: String?): Ask = when (field) {
        Field.PERSONS_INVOLVED -> {
            val role = ROLE.find(heard)
            val someone = SOMEONE.containsMatchIn(heard)
            when {
                role != null -> {
                    val owner = if (role.groupValues[1].lowercase() in setOf("my", "our")) "your" else "the"
                    Ask(field, "What is $owner ${role.groupValues[2].lowercase()}'s name?", listOf(skip(field)))
                }
                someone -> Ask(field, "Do you know who it was?", listOf(skip(field)))
                else -> Ask(field, if (type == IncidentType.ILLNESS) "Who is unwell?" else "Who was hurt?", listOf(none(field, "No one"), skip(field)))
            }
        }
        Field.EQUIPMENT_ASSET ->
            if (candidate != null) {
                Ask(field, "Was it the ${candidate.trim().replace(ARTICLE, "")}?", listOf(QuickReply("Yes", field, candidate), QuickReply("No", field, QuickReply.SKIP)))
            } else {
                Ask(field, "Which tool, machine or item was involved?", listOf(none(field, "None"), skip(field)))
            }
        Field.WITNESSES -> Ask(field, "Did anyone see it happen?", listOf(none(field, "No one"), skip(field)))
        else -> Ask(field, "Can you tell me more?", emptyList())
    }

    private fun none(field: Field, label: String) = QuickReply(label, field, QuickReply.NONE)
    private fun skip(field: Field) = QuickReply("Not sure", field, QuickReply.SKIP)
}

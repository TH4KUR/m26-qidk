package com.megathon.incident.speech

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.ValueStatus

/**
 * Short spoken acknowledgments built from what was just filled, so the hands-free loop answers
 * "Got it: water leak at Conference Room 2." without calling the language model. Only values the
 * user actually said (or confirmed) are repeated; a suggestion is never read back as if it were heard.
 */
object AckTemplates {
    private val OPENERS = listOf("Got it", "Noted", "Okay", "Understood")

    private val ORDER = listOf(
        Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY,
        Field.PERSONS_INVOLVED, Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS, Field.WITNESSES,
    )

    private const val MAX_CLAUSES = 3

    /** Null when nothing speakable changed. [turn] rotates the opener so the assistant does not repeat itself. */
    fun forChanges(report: IncidentReport, changed: Collection<Field>, turn: Int = 0): String? {
        val clauses = ORDER
            .filter { it in changed }
            .mapNotNull { field -> clause(report, field)?.let { field to it } }
            .take(MAX_CLAUSES)
        if (clauses.isEmpty()) return null

        val body = StringBuilder()
        clauses.forEachIndexed { index, (field, text) ->
            // "at X" reads well right after an incident type; on its own the location needs a verb.
            val afterType = field == Field.LOCATION && index > 0 && clauses[index - 1].first == Field.INCIDENT_TYPE
            if (index > 0) body.append(if (afterType) " " else ", ")
            body.append(
                when {
                    afterType -> "at $text"
                    field == Field.LOCATION -> "the location is $text"
                    else -> text
                },
            )
        }
        val opener = OPENERS[Math.floorMod(turn, OPENERS.size)]
        return "$opener: $body."
    }

    private fun clause(report: IncidentReport, field: Field): String? {
        val value = report.value(field)
        val text = value.text.orEmpty().trim()
        if (text.isEmpty() && value.status != ValueStatus.EXPLICIT_NONE) return null
        return when (value.status) {
            ValueStatus.REPORTED, ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED -> when (field) {
                Field.INCIDENT_TYPE -> typeWords(text)
                Field.LOCATION -> text
                Field.DATE_TIME -> text
                Field.SEVERITY -> "severity $text"
                Field.PERSONS_INVOLVED -> "$text involved"
                Field.EQUIPMENT_ASSET -> "tool or item: $text"
                Field.IMMEDIATE_ACTIONS -> "action taken: $text"
                Field.WITNESSES -> "witnesses $text"
                else -> null
            }
            ValueStatus.EXPLICIT_NONE -> when (field) {
                Field.WITNESSES -> "no witnesses"
                Field.PERSONS_INVOLVED -> "no one involved"
                else -> null
            }
            else -> null
        }
    }

    private fun typeWords(wire: String): String = when (IncidentType.fromWire(wire)) {
        IncidentType.WATER_LEAK_FLOOD -> "water leak"
        IncidentType.FIRE_SMOKE -> "fire or smoke"
        IncidentType.ELECTRICAL -> "electrical problem"
        IncidentType.SPILL_CHEMICAL -> "spill"
        IncidentType.INJURY -> "injury"
        IncidentType.ILLNESS -> "illness"
        IncidentType.NEAR_MISS -> "near miss"
        IncidentType.PROPERTY_DAMAGE -> "property damage"
        IncidentType.EQUIPMENT_FAILURE -> "equipment failure"
        IncidentType.SECURITY -> "security issue"
        IncidentType.ENVIRONMENTAL -> "environmental issue"
        IncidentType.OTHER -> "incident"
        null -> wire.replace('_', ' ')
    }
}

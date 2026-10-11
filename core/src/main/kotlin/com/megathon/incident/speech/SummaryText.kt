package com.megathon.incident.speech

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.session.Labels

/** Text the app speaks after submit ([of]) and when the user says "read it back" ([readBack]). */
object SummaryText {
    /** Mandatory fields in the order they are asked (spec D6). */
    private val MANDATORY = listOf(Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY)

    private val READ_ORDER = listOf(
        Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY,
        Field.PERSONS_INVOLVED, Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS, Field.WITNESSES,
    )

    private val LABELS = mapOf(
        Field.INCIDENT_TYPE to "What happened", Field.LOCATION to "Where", Field.DATE_TIME to "When",
        Field.SEVERITY to "Severity", Field.PERSONS_INVOLVED to "People involved",
        Field.EQUIPMENT_ASSET to "Tools or items", Field.IMMEDIATE_ACTIONS to "What was done",
        Field.WITNESSES to "Witnesses",
    )

    /** What is still missing, as the end of "I still need to know ...". */
    private val MISSING_WORDS = mapOf(
        Field.INCIDENT_TYPE to "what happened", Field.LOCATION to "where", Field.DATE_TIME to "when",
        Field.SEVERITY to "how severe it is", Field.PERSONS_INVOLVED to "who was involved",
        Field.EQUIPMENT_ASSET to "which tool or item", Field.IMMEDIATE_ACTIONS to "what was done",
        Field.WITNESSES to "who saw it",
    )

    /** `Report ready. <type> at <location>, <time>. Severity <severity>. Actions taken: <actions>.` Missing parts are skipped. */
    fun of(report: IncidentReport): String {
        val type = heard(report, Field.INCIDENT_TYPE)?.let(Labels::type)
        val location = heard(report, Field.LOCATION)
        val time = heard(report, Field.DATE_TIME)
        val severity = heard(report, Field.SEVERITY)
        val actions = heard(report, Field.IMMEDIATE_ACTIONS)

        val what = when {
            type != null && location != null -> "$type at $location"
            type != null -> type
            location != null -> "At $location"
            else -> null
        }
        val head = listOfNotNull(what, time).joinToString(", ")

        return buildString {
            append("Report ready.")
            if (head.isNotEmpty()) append(" $head.")
            if (severity != null) append(" Severity $severity.")
            if (actions != null) append(" Actions taken: $actions.")
        }
    }

    /** Every field the user has answered, then what the report still needs. */
    fun readBack(report: IncidentReport, mandatory: List<Field> = MANDATORY): String {
        val lines = READ_ORDER.mapNotNull { field ->
            val label = LABELS.getValue(field)
            val value = report.value(field)
            when (value.status) {
                ValueStatus.REPORTED, ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED ->
                    "$label: ${spoken(field, value.text.orEmpty())}."
                ValueStatus.EXPLICIT_NONE -> "$label: none."
                ValueStatus.UNKNOWN -> "$label: unknown."
                else -> null
            }
        }
        val missing = mandatory.filterNot { report.isAnswered(it) }.map { MISSING_WORDS.getValue(it) }

        return buildString {
            append(if (lines.isEmpty()) "There is nothing in the report yet." else "Here's what I have so far. ${lines.joinToString(" ")}")
            if (missing.isNotEmpty()) append(" I still need to know ${spokenList(missing)}.")
        }
    }

    private fun heard(report: IncidentReport, field: Field): String? =
        if (report.isFilled(field)) report.value(field).text?.takeIf { it.isNotBlank() } else null

    private fun spoken(field: Field, text: String): String =
        if (field == Field.INCIDENT_TYPE) Labels.type(text) else text

    private fun spokenList(items: List<String>): String =
        when (items.size) {
            0, 1 -> items.joinToString()
            2 -> items.joinToString(" and ")
            else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
        }
}

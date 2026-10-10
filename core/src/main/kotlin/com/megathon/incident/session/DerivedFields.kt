package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Severity
import java.time.LocalDate

/** Human labels and display text for report values. */
object Labels {
    val ORDER: List<Field> = listOf(
        Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY,
        Field.PERSONS_INVOLVED, Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS, Field.WITNESSES,
        Field.DESCRIPTION, Field.RECOMMENDED_FOLLOW_UP,
    )

    fun fieldLabel(f: Field): String = when (f) {
        Field.INCIDENT_TYPE -> "What happened"
        Field.LOCATION -> "Where"
        Field.DATE_TIME -> "When"
        Field.SEVERITY -> "How severe"
        Field.PERSONS_INVOLVED -> "People involved"
        Field.EQUIPMENT_ASSET -> "Equipment"
        Field.IMMEDIATE_ACTIONS -> "What was done"
        Field.WITNESSES -> "Witnesses"
        Field.DESCRIPTION -> "Summary"
        Field.RECOMMENDED_FOLLOW_UP -> "Recommended next step"
    }

    fun type(wire: String?): String = when (IncidentType.fromWire(wire)) {
        IncidentType.WATER_LEAK_FLOOD -> "Water leak or flood"
        IncidentType.FIRE_SMOKE -> "Fire or smoke"
        IncidentType.ELECTRICAL -> "Electrical problem"
        IncidentType.SPILL_CHEMICAL -> "Spill or chemical"
        IncidentType.INJURY -> "Injury"
        IncidentType.ILLNESS -> "Illness"
        IncidentType.NEAR_MISS -> "Near miss"
        IncidentType.PROPERTY_DAMAGE -> "Property damage"
        IncidentType.EQUIPMENT_FAILURE -> "Equipment failure"
        IncidentType.SECURITY -> "Security issue"
        IncidentType.ENVIRONMENTAL -> "Environmental issue"
        IncidentType.OTHER -> "Something else"
        null -> wire.orEmpty()
    }

    fun severity(wire: String?): String = when (Severity.fromWire(wire)) {
        Severity.NONE -> "No harm"
        Severity.MINOR -> "Minor"
        Severity.MODERATE -> "Moderate"
        Severity.SEVERE -> "Severe"
        Severity.CRITICAL -> "Critical"
        null -> wire.orEmpty()
    }

    fun display(f: Field, text: String?, today: LocalDate): String? = when {
        text.isNullOrBlank() -> null
        f == Field.INCIDENT_TYPE -> type(text)
        f == Field.SEVERITY -> severity(text)
        f == Field.DATE_TIME -> DateTimeDisplay.friendly(text, today)
        else -> text
    }
}

/**
 * Interim text the assistant writes at submit time (the safety-advice owner's table replaces it).
 * Both are templates over what was said: nothing here comes from a model, and the UI labels them as the assistant's.
 */
object DerivedFields {
    fun description(report: IncidentReport, today: LocalDate): String {
        fun v(f: Field) = Labels.display(f, report.value(f).text, today)
        val what = v(Field.INCIDENT_TYPE) ?: "Incident"
        val where = v(Field.LOCATION)?.let { " at $it" }.orEmpty()
        val whenText = v(Field.DATE_TIME)?.let { ", ${it.replaceFirstChar { c -> c.lowercase() }}" }.orEmpty()
        val sev = v(Field.SEVERITY)?.let { " Severity: ${it.lowercase()}." }.orEmpty()
        val done = v(Field.IMMEDIATE_ACTIONS)?.let { " Done so far: $it." }.orEmpty()
        return "$what$where$whenText.$sev$done"
    }

    fun followUp(report: IncidentReport): String {
        val severe = Severity.fromWire(report.value(Field.SEVERITY).text).let { it == Severity.SEVERE || it == Severity.CRITICAL }
        val base = when (IncidentType.fromWire(report.value(Field.INCIDENT_TYPE).text)) {
            IncidentType.WATER_LEAK_FLOOD -> "Have maintenance confirm the leak is stopped, and check nearby electrical equipment for damp."
            IncidentType.FIRE_SMOKE -> "Confirm the area is safe and inform the fire safety officer."
            IncidentType.ELECTRICAL -> "Keep people away from the equipment and have an electrician inspect it."
            IncidentType.SPILL_CHEMICAL -> "Keep the area cordoned off until the spill is cleaned up safely."
            IncidentType.INJURY -> "Make sure the injured person has medical attention, and tell their supervisor."
            IncidentType.ILLNESS -> "Make sure the person has medical attention, and tell their supervisor."
            IncidentType.EQUIPMENT_FAILURE -> "Tag the equipment out of use until it is checked."
            else -> "Tell the area supervisor and keep the area safe until it is looked at."
        }
        return if (severe) "Escalate to the safety officer today. $base" else base
    }

    /** What the device says aloud after a report is saved. */
    fun spoken(report: IncidentReport, today: LocalDate): String {
        fun v(f: Field) = Labels.display(f, report.value(f).text, today)
        val parts = listOfNotNull(
            v(Field.INCIDENT_TYPE),
            v(Field.LOCATION)?.let { "at $it" },
            v(Field.DATE_TIME)?.replaceFirstChar { it.lowercase() }?.replace(" · ", ", "),
            v(Field.SEVERITY)?.let { "severity ${it.lowercase()}" },
        )
        return "Report saved on this device. " + parts.joinToString(", ") + "."
    }
}

package com.megathon.incident.tts

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.Severity
import com.megathon.incident.session.DateTimeDisplay
import com.megathon.incident.session.DerivedFields
import com.megathon.incident.session.Labels
import java.time.LocalDate

/**
 * The English end of a report, built the same way as [HindiSummary]: it is saved, what the reporter said (kind of incident,
 * place, time, how serious), the steps to follow, and, when the incident is serious, the call to 112 with the reason.
 * One connected paragraph, read aloud and shown on the saved screen.
 *
 * Everything comes from what the reporter said. A part that is not known is left out; the reason for the 112 call is only ever
 * the incident type and the severity they stated, never a guess. The steps are [DerivedFields.followUp], the same advice the
 * report itself carries.
 */
object EnglishSummary {
    fun compose(report: IncidentReport, priority: PriorityAdvice?, today: LocalDate): String {
        val type = IncidentType.fromWire(stated(report, Field.INCIDENT_TYPE))
        val severity = Severity.fromWire(stated(report, Field.SEVERITY))
        val serious = severity == Severity.SEVERE || severity == Severity.CRITICAL
        val typePhrase = type?.takeIf { it != IncidentType.OTHER }?.let { phrase(it) }
        val severityName = severity?.takeIf { it != Severity.NONE }?.let { Labels.severity(it.wire).lowercase() }
        val whenWords = stated(report, Field.DATE_TIME)?.takeIf { it.isNotBlank() }?.let { DateTimeDisplay.spoken(it, today) }
        val place = stated(report, Field.LOCATION)?.trim()?.takeIf { it.isNotEmpty() }

        val out = mutableListOf("Your report is saved on this device.")

        if (typePhrase != null || place != null || whenWords != null) {
            val tail = (place?.let { " at $it" } ?: "") + (whenWords?.let { ", $it" } ?: "")
            val rated = severityName?.let { ", and rated the severity $it" } ?: ""
            out += "You told me about ${typePhrase ?: "an incident"}$tail$rated."
        }

        if (type != null) {
            // The advice is worked out from what the reporter stated only, so a guessed severity cannot trigger an escalation.
            val basis = listOf(Field.INCIDENT_TYPE, Field.SEVERITY).fold(IncidentReport.empty("summary", 0)) { r, f ->
                if (report.isFilled(f)) r.with(f, report.value(f)) else r
            }
            out += "Please follow these steps: ${DerivedFields.followUp(basis)}"
        }

        val loweredByUser = priority?.overriddenByUser == true && priority.level < Priority.HIGH
        val callEmergency = !loweredByUser && ((priority != null && priority.level >= Priority.HIGH) || serious)
        if (callEmergency) {
            val reasons = listOfNotNull(
                typePhrase?.let { "this is $it" },
                severityName?.takeIf { serious }?.let { "the severity is $it" },
            )
            out += "If anyone is in danger, call 112 now" +
                (if (reasons.isNotEmpty()) ", because you told me ${reasons.joinToString(" and ")}" else "") + "."
        }
        return out.joinToString(" ")
    }

    /** The incident as a noun phrase with its article, so the sentence reads as speech and not as a form. */
    private fun phrase(type: IncidentType): String = when (type) {
        IncidentType.WATER_LEAK_FLOOD -> "a water leak or flood"
        IncidentType.FIRE_SMOKE -> "a fire or smoke incident"
        IncidentType.ELECTRICAL -> "an electrical problem"
        IncidentType.SPILL_CHEMICAL -> "a spill or chemical incident"
        IncidentType.INJURY -> "an injury"
        IncidentType.ILLNESS -> "an illness"
        IncidentType.NEAR_MISS -> "a near miss"
        IncidentType.PROPERTY_DAMAGE -> "property damage"
        IncidentType.EQUIPMENT_FAILURE -> "an equipment failure"
        IncidentType.SECURITY -> "a security issue"
        else -> "an incident"
    }
}

/**
 * The value the reporter actually gave (heard, confirmed or typed). A value the app only suggested is not something they said,
 * so it is never read back as if they had: no part of the saved-report summary comes from a guess.
 */
private fun stated(report: com.megathon.incident.domain.IncidentReport, field: Field): String? =
    if (report.isFilled(field)) report.value(field).text else null

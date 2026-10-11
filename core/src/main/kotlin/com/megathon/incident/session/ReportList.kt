package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import java.time.LocalDate

/** One saved report as a line in the list of reports. Everything here is text the report already carries. */
data class ReportListRow(
    val id: String,
    /** The kind of incident, or "Incident report" when the report has none. */
    val title: String,
    val severity: String?,
    val place: String?,
    /** When it happened, in the same words the report screen uses; null when not known. */
    val whenText: String?,
    val savedAtEpochMs: Long,
    /** Only saved reports are stored on the device, so this is "Saved"; there is no draft store. */
    val status: String,
    /** The incident type as stored (`water_leak_flood`), or null when none was said: lets a screen name it in another language. */
    val typeWire: String? = null,
    /** The severity as stored (`moderate`), or null when none was said. */
    val severityWire: String? = null,
    /** True when [severityWire] is only the app's suggestion, not something the reporter said or confirmed. */
    val severityIsSuggested: Boolean = false,
    /** The stored date and time text, before it is made friendly; null when not known. */
    val whenRaw: String? = null,
)

/** Turns stored reports into list rows, newest first. Pure: no clock, no files. */
object ReportList {
    fun row(report: IncidentReport, today: LocalDate): ReportListRow {
        fun shown(f: Field) = Labels.display(f, report.value(f).text, today)
        fun raw(f: Field) = report.value(f).text?.takeIf { it.isNotBlank() }
        val suggested = report.value(Field.SEVERITY).status == ValueStatus.SUGGESTED
        return ReportListRow(
            id = report.id,
            title = shown(Field.INCIDENT_TYPE) ?: "Incident report",
            severity = shown(Field.SEVERITY)?.let { if (suggested) "$it (suggested)" else it },
            place = shown(Field.LOCATION),
            whenText = shown(Field.DATE_TIME),
            savedAtEpochMs = report.createdAtEpochMs,
            status = "Saved",
            typeWire = raw(Field.INCIDENT_TYPE),
            severityWire = raw(Field.SEVERITY),
            severityIsSuggested = suggested && raw(Field.SEVERITY) != null,
            whenRaw = raw(Field.DATE_TIME),
        )
    }

    fun rows(reports: List<IncidentReport>, today: LocalDate): List<ReportListRow> =
        reports.sortedByDescending { it.createdAtEpochMs }.map { row(it, today) }
}

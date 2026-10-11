package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportListTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private fun report(id: String, at: Long, type: String? = "water_leak_flood", place: String? = "Main Canteen", severity: String? = "moderate"): IncidentReport {
        var r = IncidentReport.empty(id, at)
        type?.let { r = r.with(Field.INCIDENT_TYPE, heard(it)) }
        place?.let { r = r.with(Field.LOCATION, heard(it)) }
        severity?.let { r = r.with(Field.SEVERITY, heard(it)) }
        r = r.with(Field.DATE_TIME, heard("2026-10-11 ≈10:00 (around 10 AM)"))
        return r
    }

    @Test
    fun aRowReadsLikeTheReportItself() {
        val row = ReportList.row(report("r1", 1_000), today)
        assertEquals("r1", row.id)
        assertEquals("Water leak or flood", row.title)
        assertEquals("Moderate", row.severity)
        assertEquals("Main Canteen", row.place)
        assertEquals("Saved", row.status)
        assertEquals(1_000L, row.savedAtEpochMs)
    }

    @Test
    fun aReportWithoutATypeStillHasATitleAndLeavesTheRestOut() {
        val row = ReportList.row(report("r2", 5, type = null, place = null, severity = null), today)
        assertEquals("Incident report", row.title)
        assertNull(row.severity)
        assertNull(row.place)
    }

    @Test
    fun newestComesFirstAndTiesKeepAStableOrder() {
        val rows = ReportList.rows(listOf(report("old", 10), report("new", 30), report("mid", 20)), today)
        assertEquals(listOf("new", "mid", "old"), rows.map { it.id })
    }

    @Test
    fun aSuggestedValueIsMarkedSuggestedNeverShownAsAnAnswer() {
        val r = report("r3", 7, severity = null).with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED, null))
        assertEquals("Minor (suggested)", ReportList.row(r, today).severity)
    }

    @Test
    fun aRowCarriesTheRawValuesSoAScreenCanSayThemInAnotherLanguage() {
        val row = ReportList.row(report("r4", 9), today)
        assertEquals("water_leak_flood", row.typeWire)
        assertEquals("moderate", row.severityWire)
        assertEquals(false, row.severityIsSuggested)
        assertEquals("2026-10-11 ≈10:00 (around 10 AM)", row.whenRaw)
    }

    @Test
    fun aReportWithNothingSaidHasNoRawValues() {
        val row = ReportList.row(IncidentReport.empty("r5", 1), today)
        assertNull(row.typeWire)
        assertNull(row.severityWire)
        assertEquals(false, row.severityIsSuggested)
        assertNull(row.whenRaw)
    }

    @Test
    fun aSuggestedSeverityKeepsItsWireValueAndIsFlagged() {
        val r = report("r6", 7, severity = null).with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED, null))
        val row = ReportList.row(r, today)
        assertEquals("minor", row.severityWire)
        assertEquals(true, row.severityIsSuggested)
    }
}

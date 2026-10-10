package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the device says after a report is saved: short sentences, words instead of a date format. */
class DerivedFieldsSpokenTest {
    private val today = LocalDate.parse("2026-10-11")

    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private fun report(vararg fields: Pair<Field, FieldValue>): IncidentReport =
        fields.fold(IncidentReport.empty("r1", 0L)) { r, (field, value) -> r.with(field, value) }

    @Test fun aFullReportIsReadAsShortSentences() {
        val r = report(
            Field.INCIDENT_TYPE to heard("water_leak_flood"),
            Field.LOCATION to heard("Conference Room 2"),
            Field.DATE_TIME to heard("2026-10-11 ≈10:00 (around 10 AM)"),
            Field.SEVERITY to heard("minor"),
        )
        assertEquals(
            "Your report is saved on this device. Water leak or flood at Conference Room 2. " +
                "That was today at about 10 in the morning. Severity is minor.",
            DerivedFields.spoken(r, today),
        )
    }

    @Test fun missingPartsAreLeftOutRatherThanReadAsBlanks() {
        val r = report(Field.INCIDENT_TYPE to heard("fire_smoke"), Field.SEVERITY to heard("severe"))
        assertEquals("Your report is saved on this device. Fire or smoke. Severity is severe.", DerivedFields.spoken(r, today))
    }

    @Test fun aLocationAloneStillReadsAsASentence() {
        val r = report(Field.LOCATION to heard("the kitchen"))
        assertEquals("Your report is saved on this device. At the kitchen.", DerivedFields.spoken(r, today))
    }

    @Test fun aDayWithoutATimeIsReadAsTheDay() {
        val r = report(Field.INCIDENT_TYPE to heard("injury"), Field.DATE_TIME to heard("2026-10-10 time missing (yesterday)"))
        assertEquals("Your report is saved on this device. Injury. That was yesterday.", DerivedFields.spoken(r, today))
    }

    @Test fun noHarmIsNotReadAsASeverityOfNoHarm() {
        val r = report(Field.INCIDENT_TYPE to heard("near_miss"), Field.SEVERITY to heard("none"))
        assertEquals("Your report is saved on this device. Near miss. No harm done.", DerivedFields.spoken(r, today))
    }

    @Test fun anEmptyReportJustSaysItIsSaved() {
        assertEquals("Your report is saved on this device.", DerivedFields.spoken(IncidentReport.empty("r1", 0L), today))
    }
}

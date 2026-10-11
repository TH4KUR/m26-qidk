package com.megathon.incident.speech

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryTextTest {
    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED)

    private fun report(vararg fields: Pair<Field, FieldValue>): IncidentReport =
        fields.fold(IncidentReport.empty("r1", 0L)) { r, (field, value) -> r.with(field, value) }

    private val slideExample = report(
        Field.INCIDENT_TYPE to heard("water_leak_flood"),
        Field.LOCATION to heard("Conference Room 2"),
        Field.DATE_TIME to heard("this morning"),
        Field.SEVERITY to heard("minor"),
        Field.IMMEDIATE_ACTIONS to heard("put a bucket under it"),
    )

    @Test
    fun summaryForSlideExample() {
        val text = SummaryText.of(slideExample)
        assertTrue(text.startsWith("Report ready. Water leak or flood at Conference Room 2"), text)
        assertTrue("Severity minor." in text, text)
        assertTrue("Actions taken: put a bucket under it." in text, text)
    }

    @Test
    fun summarySkipsMissingFields() {
        val text = SummaryText.of(report(Field.INCIDENT_TYPE to heard("fire_smoke")))
        assertEquals("Report ready. Fire or smoke.", text)
        assertFalse("null" in text)
        assertFalse("Severity" in text)
    }

    @Test
    fun aSuggestedValueIsNotReadAsIfItWereReported() {
        val text = SummaryText.of(
            report(
                Field.INCIDENT_TYPE to heard("injury"),
                Field.SEVERITY to FieldValue("severe", ValueStatus.SUGGESTED),
            ),
        )
        assertFalse("Severity" in text, text)
    }

    @Test
    fun readBackListsFilledFieldsAndWhatIsStillMissing() {
        val text = SummaryText.readBack(
            report(
                Field.INCIDENT_TYPE to heard("water_leak_flood"),
                Field.LOCATION to heard("Conference Room 2"),
            ),
        )
        assertTrue(text.startsWith("Here's what I have so far."), text)
        assertTrue("What happened: Water leak or flood." in text, text)
        assertTrue("Where: Conference Room 2." in text, text)
        assertTrue(text.endsWith("I still need to know when and how severe it is."), text)
    }

    @Test
    fun readBackOfACompleteReportHasNoMissingList() {
        val text = SummaryText.readBack(slideExample)
        assertFalse("still need" in text, text)
    }

    @Test
    fun readBackOfAnEmptyReportSaysSo() {
        assertEquals("There is nothing in the report yet. I still need to know what happened, where, when, and how severe it is.",
            SummaryText.readBack(IncidentReport.empty("r1", 0L)))
    }

    @Test
    fun anExplicitNoneIsReadAsNone() {
        val text = SummaryText.readBack(
            report(Field.WITNESSES to FieldValue.explicitNone("nobody saw it")),
        )
        assertTrue("Witnesses: none." in text, text)
    }
}

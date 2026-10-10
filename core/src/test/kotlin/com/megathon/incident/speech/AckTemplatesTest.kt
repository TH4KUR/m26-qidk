package com.megathon.incident.speech

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AckTemplatesTest {
    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED)

    private val report = IncidentReport.empty("r1", 0L)
        .with(Field.INCIDENT_TYPE, heard("water_leak_flood"))
        .with(Field.LOCATION, heard("Conference Room 2"))
        .with(Field.SEVERITY, heard("minor"))
        .with(Field.DATE_TIME, heard("this morning"))
        .with(Field.EQUIPMENT_ASSET, FieldValue("projector", ValueStatus.SUGGESTED))

    @Test
    fun acknowledgesWhatWasJustHeardInASpeakableOrder() {
        // The caller lists fields in any order; the sentence always leads with the incident type.
        val text = AckTemplates.forChanges(report, listOf(Field.LOCATION, Field.INCIDENT_TYPE))
        assertEquals("Got it: water leak at Conference Room 2.", text)
    }

    @Test
    fun speaksAtMostThreeClauses() {
        val text = AckTemplates.forChanges(
            report,
            listOf(Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY),
        )
        assertEquals("Got it: water leak at Conference Room 2, this morning.", text)
    }

    @Test
    fun aSuggestedValueIsNotAcknowledgedAsHeard() {
        assertNull(AckTemplates.forChanges(report, listOf(Field.EQUIPMENT_ASSET)))
    }

    @Test
    fun nothingChangedMeansNothingToSay() {
        assertNull(AckTemplates.forChanges(report, emptyList()))
    }

    @Test
    fun severityIsAcknowledgedOnItsOwn() {
        assertEquals("Got it: severity minor.", AckTemplates.forChanges(report, listOf(Field.SEVERITY)))
    }

    @Test
    fun explicitNoneIsAcknowledgedAsNone() {
        val r = report.with(Field.WITNESSES, FieldValue.explicitNone("nobody saw it"))
        assertEquals("Got it: no witnesses.", AckTemplates.forChanges(r, listOf(Field.WITNESSES)))
    }

    @Test
    fun theOpenerRotatesWithTheTurnSoTheAssistantDoesNotRepeatItself() {
        val openers = (0..3).map { AckTemplates.forChanges(report, listOf(Field.SEVERITY), turn = it)!!.substringBefore(":") }
        assertTrue(openers.toSet().size >= 3, "openers: $openers")
        // The same turn always gives the same sentence, so the transcript is reproducible.
        assertEquals(
            AckTemplates.forChanges(report, listOf(Field.SEVERITY), turn = 2),
            AckTemplates.forChanges(report, listOf(Field.SEVERITY), turn = 2),
        )
    }


    @Test
    fun aLocationOnItsOwnGetsAWordToHangOn() {
        assertEquals("Got it: the location is Conference Room 2.", AckTemplates.forChanges(report, listOf(Field.LOCATION)))
    }

    @Test
    fun aBlankValueIsNeverSpoken() {
        val blank = report.with(Field.SEVERITY, FieldValue("  ", ValueStatus.REPORTED))
        assertNull(AckTemplates.forChanges(blank, listOf(Field.SEVERITY)))
    }
}

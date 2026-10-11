package com.megathon.incident.tts

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.ValueStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The English end of a report mirrors [HindiSummary]: saved, what was said, the steps, and 112 with its reason. */
class EnglishSummaryTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private fun report(
        type: String? = "water_leak_flood", place: String? = "Main Canteen", whenText: String? = "2026-10-11 ≈10:00 (around 10 AM)",
        severity: String? = "moderate",
    ): IncidentReport {
        var r = IncidentReport.empty("r", 0)
        type?.let { r = r.with(Field.INCIDENT_TYPE, heard(it)) }
        place?.let { r = r.with(Field.LOCATION, heard(it)) }
        whenText?.let { r = r.with(Field.DATE_TIME, heard(it)) }
        severity?.let { r = r.with(Field.SEVERITY, heard(it)) }
        return r
    }

    private fun compose(r: IncidentReport, level: Priority = Priority.MEDIUM, byUser: Boolean = false) =
        EnglishSummary.compose(r, PriorityAdvice(level, emptyList(), overriddenByUser = byUser), today)

    @Test
    fun aNormalReportIsOneCoherentChainWithNoEmergencyCall() {
        assertEquals(
            "Your report is saved on this device. " +
                "You told me about a water leak or flood at Main Canteen, today at about 10 in the morning, and rated the severity moderate. " +
                "Please follow these steps: Have maintenance confirm the leak is stopped, and check nearby electrical equipment for damp.",
            compose(report()),
        )
    }

    @Test
    fun aSevereReportAddsEscalationAndTheEmergencyCallWithItsReason() {
        val text = compose(report(type = "fire_smoke", severity = "severe"), Priority.HIGH)
        assertTrue("Escalate to the safety officer today." in text, text)
        assertTrue(
            text.endsWith("If anyone is in danger, call 112 now, because you told me this is a fire or smoke incident and the severity is severe."),
            text,
        )
    }

    @Test
    fun theEmergencyCallIsTheLastThingHeard() {
        val text = compose(report(type = "injury", severity = "critical"), Priority.URGENT)
        assertTrue(text.indexOf("Please follow these steps") < text.indexOf("call 112"), text)
        assertTrue(text.trimEnd().endsWith("."), text)
    }

    @Test
    fun aPartThatIsNotKnownIsLeftOutNeverGuessed() {
        val text = compose(report(type = null, whenText = null, severity = null))
        assertEquals("Your report is saved on this device. You told me about an incident at Main Canteen.", text)
        assertFalse("steps" in text, text)
        assertFalse("112" in text, text)
    }

    @Test
    fun anEmptyReportIsJustTheSavedLine() {
        assertEquals("Your report is saved on this device.", compose(report(null, null, null, null)))
    }

    @Test
    fun noEmergencyCallWhenTheReporterLoweredTheLevelThemselves() {
        val text = compose(report(type = "fire_smoke", severity = "severe"), Priority.LOW, byUser = true)
        assertFalse("112" in text, text)
    }

    @Test
    fun theEmergencyCallFollowsAHighLevelEvenWithoutAStatedSeverity() {
        val text = compose(report(severity = null), Priority.HIGH)
        assertTrue(text.endsWith("If anyone is in danger, call 112 now, because you told me this is a water leak or flood."), text)
    }

    @Test
    fun aValueTheAppOnlySuggestedIsNeverReadBackAsSomethingTheReporterSaid() {
        val r = report(severity = null).with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED, null))
        val text = compose(r)
        assertFalse("minor" in text, text)
        assertFalse("severity" in text, text)
    }

    @Test
    fun aSuggestedTypeAndPlaceAreLeftOutToo() {
        val r = IncidentReport.empty("r", 0)
            .with(Field.INCIDENT_TYPE, FieldValue("fire_smoke", ValueStatus.SUGGESTED, null))
            .with(Field.LOCATION, FieldValue("Main Canteen", ValueStatus.SUGGESTED, null))
        assertEquals("Your report is saved on this device.", compose(r))
    }

    @Test
    fun aSuggestedSevereDoesNotAddEscalationOrTheEmergencyCall() {
        val r = report(type = "fire_smoke", severity = null).with(Field.SEVERITY, FieldValue("severe", ValueStatus.SUGGESTED, null))
        val text = compose(r)
        assertFalse("Escalate" in text, text)
        assertFalse("112" in text, text)
        assertTrue("Please follow these steps" in text, text)
    }
}

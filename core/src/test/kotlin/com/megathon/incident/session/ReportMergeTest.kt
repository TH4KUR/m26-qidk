package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.FieldProposal
import com.megathon.incident.llm.Rejection
import com.megathon.incident.llm.ValidationResult
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportMergeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val captured = Instant.parse("2026-10-11T09:30:00Z") // 15:00 IST on 11 Oct

    private fun ctx(utterance: String, atMs: Long = 100) = MergeContext(utterance, captured, zone, atMs)
    private fun report() = IncidentReport.empty("r1", 0)
    private fun heard(f: Field, text: String, evidence: String? = text) = FieldProposal(f, FieldValue(text, ValueStatus.REPORTED, evidence))
    private fun result(vararg p: FieldProposal, rejections: List<Rejection> = emptyList()) = ValidationResult(p.toList(), rejections)

    @Test fun fillsEmptyFieldsAndKeepsTheEvidence() {
        val out = ReportMerge.apply(report(), result(heard(Field.LOCATION, "Conference Room 2", "near Conference Room 2")), ctx("near Conference Room 2"))
        val v = out.report.value(Field.LOCATION)
        assertEquals("Conference Room 2", v.text)
        assertEquals(ValueStatus.REPORTED, v.status)
        assertEquals("near Conference Room 2", v.evidence)
        assertTrue(out.journal.isEmpty(), "filling an empty field is not a change of mind")
    }

    @Test fun aSpokenCorrectionReplacesAHeardValueAndIsJournaled() {
        val first = ReportMerge.apply(report(), result(heard(Field.LOCATION, "Conference Room 2")), ctx("Conference Room 2")).report
        val out = ReportMerge.apply(first, result(heard(Field.LOCATION, "Conference Room 3")), ctx("Actually it was Conference Room 3", atMs = 500))
        assertEquals("Conference Room 3", out.report.value(Field.LOCATION).text)
        val j = out.journal.single()
        assertEquals(Field.LOCATION, j.field)
        assertEquals("Conference Room 2", j.before)
        assertEquals("Conference Room 3", j.after)
        assertEquals(500, j.atMs)
    }

    @Test fun theSameValueAgainChangesNothing() {
        val first = ReportMerge.apply(report(), result(heard(Field.LOCATION, "Lab 3")), ctx("Lab 3")).report
        val out = ReportMerge.apply(first, result(heard(Field.LOCATION, "Lab 3")), ctx("and again Lab 3"))
        assertEquals(first, out.report)
        assertTrue(out.journal.isEmpty())
    }

    @Test fun aModelRepeatNeverOverwritesAValueTheReporterEdited() {
        val edited = report().with(Field.LOCATION, FieldValue("Server Room", ValueStatus.USER_EDITED))
        val out = ReportMerge.apply(edited, result(heard(Field.LOCATION, "Lab 3", "Lab 3")), ctx("It was minor"))
        assertEquals("Server Room", out.report.value(Field.LOCATION).text)
        assertEquals(ValueStatus.USER_EDITED, out.report.value(Field.LOCATION).status)
    }

    @Test fun aSpokenCorrectionDoesOverrideAnEditedValue() {
        val edited = report().with(Field.LOCATION, FieldValue("Server Room", ValueStatus.USER_EDITED))
        val out = ReportMerge.apply(edited, result(heard(Field.LOCATION, "Lab 3", "Lab 3")), ctx("No, it was Lab 3"))
        assertEquals("Lab 3", out.report.value(Field.LOCATION).text)
        assertEquals("Server Room", out.journal.single().before)
    }

    @Test fun aSuggestionNeverReplacesAHeardValue() {
        val first = ReportMerge.apply(report(), result(heard(Field.LOCATION, "Lab 3")), ctx("Lab 3")).report
        val suggestion = FieldProposal(Field.LOCATION, FieldValue("Lab 4", ValueStatus.SUGGESTED, "maybe lab 4"))
        val out = ReportMerge.apply(first, result(suggestion), ctx("maybe lab 4"))
        assertEquals("Lab 3", out.report.value(Field.LOCATION).text)
    }

    @Test fun aSuggestionFillsAnEmptyFieldAsSuggested() {
        val suggestion = FieldProposal(Field.LOCATION, FieldValue("Lab 4", ValueStatus.SUGGESTED, "maybe lab 4"))
        val out = ReportMerge.apply(report(), result(suggestion), ctx("maybe lab 4"))
        assertEquals(ValueStatus.SUGGESTED, out.report.value(Field.LOCATION).status)
    }

    @Test fun aTimeWithoutADateThatAlreadyPassedTodayIsAssumedToBeToday() {
        val p = FieldProposal(Field.DATE_TIME, FieldValue("date missing ≈10:00 (around 10 AM)", ValueStatus.REPORTED, "around 10 AM"), "needs clarification: date or exact time missing")
        val out = ReportMerge.apply(report(), result(p), ctx("around 10 AM"))
        assertEquals("2026-10-11 ≈10:00 (around 10 AM)", out.report.value(Field.DATE_TIME).text)
        assertEquals(setOf(Field.DATE_TIME), out.assumed)
        assertTrue(out.clarify.isEmpty())
    }

    @Test fun aTimeLaterThanNowMeansItsMostRecentPastOccurrenceYesterday() {
        val p = FieldProposal(Field.DATE_TIME, FieldValue("date missing ≈18:00 (around 6 PM)", ValueStatus.REPORTED, "around 6 PM"), "needs clarification: date or exact time missing")
        val out = ReportMerge.apply(report(), result(p), ctx("around 6 PM"))
        assertEquals("2026-10-10 ≈18:00 (around 6 PM)", out.report.value(Field.DATE_TIME).text, "18:00 has not happened yet on the 11th (it is 15:00)")
        assertEquals(setOf(Field.DATE_TIME), out.assumed)
        assertTrue(out.clarify.isEmpty())
    }

    @Test fun anAmbiguousDayMustBeAsked() {
        val p = FieldProposal(Field.DATE_TIME, FieldValue("2026-10-10 morning (kal subah)", ValueStatus.REPORTED, "kal subah"), "ambiguous (for example 'kal'); ask which day")
        val out = ReportMerge.apply(report(), result(p), ctx("kal subah"))
        assertEquals(setOf(Field.DATE_TIME), out.clarify)
    }
}

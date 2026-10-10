package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.priority.TriageLanes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestionPlannerTest {
    private val planner = QuestionPlanner()
    private val low = TriageLanes.forLevel(Priority.LOW)

    private fun heard(f: Field, t: String) = FieldValue(t, ValueStatus.REPORTED, t)
    private fun full() = IncidentReport.empty("r", 0)
        .with(Field.INCIDENT_TYPE, heard(Field.INCIDENT_TYPE, "water_leak_flood"))
        .with(Field.LOCATION, heard(Field.LOCATION, "Conference Room 2"))
        .with(Field.DATE_TIME, heard(Field.DATE_TIME, "2026-10-11 ≈10:00 (around 10 AM)"))

    @Test fun theSlideExampleAsksExactlyOneQuestionAboutSeverity() {
        val q = planner.next(full(), emptySet(), low, emptyList())!!
        assertEquals(listOf(Field.SEVERITY), q.asks.map { it.field })
        assertEquals("How severe is it?", q.spoken)
        assertEquals(listOf("Minor", "Moderate", "Severe", "Critical", "No harm"), q.asks.single().replies.map { it.label })
    }

    @Test fun nothingMissingMeansNoQuestion() {
        val r = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
        assertNull(planner.next(r, emptySet(), low, emptyList()))
    }

    @Test fun mandatoryFieldsAreAskedInPolicyOrderAndCappedByTheLane() {
        val empty = IncidentReport.empty("r", 0)
        val q = planner.next(empty, emptySet(), low, emptyList())!!
        assertEquals(listOf(Field.INCIDENT_TYPE, Field.LOCATION), q.asks.map { it.field }, "max two per turn on the LOW lane")
    }

    @Test fun anUrgentIncidentAsksOnlyForTheLocation() {
        val q = planner.next(IncidentReport.empty("r", 0), emptySet(), TriageLanes.forLevel(Priority.URGENT), emptyList())!!
        assertEquals(listOf(Field.LOCATION), q.asks.map { it.field })
        assertEquals("Where is it?", q.spoken)
    }

    @Test fun aFlaggedDateIsAskedEvenThoughItHasAValue() {
        val r = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
            .with(Field.DATE_TIME, heard(Field.DATE_TIME, "date missing ≈18:00 (around 6 PM)"))
        val q = planner.next(r, setOf(Field.DATE_TIME), low, emptyList())!!
        assertEquals(listOf(Field.DATE_TIME), q.asks.map { it.field })
        assertEquals(listOf("Today", "Yesterday"), q.asks.single().replies.map { it.label })
    }

    @Test fun theSameQuestionTwiceInARowMovesOnToReview() {
        val asked = listOf(setOf(Field.SEVERITY))
        assertNull(planner.next(full(), emptySet(), low, asked))
    }

    @Test fun aDifferentQuestionAfterAnAnswerIsStillAsked() {
        val asked = listOf(setOf(Field.SEVERITY))
        val r = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED)).with(Field.LOCATION, FieldValue(null, ValueStatus.MISSING))
        assertEquals(listOf(Field.LOCATION), planner.next(r, emptySet(), low, asked)!!.asks.map { it.field })
    }

    @Test fun aSuggestedSeverityBecomesAPreselectedReply() {
        val r = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED, "contained leak"))
        val q = planner.next(r, emptySet(), low, emptyList())!!
        val minor = q.asks.single().replies.first { it.label == "Minor" }
        assertTrue(minor.suggested)
        assertTrue(q.asks.single().replies.filter { it.label != "Minor" }.none { it.suggested })
    }

    @Test fun anAnsweredUnknownCountsAsAnswered() {
        val r = full().with(Field.SEVERITY, FieldValue(null, ValueStatus.UNKNOWN, "I don't know"))
        assertNull(planner.next(r, emptySet(), low, emptyList()))
    }
}

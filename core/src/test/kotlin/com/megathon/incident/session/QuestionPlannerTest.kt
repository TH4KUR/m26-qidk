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
        assertEquals("How bad is it?", q.spoken)
        assertEquals(listOf("Minor", "Moderate", "Severe", "Critical", "No harm"), q.asks.single().replies.map { it.label })
    }

    @Test fun aMissingTimeIsAskedAsWhenWasThat() {
        val r = full().with(Field.DATE_TIME, FieldValue(null, ValueStatus.MISSING)).with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
        val q = planner.next(r, emptySet(), low, emptyList())!!
        assertEquals(listOf(Field.DATE_TIME), q.asks.map { it.field })
        assertEquals("When was that?", q.spoken)
        assertEquals(listOf("Just now"), q.asks.single().replies.map { it.label })
    }

    @Test fun aNonUrgentLocationIsAskedAsWhereDidItHappen() {
        val r = full().with(Field.LOCATION, FieldValue(null, ValueStatus.MISSING)).with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
        assertEquals("Where did it happen?", planner.next(r, emptySet(), low, emptyList())!!.spoken)
    }

    @Test fun twoQuestionsInOneTurnAreTwoShortSentencesThatDoNotEchoEachOther() {
        val empty = IncidentReport.empty("r", 0)
        assertEquals("What kind of incident is it? Where did it happen?", planner.next(empty, emptySet(), low, emptyList())!!.spoken)
        val noPlaceNoTime = full().with(Field.LOCATION, FieldValue(null, ValueStatus.MISSING)).with(Field.DATE_TIME, FieldValue(null, ValueStatus.MISSING))
        assertEquals("Where did it happen? When was that?", planner.next(noPlaceNoTime, emptySet(), low, emptyList())!!.spoken)
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

    @Test fun aTimeWithoutADateAsksWasThatTodayWithTodayPreselected() {
        val r = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
            .with(Field.DATE_TIME, heard(Field.DATE_TIME, "date missing ≈10:00 (around 10 AM)"))
        val q = planner.next(r, setOf(Field.DATE_TIME), low, emptyList())!!
        assertEquals("Was that today?", q.spoken)
        val replies = q.asks.single().replies
        assertEquals(listOf("Today", "Yesterday"), replies.map { it.label })
        assertEquals(listOf(true, false), replies.map { it.suggested })
        assertEquals(listOf("today", "yesterday"), replies.map { it.value })
    }

    @Test fun anAmbiguousDayThatNamesADateStillAsksWhichDayWithoutPreselecting() {
        val r = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
            .with(Field.DATE_TIME, heard(Field.DATE_TIME, "2026-10-10 morning (kal subah)"))
        val q = planner.next(r, setOf(Field.DATE_TIME), low, emptyList())!!
        assertEquals("Which day was that?", q.spoken)
        assertTrue(q.asks.single().replies.none { it.suggested })
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

    // ---- one optional witness question, only when nothing mandatory is left ----------------------------------------

    private fun complete() = full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED))
        .with(Field.PERSONS_INVOLVED, heard(Field.PERSONS_INVOLVED, "Rahul"))

    @Test fun whenAPersonIsListedAndNoOneWasAskedAboutSeeingItTheWitnessQuestionIsAskedOnce() {
        val q = planner.next(complete(), emptySet(), low, emptyList())!!
        assertEquals(listOf(Field.WITNESSES), q.asks.map { it.field })
        assertEquals("Did anyone see it happen?", q.spoken)
        assertEquals(listOf("No one"), q.asks.single().replies.map { it.label })
        assertNull(planner.next(complete(), emptySet(), low, listOf(setOf(Field.WITNESSES))), "asked once, never again")
    }

    @Test fun theWitnessQuestionWaitsForTheMandatoryFields() {
        val r = complete().with(Field.SEVERITY, FieldValue(null, ValueStatus.MISSING))
        assertEquals(listOf(Field.SEVERITY), planner.next(r, emptySet(), low, emptyList())!!.asks.map { it.field })
    }

    @Test fun noWitnessQuestionWhenWitnessesAreAnsweredOrNobodyIsListed() {
        assertNull(planner.next(complete().with(Field.WITNESSES, heard(Field.WITNESSES, "Priya")), emptySet(), low, emptyList()))
        assertNull(planner.next(complete().with(Field.WITNESSES, FieldValue.explicitNone("nobody saw it")), emptySet(), low, emptyList()))
        assertNull(planner.next(full().with(Field.SEVERITY, FieldValue("minor", ValueStatus.USER_CONFIRMED)), emptySet(), low, emptyList()))
    }

    @Test fun noWitnessQuestionOnUrgentOrHighLanes() {
        assertNull(planner.next(complete(), emptySet(), TriageLanes.forLevel(Priority.URGENT), emptyList()))
        assertNull(planner.next(complete(), emptySet(), TriageLanes.forLevel(Priority.HIGH), emptyList()))
    }
}

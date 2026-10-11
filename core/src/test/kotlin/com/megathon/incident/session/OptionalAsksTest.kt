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

/**
 * Cross-questioning (the user's request): once the four mandatory answers are in, ask about the optional fields the
 * model is weakest on (tools or items, people involved, witnesses), in plain words, shaped by what was said.
 * One question at a time, at most two per report, never in an urgent lane, always skippable.
 */
class OptionalAsksTest {
    private val planner = QuestionPlanner()
    private val low = TriageLanes.forLevel(Priority.LOW)

    private fun heard(t: String) = FieldValue(t, ValueStatus.REPORTED, t)

    /** All four mandatory answers present, so only optional asks can follow. */
    private fun done(type: String) = IncidentReport.empty("r", 0)
        .with(Field.INCIDENT_TYPE, heard(type))
        .with(Field.LOCATION, heard("Conference Room 2"))
        .with(Field.DATE_TIME, heard("2026-10-11 ≈10:00 (around 10 AM)"))
        .with(Field.SEVERITY, heard("minor"))

    private fun ask(
        report: IncidentReport,
        heardText: String = "",
        asked: List<Set<Field>> = emptyList(),
        candidates: Map<Field, String> = emptyMap(),
        lane: com.megathon.incident.domain.TriageLane = low,
    ) = planner.next(report, emptySet(), lane, asked, heardText, candidates)

    @Test fun `an injury asks who was hurt, with None and Skip one tap away`() {
        val q = ask(done("injury"))!!
        assertEquals(listOf(Field.PERSONS_INVOLVED), q.asks.map { it.field })
        assertEquals("Who was hurt?", q.spoken)
        assertEquals(listOf("No one", "Not sure"), q.asks.single().replies.map { it.label })
        assertEquals(listOf(QuickReply.NONE, QuickReply.SKIP), q.asks.single().replies.map { it.value })
    }

    @Test fun `an illness asks who is unwell`() {
        assertEquals("Who is unwell?", ask(done("illness"))!!.spoken)
    }

    @Test fun `a role without a name is asked by its role`() {
        assertEquals("What is your colleague's name?", ask(done("illness"), "My colleague fainted near the lab")!!.spoken)
        assertEquals("What is the student's name?", ask(done("injury"), "A student slipped on the wet floor")!!.spoken)
    }

    @Test fun `someone is asked as do you know who it was`() {
        assertEquals("Do you know who it was?", ask(done("injury"), "Someone fainted outside North 203")!!.spoken)
    }

    @Test fun `a non-injury asks which tool, machine or item was involved`() {
        val q = ask(done("fire_smoke"))!!
        assertEquals(listOf(Field.EQUIPMENT_ASSET), q.asks.map { it.field })
        assertEquals("Which tool, machine or item was involved?", q.spoken)
        assertEquals(listOf("None", "Not sure"), q.asks.single().replies.map { it.label })
    }

    @Test fun `an illness is never asked about a tool or item`() {
        // After who is unwell and who saw it, an illness has nothing left to ask.
        val r = done("illness")
        val afterPersons = ask(r, asked = listOf(setOf(Field.PERSONS_INVOLVED)))!!
        assertEquals(listOf(Field.WITNESSES), afterPersons.asks.map { it.field })
    }

    @Test fun `a candidate the chips already found becomes a yes or no question`() {
        val q = ask(done("fire_smoke"), candidates = mapOf(Field.EQUIPMENT_ASSET to "printer"))!!
        assertEquals("Was it the printer?", q.spoken)
        val replies = q.asks.single().replies
        assertEquals(listOf("Yes", "No"), replies.map { it.label })
        assertEquals("printer", replies.first().value)
        assertEquals(QuickReply.SKIP, replies.last().value)
    }

    @Test fun `witnesses are asked once the other optional fields are answered`() {
        val r = done("fire_smoke").with(Field.EQUIPMENT_ASSET, heard("printer"))
        val q = ask(r)!!
        assertEquals(listOf(Field.WITNESSES), q.asks.map { it.field })
        assertEquals("Did anyone see it happen?", q.spoken)
        assertEquals(listOf("No one", "Not sure"), q.asks.single().replies.map { it.label })
    }

    @Test fun `at most two optional questions are asked in one report`() {
        val r = done("injury")
        assertNull(ask(r, asked = listOf(setOf(Field.PERSONS_INVOLVED), setOf(Field.WITNESSES))))
    }

    @Test fun `an optional field is never asked twice`() {
        val r = done("fire_smoke")
        val q = ask(r, asked = listOf(setOf(Field.EQUIPMENT_ASSET)))!!
        assertEquals(listOf(Field.WITNESSES), q.asks.map { it.field })
    }

    @Test fun `a field that is already filled or answered none is not asked`() {
        val r = done("injury")
            .with(Field.PERSONS_INVOLVED, FieldValue.explicitNone("nobody"))
            .with(Field.WITNESSES, heard("Ravi"))
            .with(Field.EQUIPMENT_ASSET, heard("ladder"))
        assertNull(ask(r))
    }

    @Test fun `an urgent lane never gets optional questions`() {
        assertNull(ask(done("injury"), lane = TriageLanes.forLevel(Priority.HIGH)))
        assertNull(ask(done("injury"), lane = TriageLanes.forLevel(Priority.URGENT)))
    }

    @Test fun `a mandatory gap still comes first`() {
        val r = IncidentReport.empty("r", 0).with(Field.INCIDENT_TYPE, heard("injury"))
        assertTrue(ask(r)!!.asks.all { it.field in QuestionPlanner.MANDATORY })
    }

    @Test fun `optional questions can be switched off`() {
        assertNull(QuestionPlanner(askOptional = false).next(done("injury"), emptySet(), low, emptyList()))
    }
}

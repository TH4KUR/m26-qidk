package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.testing.FakeLlmEngine
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The cross-questioning end to end through the real pipeline: after the mandatory answers, up to two optional questions. */
class OptionalAsksSessionTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private fun session() = Pipeline.newSession(FakeLlmEngine(), InMemoryReportStore(), clock)
    private fun ReportSession.say(text: String) = runBlocking { onUtterance(text) }
    private fun ReportSession.asking() = assertIs<SessionState.Asking>(ui.value.state).question
    private fun ReportSession.tap(label: String) = answer(asking().asks.single().replies.first { it.label == label })

    /** The slide sentence, severity by voice, day tapped: the four mandatory answers are in. */
    private fun mandatoryDone() = session().also {
        it.say(FakeLlmEngine.DECK_TRANSCRIPT)
        it.say("Minor, nobody was hurt.")
        it.answer((it.ui.value.state as SessionState.Asking).question.asks.first { a -> a.field == Field.DATE_TIME }.replies.first { r -> r.label == "Today" })
    }

    @Test fun `after the mandatory answers the session asks which tool or item was involved`() {
        val s = mandatoryDone()
        val q = s.asking()
        assertEquals(listOf(Field.EQUIPMENT_ASSET), q.asks.map { it.field })
        assertEquals("Which tool, machine or item was involved?", q.spoken)
        assertEquals("Which tool, machine or item was involved?", s.ui.value.say?.text)
    }

    @Test fun `None is an answer, so the field is explicitly empty and the next optional question follows`() {
        val s = mandatoryDone()
        s.tap("None")
        assertEquals(ValueStatus.EXPLICIT_NONE, s.ui.value.report.value(Field.EQUIPMENT_ASSET).status)
        assertEquals(listOf(Field.WITNESSES), s.asking().asks.map { it.field })
    }

    @Test fun `Skip records nothing and the field is never asked again`() {
        val s = mandatoryDone()
        s.tap("Skip")
        assertEquals(ValueStatus.MISSING, s.ui.value.report.value(Field.EQUIPMENT_ASSET).status)
        assertEquals(listOf(Field.WITNESSES), s.asking().asks.map { it.field })
        s.tap("Skip")
        // Two optional questions were asked: the report goes to review.
        assertEquals(SessionState.Review, s.ui.value.state)
        assertEquals(ValueStatus.MISSING, s.ui.value.report.value(Field.WITNESSES).status)
    }

    @Test fun `answering with a name by tap is recorded as confirmed by the reporter`() {
        val s = mandatoryDone()
        s.tap("None")
        s.tap("No one")
        assertEquals(ValueStatus.EXPLICIT_NONE, s.ui.value.report.value(Field.WITNESSES).status)
        assertEquals(SessionState.Review, s.ui.value.state)
        assertTrue(s.ui.value.toolLog.any { it.tool == "ask_user" })
    }
}

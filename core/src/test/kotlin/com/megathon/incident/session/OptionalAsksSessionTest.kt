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
        s.tap("Not sure")
        assertEquals(ValueStatus.MISSING, s.ui.value.report.value(Field.EQUIPMENT_ASSET).status)
        assertEquals(listOf(Field.WITNESSES), s.asking().asks.map { it.field })
        s.tap("Not sure")
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

/** The voice path: a short spoken answer answers the open optional question; a real sentence does not. */
class VoiceAnswersToOptionalAsksTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private class Counting(private val inner: FakeLlmEngine) : com.megathon.incident.llm.LlmEngine {
        override val name = "COUNTING"
        var calls = 0
        override suspend fun complete(request: com.megathon.incident.llm.LlmRequest): com.megathon.incident.llm.LlmResult { calls++; return inner.complete(request) }
    }

    private fun atWitnessQuestion(): Pair<ReportSession, Counting> {
        val llm = Counting(FakeLlmEngine())
        val s = Pipeline.newSession(llm, InMemoryReportStore(), clock)
        runBlocking { s.onUtterance(FakeLlmEngine.DECK_TRANSCRIPT); s.onUtterance("Minor, nobody was hurt.") }
        s.answer((s.ui.value.state as SessionState.Asking).question.asks.first { it.field == Field.DATE_TIME }.replies.first { it.label == "Today" })
        s.answer((s.ui.value.state as SessionState.Asking).question.asks.single().replies.first { it.label == "Not sure" }) // tools or items
        assertEquals(listOf(Field.WITNESSES), (s.ui.value.state as SessionState.Asking).question.asks.map { it.field })
        return s to llm
    }

    @Test fun `saying names answers the witness question without the first pass`() {
        val (s, llm) = atWitnessQuestion()
        val before = llm.calls
        runBlocking { s.onUtterance("Meera and Ravi") }
        assertEquals(before, llm.calls, "no model call for a short answer")
        val v = s.ui.value.report.value(Field.WITNESSES)
        assertEquals("Meera, Ravi", v.text)
        assertEquals(ValueStatus.USER_CONFIRMED, v.status)
        assertEquals("Meera and Ravi", v.evidence)
        assertEquals(SessionState.Review, s.ui.value.state)
    }

    @Test fun `saying no one is an explicit none`() {
        val (s, _) = atWitnessQuestion()
        runBlocking { s.onUtterance("koi nahi") }
        assertEquals(ValueStatus.EXPLICIT_NONE, s.ui.value.report.value(Field.WITNESSES).status)
    }

    @Test fun `saying I do not know records nothing and moves on`() {
        val (s, _) = atWitnessQuestion()
        runBlocking { s.onUtterance("pata nahi") }
        assertEquals(ValueStatus.MISSING, s.ui.value.report.value(Field.WITNESSES).status)
        assertEquals(SessionState.Review, s.ui.value.state)
    }

    @Test fun `a real sentence while a question is open is still read by the first pass`() {
        val (s, llm) = atWitnessQuestion()
        val before = llm.calls
        runBlocking { s.onUtterance("Actually it was Conference Room 3 and the leak started around ten") }
        assertTrue(llm.calls > before, "a long sentence is not swallowed as an answer")
    }
}

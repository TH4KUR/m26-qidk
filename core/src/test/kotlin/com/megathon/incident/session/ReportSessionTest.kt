package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Scripted(private vararg val outs: String) : LlmEngine {
    override val name = "SCRIPTED"
    var calls = 0
    override suspend fun complete(request: LlmRequest): LlmResult = LlmResult(outs[minOf(calls++, outs.lastIndex)], 7, Compute.SIMULATED)
}

class ReportSessionTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata")) // 15:00 IST
    private val slide = FakeLlmEngine.DECK_TRANSCRIPT

    private fun session(llm: LlmEngine = FakeLlmEngine(), store: InMemoryReportStore = InMemoryReportStore()) =
        Pipeline.newSession(llm, store, clock)

    private fun ReportSession.say(text: String, stt: StageRecord? = null) = runBlocking { onUtterance(text, stt) }
    private fun ReportSession.field(f: Field) = ui.value.fields.first { it.field == f }

    private fun reviewed(): ReportSession = session().also {
        it.say(slide)
        it.say("Minor, nobody was hurt.")
    }

    @Test fun theSlideExampleFillsTheFieldsAndAsksOnlyHowSevere() {
        val s = session()
        s.say(slide)
        val state = assertIs<SessionState.Asking>(s.ui.value.state)
        assertEquals(listOf(Field.SEVERITY), state.question.asks.map { it.field })
        assertEquals("How severe is it?", s.ui.value.say?.text)

        assertEquals("Water leak or flood", s.field(Field.INCIDENT_TYPE).value)
        assertEquals(UiStatus.HEARD, s.field(Field.INCIDENT_TYPE).status)
        assertEquals("Conference Room 2", s.field(Field.LOCATION).value)
        assertEquals("near Conference Room 2", s.field(Field.LOCATION).evidence)
        assertEquals("Today · about 10:00 AM", s.field(Field.DATE_TIME).value)
        assertEquals(UiStatus.HEARD, s.field(Field.DATE_TIME).status)
        assertTrue(s.field(Field.DATE_TIME).hint!!.contains("assumed today", ignoreCase = true))
        assertEquals("isolated the area", s.field(Field.IMMEDIATE_ACTIONS).value)
        assertEquals(listOf("update_report", "ask_user"), s.ui.value.toolLog.map { it.tool })
    }

    @Test fun severityIsOnlySuggestedUntilTheReporterAnswers() {
        val s = session()
        s.say(slide)
        assertEquals(UiStatus.SUGGESTED, s.field(Field.SEVERITY).status)
        assertEquals("Minor", s.field(Field.SEVERITY).value)
    }

    @Test fun answeringByVoiceCompletesTheReportAndMovesToReview() {
        val s = session()
        s.say(slide)
        s.say("Minor, nobody was hurt.")
        assertEquals(SessionState.Review, s.ui.value.state)
        assertEquals(UiStatus.HEARD, s.field(Field.SEVERITY).status)
        assertEquals("Minor", s.field(Field.SEVERITY).value)
        assertEquals(listOf("update_report", "ask_user", "update_report"), s.ui.value.toolLog.map { it.tool })
    }

    @Test fun tappingAReplyAnswersWithoutTheModel() {
        val llm = Scripted(FakeLlmEngine.DECK_REPLY)
        val s = session(llm)
        s.say(slide)
        val reply = (s.ui.value.state as SessionState.Asking).question.asks.single().replies.first { it.label == "Moderate" }
        s.answer(reply)
        assertEquals(1, llm.calls, "a tap must not call the model")
        assertEquals(SessionState.Review, s.ui.value.state)
        assertEquals("Moderate", s.field(Field.SEVERITY).value)
        assertEquals(UiStatus.CONFIRMED, s.field(Field.SEVERITY).status)
    }

    @Test fun aVoiceCorrectionInReviewReplacesTheValueAndKeepsTheOriginalInTheJournal() {
        val s = reviewed()
        s.say("Actually it was Conference Room 3.")
        assertEquals(SessionState.Review, s.ui.value.state)
        assertEquals("Conference Room 3", s.field(Field.LOCATION).value)
        val j = s.ui.value.journal.single { it.field == Field.LOCATION }
        assertEquals("Conference Room 2", j.before)
        assertEquals("Conference Room 3", j.after)
    }

    @Test fun aSpokenCorrectionCanBeUndoneAndTheOriginalComesBack() {
        val s = reviewed()
        s.say("Actually it was Conference Room 3.")
        assertEquals("Conference Room 3", s.field(Field.LOCATION).value)
        assertTrue(s.ui.value.canUndo)
        s.undo()
        assertEquals("Conference Room 2", s.field(Field.LOCATION).value)
        assertEquals(UiStatus.HEARD, s.field(Field.LOCATION).status)
        assertEquals(2, s.ui.value.transcripts.size, "the undone utterance is removed from what was heard")
    }

    @Test fun editingAFieldMarksItConfirmedAndCanBeUndone() {
        val s = reviewed()
        s.edit(Field.LOCATION, "Server Room")
        assertEquals("Server Room", s.field(Field.LOCATION).value)
        assertEquals(UiStatus.CONFIRMED, s.field(Field.LOCATION).status)
        assertTrue(s.ui.value.canUndo)
        s.undo()
        assertEquals("Conference Room 2", s.field(Field.LOCATION).value)
        assertEquals(UiStatus.HEARD, s.field(Field.LOCATION).status)
    }

    @Test fun acceptingTheSuggestedSeverityConfirmsIt() {
        val s = session()
        s.say(slide)
        s.acceptSuggestion(Field.SEVERITY)
        assertEquals(UiStatus.CONFIRMED, s.field(Field.SEVERITY).status)
        assertEquals(SessionState.Review, s.ui.value.state)
    }

    @Test fun anEmptyTranscriptFailsGentlyWithoutCallingTheModel() {
        val llm = Scripted(FakeLlmEngine.DECK_REPLY)
        val s = session(llm)
        s.say("   ")
        assertEquals(0, llm.calls)
        val failed = assertIs<SessionState.Failed>(s.ui.value.state)
        assertTrue(failed.message.isNotBlank())
        assertTrue(failed.retryable)
    }

    @Test fun unreadableModelOutputRetriesOnceThenFailsAndLeavesTheReportAlone() {
        val llm = Scripted("Sure!", "still nothing useful")
        val s = session(llm)
        s.say(slide)
        assertEquals(2, llm.calls)
        assertIs<SessionState.Failed>(s.ui.value.state)
        assertTrue(s.ui.value.fields.filter { it.mandatory }.all { it.status == UiStatus.NEEDED })
        assertEquals(listOf(slide), s.ui.value.transcripts, "what was heard is kept so the reporter can retry")
    }

    @Test fun theRepairRetryRecoversFromOneBadReply() {
        val llm = Scripted("Sure!", FakeLlmEngine.DECK_REPLY)
        val s = session(llm)
        s.say(slide)
        assertEquals(2, llm.calls)
        assertIs<SessionState.Asking>(s.ui.value.state)
    }

    @Test fun anUrgentIncidentSpeaksTheEmergencyLineOnceAndSkipsStraightToReview() {
        val reply = """{"tool":"update_report","arguments":{"incident_type":"fire_smoke","location":"the kitchen","evidence":{"incident_type":"fire","location":"the kitchen"}}}"""
        val s = session(Scripted(reply, "{\"tool\":\"update_report\",\"arguments\":{}}"))
        s.say("There is a fire in the kitchen")
        assertEquals(Priority.URGENT, s.ui.value.priority?.level)
        assertEquals("If anyone is in danger, call 112 now.", s.ui.value.say?.text)
        assertEquals(SessionState.Review, s.ui.value.state, "an urgent report asks nothing more than the location")
        val sayId = s.ui.value.say!!.id
        s.say("It is spreading")
        assertTrue(s.ui.value.say?.text != "If anyone is in danger, call 112 now." || s.ui.value.say?.id == sayId, "the line is spoken once")
    }

    @Test fun overridingThePriorityIsJournaled() {
        val s = session()
        s.say(slide)
        s.overridePriority(Priority.MEDIUM, "it is near the server room")
        assertEquals(true, s.ui.value.priority?.overriddenByUser)
        assertEquals(Priority.MEDIUM, s.ui.value.priority?.level)
    }

    @Test fun submittingWritesTheDerivedFieldsSavesAndLogsSubmitReport() {
        val store = InMemoryReportStore()
        val s = session(store = store)
        s.say(slide)
        s.say("Minor, nobody was hurt.")
        runBlocking { s.submit() }
        val submitted = assertIs<SessionState.Submitted>(s.ui.value.state)
        assertEquals(submitted.reportId, store.saved.single().id)
        assertTrue(s.field(Field.DESCRIPTION).writtenByAssistant)
        assertTrue(s.field(Field.DESCRIPTION).value!!.contains("Conference Room 2"))
        assertNotNull(s.field(Field.RECOMMENDED_FOLLOW_UP).value)
        assertEquals("submit_report", s.ui.value.toolLog.last().tool)
        assertTrue(s.ui.value.spokenSummary!!.contains("water leak", ignoreCase = true))
    }

    @Test fun submitIsNeverBlockedByMissingFieldsButTheyAreListed() {
        val store = InMemoryReportStore()
        val s = session(Scripted("Sure!", "nope"), store)
        s.say(slide)
        runBlocking { s.submit() }
        assertIs<SessionState.Submitted>(s.ui.value.state)
        assertTrue(s.ui.value.toolLog.last().json.contains("incident_type"), "the submit call lists what is still missing")
    }

    @Test fun stageTimingsAreRecordedWithTheirRealComputeLabel() {
        val s = session(Scripted(FakeLlmEngine.DECK_REPLY)) // reports 7 ms and SIMULATED
        s.say(slide, StageRecord("Hear", "FAKE", Compute.SIMULATED, 12))
        val stages = s.ui.value.stages
        assertEquals(listOf("Hear", "Read"), stages.map { it.stage })
        assertTrue(stages.all { it.compute == Compute.SIMULATED })
        assertEquals(12L, stages.first().ms, "the hearing stage keeps the speech engine's timing")
        assertEquals(7L, stages.last().ms, "the reading stage keeps the model's own timing")
    }

    @Test fun resetStartsACleanReport() {
        val s = reviewed()
        s.reset()
        assertEquals(SessionState.Idle, s.ui.value.state)
        assertTrue(s.ui.value.transcripts.isEmpty())
        assertNull(s.ui.value.priority?.takeIf { it.level > Priority.LOW })
        assertTrue(s.ui.value.fields.filter { it.mandatory }.all { it.status == UiStatus.NEEDED })
    }
}

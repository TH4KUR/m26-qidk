package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.priority.KeyAnswer
import com.megathon.incident.priority.PriorityAdvisor
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

    private fun ReportSession.tap(field: Field, label: String) =
        answer((ui.value.state as SessionState.Asking).question.asks.first { it.field == field }.replies.first { it.label == label })

    /** The slide sentence, the severity by voice, and the day tapped: nothing left to ask, so the report is in review. */
    private fun reviewed(): ReportSession = session().also {
        it.say(slide)
        it.say("Minor, nobody was hurt.")
        it.tap(Field.DATE_TIME, "Today")
    }

    @Test fun theSlideExampleFillsTheFieldsAndAsksWhichDayThenHowSevereOneAtATime() {
        val s = session()
        s.say(slide)
        val state = assertIs<SessionState.Asking>(s.ui.value.state)
        assertEquals(listOf(Field.DATE_TIME), state.question.asks.map { it.field }, "the time has no date, so the day is asked, alone")
        assertEquals("Was that today?", s.ui.value.say?.text)

        assertEquals("Water leak or flood", s.field(Field.INCIDENT_TYPE).value)
        assertEquals(UiStatus.HEARD, s.field(Field.INCIDENT_TYPE).status)
        assertEquals("Conference Room 2", s.field(Field.LOCATION).value)
        assertEquals("near Conference Room 2", s.field(Field.LOCATION).evidence)
        assertEquals("About 10:00 AM · day not given", s.field(Field.DATE_TIME).value)
        assertEquals(UiStatus.NEEDED, s.field(Field.DATE_TIME).status)
        assertEquals("Which day was this?", s.field(Field.DATE_TIME).hint)
        assertEquals("isolated the area", s.field(Field.IMMEDIATE_ACTIONS).value)
        assertEquals(listOf("update_report", "ask_user"), s.ui.value.toolLog.map { it.tool })
    }

    @Test fun aHindiTimeTheModelMissedIsStillReadFromTheWordsSoTheDayIsNotAskedAgain() {
        val reply = """{"tool":"update_report","arguments":{"incident_type":"water_leak_flood","location":"कमरे","evidence":{"incident_type":"पानी लीक","location":"कमरे"}}}"""
        val s = session(Scripted(reply))
        s.say("आज सुबह 10 बजे कमरे में पानी लीक हो गया")
        assertEquals(UiStatus.HEARD, s.field(Field.DATE_TIME).status)
        assertTrue("10" in s.field(Field.DATE_TIME).value.orEmpty(), s.field(Field.DATE_TIME).value.orEmpty())
        val asked = (s.ui.value.state as? SessionState.Asking)?.question?.asks?.map { it.field }.orEmpty()
        assertTrue(Field.DATE_TIME !in asked, "the time was said, so it is not asked: $asked")
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
        assertEquals(UiStatus.HEARD, s.field(Field.SEVERITY).status)
        assertEquals("Minor", s.field(Field.SEVERITY).value)
        assertEquals(listOf(Field.DATE_TIME), assertIs<SessionState.Asking>(s.ui.value.state).question.asks.map { it.field }, "only the day is still open")
        s.tap(Field.DATE_TIME, "Today")
        assertEquals(SessionState.Review, s.ui.value.state)
        assertEquals(listOf("update_report", "ask_user", "update_report", "ask_user", "update_report"), s.ui.value.toolLog.map { it.tool })
    }

    @Test fun tappingAReplyAnswersWithoutTheModel() {
        val llm = Scripted(FakeLlmEngine.DECK_REPLY)
        val s = session(llm)
        s.say(slide)
        s.tap(Field.DATE_TIME, "Today")
        assertEquals(listOf(Field.SEVERITY), assertIs<SessionState.Asking>(s.ui.value.state).question.asks.map { it.field }, "severity is the next, separate question")
        assertEquals("How bad is it?", s.ui.value.say?.text)
        s.tap(Field.SEVERITY, "Moderate")
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
        assertEquals(listOf(Field.DATE_TIME), assertIs<SessionState.Asking>(s.ui.value.state).question.asks.map { it.field })
        s.tap(Field.DATE_TIME, "Today")
        assertEquals(SessionState.Review, s.ui.value.state)
    }

    @Test fun anEmptyTranscriptFailsGentlyWithoutCallingTheModel() {
        val llm = Scripted(FakeLlmEngine.DECK_REPLY)
        val s = session(llm)
        s.say("   ")
        assertEquals(0, llm.calls)
        val failed = assertIs<SessionState.Failed>(s.ui.value.state)
        assertEquals("I didn't catch that. Could you say it again, a little closer to the microphone?", failed.message)
        assertTrue(failed.retryable)
    }

    @Test fun unreadableModelOutputRetriesOnceThenFailsAndLeavesTheReportAlone() {
        val llm = Scripted("Sure!", "still nothing useful")
        val s = session(llm)
        s.say(slide)
        assertEquals(2, llm.calls)
        assertEquals("I didn't quite get that. Could you say it again?", assertIs<SessionState.Failed>(s.ui.value.state).message)
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

    // ---- replies: hint, urgent lane, de-escalation chip, key question, undated times -----------------------------

    private fun update(args: String) = """{"tool":"update_report","arguments":$args}"""
    private val nothing = update("{}")
    private val strange = "there is something strange in the basement"
    private val fireNoPlace = update("""{"incident_type":"fire_smoke","evidence":{"incident_type":"fire"}}""")

    // ---- what the reporter stated raises the advice (PriorityAdvisor.onReport, C/008) -----------------------------

    private val printerSaid = "The printer on the second floor stopped working and the severity is critical"
    private val printerCritical = update(
        """{"incident_type":"equipment_failure","severity":"critical","evidence":{"incident_type":"printer on the second floor stopped working","severity":"severity is critical"}}""",
    )

    @Test fun aSeverityTheReporterStatedRaisesThePriorityFloor() {
        val s = session(Scripted(printerCritical))
        s.say(printerSaid)
        assertEquals("Critical", s.field(Field.SEVERITY).value)
        assertEquals(UiStatus.HEARD, s.field(Field.SEVERITY).status)
        assertEquals(Priority.URGENT, s.ui.value.priority?.level, "'critical' said by the reporter is URGENT")
        assertTrue(s.ui.value.priority!!.reasons.any { it.contains("critical") }, "the reason names what was said: ${s.ui.value.priority!!.reasons}")
    }

    @Test fun aSeverityTheReporterTypesInReviewRaisesThePriorityFloor() {
        val s = reviewed()
        assertTrue(s.ui.value.priority!!.level < Priority.HIGH, "a minor, harmless leak starts below HIGH: ${s.ui.value.priority!!.level}")
        s.edit(Field.SEVERITY, "severe")
        assertEquals(Priority.HIGH, s.ui.value.priority?.level, "'severe' set by the reporter is HIGH")
    }

    @Test fun aStatedFloorIsNotLoweredByALaterUtteranceThatSaysNothingNew() {
        val s = session(Scripted(printerCritical, nothing))
        s.say(printerSaid)
        s.say("Okay")
        assertEquals(Priority.URGENT, s.ui.value.priority?.level, "raise-only: a later chunk must not undo the floor")
    }

    @Test fun aStatedCriticalSpeaksTheEmergencyLineBecauseTheFloorIsWhatMadeItUrgent() {
        val s = session(Scripted(printerCritical))
        s.say(printerSaid)
        assertTrue(s.ui.value.say?.text.orEmpty().contains("call 112 now"), "the line must not be swallowed by the call that raised the floor: ${s.ui.value.say?.text}")
    }

    @Test fun aCriticalTheReporterTypesSpeaksTheEmergencyLineOnce() {
        val s = reviewed()
        s.edit(Field.SEVERITY, "critical")
        assertEquals("If anyone is in danger, call 112 now.", s.ui.value.say?.text)
        val sayId = s.ui.value.say!!.id
        s.edit(Field.SEVERITY, "critical")
        s.edit(Field.LOCATION, "Block C")
        assertEquals(sayId, s.ui.value.say?.id, "the line is spoken once, however often the report is edited")
    }

    @Test fun theModelsHintReachesTheAdvisorAndRaisesTheLevelButNotToUrgent() {
        val plain = session(Scripted(nothing)).also { it.say(strange) }
        val hinted = session(Scripted(update("""{"impact_hint":5,"ongoing":true}"""))).also { it.say(strange) }
        assertTrue(plain.ui.value.priority!!.level < Priority.HIGH, "without a hint: ${plain.ui.value.priority!!.level}")
        assertTrue(hinted.ui.value.priority!!.level >= Priority.HIGH, "with a hint: ${hinted.ui.value.priority!!.level}")
        assertTrue(hinted.ui.value.priority!!.level < Priority.URGENT)
    }

    @Test fun anUrgentIncidentAsksOnlyWhereAndOffersSubmitNow() {
        val s = session(Scripted(fireNoPlace))
        s.say("There is a fire")
        assertEquals(Priority.URGENT, s.ui.value.priority?.level)
        val asking = assertIs<SessionState.Asking>(s.ui.value.state)
        assertEquals(listOf(Field.LOCATION), asking.question.asks.map { it.field })
        assertTrue(s.ui.value.canSubmitNow)
    }

    @Test fun anUrgentReportCanBeSubmittedAtOnceAndListsWhatIsMissing() {
        val store = InMemoryReportStore()
        val s = session(Scripted(fireNoPlace), store)
        s.say("There is a fire")
        runBlocking { s.submit() }
        assertIs<SessionState.Submitted>(s.ui.value.state)
        assertEquals(1, store.saved.size)
        assertEquals(false, s.ui.value.canSubmitNow, "nothing to submit once it is sent")
        val call = s.ui.value.toolLog.last()
        assertEquals("submit_report", call.tool)
        for (f in listOf("location", "date_time")) assertTrue(call.json.contains(f), "missing list names $f: ${call.json}")
    }

    @Test fun skippedFieldsOnTheUrgentLaneStayFlaggedAsNeededAndDoNotBlock() {
        val s = session(Scripted(fireNoPlace, update("""{"location":"the kitchen","evidence":{"location":"the kitchen"}}""")))
        s.say("There is a fire")
        s.say("In the kitchen")
        assertEquals(SessionState.Review, s.ui.value.state, "the urgent lane asks for the place and nothing else")
        assertEquals(UiStatus.NEEDED, s.field(Field.DATE_TIME).status)
        assertTrue(s.ui.value.canSubmitNow)
    }

    @Test fun aRoutineReportDoesNotOfferSubmitNow() {
        val s = session()
        s.say(slide)
        assertEquals(false, s.ui.value.canSubmitNow)
        assertEquals(false, session().ui.value.canSubmitNow)
    }

    @Test fun aCancelledAlarmOffersToLowerTheLevelButNeverDoesItBySelf() {
        val s = session(Scripted(update("""{"incident_type":"fire_smoke","location":"the kitchen","evidence":{"incident_type":"fire","location":"the kitchen"}}"""), nothing))
        s.say("There is a fire in the kitchen")
        assertNull(s.ui.value.deescalation)
        s.say("Sorry, false alarm")
        val offer = assertNotNull(s.ui.value.deescalation)
        assertEquals(Priority.LOW, offer.offerLevel)
        assertEquals(Priority.URGENT, s.ui.value.priority?.level, "the level is unchanged until the reporter taps")
        assertEquals(false, s.ui.value.priority?.overriddenByUser)

        s.overridePriority(Priority.LOW, "cancelled alarm")
        assertEquals(Priority.LOW, s.ui.value.priority?.level)
        assertEquals(true, s.ui.value.priority?.overriddenByUser)
        assertNull(s.ui.value.deescalation, "the chip is gone once the reporter decided")
    }

    @Test fun nothingRecognisedAsksTheOneKeyQuestionFirstAndOnlyOnce() {
        val s = session(Scripted(nothing))
        s.say(strange)
        val asking = assertIs<SessionState.Asking>(s.ui.value.state)
        assertTrue(asking.question.key)
        assertEquals(PriorityAdvisor.KEY_QUESTION, asking.question.spoken, "a single spoken question, nothing else with it")
        assertEquals(PriorityAdvisor.KEY_QUESTION, s.ui.value.say?.text)

        s.answerKeyQuestion(KeyAnswer.NO)
        val next = assertIs<SessionState.Asking>(s.ui.value.state)
        assertEquals(false, next.question.key)
        assertEquals(Field.INCIDENT_TYPE, next.question.asks.first().field, "then the normal questions")

        s.say("It is near the stairs")
        val askedKey = s.ui.value.toolLog.filter { it.tool == "ask_user" && it.json.contains(PriorityAdvisor.KEY_QUESTION) }
        assertEquals(1, askedKey.size)
    }

    @Test fun answeringYesToTheKeyQuestionRaisesToUrgentAndSpeaksTheEmergencyLine() {
        val s = session(Scripted(nothing))
        s.say(strange)
        s.answerKeyQuestion(KeyAnswer.YES)
        assertEquals(Priority.URGENT, s.ui.value.priority?.level)
        assertTrue(s.ui.value.say!!.text.startsWith(PriorityAdvisor.EMERGENCY_LINE))
        assertEquals(listOf(Field.LOCATION), assertIs<SessionState.Asking>(s.ui.value.state).question.asks.map { it.field })
    }

    @Test fun aRecognisedReportDoesNotAskTheKeyQuestion() {
        val s = session()
        s.say(slide)
        assertEquals(false, (s.ui.value.state as SessionState.Asking).question.key)
    }

    @Test fun aTimeWithoutADateIsAskedAndTappingTodayConfirmsIt() {
        val s = session()
        s.say(slide)
        assertEquals("date missing ≈10:00 (around 10 AM)", s.ui.value.report.value(Field.DATE_TIME).text, "nothing was assumed")
        assertNull(s.field(Field.DATE_TIME).hint?.takeIf { it.contains("assumed", ignoreCase = true) })
        val ask = (s.ui.value.state as SessionState.Asking).question.asks.first { it.field == Field.DATE_TIME }
        assertEquals("Was that today?", ask.prompt)
        s.answer(ask.replies.first { it.label == "Today" })
        val v = s.ui.value.report.value(Field.DATE_TIME)
        assertEquals("2026-10-11 ≈10:00 (around 10 AM)", v.text)
        assertEquals(ValueStatus.USER_CONFIRMED, v.status)
        assertEquals("Today, Sun 11 Oct · about 10:00 AM", s.field(Field.DATE_TIME).value)
    }

    @Test fun tappingYesterdayStoresYesterdayAsConfirmed() {
        val s = session()
        s.say(slide)
        val ask = (s.ui.value.state as SessionState.Asking).question.asks.first { it.field == Field.DATE_TIME }
        s.answer(ask.replies.first { it.label == "Yesterday" })
        assertEquals("2026-10-10 ≈10:00 (around 10 AM)", s.ui.value.report.value(Field.DATE_TIME).text)
        assertEquals(ValueStatus.USER_CONFIRMED, s.ui.value.report.value(Field.DATE_TIME).status)
    }

    // What the 4B model really returned on the kit for "There's a massive fire in a sh2 right now.": no date_time_text.
    private val fireNoTime = """{"tool":"update_report","arguments":{"incident_type":"fire_smoke","location":"a sh2","evidence":{"incident_type":"massive fire","location":"a sh2"}}}"""

    @Test fun rightNowIsTheTimeEvenWhenTheModelSkipsIt() {
        val s = session(Scripted(fireNoTime))
        s.say("There's a massive fire in a sh2 right now.")
        val time = s.field(Field.DATE_TIME)
        assertEquals(UiStatus.HEARD, time.status)
        assertEquals("Today, Sun 11 Oct · 3:00 PM", time.value, "now means the capture time")
        assertEquals("right now", time.evidence)
        assertTrue(s.ui.value.fields.none { it.field == Field.DATE_TIME && it.status == UiStatus.NEEDED })
    }

    @Test fun aNowPhraseNeverOverwritesADateTheReporterAlreadyGave() {
        val s = session(Scripted(FakeLlmEngine.DECK_REPLY, fireNoTime))
        s.say(slide)
        val before = s.field(Field.DATE_TIME).value
        s.say("It is still burning right now.")
        assertEquals(before, s.field(Field.DATE_TIME).value)
    }

    private val leakYesterdayMorning = """{"tool":"update_report","arguments":{"incident_type":"water_leak_flood","location":"SH2","date_time_text":"yesterday morning","evidence":{"incident_type":"water leak","location":"SH2","date_time_text":"yesterday morning"}}}"""

    @Test fun aKnownDayWithoutAClockTimeIsNotQuestioned() {
        // Kit run: "It happened yesterday morning." filled When but the app still asked "Which day was this?".
        val s = session(Scripted(leakYesterdayMorning))
        s.say("There was a water leak in SH2 yesterday morning.")
        val time = s.field(Field.DATE_TIME)
        assertEquals("Yesterday, Sat 10 Oct · morning", time.value)
        assertEquals(UiStatus.HEARD, time.status)
        assertNull(time.hint, "the day is known, so there is nothing to ask about it")
        assertTrue((s.ui.value.state as? SessionState.Asking)?.question?.asks?.none { it.field == Field.DATE_TIME } ?: true)
    }

    @Test fun relativeTimesResolveAgainstTheInstantTheReporterStoppedSpeaking() {
        val s = session(Scripted(fireNoTime))
        val stopped = Instant.parse("2026-10-11T09:36:00Z") // 15:06 IST, six minutes after the session clock
        runBlocking { s.onUtterance("There's a massive fire in a sh2 right now.", capturedAt = stopped) }
        assertEquals("Today, Sun 11 Oct · 3:06 PM", s.field(Field.DATE_TIME).value)
    }
}

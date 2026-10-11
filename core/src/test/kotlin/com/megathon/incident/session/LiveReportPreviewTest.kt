package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.testing.Compute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Answers with [replies] in call order; a reply may be held until the test releases it. */
private class Controlled(private val replies: List<String>, private val hold: Boolean = false) : LlmEngine {
    override val name = "CONTROLLED"
    val requests = CopyOnWriteArrayList<LlmRequest>()
    val gates = CopyOnWriteArrayList<CompletableDeferred<Unit>>()
    val calls get() = requests.size
    override suspend fun complete(request: LlmRequest): LlmResult {
        val i = requests.size
        requests += request
        if (hold) CompletableDeferred<Unit>().also { gates += it }.await()
        return LlmResult(replies[minOf(i, replies.lastIndex)], 5, Compute.SIMULATED)
    }
}

private fun reply(vararg pairs: Pair<String, String>): String {
    val args = pairs.joinToString(",") { "\"${it.first}\":\"${it.second}\"" }
    return """{"tool":"update_report","arguments":{$args,"evidence":{$args}}}"""
}

class LiveReportPreviewTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private fun session(llm: LlmEngine) = Pipeline.newSession(llm, InMemoryReportStore(), clock)
    private fun SessionUi.provisional(f: Field) = provisional?.fields?.firstOrNull { it.field == f }?.value

    private val room2 = reply("location" to "Room 2")
    private val room3 = reply("location" to "Room 3")

    @Test fun aPreviewShowsGroundedFieldsWithoutTouchingTheCommittedSession() = runBlocking {
        val s = session(Controlled(listOf(room2)))
        val before = s.ui.value
        val id = s.beginRecording()
        s.previewRecording(id, "the pump burst near Room 2")
        val ui = s.ui.value
        assertEquals("Room 2", ui.provisional(Field.LOCATION))
        assertEquals(before.report, ui.report)
        assertEquals(SessionState.Idle, ui.state)
        assertTrue(ui.journal.isEmpty() && ui.toolLog.isEmpty() && ui.transcripts.isEmpty() && !ui.canUndo)
        assertNull(ui.say, "no question is spoken before Stop")
    }

    @Test fun aSpokenCorrectionLeavesOnlyTheNewValue() = runBlocking {
        val s = session(Controlled(listOf(room2, room3)))
        val id = s.beginRecording()
        s.previewRecording(id, "leak near Room 2")
        s.previewRecording(id, "leak near Room 2 actually Room 3")
        assertEquals("Room 3", s.ui.value.provisional(Field.LOCATION))
    }

    @Test fun aValueTheLaterPreviewOmitsDoesNotSurvive() = runBlocking {
        val s = session(Controlled(listOf(reply("location" to "Room 2", "equipment_asset" to "the pump"), reply("equipment_asset" to "the pump"))))
        val id = s.beginRecording()
        s.previewRecording(id, "the pump near Room 2")
        s.previewRecording(id, "the pump but not in Room 2 after all")
        assertNull(s.ui.value.provisional(Field.LOCATION))
        assertEquals("the pump", s.ui.value.provisional(Field.EQUIPMENT_ASSET))
    }

    @Test fun aPreviewFinishingAfterCancelHasNoEffect() = runBlocking {
        val llm = Controlled(listOf(room2), hold = true)
        val s = session(llm)
        val id = s.beginRecording()
        val running = async { s.previewRecording(id, "near Room 2") }
        withTimeout(5000) { while (llm.gates.isEmpty()) yield() }
        s.cancelRecording(id)
        llm.gates.forEach { it.complete(Unit) }
        running.await()
        assertNull(s.ui.value.provisional)
        assertTrue(s.ui.value.report.fields.isEmpty())
    }

    @Test fun anOlderPreviewCannotOverwriteANewerOne() = runBlocking {
        val llm = Controlled(listOf(room2, room3), hold = true)
        val s = session(llm)
        val id = s.beginRecording()
        val older = async { s.previewRecording(id, "near Room 2") }
        withTimeout(5000) { while (llm.gates.size < 1) yield() }
        val newer = async { s.previewRecording(id, "near Room 2 actually Room 3") }
        withTimeout(5000) { while (llm.gates.size < 2) yield() }
        llm.gates[1].complete(Unit); newer.await()
        llm.gates[0].complete(Unit); older.await()
        assertEquals("Room 3", s.ui.value.provisional(Field.LOCATION))
    }

    @Test fun anExactMatchingPreviewIsReusedAtStopWithNoSecondModelCall() = runBlocking {
        val llm = Controlled(listOf(room2))
        val s = session(llm)
        val id = s.beginRecording()
        s.previewRecording(id, "the pump burst near Room 2")
        s.onUtterance("the pump burst near Room 2", recording = id)
        assertEquals(1, llm.calls)
        assertTrue(s.ui.value.stages.single { it.stage == "Read" }.engine.contains("during recording"), "a reused call must not be shown as if it ran after Stop")
        assertEquals("Room 2", s.ui.value.fields.first { it.field == Field.LOCATION }.value)
        assertNull(s.ui.value.provisional)
        assertEquals(1, s.ui.value.transcripts.size)
        s.undo()
        assertTrue(s.ui.value.report.fields.isEmpty() && s.ui.value.transcripts.isEmpty(), "one recording is one undo step")
    }

    @Test fun aChangedFinalTranscriptForcesOneFreshCall() = runBlocking {
        val llm = Controlled(listOf(room2, room3))
        val s = session(llm)
        val id = s.beginRecording()
        s.previewRecording(id, "near Room 2")
        s.onUtterance("near Room 2 actually Room 3", recording = id)
        assertEquals(2, llm.calls)
        assertEquals("Room 3", s.ui.value.fields.first { it.field == Field.LOCATION }.value)
    }

    @Test fun anEditDuringCaptureInvalidatesThePreviewAndTheReuse() = runBlocking {
        val llm = Controlled(listOf(room2))
        val s = session(llm)
        val id = s.beginRecording()
        s.previewRecording(id, "near Room 2")
        s.edit(Field.EQUIPMENT_ASSET, "boiler")
        assertNull(s.ui.value.provisional)
        s.onUtterance("near Room 2", recording = id)
        assertEquals(2, llm.calls, "the baseline changed, so the proposal is not reused")
    }

    @Test fun aFailedPreviewIsNeverPromotedToTheFinalTurn() = runBlocking {
        val llm = Controlled(listOf("not json", "still not json", room2))
        val s = session(llm)
        val id = s.beginRecording()
        s.previewRecording(id, "near Room 2")
        assertNull(s.ui.value.provisional)
        s.onUtterance("near Room 2", recording = id)
        assertEquals(3, llm.calls)
        assertEquals("Room 2", s.ui.value.fields.first { it.field == Field.LOCATION }.value)
    }

    @Test fun rightNowIsResolvedAtTheStopInstantNotThePreviewInstant() = runBlocking {
        val llm = Controlled(listOf(reply("date_time_text" to "right now")))
        val s = session(llm)
        val id = s.beginRecording()
        s.previewRecording(id, "the lift is stuck right now")
        s.onUtterance("the lift is stuck right now", capturedAt = Instant.parse("2026-10-11T10:00:00Z"), recording = id)
        assertEquals(1, llm.calls)
        val shown = s.ui.value.fields.first { it.field == Field.DATE_TIME }.value.orEmpty()
        assertTrue("3:30 PM" in shown, shown)
    }

    @Test fun cancellingTheRecordingKeepsTheCommittedReportUnchanged() = runBlocking {
        val s = session(Controlled(listOf(room2)))
        val before = s.ui.value.report
        val id = s.beginRecording()
        s.previewRecording(id, "near Room 2")
        s.cancelRecording(id)
        assertNull(s.ui.value.provisional)
        assertEquals(before, s.ui.value.report)
    }

    @Test fun theBatchPathStillWorksWithoutARecording() = runBlocking {
        val s = session(Controlled(listOf(room2)))
        s.onUtterance("near Room 2")
        assertEquals("Room 2", s.ui.value.fields.first { it.field == Field.LOCATION }.value)
        assertIs<SessionState.Asking>(s.ui.value.state)
        Unit
    }
}

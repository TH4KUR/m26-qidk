package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Source
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
import kotlin.test.assertTrue

/** First-pass requests get the extraction reply, focused requests go to the probe's fake. */
private class DualLlm(private val extraction: String, val probe: ProbeLlm) : LlmEngine {
    override val name = "DUAL-FAKE"
    override suspend fun complete(request: LlmRequest): LlmResult =
        if ("update_report" in request.system) LlmResult(extraction, 5, Compute.SIMULATED) else probe.complete(request)
}

/** The probe end to end through the real session: after the turn, as chips, never as a write. */
class ProbeInSessionTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val said = "There was a water leak from the kettle near Conference Room 2 around 10 AM. Maintenance has already isolated the area."
    private val kettle = """{"item":"kettle","quote":"water leak from the kettle"}"""

    private fun session(probeReply: String = kettle, useProbe: Boolean = true): Pair<ReportSession, ProbeLlm> {
        val probe = ProbeLlm(item = probeReply)
        val s = Pipeline.newSession(DualLlm(FakeLlmEngine.DECK_REPLY, probe), InMemoryReportStore(), clock, useProbe = useProbe)
        runBlocking { s.onUtterance(said) }
        return s to probe
    }

    private fun ReportSession.llmChips() = ui.value.suggestions.filter { it.source == Source.LLM }

    @Test fun `a turn never calls the probe, so it adds nothing to the turn time`() {
        val (s, probe) = session()
        assertTrue(probe.systems.isEmpty())
        assertTrue(s.llmChips().isEmpty())
    }

    @Test fun `after the turn the probe offers the missed tool or item as a chip, and the report is untouched`() {
        val (s, _) = session()
        runBlocking { s.probeMissing() }
        val chip = s.llmChips().single()
        assertEquals(Field.EQUIPMENT_ASSET, chip.field)
        assertEquals("kettle", chip.proposed)
        assertEquals(ValueStatus.MISSING, s.ui.value.report.value(Field.EQUIPMENT_ASSET).status)
    }

    @Test fun `accepting the chip confirms the value for the reporter and the chip goes away`() {
        val (s, _) = session()
        runBlocking { s.probeMissing() }
        s.acceptSuggestion(s.llmChips().single())
        val v = s.ui.value.report.value(Field.EQUIPMENT_ASSET)
        assertEquals("kettle", v.text)
        assertEquals(ValueStatus.USER_CONFIRMED, v.status)
        assertTrue(s.llmChips().isEmpty())
    }

    @Test fun `a dismissed chip stays dismissed even when the probe runs again`() {
        val (s, _) = session()
        runBlocking { s.probeMissing() }
        s.rejectSuggestion(s.llmChips().single())
        assertTrue(s.llmChips().isEmpty())
        runBlocking { s.probeMissing() }
        assertTrue(s.llmChips().isEmpty())
        assertEquals(ValueStatus.MISSING, s.ui.value.report.value(Field.EQUIPMENT_ASSET).status)
    }

    @Test fun `speaking again drops the old probe chips`() {
        val (s, _) = session()
        runBlocking { s.probeMissing() }
        runBlocking { s.onUtterance("Minor, nobody was hurt.") }
        assertTrue(s.llmChips().isEmpty())
    }

    @Test fun `a nonsense reply gives no chip and the session carries on`() {
        val (s, _) = session(probeReply = "I think it was a kettle?")
        runBlocking { s.probeMissing() }
        assertTrue(s.llmChips().isEmpty())
    }

    @Test fun `without a probe nothing changes`() {
        val (s, probe) = session(useProbe = false)
        runBlocking { s.probeMissing() }
        assertTrue(probe.systems.isEmpty())
        assertTrue(s.llmChips().isEmpty())
    }
}

package com.megathon.incident.testing

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.ValueStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakePipelineRunnerTest {
    private val predictions = """
        {"gold_id":"g01","captured_at":"2026-10-11T12:00:00+05:30","asked":[["SEVERITY"]],"transcript":"There was a water leak","latency_ms":{"stt":120,"llm":900},"report":{"id":"g01","createdAtEpochMs":0,"fields":{"LOCATION":{"text":"Conference Room 2","status":"REPORTED","evidence":"near Conference Room 2"}}}}
        {"gold_id":"g02","report":{"id":"g02","createdAtEpochMs":0,"fields":{}}}
    """.trimIndent()

    @Test
    fun replaysTheRecordedReportForAGoldItem() = runBlocking {
        val out = FakePipelineRunner(predictions).run("g01", listOf("There was a water leak"))
        assertEquals("Conference Room 2", out.report.value(Field.LOCATION).text)
        assertEquals(ValueStatus.REPORTED, out.report.value(Field.LOCATION).status)
        assertEquals(listOf(listOf(Field.SEVERITY)), out.asked)
        assertEquals(mapOf("stt" to 120L, "llm" to 900L), out.latencyMs)
    }

    @Test
    fun isAlwaysSimulated() {
        assertEquals(Compute.SIMULATED, FakePipelineRunner(predictions).compute)
    }

    @Test
    fun optionalFieldsDefaultToEmpty() = runBlocking {
        val out = FakePipelineRunner(predictions).run("g02", listOf("x"))
        assertTrue(out.asked.isEmpty() && out.latencyMs.isEmpty() && out.transcript == "")
    }

    @Test
    fun unknownGoldIdFailsLoudly() {
        assertFailsWith<IllegalArgumentException> { runBlocking { FakePipelineRunner(predictions).run("g99", listOf("x")) } }
    }

    @Test
    fun outputConvertsToTheScorerPredictionFormat() = runBlocking {
        val json = FakePipelineRunner(predictions).run("g01", listOf("x")).toPrediction().toJson()
        assertEquals("g01", json["gold_id"].toString().trim('"'))
        assertTrue(json.toString().contains("\"LOCATION\""))
    }
}

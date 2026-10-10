package com.megathon.incident.session

import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.PayloadValidator
import com.megathon.incident.llm.ToolCallParser
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test

/**
 * Replays saved model outputs (`raw.jsonl`: one `output` string per turn) through the real parser, validator and merge,
 * and writes predictions in the shape of E's scorer (`tools/laptop_eval/score.py`, on `e/data-eval`).
 *
 * It does nothing unless `REPLAY_JOBS` is set, so the normal suite never touches it:
 * `REPLAY_JOBS="raw.jsonl|captured.jsonl|out.jsonl;..."` where `captured.jsonl` is any file with one
 * `{"gold_id","captured_at"}` row per gold item (E's predictions file works).
 *
 * Used to compare two versions of this code on the same outputs, never to tune on the held-out cases.
 */
class ReplayScoringHarness {
    private val json = Json { encodeDefaults = true }
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun replayWhenAsked() {
        val jobs = System.getenv("REPLAY_JOBS")?.takeIf { it.isNotBlank() } ?: return
        for (job in jobs.split(';').filter { it.isNotBlank() }) {
            val (rawPath, capturedPath, outPath) = job.split('|')
            replay(File(rawPath), File(capturedPath), File(outPath))
        }
    }

    private fun lines(f: File) = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    private fun replay(raw: File, captured: File, out: File) {
        val capturedAt = lines(captured).associate { it["gold_id"]!!.jsonPrimitive.content to it["captured_at"]!!.jsonPrimitive.content }
        val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), TemporalResolver(zone))
        val byItem = lines(raw).groupBy { it["gold_id"]!!.jsonPrimitive.content }
        val rows = StringBuilder()
        for ((id, turns) in byItem) {
            val at = capturedAt[id] ?: continue
            val instant = OffsetDateTime.parse(at).toInstant()
            var report = IncidentReport.empty(id, 0)
            for (turn in turns.sortedBy { it["turn"]!!.jsonPrimitive.content.toInt() }) {
                val utterance = turn["utterance"]!!.jsonPrimitive.content
                val parsed = ToolCallParser.parse(turn["output"]!!.jsonPrimitive.contentOrNull.orEmpty())
                if (parsed !is ParseResult.Ok) continue
                val result = validator.validate(parsed.payload, utterance, instant)
                report = ReportMerge.apply(report, result, MergeContext(utterance, instant, zone, 0)).report
            }
            val row: JsonObject = buildJsonObject {
                put("gold_id", id)
                put("captured_at", at)
                put("compute", "REPLAY")
                put("report", json.parseToJsonElement(json.encodeToString(IncidentReport.serializer(), report)))
            }
            rows.append(row.toString()).append('\n')
        }
        out.parentFile?.mkdirs()
        out.writeText(rows.toString(), Charsets.UTF_8)
    }
}

package com.megathon.incident.eval

import com.megathon.incident.eval.EvalSplit
import com.megathon.incident.eval.SplitFilter
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.ToolCallParser
import com.megathon.incident.session.InMemoryReportStore
import com.megathon.incident.session.Pipeline
import com.megathon.incident.session.PromptBuilder
import com.megathon.incident.session.QuestionPlanner
import com.megathon.incident.session.SessionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import com.megathon.incident.domain.IncidentReport
import java.io.File
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Wraps an engine and keeps every call: raw output, timings and whether it parses. */
class RecordingLlmEngine(private val inner: LlmEngine) : LlmEngine {
    data class Call(val request: LlmRequest, val result: LlmResult?, val error: String?)

    override val name get() = inner.name
    val calls = mutableListOf<Call>()

    override suspend fun complete(request: LlmRequest): LlmResult {
        return try {
            inner.complete(request).also { calls += Call(request, it, null) }
        } catch (e: Exception) {
            calls += Call(request, null, "${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}

/**
 * Debug-only: runs one split of E's gold set through the app's real pipeline (prompt, model, parser, validator,
 * merge, planner) and writes what E's scorer reads: `raw.jsonl` and `predictions.jsonl`.
 * The split is [EvalSplit.DEV] unless the caller passes [EvalSplit.HOLDOUT], and the held-out items are only ever reached
 * through `AppViewModel.debugHoldout`, which applies `HoldoutGuard` first. Output: `<filesDir>/eval/<runName>/`.
 * [onSelected] receives the ids that are about to run, before the first model call (the held-out ledger is written there).
 */
class EvalRunner(private val filesDir: File, private val engine: LlmEngine, private val warmUp: suspend () -> Long) {
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    suspend fun run(
        gold: File,
        prompts: PromptBuilder,
        runName: String,
        only: Set<String>?,
        split: EvalSplit = EvalSplit.DEV,
        onSelected: (List<String>) -> Unit = {},
    ): File {
        val outDir = File(filesDir, "eval/$runName").apply { mkdirs() }
        val items = gold.readLines().filter { it.isNotBlank() }.map { json.parseToJsonElement(it).jsonObject }
            .filter { SplitFilter.selects(it["split"]?.jsonPrimitive?.contentOrNull, split) }
            .filter { only == null || it["id"]!!.jsonPrimitive.content in only }
        onSelected(items.map { it["id"]!!.jsonPrimitive.content })
        val loadMs = warmUp()

        val raw = StringBuilder()
        val preds = StringBuilder()
        val zone = ZoneOffset.ofHoursMinutes(5, 30)
        val summary = mutableListOf<String>()

        for (item in items) {
            val id = item["id"]!!.jsonPrimitive.content
            val turns = item["turns"]!!.jsonArray.map { it.jsonPrimitive.content }
            val recording = RecordingLlmEngine(engine)
            val session = Pipeline.newSession(recording, InMemoryReportStore(), Clock.systemUTC(), java.time.ZoneId.of("Asia/Kolkata"), prompts)
            val capturedAt = OffsetDateTime.now(zone).withNano(0).toString()
            val asked = mutableListOf<List<String>>()
            var failed: String? = null
            for ((turnIndex, utterance) in turns.withIndex()) {
                val before = recording.calls.size
                try {
                    session.onUtterance(utterance, null)
                } catch (e: Exception) {
                    failed = "${e.javaClass.simpleName}: ${e.message}"
                }
                recording.calls.drop(before).forEachIndexed { callIndex, c ->
                    val parsed = c.result?.let { ToolCallParser.parse(it.text) }
                    raw.appendLine(
                        buildJsonObject {
                            put("gold_id", id); put("turn", turnIndex); put("call", callIndex); put("utterance", utterance)
                            put("output", c.result?.text ?: "")
                            put("valid", parsed is ParseResult.Ok)
                            (parsed as? ParseResult.Failure)?.let { put("failure", "${it.code}: ${it.detail}".take(300)) }
                            c.error?.let { put("error", it) }
                            c.result?.let { r ->
                                put("elapsed_ms", r.elapsedMs); put("compute", r.compute.name)
                                r.timings?.let { t ->
                                    put("ttft_ms", t.ttftMs); put("prompt_tokens", t.promptTokens); put("generated_tokens", t.generatedTokens)
                                    put("prefill_tps", t.prefillTokensPerSec); put("decode_tps", t.decodeTokensPerSec)
                                }
                            }
                        }.toString(),
                    )
                }
                (session.ui.value.state as? SessionState.Asking)?.let { asked += it.question.asks.map { a -> a.field.name.lowercase() } } ?: asked.add(emptyList())
                if (failed != null) break
            }
            val report: IncidentReport = session.ui.value.report
            val compute = recording.calls.lastOrNull { it.result != null }?.result?.compute?.name ?: "NONE"
            preds.appendLine(
                buildJsonObject {
                    put("gold_id", id); put("captured_at", capturedAt); put("compute", compute)
                    put("report", json.parseToJsonElement(json.encodeToString(IncidentReport.serializer(), report)))
                    putJsonArray("asked") { asked.forEach { turn -> add(JsonArray(turn.map { JsonPrimitive(it) })) } }
                    failed?.let { put("error", it) }
                }.toString(),
            )
            summary += "$id calls=${recording.calls.size} valid=${recording.calls.count { c -> c.result?.let { ToolCallParser.parse(it.text) is ParseResult.Ok } == true }}${failed?.let { " ERROR $it" } ?: ""}"
        }
        File(outDir, "raw.jsonl").writeText(raw.toString())
        File(outDir, "predictions.jsonl").writeText(preds.toString())
        File(outDir, "meta.json").writeText(
            buildJsonObject {
                put("run", runName); put("engine", engine.name); put("items", items.size); put("model_load_ms", loadMs)
                put("mandatory_fields", QuestionPlanner.MANDATORY.joinToString { it.name.lowercase() })
            }.toString(),
        )
        File(outDir, "summary.txt").writeText(summary.joinToString("\n"))
        return outDir
    }
}

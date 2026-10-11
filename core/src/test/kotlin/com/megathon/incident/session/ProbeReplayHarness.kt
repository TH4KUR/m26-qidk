package com.megathon.incident.session

import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ReportState
import com.megathon.incident.suggest.MissedFieldRules
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.testing.Compute
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test

/**
 * Replays saved replies of a model to [FocusedProbe]'s prompts through the real probe (grounding, plan and hedge check,
 * place, person, role and body-part guards) and writes the chips it would offer per dev item. Runs only when
 * `PROBE_REPLAY="replies.jsonl|predictions.jsonl|chips.json"` is set: `replies.jsonl` rows are
 * `{id, field, system, user, reply}`; `predictions.jsonl` is a replayed first pass (E's scorer format), whose report decides
 * which fields are still empty and what the guards compare with.
 */
class ProbeReplayHarness {
    private fun unix(s: String) = s.replace("\r\n", "\n")

    @Test
    fun replayWhenAsked() {
        val spec = System.getenv("PROBE_REPLAY")?.takeIf { it.isNotBlank() } ?: return
        val (repliesPath, predsPath, outPath) = spec.split('|')
        val lines = { p: String -> File(p).readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject } }
        val replies = lines(repliesPath)
        val byRequest = replies.associate {
            (unix(it["system"]!!.jsonPrimitive.content) to it["user"]!!.jsonPrimitive.content) to it["reply"]!!.jsonPrimitive.content
        }
        val engine = object : LlmEngine {
            override val name = "REPLAY"
            override suspend fun complete(request: LlmRequest) =
                LlmResult(byRequest[unix(request.system) to request.user] ?: "{}", 0, Compute.SIMULATED)
        }
        val probe = FocusedProbe(engine)
        val rules = MissedFieldRules(TamDetector(TamCueLists.loadDefault()))
        val transcripts = replies.associate {
            it["id"]!!.jsonPrimitive.content to it["user"]!!.jsonPrimitive.content.removePrefix("Worker: \"").removeSuffix("\"")
        }
        val out = buildJsonArray {
            for (row in lines(predsPath)) {
                val id = row["gold_id"]!!.jsonPrimitive.content
                val report = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(IncidentReport.serializer(), row["report"]!!)
                val chips = runBlocking { probe.propose(report, transcripts[id].orEmpty()) }
                val ruleChips = rules.chips(ReportState(report, emptyList()), transcripts[id].orEmpty())
                add(buildJsonObject {
                    put("id", id)
                    put("rules", buildJsonArray {
                        ruleChips.forEach { c -> add(buildJsonObject { put("field", c.field.name); put("proposed", c.proposed) }) }
                    })
                    put("chips", buildJsonArray {
                        chips.forEach { c -> add(buildJsonObject { put("field", c.field.name); put("proposed", c.proposed); put("reason", c.reason) }) }
                    })
                })
            }
        }
        File(outPath).also { it.parentFile?.mkdirs() }.writeText(out.toString(), Charsets.UTF_8)
    }
}

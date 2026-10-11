package com.megathon.incident.llm

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test

/**
 * Runs every training label of the synthetic gold through the real `ToolCallParser` and `PayloadValidator`, as A/017
 * requires ("drop anything rejected or demoted"). Runs only when `SFT_AUDIT="gold.jsonl|issues.json"` is set.
 *
 * A label is clean when it parses, no emitted field is rejected, and every emitted field comes out REPORTED
 * (a SUGGESTED or missing one means the app would not store what the label says).
 */
class SftLabelAuditHarness {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = OffsetDateTime.parse("2026-10-11T11:00:00+05:30").toInstant()
    private val keyToField = mapOf(
        "incident_type" to Field.INCIDENT_TYPE, "severity" to Field.SEVERITY, "location" to Field.LOCATION,
        "date_time_text" to Field.DATE_TIME, "persons_involved" to Field.PERSONS_INVOLVED,
        "equipment_asset" to Field.EQUIPMENT_ASSET, "immediate_actions" to Field.IMMEDIATE_ACTIONS, "witnesses" to Field.WITNESSES,
    )

    @Test
    fun auditWhenAsked() {
        val spec = System.getenv("SFT_AUDIT")?.takeIf { it.isNotBlank() } ?: return
        val (goldPath, outPath) = spec.split('|')
        val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), TemporalResolver(zone))
        val issues = linkedMapOf<String, JsonArray>()
        var rows = 0
        for (line in File(goldPath).readLines(Charsets.UTF_8).filter { it.isNotBlank() }) {
            val row = Json.parseToJsonElement(line).jsonObject
            val id = row["id"]!!.jsonPrimitive.content
            val raw = row["raw_text"]!!.jsonPrimitive.content
            val assistant = row["messages"]!!.jsonArray[2].jsonObject["content"]!!.jsonPrimitive.content
            rows++
            val problems = mutableListOf<String>()
            val parsed = ToolCallParser.parse(assistant)
            if (parsed !is ParseResult.Ok) {
                problems += "parse: ${(parsed as ParseResult.Failure).code}"
            } else {
                val result = validator.validate(parsed.payload, raw, at)
                val args = Json.parseToJsonElement(assistant).jsonObject["arguments"]!!.jsonObject
                for (key in args.keys.filter { it != "evidence" }) {
                    val field = keyToField[key] ?: continue
                    result.rejections.filter { it.field == field }.forEach { problems += "$key rejected: ${it.reason.take(70)}" }
                    val p = result.proposals.firstOrNull { it.field == field }
                    when {
                        p == null && result.rejections.none { it.field == field } -> problems += "$key missing from proposals"
                        p != null && p.value.status != ValueStatus.REPORTED -> problems += "$key ${p.value.status}: ${p.note.orEmpty().take(60)}"
                    }
                }
            }
            if (problems.isNotEmpty()) issues[id] = JsonArray(problems.map { JsonPrimitive(it) })
        }
        val out: JsonObject = buildJsonObject {
            put("rows", rows)
            put("issue_rows", issues.size)
            put("issues", JsonObject(issues))
        }
        File(outPath).also { it.parentFile?.mkdirs() }.writeText(out.toString(), Charsets.UTF_8)
    }
}

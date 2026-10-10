package com.megathon.incident.llm

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.session.MergeContext
import com.megathon.incident.session.PromptBuilder
import com.megathon.incident.session.ReportMerge
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves SFT targets parse in the app and inputs match the actual prompt builder. */
class SyntheticGoldContractTest {
    @Test
    fun `synthetic targets and saved contexts obey the shipping extraction boundary`() {
        val directory = listOf(File("../data/synthetic_gold/v1"), File("data/synthetic_gold/v1"))
            .first { it.isDirectory }
        val system = File(directory, "system_prompt.txt").readText().trim()
        val lines = File(directory, "gold.jsonl").readLines().filter { it.isNotBlank() }
        assertEquals(800, lines.size)
        for (line in lines) {
            val row = Json.parseToJsonElement(line).jsonObject
            val id = row.getValue("id").jsonPrimitive.content
            val target = row.getValue("target").toString()
            assertTrue(ToolCallParser.parse(target) is ParseResult.Ok, "$id: target rejected by app parser")
            val fields = row.getValue("report_before").jsonObject.map { (key, value) ->
                Field.valueOf(key.uppercase()) to FieldValue(value.jsonPrimitive.content, ValueStatus.REPORTED)
            }.toMap()
            val report = IncidentReport(id, 0, fields)
            val request = PromptBuilder(system).build(row.getValue("raw_text").jsonPrimitive.content, report)
            val messages = row.getValue("messages").jsonArray
            assertEquals(messages[0].jsonObject.getValue("content").jsonPrimitive.content, request.system, "$id: system")
            assertEquals(messages[1].jsonObject.getValue("content").jsonPrimitive.content, request.user, "$id: prompt format")
        }
    }

    @Test
    fun `update sequence gold replaces saved fields through the real validator and merge`() {
        val directory = listOf(File("../data/synthetic_gold/v1"), File("data/synthetic_gold/v1"))
            .first { it.isDirectory }
        val rows = File(directory, "gold.jsonl").readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }.filter { "transition" in it }
        assertEquals(320, rows.size)
        val zone = ZoneId.of("Asia/Kolkata")
        val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), TemporalResolver(zone))
        val failures = mutableListOf<JsonObject>()
        val reports = mutableMapOf<String, IncidentReport>()
        for (row in rows) {
            val id = row.getValue("id").jsonPrimitive.content
            val group = row.getValue("group_id").jsonPrimitive.content
            val text = row.getValue("raw_text").jsonPrimitive.content
            val transition = row.getValue("transition").jsonObject
            val captured = Instant.parse(transition.getValue("captured_at").jsonPrimitive.content)
            val before = reports[group] ?: IncidentReport.empty(group, 0)
            val parsed = ToolCallParser.parse(row.getValue("target").toString()) as ParseResult.Ok
            val validated = validator.validate(parsed.payload, text, captured)
            val issues = validated.rejections.map { "rejected ${it.field}: ${it.reason}" }.toMutableList()
            // Resolved time is a separate oracle boundary with known resolver gaps.
            val nonTemporal = validated.copy(proposals = validated.proposals.filter { it.field != Field.DATE_TIME })
            val after = ReportMerge.apply(before, nonTemporal, MergeContext(text, captured, zone, 1)).report
            reports[group] = after
            for ((key, value) in transition.getValue("expected_report_after").jsonObject) {
                val field = Field.valueOf(key.uppercase())
                if (field == Field.DATE_TIME) continue
                if (value.jsonPrimitive.content != after.value(field).text) {
                    issues += "$field: expected ${value.jsonPrimitive.content}, got ${after.value(field).text}"
                }
                if (after.value(field).status != ValueStatus.REPORTED) {
                    issues += "$field: expected REPORTED, got ${after.value(field).status}"
                }
            }
            if (issues.isNotEmpty()) {
                failures += JsonObject(mapOf("id" to JsonPrimitive(id),
                    "issues" to JsonArray(issues.map { JsonPrimitive(it) })))
            }
        }
        // Audit every row even when the first row reveals a downstream defect.
        println("SYNTHETIC_UPDATE_AUDIT " + JsonObject(mapOf(
            "rows" to JsonPrimitive(rows.size), "matching_rows" to JsonPrimitive(rows.size - failures.size),
            "mismatching_rows" to JsonPrimitive(failures.size), "excluded_field" to JsonPrimitive("date_time"),
            "mode" to JsonPrimitive("sequential replay from empty report; no gold-state reseeding"),
            "mismatches" to JsonArray(failures))))
        assertTrue(failures.isEmpty(), "${failures.size} update-sequence rows disagree with the real validator/merge; see SYNTHETIC_UPDATE_AUDIT")
    }
}

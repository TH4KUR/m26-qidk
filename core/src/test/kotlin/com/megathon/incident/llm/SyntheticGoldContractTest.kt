package com.megathon.incident.llm

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.session.PromptBuilder
import java.io.File
import kotlinx.serialization.json.Json
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
        assertEquals(480, lines.size)
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
}

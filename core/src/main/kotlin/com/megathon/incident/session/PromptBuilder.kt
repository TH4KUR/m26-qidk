package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.llm.LlmRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Builds the prompt the on-device model sees. Keep it small: NPU builds often fix the context near 1k tokens. */
class PromptBuilder(private val system: String) {
    /**
     * [earlier] are the worker's previous sentences (oldest first): "there" and "where it was" can only be understood with them.
     * The model sees them as plain lines, then the report so far, then the new words.
     */
    fun build(utterance: String, report: IncidentReport, earlier: List<String> = emptyList()): LlmRequest {
        val before = earlier.map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("") { "Earlier the worker said: \"$it\"\n" }
        return LlmRequest(system = system, user = "${before}Report so far: ${compact(report)}\nWorker: \"${utterance.trim()}\"")
    }

    /**
     * One repair attempt after an unreadable reply. It says what was wrong, because a small model repeats a mistake it
     * is not told about. [why] is the parser's reason (trimmed), or empty when there is none.
     */
    fun repair(previous: LlmRequest, why: String = ""): LlmRequest {
        val reason = why.lineSequence().firstOrNull().orEmpty().take(160)
        val tell = if (reason.isBlank()) "" else " It was rejected: $reason."
        return previous.copy(
            user = previous.user +
                "\nYour last reply could not be used.$tell Reply again with ONLY the JSON object. " +
                "Leave out every field that is empty (no null, [] or \"\"), and give each included field its own non-empty evidence text.",
        )
    }

    /**
     * Filled fields only, under the SAME key names the model must write (a small model copies what it sees: it once echoed
     * "date_time" and an internal date string back). A time shows as the words the worker used.
     */
    private fun compact(report: IncidentReport): String {
        val filled = Field.entries.mapNotNull { f ->
            val key = KEYS[f] ?: return@mapNotNull null
            if (!report.isFilled(f)) return@mapNotNull null
            val text = report.value(f).text.orEmpty()
            key to JsonPrimitive(if (f == Field.DATE_TIME) DateTimeDisplay.parse(text)?.phrase ?: text else text)
        }.toMap()
        return JsonObject(filled).toString()
    }

    private val KEYS = mapOf(
        Field.INCIDENT_TYPE to "incident_type", Field.SEVERITY to "severity", Field.LOCATION to "location",
        Field.DATE_TIME to "date_time_text", Field.PERSONS_INVOLVED to "persons_involved", Field.EQUIPMENT_ASSET to "equipment_asset",
        Field.IMMEDIATE_ACTIONS to "immediate_actions", Field.WITNESSES to "witnesses",
    )

    companion object {
        fun default(): PromptBuilder {
            val text = PromptBuilder::class.java.getResourceAsStream("/prompts/extract_v8.txt")
                ?.bufferedReader()?.use { it.readText() }
                ?: error("missing resource /prompts/extract_v8.txt")
            return PromptBuilder(text.trim())
        }
    }
}

package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.llm.LlmRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Builds the prompt the on-device model sees. Keep it small: NPU builds often fix the context near 1k tokens. */
class PromptBuilder(private val system: String) {
    fun build(utterance: String, report: IncidentReport): LlmRequest =
        LlmRequest(system = system, user = "Report so far: ${compact(report)}\nWorker: \"${utterance.trim()}\"")

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

    private fun compact(report: IncidentReport): String {
        val filled = Field.entries.filter { report.isFilled(it) }.associate { f ->
            f.name.lowercase() to JsonPrimitive(report.value(f).text.orEmpty())
        }
        return JsonObject(filled).toString()
    }

    companion object {
        fun default(): PromptBuilder {
            val text = PromptBuilder::class.java.getResourceAsStream("/prompts/extract_v8.txt")
                ?.bufferedReader()?.use { it.readText() }
                ?: error("missing resource /prompts/extract_v8.txt")
            return PromptBuilder(text.trim())
        }
    }
}

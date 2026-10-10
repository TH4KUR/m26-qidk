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

    /** One repair attempt after an unreadable reply. */
    fun repair(previous: LlmRequest): LlmRequest = previous.copy(user = previous.user + "\nReply with ONLY the JSON object.")

    private fun compact(report: IncidentReport): String {
        val filled = Field.entries.filter { report.isFilled(it) }.associate { f ->
            f.name.lowercase() to JsonPrimitive(report.value(f).text.orEmpty())
        }
        return JsonObject(filled).toString()
    }

    companion object {
        fun default(): PromptBuilder {
            val text = PromptBuilder::class.java.getResourceAsStream("/prompts/chat_json_system.txt")
                ?.bufferedReader()?.use { it.readText() }
                ?: error("missing resource /prompts/chat_json_system.txt")
            return PromptBuilder(text.trim())
        }
    }
}

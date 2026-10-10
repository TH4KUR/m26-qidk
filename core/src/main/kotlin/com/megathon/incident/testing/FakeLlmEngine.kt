package com.megathon.incident.testing

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult

/**
 * Canned LLM so the UI, rules and evaluation can run before GenieX works. Always [Compute.SIMULATED].
 * Returns the reply of the first [scripted] key found (case-insensitive) in the user text, else [fallback].
 */
class FakeLlmEngine(
    override val name: String = "FAKE",
    private val scripted: Map<String, String> = DEMO_SCRIPT,
    private val fallback: String = EMPTY_REPLY,
) : LlmEngine {
    override suspend fun complete(request: LlmRequest): LlmResult {
        val startedAt = System.nanoTime()
        // Match only what the worker just said (the prompt also carries the report so far, which would match old keys).
        val said = request.user.substringAfterLast("Worker:")
        val reply = scripted.entries.firstOrNull { said.contains(it.key, ignoreCase = true) }?.value ?: fallback
        return LlmResult(reply, (System.nanoTime() - startedAt) / 1_000_000, Compute.SIMULATED)
    }

    companion object {
        /** The problem-statement example, verbatim. */
        const val DECK_TRANSCRIPT =
            "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."
        const val DECK_KEY = "water leak"
        const val SEVERITY_REPLY =
            """{"tool":"update_report","arguments":{"severity":"minor","evidence":{"severity":"Minor"}}}"""
        const val CORRECTION_REPLY =
            """{"tool":"update_report","arguments":{"location":"Conference Room 3","evidence":{"location":"Conference Room 3"}}}"""

        /** The 3-minute demo, in order: the slide sentence, the severity answer, then a voice correction. Specific keys first. */
        val DEMO_SCRIPT: Map<String, String> = linkedMapOf(
            "conference room 3" to CORRECTION_REPLY,
            "minor" to SEVERITY_REPLY,
            DECK_KEY to DECK_REPLY,
        )
        const val EMPTY_REPLY = """{"tool":"update_report","arguments":{}}"""
        const val DECK_REPLY =
            """{"tool":"update_report","arguments":{"incident_type":"water_leak_flood","location":"Conference Room 2",""" +
                """"date_time_text":"around 10 AM","immediate_actions":"isolated the area","evidence":{""" +
                """"incident_type":"water leak","location":"near Conference Room 2","date_time_text":"around 10 AM",""" +
                """"immediate_actions":"already isolated the area"}}}"""
    }
}

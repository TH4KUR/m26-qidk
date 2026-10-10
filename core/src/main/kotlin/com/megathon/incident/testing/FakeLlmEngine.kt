package com.megathon.incident.testing

import com.megathon.incident.llm.Compute
import com.megathon.incident.llm.ExtractionRequest
import com.megathon.incident.llm.LlmEngine

/**
 * Canned `update_report` output per utterance, so the UI and pipeline can be built before the NPU
 * engine exists. Always [Compute.SIMULATED]; unknown input gets a valid no-op call.
 */
class FakeLlmEngine(
    canned: Map<String, String> = emptyMap(),
    private val fallback: String = NO_OP,
) : LlmEngine {
    private val answers = canned.mapKeys { normalise(it.key) }

    override val compute = Compute.SIMULATED

    override suspend fun generate(request: ExtractionRequest): String = answers[normalise(request.utterance)] ?: fallback

    companion object {
        const val NO_OP = """{"tool":"update_report","arguments":{}}"""

        const val DECK_UTTERANCE =
            "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."

        private const val DECK_ANSWER =
            """{"tool":"update_report","arguments":{"incident_type":"water_leak_flood","location":"Conference Room 2",""" +
                """"date_time_text":"around 10 AM","immediate_actions":"isolated the area","evidence":{""" +
                """"incident_type":"water leak","location":"near Conference Room 2","date_time_text":"around 10 AM",""" +
                """"immediate_actions":"already isolated the area"}}}"""

        fun withDeckExample() = FakeLlmEngine(mapOf(DECK_UTTERANCE to DECK_ANSWER))

        private fun normalise(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}

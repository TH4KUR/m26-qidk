package com.megathon.incident.testing

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One recorded reply: used when the user text contains [match] (case-insensitive). */
@Serializable
data class ReplayEntry(val match: String, val reply: String)

/**
 * Replay mode: answers from recorded fixtures (JSON Lines of [ReplayEntry]) instead of a model.
 * Still [Compute.SIMULATED]: a replayed answer must never be shown as live recognition.
 */
class ReplayLlmEngine(
    private val entries: List<ReplayEntry>,
    override val name: String = "REPLAY",
    private val fallback: String = FakeLlmEngine.EMPTY_REPLY,
) : LlmEngine {
    override suspend fun complete(request: LlmRequest): LlmResult {
        val startedAt = System.nanoTime()
        val reply = entries.firstOrNull { request.user.contains(it.match, ignoreCase = true) }?.reply ?: fallback
        return LlmResult(reply, (System.nanoTime() - startedAt) / 1_000_000, Compute.SIMULATED)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJsonLines(text: String, name: String = "REPLAY"): ReplayLlmEngine =
            ReplayLlmEngine(
                text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { json.decodeFromString<ReplayEntry>(it) }.toList(),
                name,
            )
    }
}

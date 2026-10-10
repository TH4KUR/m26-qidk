package com.megathon.incident.llm

import com.megathon.incident.testing.Compute

/** One request to the on-device LLM. The reply must hold one `update_report` envelope (spec §4). */
data class LlmRequest(
    val system: String,
    val user: String,
    /** Optional GBNF grammar that constrains the output (GenieX `SamplerConfig.grammarString`). */
    val grammar: String? = null,
)

data class LlmResult(
    /** Raw model text. Parse it with [ToolCallParser]; never trust it as-is. */
    val text: String,
    val elapsedMs: Long,
    /** Where it really ran. Fakes report [Compute.SIMULATED]; the proof strip shows it. */
    val compute: Compute,
    /** Measured by the engine when it can: time to first token, prompt and generated token counts, decode speed. */
    val timings: LlmTimings? = null,
)

data class LlmTimings(
    val ttftMs: Double,
    val promptTokens: Int,
    val generatedTokens: Int,
    val prefillTokensPerSec: Double,
    val decodeTokensPerSec: Double,
)

/** Contract for every LLM backend: [FakeLlmEngine][com.megathon.incident.testing.FakeLlmEngine] now, GenieX later. */
interface LlmEngine {
    val name: String

    suspend fun complete(request: LlmRequest): LlmResult
}

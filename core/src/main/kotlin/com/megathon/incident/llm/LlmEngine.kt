package com.megathon.incident.llm

import com.megathon.incident.domain.IncidentReport

/**
 * Where a model actually ran. Every engine reports it, and the proof strip shows it. A scripted
 * result is [SIMULATED] and must never be shown as live recognition.
 */
enum class Compute { NPU, CPU, HYBRID, SIMULATED }

/** What an engine is asked. The prompt itself is the adapter's business. */
data class ExtractionRequest(
    val utterance: String,
    val transcriptSoFar: String,
    val report: IncidentReport,
)

/**
 * Turns an utterance into the raw text of one `update_report` call (spec section 4); the caller
 * parses it with [ToolCallParser]. Implemented by A (`GenieXLlmEngine`) and by the fake in
 * `testing/`. Both must pass the same contract suite.
 */
interface LlmEngine {
    val compute: Compute
    suspend fun generate(request: ExtractionRequest): String
}

/** Release builds call this at start-up so a scripted engine can never pass as a live one. */
fun LlmEngine.requireLive(): LlmEngine {
    check(compute != Compute.SIMULATED) { "a simulated LLM engine must not run in a release build" }
    return this
}

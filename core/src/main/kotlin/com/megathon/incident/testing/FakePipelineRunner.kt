package com.megathon.incident.testing

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.eval.Prediction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** What one pipeline run over one gold item produced (Judge Bench, on-device eval). */
data class PipelineOutput(
    val goldId: String,
    val report: IncidentReport,
    val asked: List<List<Field>>,
    val transcript: String,
    val latencyMs: Map<String, Long>,
    val compute: Compute,
    val capturedAt: String? = null,
) {
    fun toPrediction() = Prediction(goldId, report, asked, capturedAt, transcript, latencyMs, compute.name)
}

/** Replays a gold item (or a recorded clip) through a pipeline. Owner: E; the real one wraps A's session. */
interface PipelineRunner {
    val compute: Compute
    suspend fun run(goldId: String, turns: List<String>): PipelineOutput
}

/**
 * Reads recorded predictions (predictions.jsonl from the laptop contest or a kit run) instead of
 * running a pipeline, so the Judge Bench screen and the scorer can be built first. Always
 * [Compute.SIMULATED].
 */
class FakePipelineRunner(predictionsJsonl: String) : PipelineRunner {
    private val byId: Map<String, Prediction> = predictionsJsonl.lines().filter { it.isNotBlank() }
        .map { Prediction.fromJson(Json.parseToJsonElement(it).jsonObject) }
        .associateBy { it.goldId }

    override val compute = Compute.SIMULATED

    override suspend fun run(goldId: String, turns: List<String>): PipelineOutput {
        val p = requireNotNull(byId[goldId]) { "no recorded prediction for $goldId" }
        return PipelineOutput(p.goldId, p.report, p.asked.orEmpty(), p.transcript, p.latencyMs, compute, p.capturedAt)
    }
}

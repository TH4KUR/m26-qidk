package com.megathon.incident.speech

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult

/** Runs every request of [inner] through the shared [lane], so it takes turns with speech and the tag model on the accelerator. */
class LaneLlmEngine(private val inner: LlmEngine, private val lane: AcceleratorLane) : LlmEngine {
    override val name: String get() = inner.name

    override suspend fun complete(request: LlmRequest): LlmResult = lane.run { inner.complete(request) }
}

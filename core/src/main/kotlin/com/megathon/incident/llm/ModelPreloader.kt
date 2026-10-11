package com.megathon.incident.llm

import kotlin.coroutines.cancellation.CancellationException

/** Something that loads a model ahead of its first use. Returns the load time in ms, 0 when it was already loaded. */
fun interface Warmable {
    suspend fun warmUp(): Long
}

sealed interface PreloadOutcome {
    val name: String

    data class Loaded(override val name: String, val ms: Long) : PreloadOutcome

    /** [reason] is what the engine said; it is logged, never hidden, and the engine's next real call simply tries again. */
    data class Failed(override val name: String, val reason: String) : PreloadOutcome
}

/**
 * Loads models ahead of the first turn, one after the other (two models loading at once would fight over memory and the NPU
 * at launch). A failed load is reported and does not stop the next one, is attempted once per [run] (no retry loop), and
 * does not throw: the engine itself fails visibly on its first real call, so nothing is hidden by loading early.
 * Cancellation is not a failure and is passed on.
 */
class ModelPreloader {
    suspend fun run(steps: List<Pair<String, Warmable>>): List<PreloadOutcome> = steps.map { (name, warmable) ->
        try {
            PreloadOutcome.Loaded(name, warmable.warmUp())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PreloadOutcome.Failed(name, e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "the model did not load")
        }
    }
}

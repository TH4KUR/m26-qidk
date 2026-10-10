package com.megathon.incident.testing

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class ContractCheck(val name: String, val passed: Boolean, val detail: String = "")

data class ContractReport(val engineName: String, val checks: List<ContractCheck>) {
    val failures: List<ContractCheck> get() = checks.filterNot { it.passed }
    val passed: Boolean get() = failures.isEmpty()

    override fun toString(): String = buildString {
        appendLine("SpeechEngine contract for $engineName:")
        checks.forEach { appendLine("  ${if (it.passed) "PASS" else "FAIL"} ${it.name}${if (it.detail.isEmpty()) "" else " (${it.detail})"}") }
    }
}

/**
 * One contract for every [SpeechEngine]: the fake, WhisperKit, sherpa-onnx QNN and AI Hub all run it, so a fake
 * cannot drift from a real engine. It returns a report instead of asserting, so `:app`'s device tests can use it
 * without extra build setup. Pass a real speech clip: an engine that hears nothing in speech fails.
 */
object SpeechEngineContract {
    /** Real engines must not report SIMULATED; fakes pass `setOf(Compute.SIMULATED)`. */
    val LIVE_COMPUTE: Set<Compute> = setOf(Compute.NPU, Compute.CPU, Compute.HYBRID)

    private const val BLOCKING_PROBE_MIN_MS = 150L
    private const val TICK_MS = 10L
    private const val TICKS_PER_BLOCKED_MS = 50L

    suspend fun verify(
        engine: SpeechEngine,
        speech: ShortArray,
        allowedCompute: Set<Compute> = LIVE_COMPUTE,
        languages: List<String> = listOf("en", "hi"),
    ): ContractReport {
        val checks = mutableListOf<ContractCheck>()

        /** [body] returns null when the check passes, or the reason it failed. */
        suspend fun check(name: String, body: suspend () -> String?) {
            checks += try {
                body()?.let { ContractCheck(name, false, it) } ?: ContractCheck(name, true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                ContractCheck(name, false, "${error::class.simpleName}: ${error.message}")
            }
        }

        check("speech sample gives non-empty text") {
            if (engine.transcribe(speech, "en").text.isBlank()) "blank text for a speech sample" else null
        }
        check("silence gives blank text") {
            val text = engine.transcribe(ShortArray(16_000), "en").text
            if (text.isNotBlank()) "heard \"$text\" in silence" else null
        }
        check("empty input gives blank text") {
            val text = engine.transcribe(ShortArray(0), "en").text
            if (text.isNotBlank()) "heard \"$text\" in empty input" else null
        }
        check("echoes the requested language") {
            languages.firstNotNullOfOrNull { language ->
                engine.transcribe(speech, language).language.takeIf { it != language }?.let { "asked for $language, got $it" }
            }
        }

        var probe: Probe? = null
        check("does not block the calling thread") {
            probe = probeCaller(engine, speech)
            val ticksNeeded = probe!!.wallMs / TICKS_PER_BLOCKED_MS
            if (probe!!.wallMs >= BLOCKING_PROBE_MIN_MS && probe!!.ticks < ticksNeeded) {
                "the caller ran ${probe!!.ticks} times in ${probe!!.wallMs} ms; transcribe() must suspend or switch dispatcher"
            } else {
                null
            }
        }
        check("elapsedMs is plausible") {
            val seen = probe ?: return@check "no transcript to inspect"
            val elapsed = seen.transcript.elapsedMs
            if (elapsed < 0 || elapsed > seen.wallMs + 50) "elapsedMs=$elapsed but the call took ${seen.wallMs} ms" else null
        }
        check("compute label is one of the allowed") {
            val seen = probe ?: return@check "no transcript to inspect"
            if (seen.transcript.compute !in allowedCompute) "reported ${seen.transcript.compute}, allowed $allowedCompute" else null
        }

        return ContractReport(engine.name, checks)
    }

    private class Probe(val transcript: Transcript, val wallMs: Long, val ticks: Int)

    /**
     * Calls the engine from a coroutine on a single thread (standing in for the main thread) while a ticker on
     * the same thread counts. An engine that blocks the thread starves the ticker.
     */
    private suspend fun probeCaller(engine: SpeechEngine, speech: ShortArray): Probe {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "contract-caller").apply { isDaemon = true } }
        try {
            return withContext(executor.asCoroutineDispatcher()) {
                val ticks = AtomicInteger()
                val ticker = launch {
                    while (isActive) {
                        ticks.incrementAndGet()
                        delay(TICK_MS)
                    }
                }
                val startedAt = System.nanoTime()
                val transcript = engine.transcribe(speech, "en")
                val wallMs = (System.nanoTime() - startedAt) / 1_000_000
                val seenTicks = ticks.get()
                ticker.cancelAndJoin()
                Probe(transcript, wallMs, seenTicks)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}

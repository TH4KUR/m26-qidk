package com.megathon.incident.testing

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Deterministic speech engine for previews, tests, and the early integration build.
 * It is deliberately marked SIMULATED so it cannot be mistaken for live recognition.
 */
class FakeSpeechEngine(
    override val name: String = "FAKE",
    private val scriptedTexts: Map<String, String> = emptyMap(),
    private val clipId: (ShortArray) -> String = { "default" },
    private val defaultText: String = "",
    private val delayMs: Long = 0,
    private val failure: Throwable? = null,
) : SpeechEngine {
    private val requestedLanguages = CopyOnWriteArrayList<String>()

    val languageRequests: List<String>
        get() = requestedLanguages.toList()

    override suspend fun transcribe(pcm: ShortArray, language: String): Transcript {
        require(delayMs >= 0) { "delayMs must not be negative" }
        val startedAt = System.nanoTime()
        requestedLanguages += language

        if (delayMs > 0) {
            delay(delayMs)
        }
        failure?.let { throw it }

        val text = if (pcm.isEmpty() || pcm.all { it == 0.toShort() }) {
            ""
        } else {
            scriptedTexts[clipId(pcm)] ?: defaultText
        }

        return Transcript(
            text = text,
            language = language,
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            compute = Compute.SIMULATED,
        )
    }
}

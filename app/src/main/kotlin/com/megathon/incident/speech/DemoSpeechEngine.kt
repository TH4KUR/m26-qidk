package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Transcript

/**
 * Scripted stand-in for speech recognition, for building and showing the screens before a real engine is wired.
 * It ignores what was said and returns the next line of the 3-minute demo, always labelled [Compute.SIMULATED]
 * (the proof sheet and a banner say so). Silence still comes back empty, so the "didn't catch that" path is real.
 */
class DemoSpeechEngine : SpeechEngine {
    override val name = "DEMO SCRIPT"
    private var next = 0

    private val script = listOf(
        FakeLlmEngine.DECK_TRANSCRIPT,
        "Minor, nobody was hurt.",
        "Actually it was Conference Room 3.",
    )

    fun restart() { next = 0 }

    override suspend fun transcribe(pcm: ShortArray, language: String): Transcript {
        val started = System.nanoTime()
        val heard = pcm.size >= MIN_SAMPLES && pcm.any { it.toInt() != 0 }
        val text = if (heard) script.getOrNull(next++).orEmpty() else ""
        return Transcript(text, language, (System.nanoTime() - started) / 1_000_000, Compute.SIMULATED)
    }

    private companion object {
        const val MIN_SAMPLES = 4_800 // 0.3 s at 16 kHz
    }
}

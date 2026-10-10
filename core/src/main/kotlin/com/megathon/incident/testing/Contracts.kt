package com.megathon.incident.testing

import com.megathon.incident.domain.Signal

enum class Compute {
    NPU,
    CPU,
    HYBRID,
    SIMULATED,
}

data class Transcript(
    val text: String,
    val language: String,
    val elapsedMs: Long,
    val compute: Compute,
)

interface SpeechEngine {
    val name: String

    suspend fun transcribe(pcm: ShortArray, language: String): Transcript
}

interface AudioSource {
    suspend fun readPcm(): ShortArray
}

/**
 * Produces signals (sound events, emotion) from raw audio. A source runs on one kind of compute, and
 * says which: `Signal` itself has no compute field, so the proof strip reads it from the source.
 */
interface SignalSource {
    val name: String
    val compute: Compute

    suspend fun signals(pcm: ShortArray): List<Signal>
}

interface Speaker {
    suspend fun speak(text: String)
}

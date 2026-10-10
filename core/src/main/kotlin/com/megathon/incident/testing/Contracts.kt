package com.megathon.incident.testing

import com.megathon.incident.Signal

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

interface SignalSource {
    suspend fun signals(pcm: ShortArray): List<Signal>
}

interface Speaker {
    suspend fun speak(text: String)
}

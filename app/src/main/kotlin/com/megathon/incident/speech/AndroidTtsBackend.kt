package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import com.megathon.incident.tts.TtsBackend

/**
 * The Android system voice as a [TtsBackend]. It speaks exactly what [SystemTtsSpeaker] always spoke (the language
 * argument is not used: the system voice is chosen once, as before), and runs on the CPU.
 */
class AndroidTtsBackend(private val speaker: SystemTtsSpeaker) : TtsBackend {
    override val compute = Compute.CPU
    override val offline: Boolean? get() = speaker.offline

    override fun label(language: String) = LABEL

    override suspend fun unavailableReason(language: String): String? = null

    override suspend fun speak(text: String, language: String) = speaker.speak(text)

    override fun stop() = speaker.stop()

    override fun shutdown() = speaker.shutdown()

    companion object {
        const val LABEL = "Android TTS (system voice)"
    }
}

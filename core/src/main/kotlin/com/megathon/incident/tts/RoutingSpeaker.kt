package com.megathon.incident.tts

import com.megathon.incident.session.StageRecord
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.Speaker
import kotlinx.coroutines.CancellationException

/**
 * A speech-output engine that can say which engine it is. Implementations: the Android system voice and Piper
 * (both in `:app`). Languages are the session language codes (`en`, `hi`, `te`).
 */
interface TtsBackend {
    /** What the engine runs on. Piper and the system voice both run on the CPU; no TTS engine here uses the NPU. */
    val compute: Compute

    /** True when the voice works with no network, false when that was not confirmed, null when unknown. */
    val offline: Boolean?

    /** Name shown on the proof strip for the voice used for [language]. */
    fun label(language: String): String

    /** Null when this engine can speak [language] right now, else a plain reason (voice files missing, library failed). */
    suspend fun unavailableReason(language: String): String?

    /** Speaks and returns only when the audio has finished (or [stop] was called). Throws [TtsBackendException] on failure. */
    suspend fun speak(text: String, language: String)

    fun stop()

    fun shutdown()
}

/**
 * The engine could not speak. [permanent] means retrying is pointless (the native library failed to load);
 * [spokeSomething] means some audio was already played, so speaking the text again with another voice would repeat it.
 */
class TtsBackendException(
    message: String,
    val permanent: Boolean = false,
    val spokeSomething: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

/** What actually spoke the last utterance, for the proof strip. */
data class SpokenWith(
    val engine: String,
    val compute: Compute,
    val offline: Boolean?,
    /** Why the preferred engine was not used for this utterance; null when it was (or none was preferred). */
    val fallbackReason: String? = null,
) {
    /** The proof-strip record for the "Speak" step: the engine that really spoke and what it ran on. */
    fun toStage(ms: Long?) = StageRecord("Speak", engine, compute, ms)

    /** One plain sentence for the proof sheet when the preferred (Piper) voice was not the one heard; null otherwise. */
    fun note(): String? = fallbackReason?.let { "The Piper voice was not used: $it. $engine spoke instead." }
}

/**
 * Speaks with [primary] when it can, otherwise with [fallback], and records which one really spoke in [last].
 * The label always names the engine that produced the audio, never the one that was asked for.
 */
class RoutingSpeaker(
    private val primary: TtsBackend?,
    private val fallback: TtsBackend,
    private val language: () -> String = { "en" },
) : Speaker {
    @Volatile
    var last: SpokenWith? = null
        private set

    @Volatile
    private var primaryDisabled: String? = null

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        val lang = language()
        var reason: String? = null
        if (primary != null) {
            reason = primaryDisabled ?: primary.unavailableReason(lang)
            if (reason == null) {
                try {
                    primary.speak(text, lang)
                    last = SpokenWith(primary.label(lang), primary.compute, primary.offline)
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    reason = failure.message ?: failure.javaClass.simpleName
                    val permanent = (failure as? TtsBackendException)?.permanent ?: (failure is LinkageError)
                    if (permanent) primaryDisabled = reason
                    if ((failure as? TtsBackendException)?.spokeSomething == true) {
                        last = SpokenWith(primary.label(lang), primary.compute, primary.offline, "stopped part way: $reason")
                        return
                    }
                }
            }
        }
        fallback.speak(text, lang)
        last = SpokenWith(fallback.label(lang), fallback.compute, fallback.offline, reason)
    }

    fun stop() {
        primary?.stop()
        fallback.stop()
    }

    fun shutdown() {
        primary?.shutdown()
        fallback.shutdown()
    }
}

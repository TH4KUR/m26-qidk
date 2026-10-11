package com.megathon.incident.engine

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.testing.FakeSpeechEngine
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.tts.TtsBackend
import com.megathon.incident.tts.TtsKind

enum class SpeechKind { FAKE, WHISPERKIT, SHERPA_QNN, AIHUB }

enum class LlmKind { FAKE, GENIEX }

/**
 * Which engines to use. Default is FAKE until the real engine passes its contract test (handoff section 8).
 * [tts] is the speech-output engine: the system voice unless Piper is asked for (debug switch `--es tts PIPER`).
 * [language] is the raw language code asked for with `--es lang` (`en` by default; `hi` is the experimental Hindi lane).
 * It is resolved to a lane by `com.megathon.incident.speech.SpeechLanes.select`, which falls back to English.
 */
data class EngineSelection(
    val speech: SpeechKind = SpeechKind.FAKE,
    val llm: LlmKind = LlmKind.FAKE,
    val tts: TtsKind = TtsKind.ANDROID,
    /** Which of the neural voice's preset styles to use (`--es voice N`); only Supertonic has more than one. */
    val ttsVoice: Int = 0,
    val language: String = "en",
)

/** A release build was asked to run a simulated engine. A scripted result must never look like live recognition. */
class SimulatedEngineInReleaseException(message: String) : IllegalStateException(message)

/** The requested engine is not wired yet (its task has not landed). */
class EngineUnavailableException(message: String) : IllegalStateException(message)

/**
 * Single place that picks engines. Pure Kotlin so the release guard is unit-tested on the JVM;
 * `:app` only registers the real providers (GenieX, WhisperKit, sherpa-onnx) as they land.
 */
class EngineFactory(
    private val isDebug: Boolean,
    private val speechProviders: Map<SpeechKind, () -> SpeechEngine> = mapOf(SpeechKind.FAKE to { FakeSpeechEngine() }),
    private val llmProviders: Map<LlmKind, () -> LlmEngine> = mapOf(LlmKind.FAKE to { FakeLlmEngine() }),
    private val ttsProviders: Map<TtsKind, () -> TtsBackend> = emptyMap(),
) {
    fun speech(kind: SpeechKind): SpeechEngine {
        refuseSimulatedInRelease(kind == SpeechKind.FAKE, "speech engine FAKE")
        val provider = speechProviders[kind] ?: throw EngineUnavailableException("speech engine $kind is not wired yet")
        return provider()
    }

    fun llm(kind: LlmKind): LlmEngine {
        refuseSimulatedInRelease(kind == LlmKind.FAKE, "LLM engine FAKE")
        val provider = llmProviders[kind] ?: throw EngineUnavailableException("LLM engine $kind is not wired yet")
        return provider()
    }

    /** A speech-output engine. Neither kind is simulated, so there is no release guard; an unwired kind is refused. */
    fun tts(kind: TtsKind): TtsBackend {
        val provider = ttsProviders[kind] ?: throw EngineUnavailableException("speech output $kind is not wired yet")
        return provider()
    }

    /** Second line of defence: a result that says SIMULATED is refused in release builds, whatever engine made it. */
    fun checkResult(compute: Compute) = refuseSimulatedInRelease(compute == Compute.SIMULATED, "a SIMULATED result")

    private fun refuseSimulatedInRelease(simulated: Boolean, what: String) {
        if (simulated && !isDebug) throw SimulatedEngineInReleaseException("Refusing $what in a release build")
    }
}

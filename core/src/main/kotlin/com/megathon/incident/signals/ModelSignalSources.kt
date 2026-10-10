package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SignalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs YamNet on one 96 x 64 log-mel patch and returns its raw logits, one per class. The device runner (ONNX Runtime) lives in `:app`. */
fun interface YamNetModel {
    fun logits(patch: FloatArray): FloatArray
}

/**
 * YamNet as a [SignalSource]: PCM -> log-mel patches ([YamNetFrontEnd]) -> the model, one patch at a time ->
 * sigmoid and the strongest window per class -> [YamNetSignalMapper]. [compute] is where the model really runs,
 * and the caller must say it truthfully. The work runs on `Dispatchers.Default`, never on the caller's thread.
 */
class YamNetSignalSource(
    private val model: YamNetModel,
    private val classNames: List<String>,
    override val compute: Compute,
) : SignalSource {
    override val name: String = "YamNet"

    override suspend fun signals(pcm: ShortArray): List<Signal> {
        if (pcm.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            val samples = FloatArray(pcm.size) { pcm[it] / PCM_SCALE }
            val logits = YamNetFrontEnd.patches(samples).map { model.logits(it) }
            YamNetSignalMapper.map(YamNetFrontEnd.classScores(logits, classNames))
        }
    }

    private companion object {
        const val PCM_SCALE = 32768f
    }
}

/** What sherpa-onnx's SenseVoice returns for an utterance; `emotion` and `event` are raw tags such as `<|ANGRY|>` and `<|Cry|>`. */
data class SenseVoiceResult(val text: String, val lang: String, val emotion: String, val event: String)

/** Runs SenseVoice on an utterance. The device runner (sherpa-onnx, QNN or CPU) lives in `:app`. */
fun interface SenseVoiceRecognizer {
    fun recognize(pcm: ShortArray): SenseVoiceResult
}

/**
 * SenseVoice as a [SignalSource]. Its emotion is mostly `<|EMO_UNKNOWN|>` (132 of 140 acted clips in our probe), which
 * the mapper drops, so an empty list is the normal answer and urgency must never depend on this source.
 */
class SenseVoiceSignalSource(
    private val recognizer: SenseVoiceRecognizer,
    override val compute: Compute,
) : SignalSource {
    override val name: String = "SenseVoice"

    override suspend fun signals(pcm: ShortArray): List<Signal> {
        if (pcm.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            val result = recognizer.recognize(pcm)
            SenseVoiceSignalMapper.map(result.emotion, result.event)
        }
    }
}

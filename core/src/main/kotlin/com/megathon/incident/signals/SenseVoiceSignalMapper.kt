package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source

/**
 * Turns SenseVoice's emotion and event tags (sherpa-onnx returns them as `<|ANGRY|>`, `<|Cry|>` ...) into
 * `Signal`s. SenseVoice has no confidence value and no Hindi, so these are suggestions with a source label,
 * never facts. An unknown emotion is no signal at all rather than a guess.
 */
object SenseVoiceSignalMapper {
    private val EMOTIONS = setOf("happy", "sad", "angry", "neutral", "fearful")

    private val EVENTS = mapOf(
        "cry" to "crying",
        "cough" to "coughing",
        "sneeze" to "sneezing",
        "laughter" to "laughter",
        "breath" to "breathing",
    )

    fun map(emotion: String?, event: String?, segmentId: Long? = null): List<Signal> = buildList {
        tag(emotion).takeIf { it in EMOTIONS }?.let { add(Signal("emotion", it, Source.SENSEVOICE, segmentId)) }
        EVENTS[tag(event)]?.let { add(Signal("event", it, Source.SENSEVOICE, segmentId)) }
    }

    private fun tag(raw: String?): String = raw.orEmpty().trim().removePrefix("<|").removeSuffix("|>").trim().lowercase()
}

package com.megathon.incident.speech

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class VadEvent {
    NONE,
    SPEECH_STARTED,

    /** Quiet lasted the whole hangover after enough speech: the utterance is complete. */
    SPEECH_ENDED,

    /** The burst was shorter than the minimum (a click, a cough): drop it, do not transcribe it. */
    SPEECH_DISCARDED,

    /** The utterance reached the maximum length (Whisper's 30 s window); speech may continue. */
    MAX_LENGTH,
}

/**
 * Energy end-of-speech detector (CPU, no model). Feed it consecutive PCM16 chunks of any size; it
 * reports when speech starts and when it has been quiet for [Config.hangoverMs]. The threshold
 * follows the room: a steady fan raises the noise floor, so only speech clearly above it counts.
 */
class EndOfSpeechDetector(private val config: Config = Config()) {
    data class Config(
        val sampleRate: Int = 16_000,
        val hangoverMs: Int = 700,
        val minSpeechMs: Int = 240,
        val maxUtteranceMs: Int = 30_000,
        /** Absolute floor for the speech threshold (RMS of PCM16). */
        val minRms: Double = 350.0,
        /** Speech must be this many times louder than the tracked room noise. */
        val noiseRatio: Double = 3.0,
        /** At the start the room is learned for this long (a fan that is already on is not speech). */
        val calibrationMs: Int = 300,
        /** A frame louder than this is speech, never room noise, even during calibration. */
        val maxCalibrationRms: Double = 1500.0,
    )

    private var noiseFloor = 0.0
    private var haveNoise = false
    private var calibratedMs = 0.0
    private var inSpeech = false
    private var elapsedMs = 0.0
    private var voicedMs = 0.0

    /** Quiet at the end of the last utterance; valid until the next utterance starts. */
    var trailingQuietMs = 0.0
        private set

    fun accept(chunk: ShortArray): VadEvent {
        if (chunk.isEmpty()) return VadEvent.NONE
        val ms = chunk.size * 1000.0 / config.sampleRate
        val rms = rms(chunk)

        if (!inSpeech && calibratedMs < config.calibrationMs && rms < config.maxCalibrationRms) {
            noiseFloor = if (haveNoise) min(noiseFloor, rms) else rms
            haveNoise = true
            calibratedMs += ms
            return VadEvent.NONE
        }

        val loud = rms >= max(config.minRms, noiseFloor * config.noiseRatio)

        if (!inSpeech) {
            if (!loud) {
                trackNoise(rms)
                return VadEvent.NONE
            }
            inSpeech = true
            elapsedMs = ms
            voicedMs = ms
            trailingQuietMs = 0.0
            return VadEvent.SPEECH_STARTED
        }

        elapsedMs += ms
        if (loud) {
            voicedMs += ms
            trailingQuietMs = 0.0
        } else {
            trailingQuietMs += ms
        }

        if (elapsedMs >= config.maxUtteranceMs) {
            endUtterance()
            return VadEvent.MAX_LENGTH
        }
        if (trailingQuietMs >= config.hangoverMs) {
            val longEnough = voicedMs >= config.minSpeechMs
            endUtterance()
            return if (longEnough) VadEvent.SPEECH_ENDED else VadEvent.SPEECH_DISCARDED
        }
        return VadEvent.NONE
    }

    /** Forget an unfinished utterance (the mic was closed). The room-noise estimate is kept. */
    fun reset() {
        endUtterance()
        trailingQuietMs = 0.0
    }

    private fun endUtterance() {
        inSpeech = false
        elapsedMs = 0.0
        voicedMs = 0.0
    }

    private fun trackNoise(rms: Double) {
        noiseFloor = when {
            !haveNoise -> rms
            rms < noiseFloor -> 0.8 * noiseFloor + 0.2 * rms // follow a quieter room quickly
            else -> 0.98 * noiseFloor + 0.02 * rms // and a louder one slowly
        }
        haveNoise = true
    }

    private fun rms(chunk: ShortArray): Double {
        var sum = 0.0
        for (sample in chunk) sum += sample.toDouble() * sample
        return sqrt(sum / chunk.size)
    }
}

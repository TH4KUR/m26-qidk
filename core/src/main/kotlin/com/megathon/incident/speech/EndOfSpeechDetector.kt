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
        /**
         * Absolute floor for the speech threshold (RMS of PCM16, about -49 dBFS). The kit's mic is quiet: measured
         * 2026-10-10, room floor about -45 dBFS and speech only about 10 dB above it, so these are low on purpose.
         */
        val minRms: Double = 120.0,
        /** Speech must be this many times louder (6 dB) than the tracked room noise. Starting values, tune on kit clips. */
        val noiseRatio: Double = 2.0,
        /** At the start the room is learned for this long (a fan that is already on is not speech). */
        val calibrationMs: Int = 300,
        /** A frame louder than this is speech, never room noise, even during calibration. */
        val maxCalibrationRms: Double = 1500.0,
        /**
         * If the signal never drops below the speech threshold for this long, it is a room that got louder (a fan or
         * AC switched on), not speech without a breath: the floor is raised to the quietest level seen in that stretch.
         */
        val noGapRebaselineMs: Int = 6_000,
    )

    private var noiseFloor = 0.0
    private var haveNoise = false
    private var calibratedMs = 0.0
    private var inSpeech = false
    private var elapsedMs = 0.0
    private var voicedMs = 0.0
    private var noGapMs = 0.0
    private var noGapMinRms = Double.MAX_VALUE

    /** Loud time so far in the current utterance (0 when none): what the minimum-speech rule is judged on. */
    val currentVoicedMs: Double get() = if (inSpeech) voicedMs else 0.0

    /** Whether the most recent frame was speech (louder than the room), also inside an utterance, so a pause shows up as quiet. */
    var lastFrameLoud = false
        private set

    /** Quiet at the end of the last utterance; valid until the next utterance starts. */
    var trailingQuietMs = 0.0
        private set

    fun accept(chunk: ShortArray): VadEvent {
        lastFrameLoud = false
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
        lastFrameLoud = loud

        if (!inSpeech) {
            if (!loud) {
                trackNoise(rms)
                return VadEvent.NONE
            }
            inSpeech = true
            elapsedMs = ms
            voicedMs = ms
            trailingQuietMs = 0.0
            noGapMs = ms
            noGapMinRms = rms
            return VadEvent.SPEECH_STARTED
        }

        elapsedMs += ms
        if (loud) {
            voicedMs += ms
            trailingQuietMs = 0.0
            noGapMs += ms
            noGapMinRms = min(noGapMinRms, rms)
            if (noGapMs >= config.noGapRebaselineMs) {
                noiseFloor = max(noiseFloor, noGapMinRms)
                noGapMs = 0.0
                noGapMinRms = Double.MAX_VALUE
            }
        } else {
            trailingQuietMs += ms
            noGapMs = 0.0
            noGapMinRms = Double.MAX_VALUE
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
        noGapMs = 0.0
        noGapMinRms = Double.MAX_VALUE
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

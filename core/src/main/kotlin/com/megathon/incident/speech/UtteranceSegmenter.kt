package com.megathon.incident.speech

/**
 * Turns a stream of mic chunks into whole utterances for a [com.megathon.incident.testing.SpeechEngine].
 * Each utterance carries a little audio from before the first loud sample (so a soft first word is not
 * clipped) and at most [tailMs] of the quiet that ended it.
 */
class UtteranceSegmenter(
    config: EndOfSpeechDetector.Config = EndOfSpeechDetector.Config(),
    private val preRollMs: Int = 300,
    private val tailMs: Int = 200,
) {
    private val detector = EndOfSpeechDetector(config)
    private val sampleRate = config.sampleRate
    private val preRoll = ArrayDeque<ShortArray>()
    private var preRollSamples = 0
    private val utterance = ArrayList<ShortArray>()
    private var inUtterance = false

    /** Returns the finished utterance's PCM, or null while nothing is complete. */
    fun push(chunk: ShortArray): ShortArray? {
        when (detector.accept(chunk)) {
            VadEvent.NONE -> if (inUtterance) utterance += chunk else addPreRoll(chunk)
            VadEvent.SPEECH_STARTED -> {
                inUtterance = true
                utterance += preRoll
                preRoll.clear()
                preRollSamples = 0
                utterance += chunk
            }
            VadEvent.SPEECH_ENDED, VadEvent.MAX_LENGTH -> {
                utterance += chunk
                return take()
            }
            VadEvent.SPEECH_DISCARDED -> {
                utterance.clear()
                inUtterance = false
            }
        }
        return null
    }

    /** The user tapped stop: return what has been heard so far, or null if there was no speech. */
    fun flush(): ShortArray? {
        val out = if (inUtterance) take() else null
        detector.reset()
        preRoll.clear()
        preRollSamples = 0
        return out
    }

    private fun take(): ShortArray {
        val all = ShortArray(utterance.sumOf { it.size })
        var at = 0
        for (part in utterance) {
            part.copyInto(all, at)
            at += part.size
        }
        utterance.clear()
        inUtterance = false
        val trimSamples = (((detector.trailingQuietMs - tailMs).coerceAtLeast(0.0)) * sampleRate / 1000).toInt()
        return all.copyOf((all.size - trimSamples).coerceAtLeast(0))
    }

    private fun addPreRoll(chunk: ShortArray) {
        preRoll += chunk
        preRollSamples += chunk.size
        val limit = preRollMs * sampleRate / 1000
        while (preRoll.size > 1 && preRollSamples - preRoll.first().size >= limit) {
            preRollSamples -= preRoll.removeFirst().size
        }
    }
}

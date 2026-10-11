package com.megathon.incident.speech

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EndOfSpeechDetectorLoudFrameTest {
    private val rate = 16_000
    private fun tone(ms: Int) = ShortArray(rate * ms / 1000) { (4000 * sin(2 * PI * 220.0 * it / rate)).toInt().toShort() }
    private fun quiet(ms: Int) = ShortArray(rate * ms / 1000) { if (it % 2 == 0) 20 else -20 }

    @Test
    fun theLastFrameIsReportedLoudOnlyWhenItWasSpeech() {
        val d = EndOfSpeechDetector()
        d.accept(quiet(400))
        assertFalse(d.lastFrameLoud)
        d.accept(tone(100))
        assertTrue(d.lastFrameLoud)
        d.accept(quiet(100))
        assertFalse(d.lastFrameLoud, "a pause inside an utterance is not speech")
    }
}

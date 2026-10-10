package com.megathon.incident.speech

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtteranceSegmenterTest {
    private val rate = 16_000

    private fun tone(ms: Int, amplitude: Int = 4000): ShortArray =
        ShortArray(rate * ms / 1000) { (amplitude * sin(2 * PI * 220.0 * it / rate)).toInt().toShort() }

    private fun silence(ms: Int) = ShortArray(rate * ms / 1000)

    private fun push(segmenter: UtteranceSegmenter, pcm: ShortArray): List<ShortArray> =
        pcm.toList().chunked(320).mapNotNull { segmenter.push(it.toShortArray()) }

    @Test
    fun nothingIsEmittedForSilence() {
        assertTrue(push(UtteranceSegmenter(), silence(3000)).isEmpty())
    }

    @Test
    fun theUtteranceComesOutOnceWithPreRollAndTrimmedTail() {
        val segmenter = UtteranceSegmenter(preRollMs = 200, tailMs = 200)
        val out = push(segmenter, silence(1000) + tone(1000) + silence(1500))

        assertEquals(1, out.size)
        val ms = out.single().size * 1000 / rate
        // 1000 ms of speech plus at most 200 ms before it and 200 ms after it, never the full 700 ms hangover.
        assertTrue(ms in 1000..1500, "utterance was $ms ms")
        assertTrue(out.single().any { it.toInt() != 0 })
    }

    @Test
    fun twoUtterancesComeOutAsTwoSegments() {
        val segmenter = UtteranceSegmenter()
        val out = push(segmenter, tone(800) + silence(1200) + tone(800) + silence(1200))
        assertEquals(2, out.size)
    }

    @Test
    fun flushReturnsTheUnfinishedUtteranceWhenTheUserTapsStop() {
        val segmenter = UtteranceSegmenter()
        assertTrue(push(segmenter, tone(900)).isEmpty())
        val flushed = assertNotNull(segmenter.flush())
        assertTrue(flushed.size >= rate * 900 / 1000)
        assertNull(segmenter.flush(), "flush must empty the buffer")
    }

    @Test
    fun aMaxLengthCutStillEmitsTheAudioSoFar() {
        val segmenter = UtteranceSegmenter(EndOfSpeechDetector.Config(maxUtteranceMs = 2000))
        val out = push(segmenter, tone(2600))
        assertEquals(1, out.size)
        assertTrue(out.single().size * 1000 / rate in 1900..2400)
    }
}

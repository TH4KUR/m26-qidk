package com.megathon.incident.speech

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndOfSpeechDetectorTest {
    private val rate = 16_000
    private val frame = 320 // 20 ms

    /** A steady tone standing in for voiced speech (RMS is amplitude / sqrt(2)). */
    private fun tone(ms: Int, amplitude: Int): ShortArray =
        ShortArray(rate * ms / 1000) { (amplitude * sin(2 * PI * 220.0 * it / rate)).toInt().toShort() }

    private fun silence(ms: Int) = ShortArray(rate * ms / 1000)

    private fun frames(pcm: ShortArray): List<ShortArray> = pcm.toList().chunked(frame).map { it.toShortArray() }

    private fun feed(detector: EndOfSpeechDetector, pcm: ShortArray): List<Pair<Int, VadEvent>> =
        frames(pcm).mapIndexedNotNull { index, chunk ->
            detector.accept(chunk).takeIf { it != VadEvent.NONE }?.let { index * 20 to it }
        }

    @Test
    fun silenceNeverStartsSpeech() {
        val events = feed(EndOfSpeechDetector(), silence(3000))
        assertTrue(events.isEmpty(), "events: $events")
    }

    @Test
    fun speechStartsThenEndsOnlyAfterTheHangover() {
        val detector = EndOfSpeechDetector()
        val events = feed(detector, silence(400) + tone(1000, 4000) + silence(1500))

        assertEquals(listOf(VadEvent.SPEECH_STARTED, VadEvent.SPEECH_ENDED), events.map { it.second })
        val endedAtMs = events.last().first
        val speechStopsAtMs = 400 + 1000
        // The end must not fire before 700 ms of quiet, and must fire soon after (one frame of slack).
        assertTrue(endedAtMs >= speechStopsAtMs + 700 - 20, "ended too early at $endedAtMs ms")
        assertTrue(endedAtMs <= speechStopsAtMs + 700 + 40, "ended too late at $endedAtMs ms")
    }

    @Test
    fun aShortPauseInsideSpeechDoesNotEndTheUtterance() {
        val events = feed(EndOfSpeechDetector(), tone(600, 4000) + silence(500) + tone(600, 4000) + silence(1500))
        assertEquals(listOf(VadEvent.SPEECH_STARTED, VadEvent.SPEECH_ENDED), events.map { it.second })
    }

    @Test
    fun steadyFanNoiseDoesNotTriggerButSpeechOverItDoes() {
        // Fan noise at amplitude 300 (RMS about 212) is the room; speech at 4000 is far above it.
        val room = tone(2000, 300)
        val detector = EndOfSpeechDetector()
        assertTrue(feed(detector, room).isEmpty(), "fan noise must not count as speech")

        val events = feed(detector, tone(800, 4000) + tone(1500, 300))
        assertEquals(listOf(VadEvent.SPEECH_STARTED, VadEvent.SPEECH_ENDED), events.map { it.second })
    }

    @Test
    fun aLoudFanThatIsOnFromTheFirstFrameIsLearnedAsTheRoom() {
        // Amplitude 800 (RMS about 566) is above the absolute floor, so only calibration can tell it is a fan.
        val detector = EndOfSpeechDetector()
        assertTrue(feed(detector, tone(2000, 800)).isEmpty(), "a fan on from the start must not start an utterance")

        val events = feed(detector, tone(800, 6000) + tone(1500, 800))
        assertEquals(listOf(VadEvent.SPEECH_STARTED, VadEvent.SPEECH_ENDED), events.map { it.second })
    }

    @Test
    fun theKitsQuietMicStillGetsItsSpeechDetected() {
        // Measured on the kit (2026-10-10): floor about -45 dBFS (RMS ~184, amplitude ~260) and speech only about
        // 10 dB above it (RMS ~490, amplitude ~700). Speech that quiet must still count, and the floor alone must not.
        val detector = EndOfSpeechDetector()
        assertTrue(feed(detector, tone(2000, 260)).isEmpty(), "the quiet room alone must not count as speech")

        val events = feed(detector, tone(900, 700) + tone(1500, 260))
        assertEquals(listOf(VadEvent.SPEECH_STARTED, VadEvent.SPEECH_ENDED), events.map { it.second })
    }

    @Test
    fun aBlipShorterThanTheMinimumIsDiscardedNotEnded() {
        // A 60 ms click is not an utterance; sending it to the engine would waste the NPU.
        val events = feed(EndOfSpeechDetector(), silence(300) + tone(60, 6000) + silence(1500))
        assertEquals(listOf(VadEvent.SPEECH_STARTED, VadEvent.SPEECH_DISCARDED), events.map { it.second })
    }

    @Test
    fun anUtteranceIsCutAtTheMaximumLengthAndSpeechContinuesAsANewOne() {
        val config = EndOfSpeechDetector.Config(maxUtteranceMs = 2000)
        val events = feed(EndOfSpeechDetector(config), tone(2500, 4000))
        // A long narration must not lose audio: the cut is followed straight away by the next utterance.
        assertEquals(
            listOf(VadEvent.SPEECH_STARTED, VadEvent.MAX_LENGTH, VadEvent.SPEECH_STARTED),
            events.map { it.second },
        )
        assertTrue(events[1].first in 1980..2040, "cut at ${events[1].first} ms")
    }

    @Test
    fun resetForgetsAnUnfinishedUtterance() {
        val detector = EndOfSpeechDetector()
        feed(detector, tone(500, 4000))
        detector.reset()
        // After a reset (mic reopened after TTS) quiet must not produce a stale SPEECH_ENDED.
        assertTrue(feed(detector, silence(2000)).isEmpty())
    }
}

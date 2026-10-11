package com.megathon.incident.scoring

import com.megathon.incident.testing.Compute
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioScoreTest {
    private val rate = 16_000

    /** A 220 Hz sine whose peak is [peakDbfs], [seconds] long. */
    private fun tone(peakDbfs: Double, seconds: Double): ShortArray {
        val amp = 32768.0 * 10.0.pow(peakDbfs / 20.0)
        return ShortArray((seconds * rate).toInt()) { (amp * sin(2 * PI * 220.0 * it / rate)).roundToInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun capture(samples: ShortArray) = AudioScore.of(samples, rate, null, null).capture
    private fun CaptureQuality.term(id: String) = terms.single { it.id == id }

    @Test
    fun aCleanClipScoresFullMarks() {
        val c = capture(tone(peakDbfs = -12.0, seconds = 4.0))
        assertEquals(listOf("peak", "mean", "clipping", "duration", "silence"), c.terms.map { it.id })
        assertEquals(100, c.total)
        assertTrue(c.terms.all { it.points == it.weight })
    }

    @Test
    fun weightsAddUpTo100() {
        assertEquals(100, capture(tone(-12.0, 4.0)).terms.sumOf { it.weight })
    }

    @Test
    fun aQuietClipLosesPeakPointsInAStraightLine() {
        // Peak -30 dBFS: (-30 - -48) / (-24 - -48) = 0.75 of 25 points = 18.75 -> 19. Mean -33 dBFS is inside the full band.
        val c = capture(tone(peakDbfs = -30.0, seconds = 4.0))
        assertEquals(19, c.term("peak").points)
        assertEquals(25, c.term("mean").points)
        assertEquals(94, c.total)
    }

    @Test
    fun aVeryQuietClipScoresZeroForLevel() {
        val c = capture(tone(peakDbfs = -60.0, seconds = 4.0))
        assertEquals(0, c.term("peak").points)
        assertEquals(0, c.term("mean").points)
    }

    @Test
    fun aClippedClipLosesClippingAndTooLoudMeanPoints() {
        val square = ShortArray(rate * 4) { if (it % 40 < 20) 32767 else -32768 }
        val c = capture(square)
        assertEquals(1.0, c.term("clipping").measured!!, 1e-9)
        assertEquals(0, c.term("clipping").points)
        assertEquals(0, c.term("mean").points) // a mean at full scale is far above -3 dBFS
        assertEquals(25, c.term("peak").points) // peak points reward enough signal; the clipping term punishes the clipping
    }

    @Test
    fun clippingIsTheFractionOfSamplesAtFullScale() {
        val s = ShortArray(1000) { if (it < 5) 32767 else 1000 }
        assertEquals(0.005, capture(s).term("clipping").measured!!, 1e-9)
        // 0.5 % of the 1 % allowance: half of the 20 points.
        assertEquals(10, capture(s).term("clipping").points)
    }

    @Test
    fun silenceIsTheShareOfTwentyMillisecondFramesBelowMinus50() {
        // 1 s of tone then 2 s of digital silence: 100 of 150 frames are silent = 2/3.
        val s = tone(-12.0, 1.0) + ShortArray(rate * 2)
        val t = capture(s).term("silence")
        assertEquals(2.0 / 3.0, t.measured!!, 1e-6)
        // 15 * (0.8 - 0.6667) / 0.5 = 4.0
        assertEquals(4, t.points)
    }

    @Test
    fun aVeryShortClipLosesDurationPoints() {
        assertEquals(0, capture(tone(-12.0, 0.4)).term("duration").points)
        assertEquals(15, capture(tone(-12.0, 2.0)).term("duration").points)
        // 1.25 s is halfway from 0.5 s to 2 s: 7.5 points, rounded up.
        assertEquals(8, capture(tone(-12.0, 1.25)).term("duration").points)
    }

    @Test
    fun everyTermShowsItsValueItsRuleAndItsPoints() {
        for (t in capture(tone(-20.0, 3.0)).terms) {
            assertTrue(t.label.isNotBlank() && t.valueText.isNotBlank() && t.rule.isNotBlank(), t.id)
            assertTrue(t.points in 0..t.weight, t.id)
        }
    }

    @Test
    fun noSamplesMeansNothingIsMeasuredAndNoTotalIsInvented() {
        val c = capture(ShortArray(0))
        assertNull(c.total)
        assertTrue(c.terms.all { it.measured == null && it.points == 0 && it.valueText == AudioScore.NOT_MEASURED })
    }

    @Test
    fun theSameClipAlwaysGivesTheSameScore() {
        val s = tone(-18.0, 3.0)
        assertEquals(AudioScore.of(s, rate, null, null), AudioScore.of(s, rate, null, null))
    }

    // ---- recognition facts ----------------------------------------------------------------------------------------------

    @Test
    fun realTimeFactorIsAudioTimeOverProcessingTime() {
        val r = AudioScore.of(tone(-12.0, 4.0), rate, RecognitionInput("Whisper small", Compute.NPU, 800L, "the lift is stuck on floor three"), null).recognition
        assertEquals("Whisper small", r.engine)
        assertEquals("NPU", r.compute)
        assertEquals(800L, r.processingMs)
        assertEquals(4000L, r.audioMs)
        assertEquals(5.0, r.realTimeFactor!!, 1e-9)
        assertEquals(7, r.words)
    }

    @Test
    fun aTimeThatWasNotReportedIsNotMeasuredNotGuessed() {
        val r = AudioScore.of(tone(-12.0, 4.0), rate, RecognitionInput("Whisper small", null, null, "hello"), null).recognition
        assertNull(r.compute)
        assertNull(r.processingMs)
        assertNull(r.realTimeFactor)
        assertEquals(AudioScore.NOT_MEASURED, r.confidence)
    }

    @Test
    fun noConfidenceNumberIsEverMadeUp() {
        val r = AudioScore.of(tone(-12.0, 1.0), rate, RecognitionInput("e", Compute.CPU, 100L, "x"), null).recognition
        assertEquals(AudioScore.NOT_MEASURED, r.confidence)
    }

    @Test
    fun noRecognitionAtAllIsNull() {
        assertNull(AudioScore.of(tone(-12.0, 1.0), rate, null, null).recognition.engine)
    }

    @Test
    fun wordsAreCountedInHindiAndEnglishAndPunctuationAloneIsNotAWord() {
        assertEquals(5, AudioScore.countWords("लैब में आग लगी है"))
        assertEquals(2, AudioScore.countWords("  fire!  ...  now "))
        assertEquals(0, AudioScore.countWords(""))
    }

    @Test
    fun theScoreSaysPlainlyThatItIsAHeuristic() {
        assertTrue(AudioScore.HEURISTIC_NOTE.contains("heuristic", ignoreCase = true))
        assertFalse(AudioScore.of(tone(-12.0, 1.0), rate, null, null).note.isBlank())
    }

    @Test
    fun theLineKeepsNumbersOnlyAndNoWords() {
        val influence = PriorityExplainer.default().explain("there is a fire in the lab", emptyList())
        val s = AudioScore.of(tone(-12.0, 4.0), rate, RecognitionInput("Whisper small", Compute.NPU, 800L, "there is a fire in the lab"), influence)
        val line = s.toLine()
        assertEquals(100, line.capture)
        assertEquals(5.0, line.realTimeFactor!!, 1e-9)
        assertEquals(7, line.words)
        assertNotNull(line.rulesScore)
        assertFalse(line.toString().contains("fire in the lab"))
    }
}

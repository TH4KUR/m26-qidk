package com.megathon.incident.signals

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The expected numbers come from the numpy reference in `tools/signals_probe/probe_yamnet.py` (`log_mel`,
 * `mel_matrix`) run on the same signal, so this checks the Kotlin port against an independent implementation.
 */
class YamNetFrontEndTest {
    /** 1.5 s at 16 kHz: 0.3 sin at 440 Hz plus 0.1 sin at 1000 Hz, rounded to float like the reference. */
    private val tones = FloatArray(24_000) { n ->
        (0.3 * sin(2 * PI * 440 * n / 16_000) + 0.1 * sin(2 * PI * 1000 * n / 16_000)).toFloat()
    }

    private val golden = listOf(
        Triple(0, 0, -4.663333415985107f), Triple(0, 5, -0.8688746690750122f), Triple(0, 20, 2.472416877746582f),
        Triple(0, 40, -6.765537738800049f), Triple(0, 63, -6.898714542388916f),
        Triple(60, 0, -4.663333415985107f), Triple(60, 5, -0.8688746690750122f), Triple(60, 20, 2.472416877746582f),
        Triple(60, 40, -6.765537738800049f), Triple(60, 63, -6.898714542388916f),
        Triple(147, 0, -4.837188720703125f), Triple(147, 5, -0.8743938207626343f), Triple(147, 20, 2.4724464416503906f),
        Triple(147, 40, -6.736673831939697f), Triple(147, 63, -6.897805690765381f),
    )

    @Test
    fun logMelMatchesTheNumpyReference() {
        val logMel = YamNetFrontEnd.logMel(tones)
        assertEquals(148, logMel.size, "1 + (24000 - 400) / 160 frames")
        assertTrue(logMel.all { it.size == 64 })
        for ((frame, bin, expected) in golden) {
            assertEquals(expected, logMel[frame][bin], 1e-3f, "frame $frame bin $bin")
        }
        val sum = logMel.sumOf { row -> row.sumOf { it.toDouble() } }
        assertEquals(-42700.0105709061, sum, 1.0, "checksum over the whole matrix")
    }

    @Test
    fun silenceIsTheLogOfTheFloorEverywhere() {
        val logMel = YamNetFrontEnd.logMel(FloatArray(2_000))
        val floor = ln(0.001).toFloat()
        assertTrue(logMel.all { row -> row.all { abs(it - floor) < 1e-5f } })
    }

    @Test
    fun aClipOfOneAndAHalfSecondsGivesTwoPatchesOfNinetySixFramesBySixtyFourBins() {
        val patches = YamNetFrontEnd.patches(tones)
        assertEquals(2, patches.size, "starts at frames 0 and 48; frame 96 would run past 148")
        assertTrue(patches.all { it.size == 96 * 64 })
        // Row-major, frames then mel bins: element (frame 3, bin 20) of patch 1 is log-mel frame 48 + 3, bin 20.
        val logMel = YamNetFrontEnd.logMel(tones)
        assertEquals(logMel[51][20], patches[1][3 * 64 + 20], 1e-6f)
    }

    @Test
    fun aClipShorterThanOnePatchIsPaddedWithTheSilenceFloor() {
        val patches = YamNetFrontEnd.patches(FloatArray(8_000) { 0.2f }) // 0.5 s
        assertEquals(1, patches.size)
        assertEquals(96 * 64, patches.single().size)
        val frames = 1 + (8_000 - 400) / 160
        val padded = patches.single()[(frames) * 64 + 10]
        assertEquals(ln(0.001).toFloat(), padded, 1e-5f)
    }

    @Test
    fun anEmptyClipStillGivesOnePatchOfSilence() {
        val patches = YamNetFrontEnd.patches(FloatArray(0))
        assertEquals(1, patches.size)
        assertTrue(patches.single().all { abs(it - ln(0.001).toFloat()) < 1e-5f })
    }

    @Test
    fun sigmoidHandlesTheModelsRawLogitRangeWithoutOverflow() {
        assertEquals(0.5f, YamNetFrontEnd.sigmoid(0f), 1e-6f)
        assertEquals(0f, YamNetFrontEnd.sigmoid(-680f)) // the observed minimum of the AI Hub export
        assertEquals(1f, YamNetFrontEnd.sigmoid(73f), 1e-6f)
        assertTrue(YamNetFrontEnd.sigmoid(2.0f) > 0.88f && YamNetFrontEnd.sigmoid(2.0f) < 0.89f)
        assertTrue(!YamNetFrontEnd.sigmoid(-10_000f).isNaN() && !YamNetFrontEnd.sigmoid(10_000f).isNaN())
    }

    @Test
    fun classScoresTakeTheStrongestWindowPerClassAfterTheSigmoid() {
        val names = listOf("Speech", "Fire alarm", "Siren")
        val windowOne = floatArrayOf(-5f, 4f, -9f)
        val windowTwo = floatArrayOf(3f, -6f, 0f)
        val scores = YamNetFrontEnd.classScores(listOf(windowOne, windowTwo), names)

        assertEquals(listOf("Speech", "Fire alarm", "Siren"), scores.map { it.label })
        assertEquals(YamNetFrontEnd.sigmoid(3f), scores[0].score, 1e-6f)
        assertEquals(YamNetFrontEnd.sigmoid(4f), scores[1].score, 1e-6f)
        assertEquals(0.5f, scores[2].score, 1e-6f)
    }

    @Test
    fun classScoresRejectALogitVectorThatDoesNotMatchTheClassList() {
        assertFailsWith<IllegalArgumentException> { YamNetFrontEnd.classScores(listOf(floatArrayOf(1f, 2f)), listOf("a", "b", "c")) }
    }

    @Test
    fun aRealFireAlarmWindowThroughSigmoidAndMapperEndToEnd() {
        // What the device does: model logits for one window -> sigmoid -> mapper. Fire alarm logit +9 is about 0.9999.
        val names = listOf("Speech", "Fire alarm", "Rain")
        val signals = YamNetSignalMapper.map(YamNetFrontEnd.classScores(listOf(floatArrayOf(-3f, 9f, -20f)), names))
        assertEquals(listOf("fire alarm"), signals.map { it.label })
    }
}

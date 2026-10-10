package com.megathon.incident.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WavWriterTest {
    private fun header(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)

    @Test
    fun headerIsCanonical44ByteRiffWavePcm16Mono() {
        val samples = shortArrayOf(0, 1, -1, 32767, -32768)
        val wav = WavWriter.encode(samples)
        val b = header(wav)
        assertEquals(44 + samples.size * 2, wav.size)
        assertEquals("RIFF", tag(wav, 0))
        assertEquals(wav.size - 8, b.getInt(4))
        assertEquals("WAVE", tag(wav, 8))
        assertEquals("fmt ", tag(wav, 12))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt())          // PCM
        assertEquals(1, b.getShort(22).toInt())          // mono
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(28))                // byte rate
        assertEquals(2, b.getShort(32).toInt())          // block align
        assertEquals(16, b.getShort(34).toInt())         // bits per sample
        assertEquals("data", tag(wav, 36))
        assertEquals(samples.size * 2, b.getInt(40))
    }

    @Test
    fun customSampleRateIsReflectedInRateFields() {
        val b = header(WavWriter.encode(shortArrayOf(1, 2), sampleRate = 8000))
        assertEquals(8000, b.getInt(24))
        assertEquals(16000, b.getInt(28))
    }

    @Test
    fun roundTripReturnsTheSameSamples() {
        val samples = ShortArray(1000) { ((it * 73) % 65536 - 32768).toShort() } + shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE)
        val wav = WavWriter.encode(samples)
        val b = header(wav)
        val back = ShortArray(samples.size) { b.getShort(44 + it * 2) }
        assertTrue(samples.contentEquals(back))
    }

    @Test
    fun emptyClipIsJustAValidHeader() {
        val wav = WavWriter.encode(ShortArray(0))
        val b = header(wav)
        assertEquals(44, wav.size)
        assertEquals(36, b.getInt(4))
        assertEquals(0, b.getInt(40))
    }
}

class ClipStatsTest {
    @Test
    fun fullScaleSquareWaveIsZeroDbfs() {
        val s = ClipStats.of(ShortArray(100) { if (it % 2 == 0) 32767 else -32768 })
        assertEquals(0.0, s!!.peakDbfs, 0.01)
        assertEquals(0.0, s.meanDbfs, 0.01)
    }

    @Test
    fun halfScaleConstantIsAboutMinusSixDbfs() {
        val s = ClipStats.of(ShortArray(100) { 16384 })
        assertEquals(-6.02, s!!.peakDbfs, 0.01)
        assertEquals(-6.02, s.meanDbfs, 0.01)
    }

    @Test
    fun peakAndMeanDiffer() {
        val s = ClipStats.of(shortArrayOf(0, 0, 0, 16384))!!   // peak -6 dB, rms = 8192 -> -12 dB
        assertEquals(-6.02, s.peakDbfs, 0.01)
        assertEquals(-12.04, s.meanDbfs, 0.01)
    }

    @Test
    fun negativeFullScaleCountsAsZeroDbfsPeak() {
        assertEquals(0.0, ClipStats.of(shortArrayOf(-32768))!!.peakDbfs, 0.001)
    }

    @Test
    fun silenceIsFlooredNotInfinite() {
        val s = ClipStats.of(ShortArray(10))!!
        assertEquals(ClipStats.FLOOR_DBFS, s.peakDbfs, 0.0)
        assertEquals(ClipStats.FLOOR_DBFS, s.meanDbfs, 0.0)
    }

    @Test
    fun emptyHasNoStats() {
        assertNull(ClipStats.of(ShortArray(0)))
    }
}

class ClipNameTest {
    @Test
    fun keepsAllowedCharacters() = assertEquals("clip_01-A", ClipName.sanitize("clip_01-A"))

    @Test
    fun replacesEverythingElse() = assertEquals("a_b_c_d", ClipName.sanitize("a b/c.d"))

    @Test
    fun pathTraversalCannotEscape() {
        val n = ClipName.sanitize("../../etc/passwd")
        assertTrue(n.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        assertTrue('/' !in n && '.' !in n)
    }

    @Test
    fun blankFallsBackToClip() {
        assertEquals("clip", ClipName.sanitize(""))
        assertEquals("clip", ClipName.sanitize(null))
    }

    @Test
    fun nonAsciiLettersAreReplaced() = assertEquals("caf_", ClipName.sanitize("café"))

    @Test
    fun overlongNamesAreTruncated() = assertEquals(64, ClipName.sanitize("x".repeat(200)).length)
}

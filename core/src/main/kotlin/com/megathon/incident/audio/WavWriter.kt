package com.megathon.incident.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Canonical 44-byte-header RIFF/WAVE, PCM16 mono, little-endian. Pure, so it is unit-tested on the JVM. */
object WavWriter {
    const val HEADER_BYTES = 44

    fun encode(samples: ShortArray, sampleRate: Int = 16_000): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArray(HEADER_BYTES + dataBytes)
        var p = 0
        fun tag(s: String) { for (ch in s) out[p++] = ch.code.toByte() }
        fun i32(v: Int) { for (k in 0 until 4) out[p++] = (v ushr (8 * k)).toByte() }
        fun i16(v: Int) { for (k in 0 until 2) out[p++] = (v ushr (8 * k)).toByte() }
        tag("RIFF"); i32(36 + dataBytes); tag("WAVE")
        tag("fmt "); i32(16); i16(1); i16(1); i32(sampleRate); i32(sampleRate * 2); i16(2); i16(16)
        tag("data"); i32(dataBytes)
        for (s in samples) i16(s.toInt())
        return out
    }
}

/** Peak and mean (RMS) level of a clip in dBFS, so a speaker can judge how loud the device heard them. */
data class ClipStats(val peakDbfs: Double, val meanDbfs: Double) {
    companion object {
        const val FLOOR_DBFS = -120.0

        fun of(samples: ShortArray): ClipStats? {
            if (samples.isEmpty()) return null
            var peak = 0
            var sum = 0.0
            for (s in samples) {
                val v = s.toInt()
                peak = maxOf(peak, abs(v))
                sum += v.toDouble() * v
            }
            return ClipStats(db(peak / 32768.0), db(sqrt(sum / samples.size) / 32768.0))
        }

        private fun db(x: Double): Double = if (x <= 0.0) FLOOR_DBFS else maxOf(FLOOR_DBFS, 20 * log10(x))
    }
}

/** Clip names become file names: only [A-Za-z0-9_-], so nothing can escape the recordings directory. */
object ClipName {
    const val MAX_LENGTH = 64

    fun sanitize(raw: String?): String {
        val cleaned = (raw ?: "").map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-') it else '_' }
            .joinToString("").take(MAX_LENGTH)
        return cleaned.ifBlank { "clip" }
    }
}

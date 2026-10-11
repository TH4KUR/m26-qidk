package com.megathon.incident.tts

/** Small audio helpers for the speech-output engines, kept off Android so they can be unit tested. */
object TtsPcm {
    /** Float samples in [-1, 1] to 16-bit PCM. Out-of-range values are clipped, not wrapped; NaN becomes silence. */
    fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { i ->
        val v = samples[i]
        if (v.isNaN()) {
            0
        } else {
            val scaled = v.coerceIn(-1f, 1f) * 32767f
            val rounded = if (scaled >= 0f) kotlin.math.floor(scaled + 0.5f) else -kotlin.math.floor(-scaled + 0.5f)
            rounded.toInt().toShort()
        }
    }

    /** How long [frames] mono frames last at [sampleRate], in milliseconds (0 when either is not positive). */
    fun durationMs(frames: Int, sampleRate: Int): Long =
        if (frames <= 0 || sampleRate <= 0) 0L else frames.toLong() * 1000L / sampleRate
}

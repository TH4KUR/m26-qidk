package com.megathon.incident.speech

import com.megathon.incident.testing.Compute

/**
 * The pure part of driving WhisperKit's streaming API for one whole utterance, so it is tested here and
 * the Android class stays thin. The pattern (init, stream 100 ms byte chunks, pad to a 30 s window,
 * deinitialize, read the accumulated text) follows how a third-party SM8750 integration used the library;
 * whether the callbacks fire synchronously during those calls has to be proven on the kit.
 */
object WhisperKitFeed {
    /** 100 ms of 16 kHz PCM16 mono. */
    const val CHUNK_BYTES = 3_200

    /** The model window: 30 s of 16 kHz PCM16 mono. QNN graphs have static shapes, so this is the unit. */
    const val WINDOW_BYTES = 960_000

    private const val BACKEND_CPU_AND_NPU = 3

    fun toPcmBytes(pcm: ShortArray): ByteArray {
        val out = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val v = pcm[i].toInt()
            out[2 * i] = v.toByte()
            out[2 * i + 1] = (v shr 8).toByte()
        }
        return out
    }

    fun chunks(bytes: ByteArray, chunkBytes: Int = CHUNK_BYTES): Sequence<ByteArray> =
        (bytes.indices step chunkBytes).asSequence().map { start -> bytes.copyOfRange(start, minOf(start + chunkBytes, bytes.size)) }

    /** Zero bytes to send after the audio so the last window is flushed; none when nothing was sent. */
    fun paddingBytes(totalBytes: Int): Int = (WINDOW_BYTES - totalBytes % WINDOW_BYTES) % WINDOW_BYTES

    /**
     * Where the encoder and decoder were *configured* to run (`CPU_ONLY=1, CPU_AND_GPU=2, CPU_AND_NPU=3`).
     * The QNN delegate can still fall back to the CPU for unsupported ops, so the proof strip pairs this
     * with a startup comparison against a CPU reference.
     */
    fun computeLabel(encoderBackend: Int, decoderBackend: Int): Compute {
        val npu = listOf(encoderBackend, decoderBackend).map { it == BACKEND_CPU_AND_NPU }
        return when {
            npu.all { it } -> Compute.NPU
            npu.any { it } -> Compute.HYBRID
            else -> Compute.CPU
        }
    }

    /**
     * Collects the callback results of one utterance into one string. Handles both separate segments and a
     * growing cumulative result. Not thread-safe by itself; the caller guards it.
     */
    class TranscriptAccumulator {
        private val parts = ArrayList<String>()
        private var last: String? = null

        fun onResult(text: String, segments: List<String>) {
            val chunk = clean(if (segments.isEmpty()) text else segments.joinToString(""))
            if (chunk.isEmpty() || chunk == last) return
            // A result that extends the previous one word for word is the same sentence growing (cumulative
            // callbacks), so it replaces it. Which of the two the library really does is unproven on the kit.
            if (last != null && chunk.startsWith("$last ")) parts[parts.lastIndex] = chunk else parts += chunk
            last = chunk
        }

        fun text(): String = clean(parts.joinToString(" "))

        fun reset() {
            parts.clear()
            last = null
        }

        private fun clean(value: String) = value.trim().replace(Regex("\\s+"), " ")
    }
}

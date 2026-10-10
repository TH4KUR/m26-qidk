package com.megathon.incident.signals

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The audio front end the Qualcomm AI Hub YamNet export needs, in pure Kotlin so it is tested on the JVM and runs
 * on the device on the CPU. The export takes one 96 x 64 log-mel patch and returns raw logits, so on the device:
 * `patches(samples)` -> model per patch -> `classScores(logitsPerPatch, classNames)` -> [YamNetSignalMapper].
 *
 * Ported from the numpy reference in `tools/signals_probe/probe_yamnet.py` and checked against it (golden values
 * in `YamNetFrontEndTest`). The reference itself was agreed with Google's `yamnet.tflite` on only 105 of 196
 * windows' top class, and was not compared bit for bit with TensorFlow's own front end, so check one real clip
 * on the device before trusting thresholds.
 */
object YamNetFrontEnd {
    const val SAMPLE_RATE = 16_000
    const val MEL_BINS = 64
    const val PATCH_FRAMES = 96
    const val PATCH_HOP_FRAMES = 48

    private const val WINDOW = 400 // 25 ms
    private const val HOP = 160 // 10 ms
    private const val FFT_SIZE = 512
    private const val SPECTRUM_BINS = FFT_SIZE / 2 + 1
    private const val LOG_OFFSET = 0.001
    private const val MEL_LOW_HZ = 125.0
    private const val MEL_HIGH_HZ = 7_500.0

    /** What a frame of digital silence gives: `ln(0 + 0.001)`. Also the padding value for a clip shorter than one patch. */
    private val SILENCE_FLOOR = ln(LOG_OFFSET).toFloat()

    private val hann = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * it / WINDOW) } // periodic

    private val cosTable = DoubleArray(FFT_SIZE / 2) { cos(2 * PI * it / FFT_SIZE) }
    private val sinTable = DoubleArray(FFT_SIZE / 2) { -sin(2 * PI * it / FFT_SIZE) }
    private val bitReversed = IntArray(FFT_SIZE) { i -> Integer.reverse(i) ushr (32 - 9) } // 2^9 = 512

    /** HTK mel filter bank, `[spectrum bin][mel bin]`, with the DC bin zeroed (YamNet's `spectrogram_to_mel_matrix`). */
    private val melMatrix: Array<DoubleArray> = run {
        fun hzToMel(hz: Double) = 1127.0 * ln(1.0 + hz / 700.0)
        val binMel = DoubleArray(SPECTRUM_BINS) { hzToMel(it * (SAMPLE_RATE / 2.0) / (SPECTRUM_BINS - 1)) }
        val low = hzToMel(MEL_LOW_HZ)
        val high = hzToMel(MEL_HIGH_HZ)
        val edges = DoubleArray(MEL_BINS + 2) { low + (high - low) * it / (MEL_BINS + 1) }
        Array(SPECTRUM_BINS) { bin ->
            DoubleArray(MEL_BINS) { mel ->
                if (bin == 0) {
                    0.0
                } else {
                    val (l, c, u) = Triple(edges[mel], edges[mel + 1], edges[mel + 2])
                    max(0.0, min((binMel[bin] - l) / (c - l), (u - binMel[bin]) / (u - c)))
                }
            }
        }
    }

    /** `[frame][mel bin]` log-mel, one frame per 10 ms; a clip shorter than 25 ms is zero-padded to one frame. */
    fun logMel(samples: FloatArray): Array<FloatArray> {
        val x = if (samples.size < WINDOW) samples.copyOf(WINDOW) else samples
        val frames = 1 + (x.size - WINDOW) / HOP
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        return Array(frames) { frame ->
            val start = frame * HOP
            for (i in 0 until FFT_SIZE) {
                re[i] = if (i < WINDOW) x[start + i] * hann[i] else 0.0
                im[i] = 0.0
            }
            fft(re, im)
            val magnitude = DoubleArray(SPECTRUM_BINS) { sqrt(re[it] * re[it] + im[it] * im[it]) }
            FloatArray(MEL_BINS) { mel ->
                var energy = 0.0
                for (bin in 0 until SPECTRUM_BINS) energy += magnitude[bin] * melMatrix[bin][mel]
                ln(energy + LOG_OFFSET).toFloat()
            }
        }
    }

    /**
     * The model's inputs: windows of [PATCH_FRAMES] frames every [PATCH_HOP_FRAMES] frames, each flattened row-major
     * (frames, then mel bins) like the `1 x 1 x 96 x 64` tensor. A clip too short for one window gives one patch
     * padded with the silence floor.
     */
    fun patches(samples: FloatArray): List<FloatArray> {
        val logMel = logMel(samples)
        val starts = (0 until max(1, logMel.size - PATCH_FRAMES + 1) step PATCH_HOP_FRAMES)
            .filter { it + PATCH_FRAMES <= logMel.size }
        if (starts.isEmpty()) {
            return listOf(FloatArray(PATCH_FRAMES * MEL_BINS) { i ->
                val frame = i / MEL_BINS
                if (frame < logMel.size) logMel[frame][i % MEL_BINS] else SILENCE_FLOOR
            })
        }
        return starts.map { start ->
            FloatArray(PATCH_FRAMES * MEL_BINS) { i -> logMel[start + i / MEL_BINS][i % MEL_BINS] }
        }
    }

    /** Numerically stable logistic function; the AI Hub export's logits reach about -680 and +73. */
    fun sigmoid(logit: Float): Float {
        val x = logit.toDouble()
        return if (x >= 0) (1.0 / (1.0 + exp(-x))).toFloat() else exp(x).let { (it / (1.0 + it)).toFloat() }
    }

    /** Sigmoid on every window's logits, then the strongest window per class, named from the class list. */
    fun classScores(logitsPerWindow: List<FloatArray>, classNames: List<String>): List<ClassScore> {
        for (window in logitsPerWindow) {
            require(window.size == classNames.size) { "${window.size} logits for ${classNames.size} classes" }
        }
        return classNames.mapIndexed { index, name ->
            ClassScore(name, logitsPerWindow.maxOfOrNull { sigmoid(it[index]) } ?: 0f)
        }
    }

    /** In-place radix-2 FFT of [FFT_SIZE] points. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until FFT_SIZE) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= FFT_SIZE) {
            val half = size / 2
            val step = FFT_SIZE / size
            var start = 0
            while (start < FFT_SIZE) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += size
            }
            size *= 2
        }
    }
}

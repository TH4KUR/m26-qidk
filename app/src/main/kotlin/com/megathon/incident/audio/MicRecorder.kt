package com.megathon.incident.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records 16 kHz mono PCM16 from the microphone while the reporter has asked it to (consent: only ever started by a tap).
 * [onLevel] reports 0..1 about ten times a second so the screen can show that the device is hearing.
 */
class MicRecorder(private val context: Context) {
    private var record: AudioRecord? = null
    private var job: Job? = null
    private var samples = ShortArray(SAMPLE_RATE * 10)
    private var count = 0

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Returns false when the microphone could not be opened (permission missing, or another app holds it). */
    @Suppress("MissingPermission")
    fun start(scope: CoroutineScope, onLevel: (Float) -> Unit, onLimit: () -> Unit): Boolean {
        if (!hasPermission() || record != null) return false
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, SAMPLE_RATE),
            )
        } catch (_: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        count = 0
        record = rec
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val chunk = ShortArray(SAMPLE_RATE / 10)
            while (record === rec) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) break
                append(chunk, n)
                onLevel(level(chunk, n))
                if (count >= MAX_SAMPLES) { onLimit(); break }
            }
        }
        return true
    }

    /** Stops and returns everything captured. Safe to call when nothing is recording. */
    suspend fun stop(): ShortArray {
        val rec = record ?: return ShortArray(0)
        record = null
        runCatching { rec.stop() }
        job?.join()
        job = null
        rec.release()
        return samples.copyOf(count)
    }

    fun cancel() {
        val rec = record ?: return
        record = null
        runCatching { rec.stop() }
        rec.release()
        job?.cancel()
        job = null
    }

    private fun append(chunk: ShortArray, n: Int) {
        if (count + n > samples.size) samples = samples.copyOf(minOf(samples.size * 2, MAX_SAMPLES + chunk.size))
        System.arraycopy(chunk, 0, samples, count, minOf(n, samples.size - count))
        count = minOf(count + n, samples.size)
    }

    /** RMS in dBFS mapped from -50 dB (quiet) to -15 dB (loud speech) onto 0..1, so steady room noise sits low. */
    private fun level(chunk: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) sum += chunk[i].toDouble() * chunk[i]
        val rms = sqrt(sum / n) / 32768.0
        val db = 20 * log10(max(rms, 1e-6))
        return ((db + 50) / 35).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SAMPLES = SAMPLE_RATE * 30
    }
}

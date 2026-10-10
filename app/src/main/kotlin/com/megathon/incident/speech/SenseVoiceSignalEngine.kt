package com.megathon.incident.speech

import android.content.Context
import com.megathon.incident.domain.Signal
import com.megathon.incident.engine.EngineUnavailableException
import com.megathon.incident.signals.SenseVoiceOutputParser
import com.megathon.incident.signals.SenseVoiceReading
import com.megathon.incident.signals.SenseVoiceSignalMapper
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SignalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * What one SenseVoice run produced: the [signals] handed to the advisor, the raw [reading] they came from (null only
 * when the model printed nothing readable), where it ran, and how long it took.
 */
data class SignalReading(val signals: List<Signal>, val reading: SenseVoiceReading?, val compute: Compute, val elapsedMs: Long)

/**
 * SenseVoice's emotion and sound-event tags, from the same audio the speech engine heard. It runs as a **child process**
 * exactly like [SherpaCliSpeechEngine] (same command-line tool, same QNN libraries), only with the SenseVoice flags.
 *
 * The tags are suggestions. This class passes them on; what they mean for urgency is decided by the advisor's rules.
 * The model's language tag is never read here. Nothing is invented on failure: a missing bundle or a failed run throws.
 */
class SenseVoiceSignalEngine(private val context: Context) : SignalSource {
    override val name = "SenseVoice (sherpa-onnx, QNN)"

    @Volatile private var lastCompute: Compute? = null

    /** Where the last run really ran. Before any run it says CPU, the cautious default. */
    override val compute: Compute get() = lastCompute ?: Compute.CPU

    private val bundle = SpeechBundle(context)
    private val senseDir = File(File(context.filesDir, "sherpa"), "sense-voice")

    init {
        val missing = missing()
        if (missing.isNotEmpty()) {
            throw EngineUnavailableException("the SenseVoice bundle is incomplete on this device. Missing: ${missing.joinToString(", ")}. Run app/scripts/speech_bundle.sh push")
        }
    }

    private fun missing(): List<String> = buildList {
        if (!bundle.cli.exists()) add("speech CLI in the APK (run app/scripts/speech_bundle.sh stage, then rebuild)")
        listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpPrepare.so", "libQnnHtpV79Stub.so", "libQnnHtpV79Skel.so")
            .filter { !File(bundle.qnnDir, it).exists() }.forEach { add("qnn/$it") }
        listOf("model.bin", "tokens.txt").filter { !File(senseDir, it).exists() }.forEach { add("sense-voice/$it") }
    }

    override suspend fun signals(pcm: ShortArray): List<Signal> = read(pcm).signals

    suspend fun read(pcm: ShortArray): SignalReading = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        // Files can disappear after construction (the bundle is pushed by hand); say so rather than fail obscurely.
        missing().takeIf { it.isNotEmpty() }?.let { throw EngineUnavailableException("the SenseVoice bundle is incomplete on this device. Missing: ${it.joinToString(", ")}") }
        val wav = File.createTempFile("tags", ".wav", context.cacheDir)
        try {
            writeWav(wav, pcm)
            val command = listOf(
                bundle.cli.path,
                "--provider=qnn",
                "--tokens=${File(senseDir, "tokens.txt").path}",
                "--sense-voice.qnn-backend-lib=${File(bundle.qnnDir, "libQnnHtp.so").path}",
                "--sense-voice.qnn-system-lib=${File(bundle.qnnDir, "libQnnSystem.so").path}",
                "--sense-voice.qnn-context-binary=${File(senseDir, "model.bin").path}",
                wav.path,
            )
            val process = ProcessBuilder(command).directory(bundle.qnnDir).redirectErrorStream(true).apply {
                // The child gets sherpa's own QNN libraries and DSP search path, never GenieX's.
                environment()["LD_LIBRARY_PATH"] = "${bundle.qnnDir.path}:${bundle.nativeDir}:/system/lib64:/vendor/lib64"
                environment()["ADSP_LIBRARY_PATH"] = "${bundle.qnnDir.path};/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp"
            }.start()
            // Read on another thread so the timeout below really bounds a hung child.
            val output = async { process.inputStream.bufferedReader().readText() }
            if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("voice tags timed out after $TIMEOUT_S s")
            }
            val log = output.await()
            check(process.exitValue() == 0) { "voice tags exited with ${process.exitValue()}: ${log.takeLast(300)}" }

            val reading = SenseVoiceOutputParser.parse(log)
                ?: error("voice tags printed nothing readable: ${log.takeLast(300)}")
            // NPU only on evidence: the QNN HTP provider announces itself in the log.
            val compute = if (log.contains("HtpProvider", ignoreCase = true)) Compute.NPU else Compute.CPU
            lastCompute = compute
            val signals = SenseVoiceSignalMapper.map(reading.emotion, reading.event)
            SignalReading(signals, reading, compute, (System.nanoTime() - started) / 1_000_000)
        } finally {
            wav.delete()
        }
    }

    /** 16 kHz mono PCM16 WAV. */
    private fun writeWav(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        RandomAccessFile(file, "rw").use { f ->
            val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            h.putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16)
            h.put("data".toByteArray()).putInt(dataBytes)
            f.write(h.array())
            val body = java.nio.ByteBuffer.allocate(dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { body.putShort(it) }
            f.write(body.array())
        }
    }

    private companion object {
        const val TIMEOUT_S = 90L
    }
}

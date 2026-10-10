package com.megathon.incident.speech

import android.content.Context
import com.megathon.incident.engine.EngineUnavailableException
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** Where the speech bundle lives on the device (pushed by `app/scripts/speech_bundle.sh`, never in the APK or git). */
class SpeechBundle(context: Context) {
    private val root = File(context.filesDir, "sherpa")
    val qnnDir = File(root, "qnn")
    val whisperDir = File(root, "whisper-small")
    /** The CLI is packed as a native library so Android extracts it where an app may execute it. */
    val cli = File(context.applicationInfo.nativeLibraryDir, "libsherpa_offline.so")
    val nativeDir: String = context.applicationInfo.nativeLibraryDir

    fun missing(): List<String> = buildList {
        if (!cli.exists()) add("speech CLI in the APK (run app/scripts/speech_bundle.sh stage, then rebuild)")
        listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpPrepare.so", "libQnnHtpV79Stub.so", "libQnnHtpV79Skel.so")
            .filter { !File(qnnDir, it).exists() }.forEach { add("qnn/$it") }
        listOf("encoder.bin", "decoder.bin", "tokens.txt").filter { !File(whisperDir, it).exists() }.forEach { add("whisper-small/$it") }
    }
}

/**
 * Whisper-Small on the Hexagon NPU through sherpa-onnx's QNN backend, run as a **child process** (the command-line tool
 * the lead tested on the kit). Why a process: the Kotlin API of this sherpa build exposes the QNN config only for SenseVoice,
 * not Whisper, and a child process also keeps sherpa's QNN 2.40 libraries apart from GenieX's 2.45 ones.
 *
 * The compute label is read from evidence, not assumed: [Compute.NPU] only when the run's log shows the Hexagon provider started.
 * There is no CPU fallback: if the QNN run fails, the engine fails and says so.
 */
class SherpaCliSpeechEngine(private val context: Context) : SpeechEngine {
    override val name = "Whisper-Small (sherpa-onnx, QNN)"
    private val bundle = SpeechBundle(context)

    init {
        val missing = bundle.missing()
        if (missing.isNotEmpty()) {
            throw EngineUnavailableException("the speech bundle is incomplete on this device. Missing: ${missing.joinToString(", ")}. Run app/scripts/speech_bundle.sh push")
        }
    }

    override suspend fun transcribe(pcm: ShortArray, language: String): Transcript = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val wav = File.createTempFile("utterance", ".wav", context.cacheDir)
        try {
            writeWav(wav, pcm)
            val w = bundle.whisperDir
            val command = listOf(
                bundle.cli.path,
                "--provider=qnn",
                "--tokens=${File(w, "tokens.txt").path}",
                "--whisper.qnn-backend-lib=${File(bundle.qnnDir, "libQnnHtp.so").path}",
                "--whisper.qnn-system-lib=${File(bundle.qnnDir, "libQnnSystem.so").path}",
                "--whisper.qnn-context-binary=${File(w, "encoder.bin").path},${File(w, "decoder.bin").path}",
                "--whisper-language=$language",
                "--whisper-task=transcribe",
                wav.path,
            )
            val process = ProcessBuilder(command).directory(bundle.qnnDir).redirectErrorStream(true).apply {
                // The child gets sherpa's own QNN 2.40 libraries and its own DSP search path; GenieX's 2.45 libraries are never seen.
                environment()["LD_LIBRARY_PATH"] = "${bundle.qnnDir.path}:${bundle.nativeDir}:/system/lib64:/vendor/lib64"
                environment()["ADSP_LIBRARY_PATH"] = "${bundle.qnnDir.path};/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp"
            }.start()
            val log = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); error("speech engine timed out") }
            check(process.exitValue() == 0) { "speech engine exited with ${process.exitValue()}: ${log.takeLast(300)}" }

            val text = parseText(log)
            // NPU only on evidence: the QNN HTP provider announces itself in the log. Otherwise say CPU, which is the honest default.
            val compute = if (log.contains("HtpProvider", ignoreCase = true)) Compute.NPU else Compute.CPU
            Transcript(text, language, (System.nanoTime() - started) / 1_000_000, compute)
        } finally {
            wav.delete()
        }
    }

    /** The CLI prints the file name, then one JSON line with a "text" field. */
    private fun parseText(log: String): String {
        val line = log.lineSequence().firstOrNull { it.trimStart().startsWith("{") && it.contains("\"text\"") } ?: return ""
        return runCatching { JSONObject(line.trim()).getString("text").trim() }.getOrDefault("")
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
}

package com.megathon.incident.speech

import android.content.Context
import com.megathon.incident.audio.WavWriter
import com.megathon.incident.engine.EngineUnavailableException
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Whisper-Small on the Hexagon NPU through the resident worker (tools/sherpa_worker): the recognizer is created once per
 * worker lifetime instead of once per utterance, which is what lets live transcription decode a window every few seconds.
 * Same isolation as [SherpaCliSpeechEngine]: a child process with sherpa's own QNN 2.40 libraries, never GenieX's 2.45.
 *
 * Compute is [Compute.NPU] only when the worker's own log shows the Hexagon provider started. No CPU fallback.
 * NOT yet built or run on the kit: the worker binary has to be built and staged first (tools/sherpa_worker/README.md).
 */
class ResidentSherpaSpeechEngine(private val context: Context, private val startLanguage: String = "en") : SpeechEngine {
    override val name = "Whisper-Small (sherpa-onnx, QNN, resident)"
    private val bundle = SpeechBundle(context)
    private val client = ResidentWorkerClient({ launchWorker() })

    /** How many times the models were loaded (one per worker started). For the kit check: it should stay at 1. */
    val workerLaunches: Int get() = client.launches

    init {
        val missing = bundle.missing().filterNot { it.startsWith("speech CLI") } + listOfNotNull(
            "speech worker in the APK (build tools/sherpa_worker, then app/scripts/speech_bundle.sh stage)".takeIf { !bundle.worker.exists() },
        )
        if (missing.isNotEmpty()) {
            throw EngineUnavailableException("the speech bundle is incomplete on this device. Missing: ${missing.joinToString(", ")}. Run app/scripts/speech_bundle.sh push")
        }
    }

    override suspend fun transcribe(pcm: ShortArray, language: String): Transcript = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        if (pcm.isEmpty()) return@withContext Transcript("", language, 0, Compute.CPU)
        // The WAV must outlive the request: the worker reads it from disk while decoding. Deleted only after the reply (or failure).
        val wav = File.createTempFile("utterance", ".wav", context.cacheDir)
        try {
            wav.writeBytes(WavWriter.encode(pcm))
            val reply = client.transcribe(wav.path, language)
            val compute = if (reply.hexagonEvidence) Compute.NPU else Compute.CPU
            Transcript(reply.text, language, (System.nanoTime() - started) / 1_000_000, compute)
        } catch (e: WorkerException) {
            throw IllegalStateException(e.message, e)
        } finally {
            wav.delete()
        }
    }

    /** The app is done with speech: ask the worker to quit and release it. */
    fun release() = runBlocking { client.close() }

    private fun launchWorker(): WorkerChannel {
        val w = bundle.whisperDir
        val command = listOf(
            bundle.worker.path,
            "--provider=qnn",
            "--tokens=${File(w, "tokens.txt").path}",
            "--whisper.qnn-backend-lib=${File(bundle.qnnDir, "libQnnHtp.so").path}",
            "--whisper.qnn-system-lib=${File(bundle.qnnDir, "libQnnSystem.so").path}",
            "--whisper.qnn-context-binary=${File(w, "encoder.bin").path},${File(w, "decoder.bin").path}",
            "--whisper-language=$startLanguage",
            "--whisper-task=transcribe",
        )
        val process = ProcessBuilder(command).directory(bundle.qnnDir).apply {
            // Same environment as the CLI adapter: sherpa's QNN 2.40 libraries and DSP search path; GenieX's 2.45 libraries are never seen.
            environment()["LD_LIBRARY_PATH"] = "${bundle.qnnDir.path}:${bundle.nativeDir}:/system/lib64:/vendor/lib64"
            environment()["ADSP_LIBRARY_PATH"] = "${bundle.qnnDir.path};/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp"
        }.start()
        return ProcessWorkerChannel(process)
    }

    companion object {
        /** Debug builds only (`--es speech_worker cli`): use the per-utterance CLI instead, as the latency baseline. */
        @Volatile
        var preferCliBaseline = false
    }
}

/** A child process as a [WorkerChannel]: stdout is protocol, stderr is drained on its own thread so the child can never block on a full pipe. */
private class ProcessWorkerChannel(private val process: Process) : WorkerChannel {
    private val out = OutputStreamWriter(process.outputStream, Charsets.UTF_8)
    private val input = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
    private val log = StringBuilder()

    init {
        Thread({
            runCatching {
                BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).forEachLine { line ->
                    // Keep the start-up part of the log (where the provider announces itself), not an endless tail.
                    synchronized(log) { if (log.length < LOG_LIMIT) log.append(line).append('\n') }
                }
            }
        }, "sherpa-worker-stderr").apply { isDaemon = true }.start()
    }

    override fun send(line: String) {
        out.write(line)
        out.write("\n")
        out.flush()
    }

    override fun readLine(): String? = input.readLine()

    override fun sawLog(marker: String) = synchronized(log) { log.contains(marker, ignoreCase = true) }

    override fun destroy() {
        runCatching { process.destroyForcibly() }
        runCatching { process.inputStream.close() }
        runCatching { process.outputStream.close() }
    }

    private companion object {
        const val LOG_LIMIT = 256 * 1024
    }
}

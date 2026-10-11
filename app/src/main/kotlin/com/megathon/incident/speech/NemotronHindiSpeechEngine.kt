package com.megathon.incident.speech

import android.content.Context
import android.util.Log
import com.megathon.incident.audio.WavWriter
import com.megathon.incident.engine.EngineUnavailableException
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Where the experimental Hindi lane's files live on the device. The CLI is the ONLINE sherpa-onnx tool packed as a
 * native library (`app/scripts/speech_bundle.sh stage-online`); the model folder is pushed to the app's private folder
 * (`push-hindi`) and is never in the APK or git. The QNN 2.40 libraries are the ones the Whisper lane already uses.
 */
class NemotronHindiFiles(context: Context) {
    private val root = File(context.filesDir, "sherpa")
    val qnnDir = File(root, "qnn")
    val modelDir = File(root, NemotronHindiBundle.FOLDER)
    val cli = File(context.applicationInfo.nativeLibraryDir, "libsherpa_online.so")
    val nativeDir: String = context.applicationInfo.nativeLibraryDir

    fun paths() = NemotronHindiPaths(cli = cli.path, nativeLibDir = nativeDir, qnnDir = qnnDir.path, modelDir = modelDir.path)

    /** What is missing or the wrong size, in words; empty when the lane can run. Stats three files, so it is cheap. */
    fun missing(): List<String> = buildList {
        if (!cli.exists()) add("online speech CLI in the APK (run app/scripts/speech_bundle.sh stage-online, then rebuild)")
        listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpPrepare.so", "libQnnHtpV79Stub.so", "libQnnHtpV79Skel.so")
            .filter { !File(qnnDir, it).exists() }.forEach { add("qnn/$it") }
        addAll(NemotronHindiBundle.problems { name -> File(modelDir, name).takeIf { it.isFile }?.length() })
    }
}

/**
 * Nemotron-3.5 ASR (streaming, 0.6B) on the Hexagon NPU through sherpa-onnx's QNN provider, as a **child process** of the
 * ONLINE CLI, fed one whole utterance per call. An experimental, additive Hindi lane: the English lane never reaches it.
 *
 * The command and the output parsing are pure functions in `:core` ([NemotronHindiCommand], [NemotronHindiOutput]) with
 * unit tests; this class only starts the process. It does not fall back to anything: a failed run is a thrown error with
 * the reason, never an empty transcript presented as a success.
 */
class NemotronHindiSpeechEngine(private val context: Context) : SpeechEngine {
    override val name = "Nemotron-3.5 ASR Hindi (sherpa-onnx, QNN), experimental"
    private val bundle = NemotronHindiFiles(context)

    /** Exit status of the most recent child process (null before the first run, or if it timed out). */
    @Volatile var lastChildExit: Int? = null
        private set

    init {
        val missing = bundle.missing()
        if (missing.isNotEmpty()) {
            throw EngineUnavailableException(
                "the Hindi speech bundle is incomplete on this device. Missing: ${missing.joinToString(", ")}. " +
                    "Run app/scripts/speech_bundle.sh push-hindi",
            )
        }
    }

    override suspend fun transcribe(pcm: ShortArray, language: String): Transcript = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        lastChildExit = null
        // Nothing to hear: no process is started and nothing is claimed about the NPU.
        if (pcm.isEmpty()) return@withContext Transcript("", "hi", 0, Compute.CPU)
        // Files can disappear after construction (the bundle is pushed by hand): say so rather than fail obscurely.
        bundle.missing().takeIf { it.isNotEmpty() }?.let {
            throw EngineUnavailableException("the Hindi speech bundle is incomplete on this device. Missing: ${it.joinToString(", ")}")
        }
        val wav = File.createTempFile("utterance-hi", ".wav", context.cacheDir)
        try {
            wav.writeBytes(WavWriter.encode(pcm))
            // Throws IllegalArgumentException for any language but Hindi: this lane never feeds the Hindi model other audio.
            val invocation = NemotronHindiCommand.build(bundle.paths(), language, wav.path)
            val process = ProcessBuilder(invocation.args).directory(File(invocation.workingDir)).redirectErrorStream(true).apply {
                // The child gets sherpa's own QNN 2.40 libraries and DSP search path; GenieX's 2.45 libraries are never seen.
                environment().putAll(invocation.environment)
            }.start()
            // Read on another thread so the timeout below really bounds a hung child.
            val output = async { process.inputStream.bufferedReader(Charsets.UTF_8).readText() }
            if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("the Hindi speech engine timed out after $TIMEOUT_S s")
            }
            val log = output.await()
            val exit = process.exitValue()
            lastChildExit = exit

            when (val result = NemotronHindiOutput.parse(log, exit)) {
                is NemotronHindiResult.Failed -> error(result.reason)
                is NemotronHindiResult.Heard -> {
                    val compute = NemotronHindiCommand.computeFor(invocation.args)
                    val ms = (System.nanoTime() - started) / 1_000_000
                    Log.i(TAG, "hindi lane: engine=nemotron-3.5 provider=qnn compute=$compute exit=$exit ms=$ms audio_ms=${pcm.size / 16} chars=${result.text.length}")
                    Transcript(result.text, "hi", ms, compute)
                }
            }
        } finally {
            wav.delete()
        }
    }

    private companion object {
        const val TIMEOUT_S = 120L
        const val TAG = "Incident"
    }
}

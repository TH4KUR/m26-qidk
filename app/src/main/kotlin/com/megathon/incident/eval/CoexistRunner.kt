package com.megathon.incident.eval

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.session.InMemoryReportStore
import com.megathon.incident.session.Pipeline
import com.megathon.incident.session.SessionState
import com.megathon.incident.speech.GenieXLlmEngine
import com.megathon.incident.speech.NemotronHindiSpeechEngine
import com.megathon.incident.speech.SherpaCliSpeechEngine
import com.megathon.incident.testing.SpeechEngine
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.Date
import java.util.Locale

/**
 * Debug-only: Whisper (child process, QNN 2.40) and the 4B model (GenieX, in process, QNN 2.45) take turns on the same
 * audio, [run]'s `loops` times, through the app's real paths: the speech engine, then a fresh [Pipeline.newSession]
 * fed the transcript (real prompt, model, parser, validator). The model resets its own context at the start of every
 * `complete` call, so each loop starts from an empty context. One failing loop is recorded and the run goes on.
 * Output: `<filesDir>/coexist/<timestamp>/loops.jsonl` and `summary.json`.
 */
class CoexistRunner(
    private val filesDir: File,
    private val speech: SpeechEngine,
    private val llm: LlmEngine,
    /** The language handed to the recogniser: `en` (Whisper child) or `hi` (the experimental Nemotron child). */
    private val language: String = "en",
) {
    private val json = Json { encodeDefaults = true; prettyPrint = false }
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    suspend fun run(pcm: ShortArray, loops: Int): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outDir = File(filesDir, "coexist/$stamp").apply { mkdirs() }
        val lines = File(outDir, "loops.jsonl").apply { writeText("") }
        // Load the model once up front so loop 1's LLM time is not the load time; the load is logged on its own.
        (llm as? GenieXLlmEngine)?.let { g ->
            runCatching { g.warmUp() }
                .onSuccess { android.util.Log.i(TAG, "coexist model load: $it ms") }
                .onFailure { android.util.Log.e(TAG, "coexist model load failed (loops will report it)", it) }
        }
        val records = mutableListOf<CoexistLoop>()
        for (i in 1..loops) {
            val rec = oneLoop(i, pcm)
            records += rec
            lines.appendText(json.encodeToString(CoexistLoop.serializer(), rec) + "\n")
            android.util.Log.i(
                TAG,
                "coexist loop $i/$loops stt=${rec.sttMs}ms llm=${rec.llmMs}ms total=${rec.totalMs}ms ok=${rec.ok} pss=${rec.pssMb}MB " +
                    "childExit=${rec.childExit}${rec.error?.let { " error=$it" } ?: ""}",
            )
        }
        File(outDir, "summary.json").writeText(pretty.encodeToString(CoexistSummary.serializer(), CoexistSummary.of(records)))
        return outDir
    }

    private suspend fun oneLoop(index: Int, pcm: ShortArray): CoexistLoop {
        val started = System.nanoTime()
        var sttMs: Long? = null
        var llmMs: Long? = null
        var transcript: String? = null
        var error: String? = null
        try {
            val s0 = System.nanoTime()
            val heard = speech.transcribe(pcm, language)
            sttMs = (System.nanoTime() - s0) / 1_000_000
            transcript = heard.text
            val recording = RecordingLlmEngine(llm)
            val session = Pipeline.newSession(recording, InMemoryReportStore(), Clock.systemUTC(), java.time.ZoneId.of("Asia/Kolkata"))
            val l0 = System.nanoTime()
            try {
                session.onUtterance(heard.text, null)
            } finally {
                llmMs = (System.nanoTime() - l0) / 1_000_000
            }
            // The session catches model errors itself, so look at what actually happened, not just at exceptions.
            recording.calls.firstOrNull { it.error != null }?.let { error = "llm: ${it.error}" }
            (session.ui.value.state as? SessionState.Failed)?.let { if (error == null) error = "session failed: ${it.message}" }
            if (recording.calls.isEmpty() && error == null) error = "llm was never called"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = "${e.javaClass.simpleName}: ${e.message}".take(300)
        }
        return CoexistLoop(
            index = index, sttMs = sttMs, llmMs = llmMs, totalMs = (System.nanoTime() - started) / 1_000_000,
            ok = error == null, error = error, pssMb = pssMb(),
            childExit = (speech as? SherpaCliSpeechEngine)?.lastChildExit ?: (speech as? NemotronHindiSpeechEngine)?.lastChildExit,
            transcript = transcript?.take(200),
        )
    }

    /** Proportional set size of this app's process (the speech child process is not included). */
    private fun pssMb(): Int = (android.os.Debug.getPss() / 1024).toInt()

    private companion object { const val TAG = "Incident" }
}

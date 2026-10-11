package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the Hindi lane's files are. [cli] is the online sherpa-onnx tool packed as a native library; [nativeLibDir] holds
 * `libonnxruntime.so` (the same file the Whisper child uses); [qnnDir] holds the QNN 2.40 libraries
 * (`files/sherpa/qnn`, shared with Whisper); [modelDir] is the pushed Nemotron bundle (`files/sherpa/nemotron-hi`).
 */
data class NemotronHindiPaths(val cli: String, val nativeLibDir: String, val qnnDir: String, val modelDir: String)

/** A child process to start: argument list (program first), its whole environment addition, and its working directory. */
data class NemotronHindiInvocation(val args: List<String>, val environment: Map<String, String>, val workingDir: String)

/** The Nemotron-3.5 model bundle: file names and the exact sizes recorded when it was verified on the build laptop. */
object NemotronHindiBundle {
    const val FOLDER = "nemotron-hi"
    const val TOKENS = "tokens.txt"

    /** The order matters: the CLI reads them as encoder, decoder, joiner. */
    val CONTEXT_BINARIES = listOf("encoder.bin", "decoder.bin", "joiner.bin")

    val EXPECTED_BYTES: Map<String, Long> = mapOf(
        "encoder.bin" to 610_848_768L,
        "decoder.bin" to 29_978_624L,
        "joiner.bin" to 9_732_096L,
    )

    /**
     * What is wrong with the bundle, one plain sentence each; empty when it is usable. [sizeOf] returns a file's size
     * in bytes by name, or null when it does not exist. A context binary of the wrong size is a truncated push.
     */
    fun problems(sizeOf: (String) -> Long?): List<String> = buildList {
        for (name in CONTEXT_BINARIES) {
            val size = sizeOf(name)
            val expected = EXPECTED_BYTES.getValue(name)
            when {
                size == null -> add("$FOLDER/$name is missing")
                size != expected -> add("$FOLDER/$name has $size bytes, expected $expected (a truncated push?)")
            }
        }
        val tokens = sizeOf(TOKENS)
        when {
            tokens == null -> add("$FOLDER/$TOKENS is missing")
            tokens <= 0L -> add("$FOLDER/$TOKENS is empty")
        }
    }
}

/** Builds the child-process command for one utterance. Pure, so the exact flags are unit-tested. */
object NemotronHindiCommand {
    /**
     * The verified invocation: the ONLINE CLI, `--model-type=nemo_transducer` (without it the CLI refuses the bundle),
     * the QNN provider and the three context binaries. The library path and the DSP search path are the same ones the
     * Whisper child gets (QNN 2.40 from `files/sherpa/qnn`; GenieX's 2.45 is never on either path). The ADSP separator is a
     * semicolon, as the DSP loader requires.
     *
     * @throws IllegalArgumentException when [language] is not Hindi: this lane never feeds the Hindi model other audio.
     */
    fun build(paths: NemotronHindiPaths, language: String, wavPath: String): NemotronHindiInvocation {
        val lane = SpeechLanes.select(language)
        require(lane.engine == RecognitionEngine.NEMOTRON_HINDI) {
            "the Hindi speech engine only handles Hindi (hi), not \"$language\""
        }
        val m = paths.modelDir
        val contexts = NemotronHindiBundle.CONTEXT_BINARIES.joinToString(",") { "$m/$it" }
        val args = listOf(
            paths.cli,
            "--provider=qnn",
            "--model-type=nemo_transducer",
            "--tokens=$m/${NemotronHindiBundle.TOKENS}",
            "--transducer.qnn-backend-lib=${paths.qnnDir}/libQnnHtp.so",
            "--transducer.qnn-system-lib=${paths.qnnDir}/libQnnSystem.so",
            "--transducer.qnn-context-binary=$contexts",
            "--language=${lane.language}",
            wavPath,
        )
        val environment = mapOf(
            "LD_LIBRARY_PATH" to "${paths.qnnDir}:${paths.nativeLibDir}:/system/lib64:/vendor/lib64",
            "ADSP_LIBRARY_PATH" to "${paths.qnnDir};/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp",
        )
        return NemotronHindiInvocation(args, environment, workingDir = m)
    }

    /** NPU only when this run asked for the QNN provider (the CLI has no CPU fallback: a failed QNN start is a failed run). */
    fun computeFor(args: List<String>): Compute = if ("--provider=qnn" in args) Compute.NPU else Compute.CPU
}

sealed interface NemotronHindiResult {
    /** [text] is blank when the model ran and heard nothing (silence). That is a real result, not a failure. */
    data class Heard(val text: String) : NemotronHindiResult

    data class Failed(val reason: String) : NemotronHindiResult
}

/** Reads the whole output of the online CLI (warnings, a human line, then a JSON line with a `text` field). Never throws. */
object NemotronHindiOutput {
    private const val TAIL_CHARS = 300

    fun parse(output: String, exitCode: Int): NemotronHindiResult {
        if (exitCode != 0) {
            return NemotronHindiResult.Failed("the Hindi speech engine exited with status $exitCode: ${tail(output)}")
        }
        if (output.isBlank()) return NemotronHindiResult.Failed("the Hindi speech engine produced no output")

        val spans = buildList {
            output.lineSequence().forEach { line ->
                val at = line.indexOf('{')
                if (at >= 0) add(line.substring(at).trim())
            }
            // A pretty-printed result spans lines: try the whole first-brace-to-last-brace span last.
            val first = output.indexOf('{')
            val last = output.lastIndexOf('}')
            if (first >= 0 && last > first) add(output.substring(first, last + 1))
        }
        if (spans.none { "\"text\"" in it }) {
            return NemotronHindiResult.Failed("the Hindi speech engine printed no result line (no JSON with a text field): ${tail(output)}")
        }
        for (span in spans) {
            val text = textOf(span) ?: continue
            return NemotronHindiResult.Heard(text.trim())
        }
        return NemotronHindiResult.Failed("the Hindi speech engine's result line was unreadable: ${tail(output)}")
    }

    private fun textOf(json: String): String? {
        val obj = try {
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val text = obj["text"] as? JsonPrimitive ?: return null
        return if (text.isString) text.content else null
    }

    private fun tail(output: String): String = output.trim().takeLast(TAIL_CHARS)
}

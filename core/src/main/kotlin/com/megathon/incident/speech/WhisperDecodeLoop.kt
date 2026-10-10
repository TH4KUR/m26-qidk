package com.megathon.incident.speech

/** The Whisper special-token ids the decode loop needs. [multilingual] has the real values for the multilingual vocabulary. */
data class WhisperTokenIds(
    val eot: Int,
    val sot: Int,
    val transcribe: Int,
    val noTimestamps: Int,
    /** Every id from here up is a timestamp token (`<|0.00|>` and later). */
    val timestampBegin: Int,
    val languages: Map<String, Int>,
    /** Never chosen at any step. */
    val suppress: Set<Int>,
    /** Never chosen as the first generated token (a space and end of text). */
    val beginSuppress: Set<Int>,
) {
    /** `[SOT, <|lang|>, <|transcribe|>, <|notimestamps|>]`: the forced prompt that fixes the language and the task. */
    fun prompt(language: String): List<Int> {
        val lang = languages[language]
            ?: throw IllegalArgumentException("unsupported language '$language' (supported: ${languages.keys.sorted()})")
        return listOf(sot, lang, transcribe, noTimestamps)
    }

    companion object {
        /**
         * Ids read from the multilingual tokenizer (openai/whisper-tiny family) on 2026-10-10: `<|hi|>` 50276,
         * `<|te|>` 50299, forced prompt for Hindi `[50258, 50276, 50359, 50363]`. Only the three languages the app
         * offers are listed. The 88 suppressed ids come from the model's `generation_config.json`.
         */
        fun multilingual(): WhisperTokenIds {
            val stream = requireNotNull(WhisperTokenIds::class.java.getResourceAsStream("/whisper/multilingual_suppress_tokens.txt")) {
                "whisper/multilingual_suppress_tokens.txt is missing from the resources"
            }
            val suppress = stream.bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.toInt() }.toSet()
            }
            return WhisperTokenIds(
                eot = 50257, sot = 50258, transcribe = 50359, noTimestamps = 50363, timestampBegin = 50364,
                languages = mapOf("en" to 50259, "hi" to 50276, "te" to 50299),
                suppress = suppress, beginSuppress = setOf(220, 50257),
            )
        }
    }
}

/**
 * One step of the decoder: feed one token id at a position, get the logits over the vocabulary for the next one.
 * The implementation keeps its own attention cache between calls (on the device: the QNN decoder graph).
 */
fun interface WhisperDecoderStep {
    fun step(inputId: Int, position: Int): FloatArray
}

/**
 * Our own greedy decode loop for engine 2b (Qualcomm AI Hub's multilingual Whisper binaries). Their reference
 * loop starts from the start token alone, so the model picks the language itself; here the language is forced by
 * feeding the whole prompt first, as the app's language toggle requires. Suppressed and timestamp tokens are
 * never chosen, the loop stops at end of text or at [maxTokens] (the binaries decode at most 200), and a token
 * repeated [maxRepeat] times in a row (byte-level BPE can loop on Indic text) is cut and kept once.
 */
class WhisperDecodeLoop(
    private val ids: WhisperTokenIds,
    private val maxTokens: Int = 200,
    private val maxRepeat: Int = 8,
) {
    /** The generated token ids, without the prompt and without end of text. */
    fun decode(language: String, decoder: WhisperDecoderStep): List<Int> {
        val prompt = ids.prompt(language)
        var logits = FloatArray(0)
        prompt.forEachIndexed { position, id -> logits = decoder.step(id, position) }

        val out = ArrayList<Int>()
        var position = prompt.size
        var runToken = -1
        var run = 0
        while (prompt.size + out.size < maxTokens) {
            val next = pick(logits, first = out.isEmpty())
            if (next == ids.eot) break
            if (next == runToken) run++ else { runToken = next; run = 1 }
            if (run >= maxRepeat) {
                repeat(run - 2) { out.removeAt(out.lastIndex) } // keep one copy of the looping token
                break
            }
            out += next
            if (prompt.size + out.size >= maxTokens) break
            logits = decoder.step(next, position++)
        }
        return out
    }

    private fun pick(logits: FloatArray, first: Boolean): Int {
        var best = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (id in logits.indices) {
            if (id in ids.suppress || id >= ids.timestampBegin || (first && id in ids.beginSuppress)) continue
            if (logits[id] > bestScore) { best = id; bestScore = logits[id] }
        }
        check(best >= 0) { "every token was suppressed" }
        return best
    }
}

package com.megathon.incident.speech

/**
 * Guards against the NPU's documented failure mode: output that is silently wrong ("!!!!", repeated tokens)
 * with no error. The same clip is transcribed on the NPU and on a CPU reference, and the two must agree
 * within a word-error-rate tolerance. The CPU text is the reference; the NPU text is what is being judged.
 */
object TranscriptAgreement {
    data class Verdict(val agrees: Boolean, val wer: Double?, val detail: String)

    fun compare(npuText: String, cpuReference: String, maxWer: Double = 0.25): Verdict {
        val referenceWords = words(cpuReference)
        if (referenceWords.isEmpty()) {
            return Verdict(false, null, "the CPU reference is empty, so the NPU output cannot be judged")
        }
        val wer = wordErrorRate(cpuReference, npuText)!!
        val detail = "WER ${"%.1f".format(wer * 100)}% between NPU and CPU (tolerance ${"%.1f".format(maxWer * 100)}%)"
        return Verdict(wer <= maxWer, wer, detail)
    }

    /** Edits (substitute, insert, delete) divided by reference words; null when the reference has no words. */
    fun wordErrorRate(reference: String, hypothesis: String): Double? {
        val ref = words(reference)
        if (ref.isEmpty()) return null
        val hyp = words(hypothesis)
        var previous = IntArray(hyp.size + 1) { it }
        for (i in 1..ref.size) {
            val current = IntArray(hyp.size + 1)
            current[0] = i
            for (j in 1..hyp.size) {
                val substitution = previous[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            previous = current
        }
        return previous[hyp.size].toDouble() / ref.size
    }

    private fun words(text: String): List<String> =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}'\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
}

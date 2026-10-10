package com.megathon.incident.domain

import java.text.Normalizer

/**
 * Decides whether an LLM-proposed value is supported by what the speaker actually said (spec D7).
 * Quote presence establishes provenance, not entailment: negation flips the result because digits,
 * negations and word order are kept, never dropped.
 */
object GroundingValidator {
    private const val OVERLAP_WITH_EVIDENCE = 0.5
    private const val OVERLAP_WITH_TRANSCRIPT = 0.8

    private val STOP_WORDS = setOf("the", "a", "an", "of", "in", "at", "near", "around", "to", "on", "is", "was", "and")
    private val SEPARATORS = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    /**
     * @param enumValue true for fields whose value is a contract enum (the value is not a quote, so only
     *                  the evidence is checked against the transcript).
     */
    fun isGrounded(value: String?, evidence: String?, transcript: String, enumValue: Boolean = false): Boolean {
        if (value.isNullOrBlank()) return false
        val haystack = tokens(transcript)
        val evidenceTokens = evidence?.takeIf { it.isNotBlank() }?.let { tokens(it) }

        if (evidenceTokens == null || evidenceTokens.isEmpty()) {
            if (enumValue) return false
            return overlap(contentTokens(value), haystack.toSet()) >= OVERLAP_WITH_TRANSCRIPT
        }
        if (!containsSequence(haystack, evidenceTokens)) return false
        if (enumValue) return true
        return overlap(contentTokens(value), evidenceTokens.toSet()) >= OVERLAP_WITH_EVIDENCE
    }

    /** True when [phrase] occurs in [text] as a whole-token, in-order sequence (case, punctuation ignored). */
    fun containsPhrase(text: String, phrase: String): Boolean {
        val needle = tokens(phrase)
        return needle.isNotEmpty() && containsSequence(tokens(text), needle)
    }

    internal fun tokens(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
            .split(SEPARATORS).filter { it.isNotEmpty() }

    private fun contentTokens(text: String): List<String> = tokens(text).filterNot { it in STOP_WORDS }

    /** Fraction of [valueTokens] present in [reference]; 0 when there is nothing to compare. */
    private fun overlap(valueTokens: List<String>, reference: Set<String>): Double {
        if (valueTokens.isEmpty()) return 0.0
        return valueTokens.count { it in reference }.toDouble() / valueTokens.size
    }

    /** Whole-token, in-order match, so "room 2" never matches inside "room 20". */
    private fun containsSequence(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.size > haystack.size) return false
        for (start in 0..haystack.size - needle.size) {
            var all = true
            for (k in needle.indices) {
                if (haystack[start + k] != needle[k]) { all = false; break }
            }
            if (all) return true
        }
        return false
    }
}

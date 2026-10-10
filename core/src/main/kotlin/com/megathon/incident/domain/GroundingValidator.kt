package com.megathon.incident.domain

import java.text.Normalizer

/** What the transcript says ([heard]) against what the model proposed ([meant]), both as lower-case words. */
data class NearMiss(val heard: String, val meant: String)

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

    /**
     * Same checks as [isGrounded], but a transcript word may differ from the proposed word by sound
     * ([sameSound], D's shared homophone table) or, when [allowSpelling], by one letter in a word of
     * four or more letters. Returns what was heard and what was proposed, or null when the value is
     * not a near-miss (including when it is already grounded exactly).
     *
     * A near-miss is never "grounded": the caller must store it as a suggestion (a chip), never as
     * heard, and the 0.5 / 0.8 overlap thresholds are not lowered. Devanagari tokens are matched by
     * sound table only; a one-letter difference in a Hindi word is a different word.
     */
    fun nearMiss(
        value: String?,
        evidence: String?,
        transcript: String,
        enumValue: Boolean = false,
        allowSpelling: Boolean = true,
        sameSound: (String, String) -> Boolean,
    ): NearMiss? {
        if (value.isNullOrBlank()) return null
        val haystack = tokens(transcript)
        val evidenceTokens = evidence?.takeIf { it.isNotBlank() }?.let { tokens(it) }?.takeIf { it.isNotEmpty() }
        fun near(heard: String, meant: String) = nearToken(heard, meant, allowSpelling, sameSound)

        if (evidenceTokens == null) {
            if (enumValue) return null
            val content = contentTokens(value)
            if (content.isEmpty()) return null
            val pool = haystack.toSet()
            val differing = mutableListOf<Pair<String, String>>()
            var matched = 0
            for (v in content) {
                if (v in pool) { matched++; continue }
                val h = haystack.firstOrNull { near(it, v) } ?: continue
                matched++
                differing += h to v
            }
            if (differing.isEmpty() || matched.toDouble() / content.size < OVERLAP_WITH_TRANSCRIPT) return null
            return NearMiss(differing.joinToString(" ") { it.first }, differing.joinToString(" ") { it.second })
        }

        if (containsSequence(haystack, evidenceTokens)) return null // exact: not a near-miss
        if (!enumValue && overlap(contentTokens(value), evidenceTokens.toSet()) < OVERLAP_WITH_EVIDENCE) return null
        if (evidenceTokens.size > haystack.size) return null
        for (start in 0..haystack.size - evidenceTokens.size) {
            val window = haystack.subList(start, start + evidenceTokens.size)
            if (evidenceTokens.indices.all { k -> window[k] == evidenceTokens[k] || near(window[k], evidenceTokens[k]) }) {
                return NearMiss(window.joinToString(" "), evidenceTokens.joinToString(" "))
            }
        }
        return null
    }

    private fun nearToken(heard: String, meant: String, allowSpelling: Boolean, sameSound: (String, String) -> Boolean): Boolean {
        if (heard == meant) return false
        if (sameSound(heard, meant)) return true
        return allowSpelling && heard.length >= 4 && meant.length >= 4 &&
            heard.all { it.isLetter() } && meant.all { it.isLetter() } && withinOneEdit(heard, meant)
    }

    /** True when [a] and [b] differ by at most one insertion, deletion or substitution. */
    private fun withinOneEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return when {
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
            a.length < b.length -> a.substring(i) == b.substring(i + 1)
            else -> a.substring(i + 1) == b.substring(i)
        }
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

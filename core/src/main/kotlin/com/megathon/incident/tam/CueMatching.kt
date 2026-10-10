package com.megathon.incident.tam

import java.util.concurrent.ConcurrentHashMap

internal data class CueHit(val phrase: String, val range: IntRange)

/**
 * Phrase matching shared by the TAM detector and the urgency extractor, so negation and masking
 * behave identically in both. Both scripts match whole words: a Devanagari word ends where the
 * Devanagari block (letters, matras, virama, joiners) ends.
 */
internal object CueMatching {
    private val DEVANAGARI = 0x0900..0x097F
    private val NUMBER_AFTER = Regex("^\\.?\\s*\\d")
    private val regexCache = ConcurrentHashMap<String, Regex>()

    fun isDevanagari(phrase: String) = phrase.any { it.code in DEVANAGARI }

    private const val LATIN_BEFORE = "(?<![\\p{L}\\p{N}'])"
    private const val LATIN_AFTER = "(?![\\p{L}\\p{N}'])"
    // A Devanagari word ends where the Devanagari block (letters, matras, virama, ZWJ/ZWNJ) ends.
    // The danda and double danda (U+0964, U+0965) are punctuation, not part of a word.
    private const val DEVA_BEFORE = "(?<![\\u0900-\\u0963\\u0966-\\u097F\\u200C\\u200D])"
    private const val DEVA_AFTER = "(?![\\u0900-\\u0963\\u0966-\\u097F\\u200C\\u200D])"

    /**
     * All occurrences of every phrase, longest phrases first. [text] must already be lower-cased and
     * NFC-normalised. Both scripts match whole words: "आग" (fire) does not match inside "आगे" (ahead)
     * and "काबू" (under control) does not match inside "बेकाबू" (out of control).
     */
    fun find(text: String, phrases: Collection<String>): List<CueHit> {
        val hits = mutableListOf<CueHit>()
        for (phrase in phrases.sortedByDescending { it.length }) {
            if (phrase.isEmpty()) continue
            val regex = regexCache.getOrPut(phrase) {
                val (before, after) = if (isDevanagari(phrase)) DEVA_BEFORE to DEVA_AFTER else LATIN_BEFORE to LATIN_AFTER
                Regex(before + Regex.escape(phrase) + after)
            }
            regex.findAll(text).forEach { hits += CueHit(phrase, it.range) }
        }
        return hits
    }

    /** Replaces every hit with spaces (same length), so later scans cannot see those characters. */
    fun mask(text: String, hits: List<CueHit>): String {
        val chars = text.toCharArray()
        for (h in hits) for (i in h.range) chars[i] = ' '
        return String(chars)
    }

    /** "room no. 2", "flat no 14": "no" is an abbreviation for "number", not a negation. */
    fun isNumberAbbreviation(text: String, range: IntRange): Boolean =
        NUMBER_AFTER.containsMatchIn(text.substring(range.last + 1))

    /** Hits of [negations] in [maskedText] after dropping the "no. 2" abbreviation reading. */
    fun negations(maskedText: String, negations: Collection<String>): List<CueHit> =
        find(maskedText, negations).filterNot { it.phrase == "no" && isNumberAbbreviation(maskedText, it.range) }

    fun overlaps(a: IntRange, b: IntRange): Boolean = a.first <= b.last && b.first <= a.last

    private val WORD = Regex("[\\p{L}\\p{M}\\p{N}]+")

    /**
     * Number of whole words between two hits (0 when they touch or overlap). Used to decide whether a
     * negation belongs to a cue: "has not been fixed" (1 word apart) versus "nobody was hurt and it
     * was fixed" (5 words apart).
     */
    fun gapWords(text: String, a: IntRange, b: IntRange): Int {
        if (overlaps(a, b)) return 0
        val (first, second) = if (a.first < b.first) a to b else b to a
        val from = first.last + 1
        val to = second.first
        if (from >= to) return 0
        return WORD.findAll(text.substring(from, to)).count()
    }
}

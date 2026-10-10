package com.megathon.incident.tam

import java.util.concurrent.ConcurrentHashMap

internal data class CueHit(val phrase: String, val range: IntRange)

/**
 * Phrase matching shared by the TAM detector and the urgency extractor, so negation and masking
 * behave identically in both. Latin phrases match whole words; Devanagari phrases match as substrings
 * (the script has no reliable word boundaries in ASR output).
 */
internal object CueMatching {
    private val DEVANAGARI = 0x0900..0x097F
    private val NUMBER_AFTER = Regex("^\\.?\\s*\\d")
    private val regexCache = ConcurrentHashMap<String, Regex>()

    fun isDevanagari(phrase: String) = phrase.any { it.code in DEVANAGARI }

    /** All occurrences of every phrase, longest phrases first. [text] must already be lower-cased. */
    fun find(text: String, phrases: Collection<String>): List<CueHit> {
        val hits = mutableListOf<CueHit>()
        for (phrase in phrases.sortedByDescending { it.length }) {
            if (phrase.isEmpty()) continue
            if (isDevanagari(phrase)) {
                var from = 0
                while (true) {
                    val i = text.indexOf(phrase, from)
                    if (i < 0) break
                    hits += CueHit(phrase, i until i + phrase.length)
                    from = i + phrase.length
                }
            } else {
                val regex = regexCache.getOrPut(phrase) {
                    Regex("(?<![\\p{L}\\p{N}'])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}'])")
                }
                regex.findAll(text).forEach { hits += CueHit(phrase, it.range) }
            }
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
}

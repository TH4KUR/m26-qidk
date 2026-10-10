package com.megathon.incident.tam

import java.text.Normalizer

/**
 * Spans of hedging language in a text ("I think", "maybe", "shayad"). D uses it to mark a value
 * UNCONFIRMED; it is the part of [TamDetector] that D needs on its own.
 *
 * Ranges index the NFC-normalised, lower-cased text (identical to the input for ordinary Latin and
 * precomposed Devanagari), are sorted, and never overlap.
 */
interface HedgeDetector {
    fun hedges(text: String): List<IntRange>
}

class TamHedgeDetector(private val lists: TamCueLists) : HedgeDetector {
    override fun hedges(text: String): List<IntRange> {
        val t = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
        val kept = mutableListOf<IntRange>()
        // Longest phrases first so "i think" wins over a shorter overlapping cue.
        for (hit in CueMatching.find(t, lists.hedged).sortedByDescending { it.phrase.length }) {
            if (kept.none { CueMatching.overlaps(it, hit.range) }) kept += hit.range
        }
        return kept.sortedBy { it.first }
    }
}

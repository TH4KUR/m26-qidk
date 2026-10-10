package com.megathon.incident.testing

import com.megathon.incident.tam.HedgeDetector

/** A few fixed hedge words, so D can code and test against the interface before the real detector. */
class FakeHedgeDetector(private val words: List<String> = listOf("i think", "maybe", "probably", "shayad")) : HedgeDetector {
    override fun hedges(text: String): List<IntRange> {
        val t = text.lowercase()
        val out = mutableListOf<IntRange>()
        for (w in words.sortedByDescending { it.length }) {
            var from = 0
            while (true) {
                val i = t.indexOf(w, from)
                if (i < 0) break
                val r = i until i + w.length
                if (out.none { it.first <= r.last && r.first <= it.last }) out += r
                from = i + w.length
            }
        }
        return out.sortedBy { it.first }
    }
}

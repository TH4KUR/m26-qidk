package com.megathon.incident.suggest

import java.text.Normalizer

/** A site entry the heard words most likely meant, with the reason shown on the chip. */
data class Snap(val entry: SiteEntry, val reason: String)

/**
 * Maps a heard place or asset ("conference room to", "lab see four", "maple hostle") to a site name.
 * Three steps, first hit wins:
 *  1. exact name or alias ("CR2" -> Conference Room 2); the canonical name itself needs no chip;
 *  2. sound-alike and number words, via [HomophoneTable] and spoken digits ("to" -> 2, "see" -> C,
 *     "one oh five" -> 105);
 *  3. fuzzy spelling with [JaroWinkler] at [threshold].
 * Numbers and single letters are identifiers and must match exactly in every step, so "Room 20"
 * never becomes "Room 2" and a room missing from the site never snaps to a neighbour. Ambiguous
 * matches give nothing. Rules and arithmetic on the CPU only.
 */
class GazetteerSnapper(
    private val site: SiteDirectory,
    private val homophones: HomophoneTable,
    private val threshold: Double = 0.88,
) {

    fun snap(heard: String, kind: EntryKind): Snap? {
        val words = dropLeadingFillers(words(heard))
        if (words.isEmpty()) return null
        val said = words.joinToString(" ")

        site.lookup(said)?.let { hit ->
            if (hit.kind != kind || SiteDirectory.key(hit.name) == SiteDirectory.key(said)) return null
            return Snap(hit, "Heard \"$said\"; the site has ${hit.name}.")
        }

        val variants = expand(words)
        val soundAlike = variants.mapNotNull { v -> site.lookup(v.joinToString(" "))?.takeIf { it.kind == kind }?.let { v to it } }
        if (soundAlike.isNotEmpty()) {
            if (soundAlike.map { it.second.id }.distinct().size > 1) return null
            val (variant, hit) = soundAlike.first()
            return Snap(hit, "Heard \"${changedSpan(words, variant)}\"; the site has ${hit.name}.")
        }

        return fuzzy(asIdentifiers(words), kind)?.let { Snap(it, "Heard \"$said\"; closest site name is ${it.name}.") }
    }

    /**
     * One reading for fuzzy matching in which every word that can be a number or a letter is one
     * ("two"/"to"/"too" -> 2, "see" -> c). Otherwise a number spelled as a word would slip past the
     * exact-identifier rule: "chiller two" would fuzzily match "chiller".
     */
    private fun asIdentifiers(words: List<String>): List<String> = words.map { w ->
        val variants = homophones.variants(w)
        DIGITS[w]
            ?: variants.firstOrNull { v -> v.all(Char::isDigit) }
            ?: variants.firstOrNull { v -> v.length == 1 }.takeIf { w.length > 1 }
            ?: w
    }

    /** Every reading of [words] with sound-alikes and spoken digits swapped in, excluding [words] itself. */
    private fun expand(words: List<String>): List<List<String>> {
        val options = words.map { w -> (listOf(w) + homophones.variants(w) + listOfNotNull(DIGITS[w])).distinct() }
        var readings = listOf(emptyList<String>())
        for (opts in options) {
            readings = readings.flatMap { r -> opts.map { r + it } }
            if (readings.size > MAX_READINGS) return emptyList()
        }
        return readings.filter { it != words }
    }

    private fun fuzzy(reading: List<String>, kind: EntryKind): SiteEntry? {
        val (ids, text) = split(reading)
        if (text.isEmpty()) return null
        val best = mutableMapOf<String, Pair<SiteEntry, Double>>()
        for (entry in site.entries) {
            if (entry.kind != kind) continue
            for (form in listOf(entry.name) + entry.aliases) {
                val (formIds, formText) = split(asIdentifiers(dropLeadingFillers(words(form))))
                if (formIds != ids || formText.isEmpty()) continue
                val score = JaroWinkler.similarity(text, formText)
                if (score > (best[entry.id]?.second ?: -1.0)) best[entry.id] = entry to score
            }
        }
        val ranked = best.values.sortedByDescending { it.second }
        val top = ranked.firstOrNull() ?: return null
        if (top.second < threshold) return null
        val runnerUp = ranked.getOrNull(1)?.second ?: 0.0
        return if (top.second - runnerUp < AMBIGUITY_MARGIN) null else top.first
    }

    companion object {
        private const val MAX_READINGS = 512
        private const val AMBIGUITY_MARGIN = 0.02
        private val SEPARATORS = Regex("[^\\p{L}\\p{M}\\p{N}]+")
        private val FILLERS = setOf("the", "a", "an", "near", "in", "at", "inside", "outside", "by", "behind", "beside", "next", "of", "from", "on")
        private val DIGITS = mapOf(
            "zero" to "0", "oh" to "0", "o" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
            "eleven" to "11", "twelve" to "12", "twenty" to "20", "thirty" to "30", "forty" to "40", "fifty" to "50",
        )

        private fun words(text: String): List<String> =
            Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().split(SEPARATORS).filter { it.isNotEmpty() }

        private fun dropLeadingFillers(words: List<String>): List<String> = words.dropWhile { it in FILLERS }

        /** Identifiers (anything with a digit, or a single letter) joined exactly; the remaining words as text. */
        private fun split(words: List<String>): Pair<String, String> {
            val (ids, text) = words.partition { w -> w.any(Char::isDigit) || w.length == 1 }
            return ids.joinToString("") to text.joinToString(" ")
        }

        /** The words that changed, plus the one before for context: "conference room to" -> "room to". */
        private fun changedSpan(heard: List<String>, variant: List<String>): String {
            val changed = heard.indices.filter { heard[it] != variant[it] }
            val from = (changed.first() - 1).coerceAtLeast(0)
            return heard.subList(from, changed.last() + 1).joinToString(" ")
        }
    }
}

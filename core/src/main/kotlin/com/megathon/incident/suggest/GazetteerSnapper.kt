package com.megathon.incident.suggest

import java.text.Normalizer

/**
 * A site entry the heard words most likely meant, with the reason shown on the chip. [proposed] is the
 * whole corrected value: the site name, or the heard phrase with only the misheard part replaced.
 */
data class Snap(val entry: SiteEntry, val reason: String, val proposed: String = entry.name)

/**
 * Maps a heard place or asset ("conference room to", "lab see four", "maple hostle") to a site name.
 * Three steps, first hit wins:
 *  1. exact name or alias ("CR2" -> Conference Room 2); the canonical name itself needs no chip;
 *  2. sound-alike and number words, via [HomophoneTable] and spoken digits ("to" -> 2, "see" -> C,
 *     "one oh five" -> 105);
 *  3. fuzzy spelling with [JaroWinkler] at [threshold].
 * If the whole phrase matches nothing, a site name *inside* it is looked for ("second floor of new
 * lotus"): steps 1 and 2 on windows of up to [MAX_SPAN] words, never a lone generic word, and only the
 * misheard part is replaced, so a more specific place keeps its detail. Two different places in one
 * phrase give a chip only for the one after "to / in / at" ("from X to Y").
 * Numbers and single letters are identifiers and must match exactly in every step, so "Room 20"
 * never becomes "Room 2" and a room missing from the site never snaps to a neighbour. Ambiguous
 * matches give nothing. Rules and arithmetic on the CPU only.
 */
class GazetteerSnapper(
    private val site: SiteDirectory,
    private val homophones: HomophoneTable,
    private val threshold: Double = 0.88,
) {

    private data class Token(val word: String, val start: Int, val end: Int)

    private sealed interface Whole {
        data class Hit(val entry: SiteEntry, val reason: String) : Whole
        object Stop : Whole
        object Miss : Whole
    }

    private data class SpanHit(val from: Int, val to: Int, val entry: SiteEntry, val canonical: Boolean, val said: String)

    fun snap(heard: String, kind: EntryKind): Snap? {
        val text = Normalizer.normalize(heard, Normalizer.Form.NFC)
        val tokens = TOKEN.findAll(text).map { Token(it.value.lowercase(), it.range.first, it.range.last + 1) }.toList()
        val first = tokens.indexOfFirst { it.word !in FILLERS }
        if (first < 0) return null

        return when (val whole = matchWhole(tokens.subList(first, tokens.size).map { it.word }, kind)) {
            is Whole.Hit -> Snap(whole.entry, whole.reason, render(text, tokens, listOf(Triple(first, tokens.size - 1, whole.entry.name))))
            Whole.Stop -> null
            // Inside a longer phrase an equipment word names a type ("hot water heater"), not one asset.
            Whole.Miss -> if (kind == EntryKind.PLACE) matchSpans(text, tokens, first, kind) else null
        }
    }

    /** The three steps on the whole phrase (after leading fillers). Stop = a definite "no chip". */
    private fun matchWhole(words: List<String>, kind: EntryKind): Whole {
        val said = words.joinToString(" ")
        site.lookup(said)?.let { hit ->
            if (hit.kind != kind || SiteDirectory.key(hit.name) == SiteDirectory.key(said)) return Whole.Stop
            return Whole.Hit(hit, "Heard \"$said\"; the site has ${hit.name}.")
        }
        soundAlike(words, kind)?.let { (variant, hit) ->
            return if (hit == null) Whole.Stop else Whole.Hit(hit, "Heard \"${changedSpan(words, variant)}\"; the site has ${hit.name}.")
        }
        return fuzzy(asIdentifiers(words), kind)?.let { Whole.Hit(it, "Heard \"$said\"; closest site name is ${it.name}.") } ?: Whole.Miss
    }

    /** Null: no sound-alike reading names a site entry. A null entry: readings name different entries (ambiguous). */
    private fun soundAlike(words: List<String>, kind: EntryKind): Pair<List<String>, SiteEntry?>? {
        val hits = expand(words).mapNotNull { v -> site.lookup(v.joinToString(" "))?.takeIf { it.kind == kind }?.let { v to it } }
        if (hits.isEmpty()) return null
        return if (hits.map { it.second.id }.distinct().size > 1) hits.first().first to null else hits.first()
    }

    private fun matchSpans(text: String, tokens: List<Token>, first: Int, kind: EntryKind): Snap? {
        val hits = mutableListOf<SpanHit>()
        var i = first
        while (i < tokens.size) {
            val hit = (minOf(MAX_SPAN, tokens.size - i) downTo 1).firstNotNullOfOrNull { len -> spanAt(tokens, i, len, kind) }
            if (hit != null) { hits += hit; i = hit.to + 1 } else i++
        }
        val places = hits.map { it.entry }.distinctBy { it.id }
        return when {
            places.isEmpty() -> null
            places.size == 1 -> {
                val entry = places.single()
                // The right name already said ("library reading room") needs no chip.
                if (SiteDirectory.key(entry.name) in SiteDirectory.key(text)) return null
                val fixes = hits.filterNot { it.canonical }
                if (fixes.isEmpty()) return null
                Snap(entry, "Heard \"${fixes.first().said}\"; the site has ${entry.name}.", render(text, tokens, fixes.map { Triple(it.from, it.to, entry.name) }))
            }
            else -> {
                val targets = hits.filter { h -> wordBefore(tokens, h.from) in DIRECTION }.map { it.entry }.distinctBy { it.id }
                val target = targets.singleOrNull() ?: return null
                val names = places.joinToString("\" and \"") { it.name }
                Snap(target, "Heard two places, \"$names\"; the incident seems to be at ${target.name}.")
            }
        }
    }

    /** A window that names a site entry exactly or through sound-alikes; a lone word only if it holds a digit ("sh2"). */
    private fun spanAt(tokens: List<Token>, from: Int, len: Int, kind: EntryKind): SpanHit? {
        val words = tokens.subList(from, from + len).map { it.word }
        if (len == 1 && words[0].none(Char::isDigit)) return null
        val said = words.joinToString(" ")
        site.lookup(said)?.takeIf { it.kind == kind }?.let { e ->
            return SpanHit(from, from + len - 1, e, SiteDirectory.key(e.name) == SiteDirectory.key(said), said)
        }
        val (variant, hit) = soundAlike(words, kind) ?: return null
        return hit?.let { SpanHit(from, from + len - 1, it, false, changedSpan(words, variant)) }
    }

    /** The word before a token, skipping articles ("to the research block" -> "to"). */
    private fun wordBefore(tokens: List<Token>, index: Int): String? =
        tokens.subList(0, index).lastOrNull { it.word !in ARTICLES }?.word

    /**
     * [text] with each (from, to) token range replaced by a name, right to left; an article right before a
     * replaced range goes too ("a sh2" -> "Seminar Hall 2", "near the conference room to" -> "near Conference Room 2").
     */
    private fun render(text: String, tokens: List<Token>, replacements: List<Triple<Int, Int, String>>): String {
        var out = text
        for ((from, to, name) in replacements.sortedByDescending { it.first }) {
            val start = if (from > 0 && tokens[from - 1].word in ARTICLES) tokens[from - 1].start else tokens[from].start
            out = out.substring(0, start) + name + out.substring(tokens[to].end)
        }
        return out.replace(SPACES, " ").trim()
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

    /**
     * Spelling fixes only: the same number of words, each word close to its partner (score = the
     * weakest pair). Adding or dropping words is not a spelling fix: "sports ground gate" is a more
     * specific place than "sports ground", not a misspelling of it.
     */
    private fun fuzzy(reading: List<String>, kind: EntryKind): SiteEntry? {
        val (ids, text) = split(reading)
        if (text.isEmpty()) return null
        val best = mutableMapOf<String, Pair<SiteEntry, Double>>()
        for (entry in site.entries) {
            if (entry.kind != kind) continue
            for (form in listOf(entry.name) + entry.aliases) {
                val (formIds, formText) = split(asIdentifiers(dropLeadingFillers(words(form))))
                if (formIds != ids || formText.size != text.size) continue
                val score = text.indices.minOf { i -> JaroWinkler.similarity(text[i], formText[i]) }
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
        private const val MAX_SPAN = 6
        private val SEPARATORS = Regex("[^\\p{L}\\p{M}\\p{N}]+")
        private val TOKEN = Regex("[\\p{L}\\p{M}\\p{N}]+")
        private val SPACES = Regex("\\s+")
        private val ARTICLES = setOf("the", "a", "an")
        private val DIRECTION = setOf("to", "into", "in", "at", "inside")
        private val FILLERS = setOf("the", "a", "an", "near", "in", "at", "inside", "outside", "by", "behind", "beside", "next", "of", "from", "on")
        private val DIGITS = mapOf(
            "zero" to "0", "oh" to "0", "o" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
            "eleven" to "11", "twelve" to "12", "twenty" to "20", "thirty" to "30", "forty" to "40", "fifty" to "50",
        )

        private fun words(text: String): List<String> =
            Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().split(SEPARATORS).filter { it.isNotEmpty() }

        private fun dropLeadingFillers(words: List<String>): List<String> = words.dropWhile { it in FILLERS }

        /** Identifiers (anything with a digit, or a single letter) joined exactly; the remaining words. */
        private fun split(words: List<String>): Pair<String, List<String>> {
            val (ids, text) = words.partition { w -> w.any(Char::isDigit) || w.length == 1 }
            return ids.joinToString("") to text
        }

        /** The words that changed, plus the one before for context: "conference room to" -> "room to". */
        private fun changedSpan(heard: List<String>, variant: List<String>): String {
            val changed = heard.indices.filter { heard[it] != variant[it] }
            val from = (changed.first() - 1).coerceAtLeast(0)
            return heard.subList(from, changed.last() + 1).joinToString(" ")
        }
    }
}

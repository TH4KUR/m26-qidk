package com.megathon.incident.tam

import com.megathon.incident.domain.Aspect
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.Tam
import com.megathon.incident.domain.Tense
import java.text.Normalizer

/**
 * One clause of the transcript with its tense/aspect/modality.
 * [hedged] is kept separately because a clause can be both negated and hedged ("I think nobody was
 * hurt"); [Tam.modality] holds only the highest-precedence reading.
 */
data class TamClause(
    val text: String,
    val tam: Tam,
    val hedged: Boolean,
    val cues: List<String>,
    /** ONGOING because of an explicit continuation cue ("still", "since morning"), not just "is leaking". */
    val explicitOngoing: Boolean = false,
)

/**
 * Rule-based, CPU-only detector (spec D17). Decides per clause, so "the water has stopped but it is
 * still leaking" yields COMPLETED then ONGOING. Cue lists are data (see [TamCueLists]).
 *
 * Known limits: commas do not split clauses (lists of names would break negation scope), so
 * "we will call maintenance, it is still leaking" is one PLANNED clause that is still ONGOING.
 */
class TamDetector(val lists: TamCueLists) {

    fun analyze(text: String): List<TamClause> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC).replace('’', '\'')
        return splitClauses(normalized).map { analyzeClause(it) }
    }

    /**
     * [text] (any case) with stated severities and failure states ("no harm", "without an injury", "is not working",
     * "rate this as none") blanked out, same length. For callers that scan for negation words themselves.
     */
    fun maskStatedStates(text: String): String {
        val t = text.lowercase()
        val hits = CueMatching.find(t, SEVERITY_ABSENCE) + SEVERITY_NONE.findAll(t).map { CueHit("none", it.groups[1]!!.range) }
        return CueMatching.mask(t, hits)
    }

    // ---- clause splitting ----------------------------------------------------------------------

    private val breakRegex: Regex? = lists.clauseBreaks.takeIf { it.isNotEmpty() }?.let { words ->
        val latin = words.filter { w -> w.none { it.code in DEVANAGARI } }.sortedByDescending { it.length }
        val deva = words.filter { w -> w.any { it.code in DEVANAGARI } }.sortedByDescending { it.length }
        val parts = buildList {
            if (latin.isNotEmpty()) add("(?<![\\p{L}\\p{N}])(?:" + latin.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])")
            if (deva.isNotEmpty()) add("(?:" + deva.joinToString("|") { Regex.escape(it) } + ")")
        }
        Regex(parts.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    private fun splitClauses(text: String): List<String> =
        text.split(SENTENCE_END)
            .flatMap { sentence -> breakRegex?.let { sentence.split(it) } ?: listOf(sentence) }
            .map { it.trim() }
            .filter { it.any { c -> c.isLetterOrDigit() } }

    // ---- clause analysis -----------------------------------------------------------------------

    private fun analyzeClause(clause: String): TamClause {
        val t = withoutNames(clause).lowercase()

        val hedged = CueMatching.find(t, lists.hedged)
        val completed = CueMatching.find(t, lists.completed)
        // Mask phrases that contain a negation word ("not sure", "no longer", "pata nahi") so they are
        // not read as negation; a leftover "not" ("has not been fixed") still negates.
        // A stated severity ("no harm", "not serious", "I rate this as none") says how bad it was, not that the rest of the
        // clause did not happen: in a fragment without punctuation it must not negate "we closed the valve".
        val statedSeverity = CueMatching.find(t, SEVERITY_ABSENCE) +
            SEVERITY_NONE.findAll(t).map { CueHit("none", it.groups[1]!!.range) }
        val masked = CueMatching.mask(t, hedged + completed + statedSeverity)
        val negated = CueMatching.negations(masked, lists.negated)
        val planned = CueMatching.find(t, lists.planned)
        val explicitHits = CueMatching.find(t, lists.ongoing)
        val progressiveHits = (CueMatching.find(t, lists.progressive) + progressive(t))
            // "lag raha hai" is a hedge, not a progressive.
            .filterNot { p -> hedged.any { CueMatching.overlaps(it.range, p.range) } }
        val future = CueMatching.find(t, lists.future)
        val past = CueMatching.find(t, lists.past)
        val present = CueMatching.find(t, lists.present)

        // A negation belongs to a cue when it sits within two words of it. "has not been fixed" means
        // the problem is still there; "nobody was hurt and it was fixed" does not.
        fun negatedNearby(c: CueHit) = negated.any { CueMatching.gapWords(t, it.range, c.range) <= 2 }
        val liveExplicit = explicitHits.filterNot(::negatedNearby)
        val liveProgressive = progressiveHits.filterNot(::negatedNearby)
        val liveCompleted = completed.filterNot(::negatedNearby)
        val unresolved = completed.filter { it.phrase in lists.resolution && negatedNearby(it) }
        val ongoing = liveExplicit + liveProgressive

        val modality = when {
            negated.isNotEmpty() -> Modality.NEGATED
            planned.isNotEmpty() -> Modality.PLANNED
            hedged.isNotEmpty() -> Modality.HEDGED
            else -> Modality.ASSERTED
        }
        val aspect = when {
            ongoing.isNotEmpty() -> Aspect.ONGOING
            unresolved.isNotEmpty() -> Aspect.ONGOING
            modality == Modality.PLANNED -> Aspect.NONE
            liveCompleted.isNotEmpty() -> Aspect.COMPLETED
            else -> Aspect.NONE
        }
        val tense = when {
            aspect == Aspect.ONGOING -> Tense.PRESENT
            future.isNotEmpty() -> Tense.FUTURE
            past.isNotEmpty() || aspect == Aspect.COMPLETED -> Tense.PAST
            present.isNotEmpty() -> Tense.PRESENT
            else -> Tense.UNKNOWN
        }
        val cues = (hedged + completed + negated + planned + ongoing + future + past + present)
            .map { it.phrase }.distinct()
        return TamClause(
            clause, Tam(tense, aspect, modality), hedged = hedged.isNotEmpty(), cues = cues,
            explicitOngoing = aspect == Aspect.ONGOING && (liveExplicit.isNotEmpty() || unresolved.isNotEmpty()),
        )
    }

    /**
     * A capitalised "Bina" (without), "Diya" (did), "Will" or "May" is a person, not a cue. The clause keeps its text; only
     * the cue view is blanked, with the same length so every range stays aligned. "Will" and "May" at the very start of a
     * clause stay cues ("Will call the plumber"): there the capital letter says nothing.
     */
    private fun withoutNames(clause: String): String =
        NAME_LIKE.replace(clause) { m ->
            val atStart = m.range.first == 0
            if (atStart && m.value in MODAL_NAMES) m.value else "x".repeat(m.value.length)
        }

    /** "is leaking", "are flooding", "has been leaking": present progressive, excluding noun look-alikes. */
    private fun progressive(text: String): List<CueHit> =
        PROGRESSIVE.findAll(text)
            .filterNot { it.groupValues[1] in NON_VERB_ING }
            .map { CueHit("is " + it.groupValues[1], it.range) }
            .toList()

    private companion object {
        val DEVANAGARI = 0x0900..0x097F

        // Case-sensitive on purpose: only the capitalised spelling is read as a name.
        val NAME_LIKE = Regex("(?<![\\p{L}\\p{N}'])(Bina|Diya|Will|May)(?![\\p{L}\\p{N}'])")
        val MODAL_NAMES = setOf("Will", "May")

        // Said about how bad it was, or in what state a thing is: neither says the rest of the clause did not happen.
        val SEVERITY_ABSENCE = listOf(
            "no harm", "no damage", "nothing serious", "not serious",
            "without an injury", "without any injury", "without injury", "without injuries", "without any injuries",
            "without harm", "without any harm", "without damage", "without any damage", "without a scratch",
            // a failure state: "the reader is not working" reports a fault, it does not deny the report
            "not working", "isn't working", "is not working", "not functioning", "isn't functioning", "not responding",
            "isn't responding", "doesn't work", "does not work", "won't start", "will not start", "not starting",
            "not opening", "not charging", "not turning on", "not coming on",
            "kaam nahi kar raha", "kaam nahi kar rahi", "chal nahi raha", "chal nahi rahi",
            "काम नहीं कर रहा", "काम नहीं कर रही", "चल नहीं रहा", "चल नहीं रही",
        )
        val SEVERITY_NONE = Regex("(?:severity|rating|rated?|rates)\\b(?:\\W+\\w+){0,4}?\\W+(none)(?![\\p{L}\\p{N}])")

        val SENTENCE_END = Regex("[!?;।]+|\\.(?=\\s+[\\p{Lu}\\p{Lo}])|\\.\\s*$")
        val PROGRESSIVE = Regex(
            "(?<![\\p{L}\\p{N}])(?:is|are|am|it's|they're|we're|has been|have been)\\s+(?:\\p{L}+\\s+)?(\\p{L}{2,}ing)(?![\\p{L}])",
        )
        val NON_VERB_ING = setOf(
            "nothing", "anything", "something", "everything", "morning", "evening", "building", "ceiling",
            "during", "thing", "king", "ring", "bring", "string", "spring", "wing", "sing", "king",
        )
    }
}

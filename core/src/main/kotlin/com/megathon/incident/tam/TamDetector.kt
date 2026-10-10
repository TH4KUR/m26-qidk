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
        val t = clause.lowercase()

        val hedged = CueMatching.find(t, lists.hedged)
        val completed = CueMatching.find(t, lists.completed)
        // Mask phrases that contain a negation word ("not sure", "no longer", "pata nahi") so they are
        // not read as negation; a leftover "not" ("has not been fixed") still negates.
        val masked = CueMatching.mask(t, hedged + completed)
        val negated = CueMatching.negations(masked, lists.negated)
        val planned = CueMatching.find(t, lists.planned)
        val explicitOngoing = CueMatching.find(t, lists.ongoing)
        val ongoing = explicitOngoing + CueMatching.find(t, lists.progressive) + progressive(t)
        val future = CueMatching.find(t, lists.future)
        val past = CueMatching.find(t, lists.past)
        val present = CueMatching.find(t, lists.present)

        val modality = when {
            negated.isNotEmpty() -> Modality.NEGATED
            planned.isNotEmpty() -> Modality.PLANNED
            hedged.isNotEmpty() -> Modality.HEDGED
            else -> Modality.ASSERTED
        }
        val aspect = when {
            modality == Modality.NEGATED -> Aspect.NONE
            ongoing.isNotEmpty() -> Aspect.ONGOING
            modality == Modality.PLANNED -> Aspect.NONE
            completed.isNotEmpty() -> Aspect.COMPLETED
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
            explicitOngoing = aspect == Aspect.ONGOING && explicitOngoing.isNotEmpty(),
        )
    }

    /** "is leaking", "are flooding", "has been leaking": present progressive, excluding noun look-alikes. */
    private fun progressive(text: String): List<CueHit> =
        PROGRESSIVE.findAll(text)
            .filterNot { it.groupValues[1] in NON_VERB_ING }
            .map { CueHit("is " + it.groupValues[1], it.range) }
            .toList()

    private companion object {
        val DEVANAGARI = 0x0900..0x097F

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

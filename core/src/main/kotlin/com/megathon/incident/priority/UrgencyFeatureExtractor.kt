package com.megathon.incident.priority

import com.megathon.incident.domain.Aspect
import com.megathon.incident.domain.LlmHint
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Tam
import com.megathon.incident.domain.Tense
import com.megathon.incident.domain.UrgencyReason
import com.megathon.incident.tam.CueHit
import com.megathon.incident.tam.CueMatching
import com.megathon.incident.tam.TamDetector

/** The cumulative transcript so far plus any sound/emotion signals (design doc 2.2). */
data class UrgencyInput(
    val transcript: String,
    val signals: List<Signal> = emptyList(),
    val hint: LlmHint? = null,
)

data class HazardHit(val label: String, val impact: Int, val phrase: String, val immediacy: Int)

/**
 * Everything the scoring strategies need, computed once, so they can be compared fairly on the same
 * scenarios.
 */
data class UrgencyFeatures(
    val impact: Int,
    val immediacy: Int,
    val hazards: List<HazardHit>,
    val peopleAtRisk: Boolean,
    val distress: Boolean,
    val resolved: Boolean,
    val ongoing: Boolean,
    val lowUrgencyStated: Boolean,
    val reasons: List<UrgencyReason>,
    /** Sounds heard that call for the emergency line on their own (gunshot, fire alarm, screaming...). */
    val dangerSignals: List<String> = emptyList(),
)

/**
 * Turns a transcript into impact (1..5) and immediacy (1..4). CPU rules only.
 *
 * Principles (spec D18): signals only raise; "nobody hurt", a calm voice or a missing signal never
 * lower anything; a hazard that is explicitly negated ("there is no fire") is not counted.
 */
class UrgencyFeatureExtractor(
    private val lexicon: HazardLexicon,
    private val tam: TamDetector,
) {
    private val cfg = lexicon.file

    private data class Piece(val text: String, val tam: Tam, val explicitOngoing: Boolean)

    /** A hazard family hit, or a compound built from one. */
    private data class Found(
        val label: String, val impact: Int, val phrase: String,
        val presentIsOngoing: Boolean, val forceOngoing: Boolean, val peopleIntrinsic: Boolean,
    )

    private data class ScannedPiece(val tam: Tam, val explicitOngoing: Boolean, val negated: Boolean, val found: List<Found>)

    fun extract(input: UrgencyInput): UrgencyFeatures {
        // ---- pass 1: scan every piece --------------------------------------------------------
        var resolved = false
        var peopleAtRisk = false
        var lowUrgency = false
        var distress = EXCLAMATIONS.containsMatchIn(input.transcript)
        val weakDistress = mutableSetOf<String>()
        var anyUnresolved = false

        val scanned = pieces(input.transcript).mapIndexed { pieceIndex, p ->
            val t = p.text.lowercase()
            val raw = hazardHits(t)
            val resolution = CueMatching.find(t, cfg.resolutionCues)
            val completedHits = CueMatching.find(t, tam.lists.completed)
            val low = CueMatching.find(t, cfg.lowUrgencyCues)
            // Mask phrases that contain negation words ("not breathing", "not sure", "no longer",
            // "not urgent") so only a stand-alone negation can cancel a hazard.
            val masked = CueMatching.mask(
                t,
                raw.map { CueHit(it.phrase, it.range) } + resolution + low +
                    CueMatching.find(t, tam.lists.hedged) + completedHits,
            )
            val negHits = CueMatching.negations(masked, tam.lists.negated)
            // A negation next to a resolution cue ("has not been fixed", "kaabu mein nahi") says the
            // problem is NOT over. It does not deny the hazard.
            val closing = resolution + completedHits.filter { it.phrase in tam.lists.resolution }
            val unresolvedNegs = negHits.filter { n -> closing.any { CueMatching.gapWords(t, n.range, it.range) <= 2 } }
            val negated = negHits.size > unresolvedNegs.size
            val unresolved = !negated && unresolvedNegs.isNotEmpty()

            val people = !negated && CueMatching.find(t, cfg.peopleCues).isNotEmpty()
            if (!negated) {
                if (resolution.isNotEmpty() && !unresolved) resolved = true
                if (people) peopleAtRisk = true
                if (low.isNotEmpty()) lowUrgency = true
                val unmasked = CueMatching.mask(t, low)
                if (CueMatching.find(unmasked, cfg.distressStrongCues).isNotEmpty()) distress = true
                // Longest first, so "please hurry" and the "hurry" inside it count once.
                val kept = mutableListOf<CueHit>()
                for (h in CueMatching.find(unmasked, cfg.distressCues).sortedByDescending { it.phrase.length }) {
                    if (kept.none { CueMatching.overlaps(it.range, h.range) }) kept += h
                }
                kept.forEach { weakDistress += "$pieceIndex:${it.range.first}" }
            }
            if (unresolved) anyUnresolved = true

            val found = if (negated) emptyList() else {
                val base = raw.map { Found(it.def.label, it.def.impact, it.phrase, it.def.presentIsOngoing, false, it.def.peopleIntrinsic) }
                base + compoundHits(t, raw.map { it.def.label }, people)
            }
            ScannedPiece(p.tam, p.explicitOngoing, negated, found)
        }

        // ---- pass 2: immediacy per hazard, now that "resolved" is known ----------------------
        val hits = mutableListOf<HazardHit>()
        var ongoing = false
        var ongoingAspectSeen = false
        for (s in scanned) {
            if (!s.negated && s.tam.aspect == Aspect.ONGOING) ongoingAspectSeen = true
            for (f in s.found) {
                val m = if (anyUnresolved && f.impact > 1) 4 else hazardImmediacy(f, s.tam, s.explicitOngoing, resolved)
                if (m == 4) ongoing = true
                hits += HazardHit(f.label, f.impact, f.phrase, m)
            }
        }

        val reasons = mutableListOf<UrgencyReason>()
        var impact: Int
        var immediacy: Int
        if (hits.isEmpty()) {
            impact = cfg.defaultImpact
            immediacy = if (ongoingAspectSeen || anyUnresolved) 4 else 2
            if (ongoingAspectSeen || anyUnresolved) ongoing = true
            reasons += UrgencyReason("no hazard phrase matched; default impact ${cfg.defaultImpact}", Source.RULE)
        } else {
            impact = hits.maxOf { it.impact }
            immediacy = hits.maxOf { it.immediacy }
            val top = hits.maxWith(compareBy({ it.impact }, { it.immediacy }))
            reasons += UrgencyReason("${top.label.replace('_', ' ')} (\"${top.phrase}\") → impact ${top.impact}", Source.TEXT_CUE)
            if (immediacy >= 3) {
                reasons += UrgencyReason(
                    if (ongoing) "happening now → immediacy $immediacy" else "present or unresolved → immediacy $immediacy",
                    Source.TEXT_CUE,
                )
            }
        }

        val topImpact = scanned.flatMap { it.found }.maxOfOrNull { it.impact } ?: 0
        val peopleAreInherent = topImpact > 0 &&
            scanned.flatMap { it.found }.filter { it.impact == topImpact }.all { it.peopleIntrinsic }
        if (peopleAtRisk && !peopleAreInherent && impact in 3..4) {
            impact += 1
            reasons += UrgencyReason("people may be at risk → impact +1", Source.TEXT_CUE)
        }

        // ---- the model's hint: raise-only, one step above the rules, immediacy at most 3 ------
        input.hint?.let { h ->
            h.impact?.let { v ->
                val cap = minOf(5, impact + 1)
                val raised = minOf(v, cap)
                if (raised > impact) {
                    reasons += UrgencyReason(
                        "the language model suggests impact $v" + (if (v > cap) ", capped at $cap by the rules" else ""),
                        Source.LLM,
                    )
                    impact = raised
                }
            }
            if (h.ongoing == true && immediacy < 3) {
                immediacy = 3
                reasons += UrgencyReason("the language model says it is still continuing → immediacy 3", Source.LLM)
            }
        }

        // An explicit "not urgent" only applies to nuisances; a real hazard is never talked down.
        if (lowUrgency && impact <= 2) {
            immediacy = 1
            reasons += UrgencyReason("reporter says it is not urgent → immediacy 1", Source.TEXT_CUE)
        }

        // ---- sound and emotion signals: only ever raise --------------------------------------
        val dangerSignals = mutableListOf<String>()
        for (sig in input.signals) {
            when (sig.kind) {
                "event" -> {
                    val label = sig.label.lowercase()
                    val def = cfg.signals.firstOrNull { d -> d.matches.any { label.contains(it) } } ?: continue
                    impact = maxOf(impact, def.minImpact)
                    immediacy = maxOf(immediacy, def.minImmediacy)
                    if (def.minImmediacy >= 4) {
                        ongoing = true
                        if (def.label !in dangerSignals) dangerSignals += def.label
                    }
                    reasons += UrgencyReason("${def.label} heard → impact ≥ ${def.minImpact}, immediacy ≥ ${def.minImmediacy}", sig.source)
                }
                "emotion" -> if (cfg.distressEmotions.any { sig.label.equals(it, ignoreCase = true) }) {
                    distress = true
                    reasons += UrgencyReason("${sig.label} voice → distress", sig.source)
                }
            }
        }

        // Strong cues ("help", "bachao") and emotion count alone; weak ones ("hurry") only with a real
        // hazard or together, so "please hurry up and fix the AC" does not inflate a fault.
        if (!distress && (weakDistress.size >= 2 || (weakDistress.isNotEmpty() && impact >= 3))) distress = true
        if (distress) {
            val raised = minOf(4, immediacy + 1)
            if (raised > immediacy) {
                immediacy = raised
                reasons += UrgencyReason("distress in the report → immediacy +1", Source.TEXT_CUE)
            }
        }

        return UrgencyFeatures(
            impact = impact.coerceIn(1, 5),
            immediacy = immediacy.coerceIn(1, 4),
            hazards = hits,
            peopleAtRisk = peopleAtRisk,
            distress = distress,
            resolved = resolved,
            ongoing = ongoing,
            lowUrgencyStated = lowUrgency,
            reasons = reasons,
            dangerSignals = dangerSignals,
        )
    }

    /** The cancellation phrase in [text] ("false alarm", "galti se"), longest match, or null. */
    fun cancelCue(text: String): String? {
        val t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).lowercase()
        return CueMatching.find(t, cfg.cancelCues).maxByOrNull { it.phrase.length }?.phrase
    }

    private fun hazardImmediacy(h: Found, tam: Tam, explicitOngoing: Boolean, resolved: Boolean): Int {
        if (h.forceOngoing) return 4
        val base = when {
            // "is leaking" alone is a present state for a fault; only "still", "since", "spreading" escalate it.
            tam.aspect == Aspect.ONGOING -> if (h.impact <= 2 && !explicitOngoing) 2 else 4
            tam.modality == Modality.PLANNED || tam.tense == Tense.FUTURE -> if (h.impact <= 1) 2 else 3
            tam.tense == Tense.PAST -> if (resolved || h.impact <= 2) 2 else 3
            h.presentIsOngoing -> 4
            else -> if (h.impact <= 2) 2 else 3
        }
        // A nuisance is never more urgent than "today".
        return if (h.impact <= 1) minOf(base, 2) else base
    }

    // ---- helpers -------------------------------------------------------------------------------

    private data class RawHazard(val def: HazardDef, val phrase: String, val range: IntRange)

    private fun hazardHits(t: String): List<RawHazard> =
        cfg.hazards.mapNotNull { def ->
            val blocked = CueMatching.find(t, def.notWhen)
            CueMatching.find(t, def.phrases)
                .firstOrNull { h -> blocked.none { CueMatching.overlaps(it.range, h.range) } }
                ?.let { RawHazard(def, it.phrase, it.range) }
        }

    private fun compoundHits(t: String, presentLabels: List<String>, peoplePresent: Boolean): List<Found> =
        cfg.compounds.mapNotNull { c ->
            if (c.requires.isNotEmpty() && c.requires.none { it in presentLabels }) return@mapNotNull null
            if (c.requiresPeople && !peoplePresent) return@mapNotNull null
            CueMatching.find(t, c.andAnyOf).firstOrNull()
                ?.let { Found(c.label, c.impact, it.phrase, presentIsOngoing = false, forceOngoing = c.forceOngoing, peopleIntrinsic = c.peopleIntrinsic) }
        }

    /** TAM clauses, further split at commas so negation stays local ("no fire, only smoke"). */
    private fun pieces(transcript: String): List<Piece> =
        tam.analyze(transcript).flatMap { clause ->
            val parts = clause.text.split(COMMA).map { it.trim() }.filter { it.any { c -> c.isLetterOrDigit() } }
            if (parts.size <= 1) listOf(Piece(clause.text, clause.tam, clause.explicitOngoing))
            else parts.map { part ->
                val sub = tam.analyze(part).firstOrNull()
                Piece(part, sub?.tam ?: clause.tam, sub?.explicitOngoing ?: clause.explicitOngoing)
            }
        }

    private companion object {
        val COMMA = Regex("[,،]")
        val EXCLAMATIONS = Regex("!{2,}")
    }
}

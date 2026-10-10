package com.megathon.incident.priority

import com.megathon.incident.domain.Aspect
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

    private data class Piece(val text: String, val tam: Tam)

    /** A hazard family hit, or a compound built from one. */
    private data class Found(val label: String, val impact: Int, val phrase: String, val presentIsOngoing: Boolean, val forceOngoing: Boolean)

    private data class ScannedPiece(val tam: Tam, val negated: Boolean, val found: List<Found>)

    fun extract(input: UrgencyInput): UrgencyFeatures {
        // ---- pass 1: scan every piece --------------------------------------------------------
        var resolved = false
        var peopleAtRisk = false
        var lowUrgency = false
        var distress = EXCLAMATIONS.containsMatchIn(input.transcript)

        val scanned = pieces(input.transcript).map { p ->
            val t = p.text.lowercase()
            val raw = hazardHits(t)
            val resolution = CueMatching.find(t, cfg.resolutionCues)
            val low = CueMatching.find(t, cfg.lowUrgencyCues)
            // Mask phrases that contain negation words ("not breathing", "not sure", "no longer",
            // "not urgent") so only a stand-alone negation cancels the hazard.
            val masked = CueMatching.mask(
                t,
                raw.map { CueHit(it.phrase, it.range) } + resolution + low +
                    CueMatching.find(t, tam.lists.hedged) + CueMatching.find(t, tam.lists.completed),
            )
            val negated = CueMatching.negations(masked, tam.lists.negated).isNotEmpty()
            if (!negated) {
                if (resolution.isNotEmpty()) resolved = true
                if (CueMatching.find(t, cfg.peopleCues).isNotEmpty()) peopleAtRisk = true
                if (low.isNotEmpty()) lowUrgency = true
                if (CueMatching.find(CueMatching.mask(t, low), cfg.distressCues).isNotEmpty()) distress = true
            }
            val found = if (negated) emptyList() else {
                val base = raw.map { Found(it.def.label, it.def.impact, it.phrase, it.def.presentIsOngoing, false) }
                base + compoundHits(t, raw.map { it.def.label })
            }
            ScannedPiece(p.tam, negated, found)
        }

        // ---- pass 2: immediacy per hazard, now that "resolved" is known ----------------------
        val hits = mutableListOf<HazardHit>()
        var ongoing = false
        var ongoingAspectSeen = false
        for (s in scanned) {
            if (!s.negated && s.tam.aspect == Aspect.ONGOING) ongoingAspectSeen = true
            for (f in s.found) {
                val m = hazardImmediacy(f, s.tam, resolved)
                if (m == 4) ongoing = true
                hits += HazardHit(f.label, f.impact, f.phrase, m)
            }
        }

        val reasons = mutableListOf<UrgencyReason>()
        var impact: Int
        var immediacy: Int
        if (hits.isEmpty()) {
            impact = cfg.defaultImpact
            immediacy = if (ongoingAspectSeen) 4 else 2
            if (ongoingAspectSeen) ongoing = true
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

        if (peopleAtRisk && impact in 3..4) {
            impact += 1
            reasons += UrgencyReason("people may be at risk → impact +1", Source.TEXT_CUE)
        }

        // An explicit "not urgent" only applies to nuisances; a real hazard is never talked down.
        if (lowUrgency && impact <= 2) {
            immediacy = 1
            reasons += UrgencyReason("reporter says it is not urgent → immediacy 1", Source.TEXT_CUE)
        }

        // ---- sound and emotion signals: only ever raise --------------------------------------
        for (sig in input.signals) {
            when (sig.kind) {
                "event" -> {
                    val label = sig.label.lowercase()
                    val def = cfg.signals.firstOrNull { d -> d.matches.any { label.contains(it) } } ?: continue
                    impact = maxOf(impact, def.minImpact)
                    immediacy = maxOf(immediacy, def.minImmediacy)
                    if (def.minImmediacy >= 4) ongoing = true
                    reasons += UrgencyReason("${def.label} heard → impact ≥ ${def.minImpact}, immediacy ≥ ${def.minImmediacy}", sig.source)
                }
                "emotion" -> if (cfg.distressEmotions.any { sig.label.equals(it, ignoreCase = true) }) {
                    distress = true
                    reasons += UrgencyReason("${sig.label} voice → distress", sig.source)
                }
            }
        }

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
        )
    }

    private fun hazardImmediacy(h: Found, tam: Tam, resolved: Boolean): Int {
        if (h.forceOngoing) return 4
        val base = when {
            tam.aspect == Aspect.ONGOING -> 4
            tam.modality == Modality.PLANNED || tam.tense == Tense.FUTURE -> if (h.impact <= 1) 2 else 3
            tam.aspect == Aspect.COMPLETED -> 2
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

    private fun compoundHits(t: String, presentLabels: List<String>): List<Found> =
        cfg.compounds.mapNotNull { c ->
            if (c.requires.none { it in presentLabels }) return@mapNotNull null
            CueMatching.find(t, c.andAnyOf).firstOrNull()
                ?.let { Found(c.label, c.impact, it.phrase, presentIsOngoing = false, forceOngoing = c.forceOngoing) }
        }

    /** TAM clauses, further split at commas so negation stays local ("no fire, only smoke"). */
    private fun pieces(transcript: String): List<Piece> =
        tam.analyze(transcript).flatMap { clause ->
            val parts = clause.text.split(COMMA).map { it.trim() }.filter { it.any { c -> c.isLetterOrDigit() } }
            if (parts.size <= 1) listOf(Piece(clause.text, clause.tam))
            else parts.map { part -> Piece(part, tam.analyze(part).firstOrNull()?.tam ?: clause.tam) }
        }

    private companion object {
        val COMMA = Regex("[,،]")
        val EXCLAMATIONS = Regex("!{2,}")
    }
}

package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source

/**
 * Turns YamNet's AudioSet scores into hazard `Signal`s. Labels are the words `priority/hazards.json`
 * matches on ("fire alarm", "screaming", "siren", "gunshot", "explosion", "glass breaking", "crying");
 * "running water" and "hissing" are also emitted for the report's context even though no rule uses them yet.
 * The score thresholds are starting values, not tuned: they must be checked on our own alarm clips.
 */
object YamNetSignalMapper {
    private class Rule(val label: String, val audioSetNames: Set<String>, val minScore: Float)

    private val RULES = listOf(
        Rule("fire alarm", setOf("fire alarm", "smoke detector, smoke alarm"), 0.30f),
        Rule(
            "siren",
            setOf("siren", "civil defense siren", "emergency vehicle", "ambulance (siren)", "police car (siren)", "fire engine, fire truck (siren)"),
            0.30f,
        ),
        Rule("screaming", setOf("screaming"), 0.30f),
        Rule("explosion", setOf("explosion"), 0.40f),
        Rule("gunshot", setOf("gunshot, gunfire", "machine gun", "fusillade", "artillery fire"), 0.40f),
        // "Glass" alone is clinking and cups, so only AudioSet's "Shatter" counts as breaking glass.
        Rule("glass breaking", setOf("shatter"), 0.30f),
        Rule("crying", setOf("crying, sobbing", "baby cry, infant cry"), 0.40f),
        // Taps and rain are everywhere, so running water needs a clearly stronger score.
        Rule("running water", setOf("water", "pour", "trickle, dribble", "gush", "drip"), 0.60f),
        Rule("hissing", setOf("hiss"), 0.50f),
    )

    private val byAudioSetName: Map<String, Rule> =
        RULES.flatMap { rule -> rule.audioSetNames.map { it to rule } }.toMap()

    /** Every AudioSet display name (lowercase) the rules listen for, so a test can audit them against YamNet's real class list. */
    val audioSetNames: Set<String> get() = byAudioSetName.keys

    /** Strongest hazard first; one signal per hazard however many AudioSet classes fired for it. */
    fun map(scores: List<ClassScore>, segmentId: Long? = null): List<Signal> {
        val best = LinkedHashMap<Rule, Float>()
        for (class_ in scores) {
            val rule = byAudioSetName[class_.label.trim().lowercase()] ?: continue
            if (class_.score < rule.minScore) continue
            if (class_.score > (best[rule] ?: 0f)) best[rule] = class_.score
        }
        return best.entries
            .sortedByDescending { it.value }
            .map { Signal("event", it.key.label, Source.YAMNET, segmentId) }
    }
}

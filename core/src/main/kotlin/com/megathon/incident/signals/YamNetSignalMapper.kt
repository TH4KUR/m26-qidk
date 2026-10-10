package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source

/**
 * Turns YamNet's AudioSet scores into hazard `Signal`s. Labels are the words `priority/hazards.json`
 * matches on ("fire alarm", "screaming", "siren", "gunshot", "explosion", "glass breaking", "crying");
 * "alarm", "running water" and "hissing" are also emitted for context even though no priority rule uses them yet.
 *
 * Scores must be probabilities in 0..1. The Qualcomm AI Hub ONNX export returns raw logits (seen -680 to +73),
 * so the caller applies a sigmoid first (see [YamNetFrontEnd.sigmoid]).
 *
 * The thresholds come from a probe on public proxy clips (75 ESC-50 clips, 5 per category, plus one smoke-alarm
 * recording): sanity checks, not tuning. `docs/experiments/B-20261011-signals-probe.md` has the numbers.
 */
object YamNetSignalMapper {
    /** A hazard label, the AudioSet names that raise it (each with its own minimum score), and classes that veto it. */
    private class Rule(
        val label: String,
        val names: Map<String, Float>,
        val vetoedBy: Map<String, Float> = emptyMap(),
    )

    private fun rule(label: String, minScore: Float, vararg names: String, vetoedBy: Map<String, Float> = emptyMap()) =
        Rule(label, names.associateWith { minScore }, vetoedBy)

    private val RULES = listOf(
        rule("fire alarm", 0.30f, "fire alarm", "smoke detector, smoke alarm"),
        // A beeping alarm the model calls only "Alarm" (a clock alarm scored "Fire alarm" about 0.12) stays visible
        // as a separate, weaker label. "Alarm clock" is deliberately not listed: it is not an alarm.
        rule("alarm", 0.50f, "alarm"),
        rule(
            "siren", 0.30f,
            "siren", "civil defense siren", "emergency vehicle", "ambulance (siren)", "police car (siren)", "fire engine, fire truck (siren)",
        ),
        rule("screaming", 0.30f, "screaming"),
        rule("explosion", 0.40f, "explosion"),
        rule("gunshot", 0.40f, "gunshot, gunfire", "machine gun", "fusillade", "artillery fire"),
        // "Glass" alone is clinking and cups, so it never counts. "Breaking" is generic, so it needs a clear score.
        Rule("glass breaking", mapOf("shatter" to 0.20f, "breaking" to 0.50f)),
        rule("crying", 0.40f, "crying, sobbing", "baby cry, infant cry"),
        // "Water" scores 0.96 on rain too, so the rain classes veto running water. Taps and rain are everywhere,
        // so it needs a clearly stronger score than an alarm.
        rule(
            "running water", 0.60f, "water", "pour", "trickle, dribble", "gush", "drip",
            vetoedBy = mapOf("rain" to 0.30f, "raindrop" to 0.30f, "rain on surface" to 0.30f),
        ),
        rule("hissing", 0.50f, "hiss"),
    )

    /** Every AudioSet display name (lowercase) the rules read, so a test can audit them against YamNet's real class list. */
    val audioSetNames: Set<String> =
        RULES.flatMap { it.names.keys + it.vetoedBy.keys }.toSet()

    /** Strongest hazard first; one signal per hazard however many AudioSet classes fired for it. */
    fun map(scores: List<ClassScore>, segmentId: Long? = null): List<Signal> {
        val byName = HashMap<String, Float>()
        for (class_ in scores) {
            val key = class_.label.trim().lowercase()
            if (class_.score > (byName[key] ?: -1f)) byName[key] = class_.score
        }
        val best = ArrayList<Pair<Rule, Float>>()
        for (rule in RULES) {
            if (rule.vetoedBy.any { (name, min) -> (byName[name] ?: 0f) >= min }) continue
            val score = rule.names
                .mapNotNull { (name, min) -> byName[name]?.takeIf { it >= min } }
                .maxOrNull() ?: continue
            best += rule to score
        }
        return best.sortedByDescending { it.second }.map { (rule, _) -> Signal("event", rule.label, Source.YAMNET, segmentId) }
    }
}

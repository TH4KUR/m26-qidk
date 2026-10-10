package com.megathon.incident.priority

import com.megathon.incident.domain.UrgencyAssessment
import com.megathon.incident.domain.UrgencyReason
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.priorityForScore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Maps [UrgencyFeatures] to a 1..20 score. All strategies read the same features, so they can be
 * compared on the same scenarios (design doc 2.4). People and distress are already folded into the
 * features' impact and immediacy; strategies must not add them again.
 */
interface UrgencyStrategy {
    val id: String
    fun assess(f: UrgencyFeatures): UrgencyAssessment
}

private fun UrgencyStrategy.build(f: UrgencyFeatures, score: Int, why: String) = UrgencyAssessment(
    score = score.coerceIn(1, 20),
    level = priorityForScore(score.coerceIn(1, 20)),
    impact = f.impact,
    immediacy = f.immediacy,
    reasons = f.reasons + UrgencyReason(why, Source.RULE),
    strategy = id,
)

/** S1: the standard impact x urgency risk matrix. */
class RiskMatrixStrategy : UrgencyStrategy {
    override val id = "risk-matrix"
    override fun assess(f: UrgencyFeatures) =
        build(f, f.impact * f.immediacy, "risk matrix: impact ${f.impact} × immediacy ${f.immediacy} = ${f.impact * f.immediacy}")
}

/** S2: additive points per level, in the style of early-warning scores. */
class AdditiveStrategy : UrgencyStrategy {
    override val id = "additive"
    private val impactPoints = intArrayOf(0, 1, 3, 6, 10, 14)
    private val immediacyPoints = intArrayOf(0, 0, 1, 3, 5)
    override fun assess(f: UrgencyFeatures): UrgencyAssessment {
        val points = impactPoints[f.impact.coerceIn(1, 5)] + immediacyPoints[f.immediacy.coerceIn(1, 4)]
        return build(f, points, "additive: impact points ${impactPoints[f.impact]} + immediacy points ${immediacyPoints[f.immediacy]}")
    }
}

/** S3: an ordered decision tree, in the style of emergency-dispatch determinant levels. */
class ProtocolTreeStrategy : UrgencyStrategy {
    override val id = "protocol-tree"
    override fun assess(f: UrgencyFeatures): UrgencyAssessment {
        val i = f.impact
        val m = f.immediacy
        val score = when {
            i >= 5 && m >= 3 -> 20
            i >= 5 && m == 2 -> 14
            i >= 5 -> 10
            i == 4 && m >= 4 -> 17
            i == 4 && m == 3 -> 13
            i == 4 && m == 2 -> 8
            i == 4 -> 6
            i == 3 && m >= 4 -> 12
            i == 3 && m == 3 -> 8
            i == 3 -> 5
            i == 2 && m >= 4 -> 7
            i == 2 && m == 3 -> 5
            i == 2 && m == 2 -> 3
            i == 2 -> 2
            m >= 2 -> 2
            else -> 1
        }
        return build(f, score, "protocol tree: impact $i, immediacy $m → $score")
    }
}

@Serializable
data class RuleDef(
    val id: String,
    val minImpact: Int = 1,
    val minImmediacy: Int = 1,
    val requires: List<String> = emptyList(),
    val score: Int,
    val reason: String = "",
)

@Serializable
data class RulesFile(val version: Int = 1, val note: String = "", val rules: List<RuleDef> = emptyList())

/** Authored rules (spec D18, `policies/priority_rules.json`). Edit the JSON, not the code. */
class PriorityRules(val rules: List<RuleDef>) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val DEFAULT_RESOURCE = "/policies/priority_rules.json"

        fun fromJson(document: String) = PriorityRules(json.decodeFromString(RulesFile.serializer(), document).rules)

        fun loadDefault(): PriorityRules {
            val stream = PriorityRules::class.java.getResourceAsStream(DEFAULT_RESOURCE)
                ?: error("missing resource $DEFAULT_RESOURCE")
            return fromJson(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
    }
}

/** S4: the highest authored rule that fires. Monotonic by construction. */
class AuthoredRulesStrategy(private val rules: PriorityRules) : UrgencyStrategy {
    override val id = "authored-rules"
    override fun assess(f: UrgencyFeatures): UrgencyAssessment {
        val fired = rules.rules.filter { r ->
            f.impact >= r.minImpact && f.immediacy >= r.minImmediacy && r.requires.all { req ->
                when (req) {
                    "people" -> f.peopleAtRisk
                    "distress" -> f.distress
                    "ongoing" -> f.ongoing
                    else -> false
                }
            }
        }
        val top = fired.maxByOrNull { it.score }
        return build(f, top?.score ?: 1, top?.let { "rule ${it.id}: ${it.reason}" } ?: "no rule fired; minimum score")
    }
}

object UrgencyStrategies {
    const val DEFAULT_ID = "risk-matrix"

    fun all(rules: PriorityRules): List<UrgencyStrategy> =
        listOf(RiskMatrixStrategy(), AdditiveStrategy(), ProtocolTreeStrategy(), AuthoredRulesStrategy(rules))
}

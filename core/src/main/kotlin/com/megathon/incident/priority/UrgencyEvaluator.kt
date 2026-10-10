package com.megathon.incident.priority

import com.megathon.incident.domain.priorityForScore

/** A transcript with the urgency (1..20) a human judged right for it. */
data class LabelledScenario(val id: String, val text: String, val target: Int)

/**
 * Per-strategy quality on a labelled set (design doc 2.4).
 *
 * Under-triage (predicting a lower band than the target) is the costly error, so [cost] weights it
 * three times as heavily as over-triage.
 */
data class StrategyMetrics(
    val strategyId: String,
    val n: Int,
    val bandAccuracy: Double,
    val meanAbsError: Double,
    val underTriageRate: Double,
    val overTriageRate: Double,
) {
    val cost: Double get() = 3 * underTriageRate + overTriageRate
}

object UrgencyEvaluator {
    fun evaluate(
        scenarios: List<LabelledScenario>,
        extractor: UrgencyFeatureExtractor,
        strategies: List<UrgencyStrategy>,
    ): List<StrategyMetrics> {
        val features = scenarios.map { extractor.extract(UrgencyInput(it.text)) }
        return strategies.map { strategy ->
            var bandHits = 0
            var under = 0
            var over = 0
            var absError = 0
            scenarios.forEachIndexed { i, s ->
                val predicted = strategy.assess(features[i]).score
                val predictedBand = priorityForScore(predicted)
                val targetBand = priorityForScore(s.target)
                when {
                    predictedBand == targetBand -> bandHits++
                    predictedBand < targetBand -> under++
                    else -> over++
                }
                absError += kotlin.math.abs(predicted - s.target)
            }
            val n = scenarios.size.coerceAtLeast(1)
            StrategyMetrics(
                strategyId = strategy.id,
                n = scenarios.size,
                bandAccuracy = bandHits.toDouble() / n,
                meanAbsError = absError.toDouble() / n,
                underTriageRate = under.toDouble() / n,
                overTriageRate = over.toDouble() / n,
            )
        }
    }
}

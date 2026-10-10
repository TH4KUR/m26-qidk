package com.megathon.incident.priority

import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlin.test.Test
import kotlin.test.assertEquals

class UrgencyEvaluatorTest {
    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))
    private val strategies = listOf<UrgencyStrategy>(RiskMatrixStrategy())

    @Test
    fun perfectPredictionsScoreOneAndNeverUnderTriage() {
        val m = UrgencyEvaluator.evaluate(
            listOf(LabelledScenario("a", "my house is on fire", 20), LabelledScenario("b", "there is a pothole near the gate", 2)),
            extractor, strategies,
        ).single()
        assertEquals(2, m.n)
        assertEquals(1.0, m.bandAccuracy)
        assertEquals(0.0, m.meanAbsError)
        assertEquals(0.0, m.underTriageRate)
        assertEquals(0.0, m.overTriageRate)
    }

    @Test
    fun predictingALowerBandThanTheTargetIsUnderTriageAndCostsThreeTimesMore() {
        // The pothole is labelled URGENT on purpose, so the prediction (LOW) is an under-triage.
        val m = UrgencyEvaluator.evaluate(
            listOf(LabelledScenario("a", "my house is on fire", 20), LabelledScenario("b", "there is a pothole near the gate", 15)),
            extractor, strategies,
        ).single()
        assertEquals(0.5, m.bandAccuracy)
        assertEquals(0.5, m.underTriageRate)
        assertEquals(0.0, m.overTriageRate)
        assertEquals(6.5, m.meanAbsError)
        assertEquals(1.5, m.cost)
    }

    @Test
    fun predictingAHigherBandIsOverTriage() {
        val m = UrgencyEvaluator.evaluate(
            listOf(LabelledScenario("a", "my house is on fire", 3)),
            extractor, strategies,
        ).single()
        assertEquals(1.0, m.overTriageRate)
        assertEquals(0.0, m.underTriageRate)
        assertEquals(1.0, m.cost)
    }

    @Test
    fun oneMetricsRowPerStrategyInInputOrder() {
        val rows = UrgencyEvaluator.evaluate(
            listOf(LabelledScenario("a", "my house is on fire", 20)), extractor,
            listOf(RiskMatrixStrategy(), AdditiveStrategy()),
        )
        assertEquals(listOf("risk-matrix", "additive"), rows.map { it.strategyId })
    }
}

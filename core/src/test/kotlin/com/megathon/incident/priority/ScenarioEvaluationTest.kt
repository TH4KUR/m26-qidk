package com.megathon.incident.priority

import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measures every strategy on two hand-labelled sets (design doc 2.4):
 *  - **dev**: used while tuning the lexicon;
 *  - **holdout**: written before the first run and never tuned on, so it estimates generalisation.
 * Targets are the author's judgement and are meant to be reviewed; they are not ground truth.
 */
class ScenarioEvaluationTest {
    @Serializable
    private data class Row(val id: String, val text: String, val target: Int, val lang: String = "en")

    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))
    private val strategies = UrgencyStrategies.all(PriorityRules.loadDefault())

    private fun load(name: String): List<LabelledScenario> {
        val text = javaClass.getResourceAsStream("/priority/$name")!!.bufferedReader(Charsets.UTF_8).readText()
        return text.lines().filter { it.isNotBlank() }
            .map { Json.decodeFromString(Row.serializer(), it) }
            .map { LabelledScenario(it.id, it.text, it.target) }
    }

    private fun report(title: String, rows: List<StrategyMetrics>) {
        println("== $title (n=${rows.first().n})")
        println("strategy         bandAcc  MAE    under  over   cost")
        rows.forEach {
            println("%-15s  %.2f     %.2f   %.2f   %.2f   %.2f".format(it.strategyId, it.bandAccuracy, it.meanAbsError, it.underTriageRate, it.overTriageRate, it.cost))
        }
    }

    /** Prints misses for the dev set only, so there is no temptation to tune on the holdout items. */
    private fun printDevMisses(scenarios: List<LabelledScenario>) {
        val s = strategies.single { it.id == UrgencyStrategies.DEFAULT_ID }
        println("== dev misses (default strategy)")
        scenarios.forEach { sc ->
            val a = s.assess(extractor.extract(UrgencyInput(sc.text)))
            val want = com.megathon.incident.domain.priorityForScore(sc.target)
            if (a.level != want) {
                val kind = if (a.level < want) "UNDER" else "over "
                println("$kind ${sc.id} target=${sc.target}($want) got=${a.score}(${a.level}) i=${a.impact} m=${a.immediacy} :: ${sc.text}")
            }
        }
    }

    @Test
    fun catalogueSizesAndIdsAreSane() {
        val dev = load("scenarios_dev.jsonl")
        val holdout = load("scenarios_holdout.jsonl") + load("scenarios_holdout2.jsonl")
        assertTrue(dev.size >= 50, "dev has ${dev.size}")
        assertTrue(holdout.size >= 40, "holdout has ${holdout.size}")
        assertEquals(dev.size + holdout.size, (dev + holdout).map { it.id }.toSet().size, "scenario ids must be unique")
        assertTrue((dev + holdout).all { it.target in 1..20 })
    }

    @Test
    fun theDefaultStrategyNeverUnderTriagesTheDevSetAndIsMostlyRight() {
        val devSet = load("scenarios_dev.jsonl")
        val rows = UrgencyEvaluator.evaluate(devSet, extractor, strategies)
        report("dev", rows)
        printDevMisses(devSet)
        val default = rows.single { it.strategyId == UrgencyStrategies.DEFAULT_ID }
        assertEquals(0.0, default.underTriageRate, "under-triage on dev: $default")
        assertTrue(default.bandAccuracy >= 0.85, "band accuracy on dev: $default")
    }

    @Test
    fun holdoutOneIsReportedButNoLongerCountsAsUnseen() {
        // First run on 2026-10-10, before the extractor refinements: band accuracy 0.20, under-triage 0.60.
        // The default-impact change was motivated by that aggregate, so this set is now contaminated.
        val rows = UrgencyEvaluator.evaluate(load("scenarios_holdout.jsonl"), extractor, strategies)
        report("holdout 1 (contaminated)", rows)
        assertTrue(rows.all { it.n == 20 })
    }

    @Test
    fun holdoutTwoWasUnseenAndStaysWithinTheBoundFixedBeforeItsFirstRun() {
        val rows = UrgencyEvaluator.evaluate(load("scenarios_holdout2.jsonl"), extractor, strategies)
        report("holdout 2 (unseen at first run)", rows)
        val default = rows.single { it.strategyId == UrgencyStrategies.DEFAULT_ID }
        assertTrue(default.underTriageRate <= 0.25, "under-triage on holdout 2: $default")
        assertTrue(default.bandAccuracy >= 0.50, "band accuracy on holdout 2: $default")
    }
}

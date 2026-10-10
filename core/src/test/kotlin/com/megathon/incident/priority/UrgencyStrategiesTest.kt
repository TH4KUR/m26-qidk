package com.megathon.incident.priority

import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.priorityForScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrgencyStrategiesTest {
    private val strategies = UrgencyStrategies.all(PriorityRules.loadDefault())

    private fun feat(impact: Int, immediacy: Int, people: Boolean = false, distress: Boolean = false) = UrgencyFeatures(
        impact = impact, immediacy = immediacy, hazards = emptyList(), peopleAtRisk = people, distress = distress,
        resolved = false, ongoing = immediacy == 4, lowUrgencyStated = false, reasons = emptyList(),
    )

    private fun strategy(id: String) = strategies.single { it.id == id }

    // ---- the score bands -----------------------------------------------------------------------

    @Test fun bandBoundaries() {
        assertEquals(Priority.LOW, priorityForScore(1))
        assertEquals(Priority.LOW, priorityForScore(4))
        assertEquals(Priority.MEDIUM, priorityForScore(5))
        assertEquals(Priority.MEDIUM, priorityForScore(9))
        assertEquals(Priority.HIGH, priorityForScore(10))
        assertEquals(Priority.HIGH, priorityForScore(14))
        assertEquals(Priority.URGENT, priorityForScore(15))
        assertEquals(Priority.URGENT, priorityForScore(20))
    }

    // ---- individual strategies -----------------------------------------------------------------

    @Test fun riskMatrixIsImpactTimesImmediacy() {
        val s = strategy("risk-matrix")
        assertEquals(20, s.assess(feat(5, 4)).score)
        assertEquals(12, s.assess(feat(3, 4)).score)
        assertEquals(4, s.assess(feat(2, 2)).score)
        assertEquals(1, s.assess(feat(1, 1)).score)
    }

    @Test fun additiveUsesTheDocumentedPointTables() {
        val s = strategy("additive")
        assertEquals(19, s.assess(feat(5, 4)).score)   // 14 + 5
        assertEquals(9, s.assess(feat(3, 3)).score)    // 6 + 3
        assertEquals(4, s.assess(feat(2, 2)).score)    // 3 + 1
        assertEquals(1, s.assess(feat(1, 1)).score)    // 1 + 0
    }

    @Test fun peopleAndDistressAreNotCountedTwice() {
        // The extractor has already folded them into impact and immediacy.
        for (s in strategies) {
            assertEquals(s.assess(feat(3, 3)).score, s.assess(feat(3, 3, people = true, distress = true)).score, s.id)
        }
    }

    @Test fun protocolTreeSendsLifeThreatsToTheTop() {
        val s = strategy("protocol-tree")
        assertEquals(20, s.assess(feat(5, 4)).score)
        assertEquals(20, s.assess(feat(5, 3)).score)
        assertTrue(s.assess(feat(5, 2)).score in 10..14, "a resolved life-threat stays at least HIGH")
    }

    @Test fun authoredRulesPickTheHighestFiredRule() {
        val s = strategy("authored-rules")
        assertEquals(20, s.assess(feat(5, 4)).score)
        assertTrue(s.assess(feat(4, 4)).score >= 15)
        assertTrue(s.assess(feat(1, 1)).score <= 2)
    }

    @Test fun everyAssessmentCarriesItsStrategyAndALevel() {
        strategies.forEach { s ->
            val a = s.assess(feat(4, 3))
            assertEquals(s.id, a.strategy)
            assertEquals(priorityForScore(a.score), a.level)
            assertEquals(4, a.impact)
            assertEquals(3, a.immediacy)
        }
    }

    // ---- safety properties, checked over the whole grid ----------------------------------------

    @Test fun scoresStayInOneToTwenty() {
        for (s in strategies) for (i in 1..5) for (m in 1..4) for (p in listOf(false, true)) for (d in listOf(false, true)) {
            val score = s.assess(feat(i, m, p, d)).score
            assertTrue(score in 1..20, "${s.id} gave $score for impact=$i immediacy=$m people=$p distress=$d")
        }
    }

    @Test fun raisingImpactNeverLowersTheScore() {
        for (s in strategies) for (m in 1..4) for (i in 1..4) {
            val low = s.assess(feat(i, m)).score
            val high = s.assess(feat(i + 1, m)).score
            assertTrue(high >= low, "${s.id}: impact $i→${i + 1} at immediacy $m went $low→$high")
        }
    }

    @Test fun raisingImmediacyNeverLowersTheScore() {
        for (s in strategies) for (i in 1..5) for (m in 1..3) {
            val low = s.assess(feat(i, m)).score
            val high = s.assess(feat(i, m + 1)).score
            assertTrue(high >= low, "${s.id}: immediacy $m→${m + 1} at impact $i went $low→$high")
        }
    }

    @Test fun peopleAndDistressNeverLowerTheScore() {
        for (s in strategies) for (i in 1..5) for (m in 1..4) {
            val base = s.assess(feat(i, m)).score
            assertTrue(s.assess(feat(i, m, people = true)).score >= base, "${s.id} people at $i/$m")
            assertTrue(s.assess(feat(i, m, distress = true)).score >= base, "${s.id} distress at $i/$m")
        }
    }

    @Test fun strategyIdsAreUniqueAndTheDefaultExists() {
        assertEquals(strategies.size, strategies.map { it.id }.toSet().size)
        assertTrue(strategies.any { it.id == UrgencyStrategies.DEFAULT_ID })
    }
}

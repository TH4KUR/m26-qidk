package com.megathon.incident.tam

import com.megathon.incident.testing.FakeHedgeDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One contract suite, run against the simple fake D codes against and against the real detector. */
abstract class HedgeDetectorContract {
    protected abstract fun detector(): HedgeDetector

    private fun spans(text: String) = detector().hedges(text).map { text.lowercase().substring(it.first, it.last + 1) }

    @Test fun emptyTextHasNoHedges() = assertTrue(detector().hedges("").isEmpty())

    @Test fun findsIThink() = assertTrue("i think" in spans("I think it is a gas leak"), spans("I think it is a gas leak").toString())

    @Test fun findsMaybe() = assertTrue("maybe" in spans("maybe it was Room 3"))

    @Test fun aPlainStatementHasNone() = assertTrue(detector().hedges("The leak is in Room 2").isEmpty())

    @Test fun rangesStayInsideTheText() {
        val text = "I think maybe it is probably Room 2"
        detector().hedges(text).forEach { assertTrue(it.first >= 0 && it.last < text.length, "$it outside ${text.length}") }
    }

    @Test fun rangesAreSortedAndDoNotOverlap() {
        val r = detector().hedges("I think maybe it is probably Room 2")
        r.zipWithNext().forEach { (a, b) -> assertTrue(a.last < b.first, "$a overlaps $b") }
    }

    @Test fun isDeterministic() {
        val t = "I think maybe it was Room 2, shayad Room 3"
        assertEquals(detector().hedges(t), detector().hedges(t))
    }
}

class FakeHedgeDetectorContractTest : HedgeDetectorContract() {
    override fun detector(): HedgeDetector = FakeHedgeDetector()
}

class TamHedgeDetectorContractTest : HedgeDetectorContract() {
    override fun detector(): HedgeDetector = TamHedgeDetector(TamCueLists.loadDefault())

    @Test fun findsHindiShayad() = assertEquals(1, detector().hedges("shayad Room 3 hai").size)

    @Test fun notSureIsOneHedgeNotNegation() = assertEquals(1, detector().hedges("I am not sure").size)

    @Test fun longestPhraseWinsOverItsParts() {
        val r = detector().hedges("I think so")
        assertEquals(1, r.size)
    }
}

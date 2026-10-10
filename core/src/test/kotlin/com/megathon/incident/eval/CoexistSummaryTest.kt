package com.megathon.incident.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoexistSummaryTest {
    private fun loop(i: Int, stt: Long?, llm: Long?, pss: Int, ok: Boolean = true) =
        CoexistLoop(i, stt, llm, (stt ?: 0) + (llm ?: 0), ok, if (ok) null else "boom", pss, 0, "t")

    @Test
    fun medianOfOddAndEvenCounts() {
        assertEquals(5.0, CoexistSummary.median(listOf(9L, 1L, 5L)))
        assertEquals(2.5, CoexistSummary.median(listOf(4L, 1L, 3L, 2L)))
        assertNull(CoexistSummary.median(emptyList()))
    }

    @Test
    fun summarisesTimingsAndPss() {
        val s = CoexistSummary.of(listOf(loop(1, 100, 1000, 800), loop(2, 300, 3000, 900), loop(3, 200, 2000, 850)))
        assertEquals(3, s.n)
        assertEquals(0, s.failures)
        assertEquals(TimingStats(100, 200.0, 300), s.stt)
        assertEquals(TimingStats(1000, 2000.0, 3000), s.llm)
        assertEquals(TimingStats(1100, 2200.0, 3300), s.total)
        assertEquals(800, s.pssFirstMb)
        assertEquals(850, s.pssLastMb)
        assertEquals(900, s.pssMaxMb)
        assertEquals(50, s.pssGrowthMb)
    }

    @Test
    fun failuresAreCountedAndStagesThatNeverRanAreSkipped() {
        val s = CoexistSummary.of(listOf(loop(1, 100, 1000, 800), loop(2, null, null, 790, ok = false), loop(3, 120, null, 780, ok = false)))
        assertEquals(3, s.n)
        assertEquals(2, s.failures)
        assertEquals(TimingStats(100, 110.0, 120), s.stt)
        assertEquals(TimingStats(1000, 1000.0, 1000), s.llm)
        assertEquals(-20, s.pssGrowthMb)
        assertEquals(800, s.pssMaxMb)
    }

    @Test
    fun noLoopsGivesEmptySummary() {
        val s = CoexistSummary.of(emptyList())
        assertEquals(0, s.n)
        assertNull(s.stt)
        assertNull(s.pssFirstMb)
        assertNull(s.pssGrowthMb)
    }
}

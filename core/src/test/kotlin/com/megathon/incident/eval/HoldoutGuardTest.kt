package com.megathon.incident.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The held-out 13 are a one-shot measurement. These tests pin the ways a run could happen by accident:
 * a dev invocation can never include them, a held-out run needs the explicit word, never shares an invocation with a dev run,
 * and the same prompt on the same build cannot be run twice without a second explicit word.
 */
class HoldoutGuardTest {
    private val ledger0 = emptyList<HoldoutRun>()
    private fun run(prompt: String = "p1", build: String = "b1", gold: String = "g1") =
        HoldoutRun(promptHash = prompt, build = build, goldHash = gold, ids = listOf("h01", "h02"), atMs = 1L)

    private fun decide(
        holdout: String? = "CONFIRM",
        again: String? = null,
        dev: Boolean = false,
        prompt: String = "p1",
        build: String = "b1",
        ledger: HoldoutLedger = HoldoutLedger(ledger0, unreadable = 0),
    ) = HoldoutGuard.decide(holdout, again, devRequested = dev, promptHash = prompt, build = build, ledger = ledger)

    // ---- which items a split selects ----

    @Test fun aDevRunSelectsDevItemsAndNeverHeldOutOnes() {
        assertTrue(SplitFilter.selects("dev", EvalSplit.DEV))
        assertTrue(!SplitFilter.selects("holdout", EvalSplit.DEV))
        assertTrue(!SplitFilter.selects(null, EvalSplit.DEV), "an item with no split label is not run as dev")
        assertTrue(!SplitFilter.selects("test", EvalSplit.DEV), "an unknown label is not run as dev")
    }

    @Test fun aHeldOutRunSelectsOnlyHeldOutItems() {
        assertTrue(SplitFilter.selects("holdout", EvalSplit.HOLDOUT))
        assertTrue(!SplitFilter.selects("dev", EvalSplit.HOLDOUT))
        assertTrue(!SplitFilter.selects(null, EvalSplit.HOLDOUT))
    }

    // ---- when a held-out run is allowed ----

    @Test fun noHoldoutExtraMeansNoHeldOutRun() {
        assertEquals(HoldoutDecision.NotRequested, decide(holdout = null))
    }

    @Test fun anythingButTheExactWordIsRefused() {
        for (word in listOf("confirm", "true", "1", "yes", "", " CONFIRM")) {
            assertIs<HoldoutDecision.Refused>(decide(holdout = word), "'$word' must not start a held-out run")
        }
    }

    @Test fun aHeldOutRunNeverSharesAnInvocationWithADevRun() {
        val d = assertIs<HoldoutDecision.Refused>(decide(dev = true))
        assertTrue(d.reason.contains("dev"), d.reason)
    }

    @Test fun theFirstRunOfAPromptOnABuildIsAllowed() {
        assertEquals(HoldoutDecision.Allowed(repeat = false), decide())
    }

    @Test fun aSecondRunOfTheSamePromptOnTheSameBuildIsRefused() {
        val d = assertIs<HoldoutDecision.Refused>(decide(ledger = HoldoutLedger(listOf(run()), 0)))
        assertTrue(d.reason.contains("holdout_again"), "the refusal must name the second flag: ${d.reason}")
    }

    @Test fun theSecondExplicitFlagAllowsARepeatAndSaysSo() {
        assertEquals(HoldoutDecision.Allowed(repeat = true), decide(again = "CONFIRM_AGAIN", ledger = HoldoutLedger(listOf(run()), 0)))
    }

    @Test fun theSecondFlagWithTheWrongWordIsRefused() {
        assertIs<HoldoutDecision.Refused>(decide(again = "yes", ledger = HoldoutLedger(listOf(run()), 0)))
    }

    @Test fun theSecondFlagAloneDoesNotStartARun() {
        assertEquals(HoldoutDecision.NotRequested, decide(holdout = null, again = "CONFIRM_AGAIN"))
    }

    @Test fun aDifferentPromptOnTheSameBuildIsAFreshMeasurement() {
        assertEquals(HoldoutDecision.Allowed(false), decide(prompt = "p2", ledger = HoldoutLedger(listOf(run(prompt = "p1")), 0)))
    }

    @Test fun theSamePromptOnADifferentBuildIsAFreshMeasurement() {
        assertEquals(HoldoutDecision.Allowed(false), decide(build = "b2", ledger = HoldoutLedger(listOf(run(build = "b1")), 0)))
    }

    @Test fun anUnreadableLedgerIsRefusedBecauseItMayHideAnEarlierRun() {
        val d = assertIs<HoldoutDecision.Refused>(decide(ledger = HoldoutLedger(emptyList(), unreadable = 1)))
        assertTrue(d.reason.contains("ledger"), d.reason)
    }

    // ---- the record that makes the refusal possible ----

    @Test fun aRunSurvivesBeingWrittenAndReadBack() {
        val written = run(prompt = "1ae9711fdb3f0bdb", build = "fb02d9746ebb5f45", gold = "e1f1c009fd00a3d4")
        val ledger = HoldoutLedger.parse(written.toLine() + "\n" + run(prompt = "other").toLine() + "\n")
        assertEquals(0, ledger.unreadable)
        assertEquals(listOf(written, run(prompt = "other")), ledger.runs)
    }

    @Test fun blankLinesAreIgnoredAndGarbageIsCountedNotSkippedSilently() {
        val ledger = HoldoutLedger.parse("\n" + run().toLine() + "\nnot json at all\n\n")
        assertEquals(1, ledger.runs.size)
        assertEquals(1, ledger.unreadable)
    }

    @Test fun theShortHashIsTheFirstSixteenHexDigitsOfSha256() {
        // sha256("abc") = ba7816bf 8f01cfea 414140de 5dae2223 ...: the same prefix style as the prompt and gold hashes in the logs
        assertEquals("ba7816bf8f01cfea", ShortHash.of("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea", ShortHash.of(java.io.ByteArrayInputStream("abc".toByteArray())))
    }

    @Test fun anEmptyLedgerTextIsAnEmptyLedger() {
        assertEquals(HoldoutLedger(emptyList(), 0), HoldoutLedger.parse(""))
    }
}

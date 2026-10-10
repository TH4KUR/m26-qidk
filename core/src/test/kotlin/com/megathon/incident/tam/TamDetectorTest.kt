package com.megathon.incident.tam

import com.megathon.incident.domain.Aspect
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.Tam
import com.megathon.incident.domain.Tense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TamDetectorTest {
    private val detector = TamDetector(TamCueLists.loadDefault())

    private fun one(text: String): TamClause {
        val clauses = detector.analyze(text)
        assertEquals(1, clauses.size, "expected one clause for '$text' but got ${clauses.map { it.text }}")
        return clauses.single()
    }

    private fun tam(text: String) = one(text).tam

    // ---- the problem-statement example ---------------------------------------------------------

    @Test
    fun slideSentenceSplitsIntoTwoClauses() {
        val c = detector.analyze("There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area.")
        assertEquals(2, c.size)
        assertEquals(Tam(Tense.PAST, Aspect.NONE, Modality.ASSERTED), c[0].tam)
        assertEquals(Tam(Tense.PAST, Aspect.COMPLETED, Modality.ASSERTED), c[1].tam)
    }

    // ---- aspect: ongoing vs completed ----------------------------------------------------------

    @Test fun stillLeakingIsOngoingPresent() =
        assertEquals(Tam(Tense.PRESENT, Aspect.ONGOING, Modality.ASSERTED), tam("the pipe is still leaking"))

    @Test fun presentProgressiveIsOngoing() = assertEquals(Aspect.ONGOING, tam("water is leaking from the ceiling").aspect)

    @Test fun nothingIsNotAProgressiveVerb() = assertEquals(Aspect.NONE, tam("there is nothing wrong with the lift").aspect)

    @Test fun pastProgressiveWithCompletionIsCompleted() =
        assertEquals(Tam(Tense.PAST, Aspect.COMPLETED, Modality.ASSERTED), tam("it was leaking, we turned it off"))

    @Test fun pastProgressiveAloneIsNotOngoing() {
        val t = tam("the pipe was leaking")
        assertEquals(Tense.PAST, t.tense)
        assertEquals(Aspect.NONE, t.aspect)
    }

    @Test fun noLongerLeakingIsCompletedNotNegated() =
        assertEquals(Tam(Tense.PAST, Aspect.COMPLETED, Modality.ASSERTED), tam("it is no longer leaking"))

    @Test fun ongoingWinsWhenBothCuesAreInOneClause() =
        assertEquals(Aspect.ONGOING, tam("it already stopped and is still leaking").aspect)

    @Test fun butSplitsContrastingClauses() {
        val c = detector.analyze("the water has stopped but it is still leaking")
        assertEquals(listOf(Aspect.COMPLETED, Aspect.ONGOING), c.map { it.tam.aspect })
    }

    @Test fun exclamationSplitsClauses() =
        assertEquals(2, detector.analyze("Fire in the kitchen! Everyone is out.").size)

    // ---- tense / plan --------------------------------------------------------------------------

    @Test fun futureIntentIsPlanned() =
        assertEquals(Tam(Tense.FUTURE, Aspect.NONE, Modality.PLANNED), tam("we will call maintenance"))

    @Test fun obligationIsPlannedAndSuppressesCompleted() {
        val t = tam("it needs to be isolated")
        assertEquals(Modality.PLANNED, t.modality)
        assertEquals(Aspect.NONE, t.aspect)
    }

    @Test fun presentStateHasNoAspect() =
        assertEquals(Tam(Tense.PRESENT, Aspect.NONE, Modality.ASSERTED), tam("there is water on the floor"))

    // ---- modality: hedged / negated ------------------------------------------------------------

    @Test fun iThinkIsHedged() {
        val c = one("I think it is a gas leak")
        assertEquals(Modality.HEDGED, c.tam.modality)
        assertTrue(c.hedged)
    }

    @Test fun notSureIsHedgedNotNegated() = assertEquals(Modality.HEDGED, tam("I am not sure what happened").modality)

    @Test fun dontKnowIsHedgedNotNegated() = assertEquals(Modality.HEDGED, tam("I don't know who it was").modality)

    @Test fun nobodyWasHurtIsNegated() = assertEquals(Modality.NEGATED, tam("nobody was hurt").modality)

    @Test fun didNotSeeIsNegated() = assertEquals(Modality.NEGATED, tam("Ravi did not see it").modality)

    @Test fun plainStatementIsAsserted() = assertEquals(Modality.ASSERTED, tam("Ravi saw it").modality)

    @Test fun negatedCompletionDoesNotCountAsCompleted() {
        val t = tam("the leak has not been fixed")
        assertEquals(Modality.NEGATED, t.modality)
        assertEquals(Aspect.NONE, t.aspect)
    }

    @Test fun roomNumberAbbreviationIsNotANegation() = assertEquals(Modality.ASSERTED, tam("Room no. 2 is flooded").modality)

    @Test fun hedgeIsKeptEvenWhenNegationWins() {
        val c = one("I think nobody was hurt")
        assertEquals(Modality.NEGATED, c.tam.modality)
        assertTrue(c.hedged)
    }

    @Test fun clauseKeepsTheCuesThatFired() {
        val c = one("the pipe is still leaking")
        assertTrue("still" in c.cues, "cues were ${c.cues}")
    }

    // ---- Hindi / Hinglish ----------------------------------------------------------------------

    @Test fun hindiAbhiBhiHoRahaHaiIsOngoing() =
        assertEquals(Tam(Tense.PRESENT, Aspect.ONGOING, Modality.ASSERTED), tam("abhi bhi paani leak ho raha hai"))

    @Test fun hindiPastProgressiveWithClosedAction() {
        val t = tam("paani leak ho raha tha humne band kar diya")
        assertEquals(Tense.PAST, t.tense)
        assertEquals(Aspect.COMPLETED, t.aspect)
    }

    @Test fun hindiShayadIsHedged() = assertEquals(Modality.HEDGED, tam("shayad gas leak hai").modality)

    @Test fun hindiKoiNahiIsNegated() = assertEquals(Modality.NEGATED, tam("koi ghayal nahi hua").modality)

    @Test fun hindiFutureIntentIsPlanned() =
        assertEquals(Tam(Tense.FUTURE, Aspect.NONE, Modality.PLANNED), tam("hum maintenance ko bulayenge"))

    @Test fun devanagariOngoing() = assertEquals(Aspect.ONGOING, tam("पानी अभी भी रिस रहा है").aspect)

    @Test fun devanagariNegation() = assertEquals(Modality.NEGATED, tam("कोई घायल नहीं हुआ").modality)

    @Test fun devanagariDanda() = assertEquals(2, detector.analyze("आग लगी है। सब बाहर हैं।").size)

    @Test fun hinglishMixIsOngoing() = assertEquals(Aspect.ONGOING, tam("water still leak ho raha hai").aspect)

    // ---- explicit continuation vs plain progressive --------------------------------------------

    @Test fun plainProgressiveIsOngoingButNotExplicit() {
        val c = one("water is leaking from the ceiling")
        assertEquals(Aspect.ONGOING, c.tam.aspect)
        assertFalse(c.explicitOngoing)
    }

    @Test fun stillIsExplicitContinuation() = assertTrue(one("the pipe is still leaking").explicitOngoing)

    @Test fun sinceMorningIsExplicitContinuation() {
        val c = one("the lights are off since morning")
        assertEquals(Aspect.ONGOING, c.tam.aspect)
        assertTrue(c.explicitOngoing)
    }

    @Test fun hindiPlainProgressiveIsNotExplicit() {
        val c = one("paani leak ho raha hai")
        assertEquals(Aspect.ONGOING, c.tam.aspect)
        assertFalse(c.explicitOngoing)
    }

    @Test fun hindiSubahSeIsExplicit() = assertTrue(one("subah se paani aa raha hai").explicitOngoing)

    @Test fun hindiAbhiBhiIsExplicit() = assertTrue(one("abhi bhi paani leak ho raha hai").explicitOngoing)

    // ---- housekeeping --------------------------------------------------------------------------

    @Test fun emptyTextHasNoClauses() = assertTrue(detector.analyze("   ").isEmpty())

    @Test fun defaultCueListsLoadBothLanguages() {
        val lists = TamCueLists.loadDefault()
        assertTrue(lists.ongoing.any { it == "still" })
        assertTrue(lists.ongoing.any { it == "अभी भी" })
        assertFalse(lists.hedged.isEmpty() || lists.negated.isEmpty() || lists.planned.isEmpty())
    }
}

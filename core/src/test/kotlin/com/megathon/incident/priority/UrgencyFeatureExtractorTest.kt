package com.megathon.incident.priority

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrgencyFeatureExtractorTest {
    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))

    private fun f(text: String, signals: List<Signal> = emptyList()) = extractor.extract(UrgencyInput(text, signals))

    private fun labels(text: String) = f(text).hazards.map { it.label }

    // ---- impact --------------------------------------------------------------------------------

    @Test fun fireIsImpactFive() = assertEquals(5, f("my house is on fire").impact)

    @Test fun potholeIsImpactOne() = assertEquals(1, f("there is a pothole near the gate").impact)

    @Test fun plainWaterLeakIsImpactTwo() = assertEquals(2, f("there is a water leak near conference room 2").impact)

    @Test fun waterLeakNearElectricsIsImpactThree() =
        assertEquals(3, f("water is leaking near the electrical panel").impact)

    @Test fun unmatchedTextIsTreatedAsAPossibleHazardUntilClarified() {
        // Unknown must never default to trivial: under-triage costs three times more than over-triage.
        val r = f("the notice board needs to be moved")
        assertEquals(3, r.impact)
        assertTrue(r.reasons.any { it.text.contains("default") })
    }

    @Test fun highestImpactHazardWins() = assertEquals(5, f("water leak and also a person is unconscious").impact)

    // ---- negation, drills, hedges --------------------------------------------------------------

    @Test fun negatedHazardIsIgnored() = assertFalse("fire" in labels("there is no fire, only a small water leak"))

    @Test fun nobodyHurtDoesNotBecomeAnInjury() {
        val r = f("nobody is hurt, just a small water leak near the stairs")
        assertFalse("minor_injury" in r.hazards.map { it.label })
        assertEquals(2, r.impact)
    }

    @Test fun fireDrillIsNotAFire() = assertFalse("fire" in labels("the fire drill is scheduled at 3 pm"))

    @Test fun hedgedHazardStillCounts() = assertEquals(5, f("I think there is a fire in the basement").impact)

    @Test fun roomNumberAbbreviationDoesNotCancelTheHazard() = assertTrue("fire" in labels("fire in room no. 4"))

    // ---- immediacy -----------------------------------------------------------------------------

    @Test fun fireInTheKitchenIsHappeningNow() = assertEquals(4, f("there is a fire in the kitchen").immediacy)

    @Test fun ongoingLeakIsImmediacyFour() = assertEquals(4, f("the pipe is still leaking").immediacy)

    @Test fun plainLeakIsImmediacyTwoBecauseItIsALowImpactState() =
        assertEquals(2, f("there is a water leak near the stairs").immediacy)

    @Test fun spillOnAWalkwayIsImmediacyThree() = assertEquals(3, f("there is a spill on the walkway").immediacy)

    @Test fun laterResolutionLowersAPastHazardToImmediacyTwo() {
        val r = f("There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area.")
        assertEquals(2, r.immediacy)
        assertTrue(r.resolved)
        assertFalse(r.ongoing)
    }

    @Test fun resolutionDoesNotHideALaterOngoingHazard() {
        val r = f("the water has stopped but it is still leaking")
        assertTrue(r.ongoing)
        assertEquals(4, r.immediacy)
    }

    @Test fun pastFireWithNoResolutionStaysAtImmediacyThree() = assertEquals(3, f("there was a fire in the pantry").immediacy)

    @Test fun pastFireThatIsOutIsImmediacyTwo() = assertEquals(2, f("there was a small fire in the pantry but it is out now").immediacy)

    @Test fun predictedHazardIsImmediacyThree() = assertEquals(3, f("the water will spread to the lab").immediacy)

    @Test fun nuisanceCannotBeMoreUrgentThanImmediacyTwo() = assertEquals(2, f("the corridor light keeps flickering").immediacy)

    @Test fun explicitLowUrgencyWinsOverNuisanceDefault() = assertEquals(1, f("a pothole near the gate, not urgent").immediacy)

    // ---- people at risk ------------------------------------------------------------------------

    @Test fun peopleInsideRaiseImpactForSeriousHazards() {
        val r = f("there is a spill near the lab and students are walking through it")
        assertTrue(r.peopleAtRisk)
        assertEquals(4, r.impact)
    }

    @Test fun peopleDoNotRaiseANuisance() = assertEquals(1, f("people are complaining about the pothole near the gate").impact)

    @Test fun noOneInsideIsNotPeopleAtRisk() = assertFalse(f("there is smoke but no one inside").peopleAtRisk)

    // ---- distress and sound signals ------------------------------------------------------------

    @Test fun distressWordsAreDetectedAndNeverReduceAnything() {
        val calm = f("there is a spill in the corridor")
        val shouting = f("there is a spill in the corridor, please hurry, help!!")
        assertTrue(shouting.distress)
        assertTrue(shouting.immediacy >= calm.immediacy)
        assertTrue(shouting.impact >= calm.impact)
    }

    @Test fun fireAlarmSoundRaisesAnOtherwiseQuietReport() {
        val quiet = f("there is some noise in the corridor")
        val withAlarm = f("there is some noise in the corridor", listOf(Signal("event", "fire alarm", Source.YAMNET, 1)))
        assertTrue(withAlarm.impact > quiet.impact)
        assertEquals(4, withAlarm.immediacy)
        assertTrue(withAlarm.reasons.any { it.source == Source.YAMNET })
    }

    @Test fun aSignalNeverLowersAnExistingAssessment() {
        val base = f("there is a fire in the kitchen")
        val withSignal = f("there is a fire in the kitchen", listOf(Signal("event", "laughter", Source.YAMNET, 1)))
        assertTrue(withSignal.impact >= base.impact)
        assertTrue(withSignal.immediacy >= base.immediacy)
    }

    @Test fun calmEmotionNeverLowersUrgency() {
        val base = f("there is a fire in the kitchen")
        val calm = f("there is a fire in the kitchen", listOf(Signal("emotion", "neutral", Source.SENSEVOICE, 1)))
        assertEquals(base.impact, calm.impact)
        assertEquals(base.immediacy, calm.immediacy)
    }

    // ---- Hindi / Hinglish ----------------------------------------------------------------------

    @Test fun devanagariFire() {
        val r = f("मेरे घर में आग लगी है")
        assertEquals(5, r.impact)
        assertEquals(4, r.immediacy)
    }

    @Test fun hinglishFireWithPeopleTrapped() {
        val r = f("ghar mein aag lag gayi hai, log andar phase hain")
        assertEquals(5, r.impact)
        assertTrue(r.peopleAtRisk)
    }

    @Test fun hindiOngoingLeak() {
        val r = f("kitchen mein paani leak ho raha hai abhi bhi")
        assertEquals(2, r.impact)
        assertEquals(4, r.immediacy)
    }

    @Test fun hindiNegatedFire() = assertFalse("fire" in labels("aag nahi lagi hai, sirf paani leak hai"))

    @Test fun becameUnconsciousIsNotTreatedAsResolved() {
        // "ho gaya" means it happened; it must not read as "it is over".
        val r = f("ek aadmi behosh ho gaya hai")
        assertEquals(5, r.impact)
        assertTrue(r.immediacy >= 3, "immediacy was ${r.immediacy}")
    }

    @Test fun peopleInherentToTheHazardAreNotCountedAgain() {
        // A fight is about people; the people cue must not add another +1.
        assertEquals(3, f("two people are fighting in the canteen").impact)
    }

    @Test fun hangingWireIsAHazard() =
        assertTrue(f("a wire is hanging from the ceiling near the staircase").impact >= 3)

    @Test fun plainProgressiveLeakIsNotEscalating() = assertEquals(2, f("the tap in the washroom is leaking").immediacy)

    @Test fun hindiPlainProgressiveLeakIsNotEscalating() = assertEquals(2, f("paani leak ho raha hai").immediacy)

    @Test fun devanagariPlainProgressiveLeakIsNotEscalating() = assertEquals(2, f("थोड़ा पानी लीक हो रहा है").immediacy)

    @Test fun sinceMorningMeansTheFaultIsContinuing() =
        assertEquals(4, f("there is a power cut in the lab since morning").immediacy)

    @Test fun aBurstPipeIsFloodingNow() = assertEquals(4, f("the water pipe has burst in the basement").immediacy)

    @Test fun someoneAloneIsNotPeopleAtRisk() =
        assertEquals(3, f("there was a spill of oil in the corridor and someone could slip").impact)

    @Test fun suspiciousPersonIsNotBoostedForBeingAPerson() =
        assertEquals(3, f("there is a suspicious person near the parking lot").impact)

    @Test fun aheadIsNotFire() = assertFalse("fire" in labels("आगे जाकर बाएं मुड़ो"))

    @Test fun outOfControlFireIsNotReportedAsResolved() {
        val r = f("आग बेकाबू है")
        assertFalse(r.resolved)
        assertEquals(5, r.impact)
    }

    @Test fun nuktaVariantsOfTheSameWordBothMatch() {
        val precomposed = "तेज़ाब गिरा है"   // तेज़ाब with U+095B
        val decomposed = "तेज़ाब गिरा है" // ज + nukta
        assertTrue("chemical" in labels(precomposed), labels(precomposed).toString())
        assertTrue("chemical" in labels(decomposed), labels(decomposed).toString())
    }

    @Test fun devanagariLeakWordIsRecognised() = assertTrue("leak" in labels("थोड़ा पानी लीक हो रहा है"))
}

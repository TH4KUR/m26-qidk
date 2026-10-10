package com.megathon.incident.priority

import com.megathon.incident.domain.Priority
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Injury and shock wordings that the first lexicon missed (found by running the advisor over E's dev transcripts, not
 * over any urgency holdout). Each positive case must reach HIGH; each look-alike must not be raised by the new phrases.
 */
class InjuryCoverageTest {
    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))
    private val strategy = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == UrgencyStrategies.DEFAULT_ID }

    private fun level(text: String): Priority = strategy.assess(extractor.extract(UrgencyInput(text))).level

    private fun assertHigh(text: String) = assertTrue(level(text) >= Priority.HIGH, "'$text' was ${level(text)}")

    @Test fun aBrokenBodyPartIsSerious() {
        assertHigh("A worker in the warehouse got hit by a forklift. His ankle looks broken.")
        assertHigh("She fell down the stairs and her wrist is broken.")
        assertHigh("I think I have a broken rib.")
    }

    @Test fun aShockWithBurnsIsSerious() {
        assertHigh("The power lines touched Naveen's hands. He got a shock and burns on both hands.")
        assertHigh("The electrician received a shock from the panel and is shaking.")
    }

    @Test fun aHeadHitWithDizzinessIsSerious() {
        assertHigh("The nurse fell and hit her head. She seems dizzy.")
    }

    @Test fun beingHitByAVehicleIsSerious() {
        assertHigh("A student was hit by a car near the main gate.")
    }

    @Test fun thingsThatAreMerelyBrokenAreNotInjuries() {
        assertTrue(level("The door lock looks broken.") <= Priority.LOW)
        assertTrue(level("The window is broken in the corridor.") <= Priority.MEDIUM)
    }

    @Test fun aCarHitByABranchIsNotAPersonHit() {
        assertTrue(level("A branch hit a parked car near the gate.") < Priority.HIGH)
    }
}

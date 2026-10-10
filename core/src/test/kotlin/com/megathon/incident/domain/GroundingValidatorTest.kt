package com.megathon.incident.domain

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroundingValidatorTest {
    private val slide = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."

    private fun grounded(value: String?, evidence: String?, transcript: String = slide, enumValue: Boolean = false) =
        GroundingValidator.isGrounded(value, evidence, transcript, enumValue)

    @Test fun exactEvidenceIsGrounded() = assertTrue(grounded("Conference Room 2", "near Conference Room 2"))

    @Test fun valueOnlyOverlapIsGroundedWhenNoEvidence() = assertTrue(grounded("conference room 2", null))

    @Test fun blankEvidenceCountsAsNoEvidence() = assertTrue(grounded("conference room 2", "   "))

    @Test fun inventedNameIsNotGrounded() = assertFalse(grounded("John", "witness John"))

    @Test fun punctuationAndCaseAreIgnored() = assertTrue(grounded("Area isolated", "ALREADY isolated, the area!"))

    @Test fun blankValueIsNotGrounded() = assertFalse(grounded("  ", "near Conference Room 2"))

    @Test fun nullValueIsNotGrounded() = assertFalse(grounded(null, "near Conference Room 2"))

    @Test fun evidenceMustMatchDigitsExactly() = assertFalse(grounded("Conference Room 3", "near Conference Room 3"))

    @Test fun roomTwoDoesNotMatchInsideRoomTwenty() =
        assertFalse(grounded("Room 2", "Room 2", transcript = "the fire was in Room 20"))

    @Test fun negationFlipsGrounding() {
        assertFalse(grounded("Ravi", "Ravi saw it", transcript = "Ravi did not see it"))
        assertTrue(grounded("Ravi", "Ravi did not see it", transcript = "Ravi did not see it"))
    }

    @Test fun evidenceThatDoesNotSupportTheValueIsRejected() =
        assertFalse(grounded("Priya", "near Conference Room 2"))

    @Test fun enumValuesSkipTheValueOverlapCheck() =
        assertTrue(grounded("water_leak_flood", "water leak", enumValue = true))

    @Test fun enumValueStillNeedsEvidenceInTheTranscript() =
        assertFalse(grounded("fire_smoke", "smoke everywhere", enumValue = true))

    @Test fun noEvidenceAndPartialOverlapIsNotGrounded() =
        assertFalse(grounded("Conference Room 5", null))

    @Test fun stopWordsDoNotInflateOverlap() = assertFalse(grounded("the of at", null))

    @Test fun unicodeNormalisationDifferencesAreIgnored() =
        assertTrue(grounded("café", "café", transcript = "we met at the café near the lift"))

    @Test fun hindiEvidenceIsGrounded() =
        assertTrue(grounded("रूम 2", "रूम 2 के पास", transcript = "कॉन्फ़्रेंस रूम 2 के पास पानी का रिसाव है"))

    @Test fun hindiMatrasAreNotSplitIntoSeparateTokens() =
        assertFalse(grounded("पानी", "पानी", transcript = "पाना मुश्किल है"))

    // ---- near-miss: sound-alike or one-letter differences are never "grounded" ---------------------

    private val table = com.megathon.incident.suggest.HomophoneTable(
        listOf(listOf("week", "weak"), listOf("two", "to", "too", "2"), listOf("severe", "sever")),
    )

    private fun near(
        value: String?, evidence: String?, transcript: String, enumValue: Boolean = false, allowSpelling: Boolean = true,
    ) = GroundingValidator.nearMiss(value, evidence, transcript, enumValue, allowSpelling, table::sameSound)

    @Test fun aSoundAlikeEvidenceIsANearMissButNotGrounded() {
        val said = "the boiler broke last weak"
        assertFalse(grounded("last week", "last week", said))
        val n = near("last week", "last week", said)!!
        assertEquals("last weak", n.heard)
        assertEquals("last week", n.meant)
    }

    @Test fun anExactMatchIsNotANearMiss() = assertNull(near("last week", "last week", "the boiler broke last week"))

    @Test fun aNearMissNeverLowersTheEvidenceOverlapThreshold() =
        assertNull(near("completely different pump", "weak pump", "the week pump failed"))

    @Test fun aSpokenNumberCanBeAWrittenDigit() {
        val n = near("Conference Room 2", "Conference Room 2", "water near conference room to")!!
        assertEquals("conference room to", n.heard)
    }

    @Test fun roomTwentyIsNotANearMissForRoomTwo() = assertNull(near("room 2", "room 2", "water in room 20"))

    @Test fun oneLetterSpellingNeedsFourLetters() {
        assertNotNull(near("leak", "leak", "water leaks near the lab"))
        assertNull(near("cat", "cat", "a car near the lab"))
    }

    @Test fun spellingCanBeSwitchedOff() = assertNull(near("Rahil", "Rahil", "Rahul saw it", allowSpelling = false))

    @Test fun anEnumNeedsOnlyItsEvidenceToBeNear() {
        val n = near("high", "severe", "the leak is sever", enumValue = true)!!
        assertEquals("sever", n.heard)
        assertEquals("severe", n.meant)
    }

    @Test fun noEvidenceUsesTheBagOfWordsRuleAndReportsOnlyTheDifferingWords() {
        val n = near("last week", null, "it was last weak")!!
        assertEquals("weak", n.heard)
        assertEquals("week", n.meant)
    }

    @Test fun aMissingWordIsNotANearMiss() = assertNull(near("last week", "last week", "it was weak"))

    @Test fun devanagariIsNeverMatchedBySpelling() = assertNull(near("पानी", "पानी", "पाना मुश्किल है"))
}

package com.megathon.incident.domain

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
}

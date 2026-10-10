package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptAgreementTest {
    private val sentence = "There is a water leak in Conference Room 2"

    @Test
    fun identicalTextAgreesWithZeroErrors() {
        val verdict = TranscriptAgreement.compare(sentence, sentence)
        assertTrue(verdict.agrees)
        assertEquals(0.0, verdict.wer)
    }

    @Test
    fun caseAndPunctuationDoNotCount() {
        assertTrue(TranscriptAgreement.compare("There's a water leak, in conference room 2.", "there's a WATER leak in Conference Room 2").agrees)
    }

    @Test
    fun oneWrongWordInNineStillAgreesAtTheDefaultTolerance() {
        val verdict = TranscriptAgreement.compare("There is a water leak in Conference Room 3", sentence)
        assertEquals(1.0 / 9, verdict.wer!!, 1e-9)
        assertTrue(verdict.agrees)
    }

    @Test
    fun garbledNpuOutputIsCaught() {
        // The documented silent failure: no error, just "!!!!" or repeated tokens.
        assertFalse(TranscriptAgreement.compare("!!!! !!!! !!!!", sentence).agrees)
        assertFalse(TranscriptAgreement.compare("the the the the the the the the", sentence).agrees)
    }

    @Test
    fun aBlankNpuResultForSpeechDisagrees() {
        val verdict = TranscriptAgreement.compare("", sentence)
        assertFalse(verdict.agrees)
        assertEquals(1.0, verdict.wer)
    }

    @Test
    fun aBlankCpuReferenceCannotJudgeAnything() {
        val verdict = TranscriptAgreement.compare(sentence, "   ")
        assertFalse(verdict.agrees)
        assertNull(verdict.wer)
        assertTrue("reference" in verdict.detail, verdict.detail)
    }

    @Test
    fun wordErrorRateCountsSubstitutionsInsertionsAndDeletions() {
        assertEquals(1.0 / 3, TranscriptAgreement.wordErrorRate("a b c", "a x c")!!, 1e-9)
        assertEquals(1.0 / 3, TranscriptAgreement.wordErrorRate("a b c", "a b")!!, 1e-9)
        assertEquals(1.0 / 3, TranscriptAgreement.wordErrorRate("a b c", "a b c d")!!, 1e-9)
        assertNull(TranscriptAgreement.wordErrorRate("", "anything"))
    }

    @Test
    fun theToleranceIsAParameterAndTheDetailSaysWhatWasMeasured() {
        val strict = TranscriptAgreement.compare("There is a water leak in Conference Room 3", sentence, maxWer = 0.05)
        assertFalse(strict.agrees)
        assertTrue("11.1%" in strict.detail, strict.detail)
    }


    @Test
    fun devanagariWordsAreNotShreddedIntoFragments() {
        // Vowel signs (matras) are combining marks; stripping them would split one Hindi word into several tokens.
        val reference = "शायद पानी बह रहा है"
        assertEquals(0.0, TranscriptAgreement.wordErrorRate(reference, reference))
        assertEquals(1.0 / 5, TranscriptAgreement.wordErrorRate(reference, "शायद पानी बह रही है")!!, 1e-9)
        assertTrue(TranscriptAgreement.compare("शायद पानी बह रही है", reference).agrees)
    }
}

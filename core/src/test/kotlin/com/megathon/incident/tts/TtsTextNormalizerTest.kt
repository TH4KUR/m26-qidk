package com.megathon.incident.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TtsTextNormalizerTest {
    private fun n(text: String, language: String = "en") = TtsTextNormalizer.normalize(text, language)

    @Test
    fun emergencyNumbersAreSpokenDigitByDigit() {
        assertEquals("Call one one two now.", n("Call 112 now."))
        assertEquals("Dial one zero zero for the police.", n("Dial 100 for the police."))
        assertEquals("one zero eight", n("108"))
    }

    @Test
    fun otherNumbersAreSpokenAsWords() {
        assertEquals("There are twelve people.", n("There are 12 people."))
        assertEquals("About one hundred and fifty people.", n("About 150 people."))
        assertEquals("two thousand five hundred", n("2500"))
        assertEquals("zero", n("0"))
        assertEquals("twenty one", n("21"))
    }

    @Test
    fun longOrZeroLeadingNumbersAreSpokenDigitByDigit() {
        assertEquals("Ring zero nine eight seven six five four three two one.", n("Ring 0987654321."))
        assertEquals("zero zero seven", n("007"))
    }

    @Test
    fun codesAreSpokenLetterThenNumber() {
        assertEquals("Block B two", n("Block B-2"))
        assertEquals("Meet at B two.", n("Meet at B-2."))
        assertEquals("C P seven", n("CP-7"))
        assertEquals("A twelve", n("A12"))
        assertEquals("Gate T three", n("Gate T3"))
    }

    @Test
    fun threeDigitNumbersAfterAPlaceWordAreSpokenDigitByDigit() {
        assertEquals("Room two zero four", n("Room 204"))
        assertEquals("the lab one one five", n("the lab 115"))
        assertEquals("Gate three", n("Gate 3"))
        assertEquals("Room twelve", n("Room 12"))
    }

    @Test
    fun timesAreSpokenTheWayPeopleSayThem() {
        assertEquals("It happened at ten o'clock.", n("It happened at 10:00."))
        assertEquals("ten thirty", n("10:30"))
        assertEquals("nine oh five", n("9:05"))
        assertEquals("ten thirty A M", n("10:30 AM"))
        assertEquals("three o'clock P M", n("3:00 pm"))
        assertEquals("fourteen thirty", n("14:30"))
        assertEquals("fourteen hundred", n("14:00"))
    }

    @Test
    fun ordinalsAreSpokenAsWords() {
        assertEquals("on the second floor", n("on the 2nd floor"))
        assertEquals("the twenty first", n("the 21st"))
        assertEquals("the eleventh", n("the 11th"))
    }

    @Test
    fun decimalsAreSpokenWithPoint() {
        assertEquals("three point five", n("3.5"))
    }

    @Test
    fun symbolsAreStrippedOrSpelled() {
        assertEquals("Pipes and valves", n("Pipes & valves"))
        assertEquals("fifty percent", n("50%"))
        assertEquals("Water leak", n("**Water** leak"))
        assertEquals("Water leak", n("# Water leak"))
        assertEquals("Hello, world.", n("Hello,   world."))
        val cleaned = n("Check the ~pump~ <now> [urgent] {please} |ok| \\ ^")
        for (c in "~<>[]{}|\\^") assertFalse(c in cleaned, "symbol $c survived in: $cleaned")
    }

    @Test
    fun emojiAndOtherSymbolsAreDropped() {
        assertEquals("Done.", n("Done. 😀"))
        assertEquals("Fire", n("🔥 Fire"))
    }

    @Test
    fun hyphenatedWordsAndDashesBecomePauses() {
        assertEquals("well known pump", n("well-known pump"))
        assertEquals("The pump, it broke.", n("The pump — it broke."))
    }

    @Test
    fun hindiLettersAndVowelSignsSurvive() {
        val text = "पानी का रिसाव कमरा दो में है।"
        assertEquals(text, n(text, "hi"))
    }

    @Test
    fun hindiKeepsDigitsButSplitsEmergencyNumbers() {
        assertEquals("1 1 2 पर फोन करें", n("112 पर फोन करें", "hi"))
        assertEquals("कमरा 12 में", n("कमरा 12 में", "hi"))
    }

    @Test
    fun emptyOrSymbolOnlyTextBecomesEmpty() {
        assertEquals("", n(""))
        assertEquals("", n("   \n\t "))
        assertEquals("", n("**~~**"))
    }

    @Test
    fun whitespaceIsCollapsed() {
        assertEquals("a b c", n("a   b\n\nc"))
    }

    @Test
    fun apostrophesAndSentencePunctuationAreKept() {
        assertEquals("I don't know. Is it safe?", n("I don't know.  Is it safe?"))
    }
}

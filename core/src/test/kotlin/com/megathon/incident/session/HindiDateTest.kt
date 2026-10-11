package com.megathon.incident.session

import com.megathon.incident.tts.HindiPhrases
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HindiDateTest {
    private val today = LocalDate.parse("2026-10-11") // a Sunday

    private fun friendly(text: String) = HindiDate.friendly(text, today)

    @Test fun todayWithAnApproximateTime() =
        assertEquals("आज, रविवार ११ अक्टूबर · लगभग सुबह १० बजे", friendly("2026-10-11 ≈10:00 (around 10 AM)"))

    @Test fun todayWithAnExactTime() =
        assertEquals("आज, रविवार ११ अक्टूबर · सुबह १० बजे", friendly("2026-10-11 10:00 (10 AM)"))

    @Test fun yesterdayIsKal() =
        assertEquals("कल, शनिवार १० अक्टूबर · दोपहर २:३० बजे", friendly("2026-10-10 14:30 (yesterday at 2:30 PM)"))

    @Test fun anotherDayHasNeitherAajNorKal() =
        assertEquals("शनिवार ३ अक्टूबर · सुबह ९:०५ बजे", friendly("2026-10-03 09:05 (last Saturday)"))

    @Test fun minutesAreShownAsHourAndMinute() {
        assertEquals("आज, रविवार ११ अक्टूबर · सुबह १०:३० बजे", friendly("2026-10-11 10:30 (half past ten)"))
        assertEquals("आज, रविवार ११ अक्टूबर · रात ९:१५ बजे", friendly("2026-10-11 21:15 (quarter past nine)"))
    }

    @Test fun midnightIsRaatBarahAndNoonIsDopaharBarah() {
        assertEquals("आज, रविवार ११ अक्टूबर · रात १२ बजे", friendly("2026-10-11 00:00 (midnight)"))
        assertEquals("आज, रविवार ११ अक्टूबर · रात १२:३० बजे", friendly("2026-10-11 00:30 (half past midnight)"))
        assertEquals("आज, रविवार ११ अक्टूबर · दोपहर १२ बजे", friendly("2026-10-11 12:00 (noon)"))
    }

    @Test fun partOfDayBoundaries() {
        val expected = mapOf(
            0 to "रात १२", 1 to "रात १", 4 to "रात ४", 5 to "सुबह ५", 11 to "सुबह ११", 12 to "दोपहर १२", 13 to "दोपहर १",
            15 to "दोपहर ३", 16 to "शाम ४", 19 to "शाम ७", 20 to "रात ८", 23 to "रात ११",
        )
        for ((hour, words) in expected) {
            val text = "2026-10-11 %02d:00 (x)".format(hour)
            assertEquals("आज, रविवार ११ अक्टूबर · $words बजे", friendly(text), "hour $hour")
        }
    }

    @Test fun anApproximateTimeWithMinutes() =
        assertEquals("आज, रविवार ११ अक्टूबर · लगभग शाम ५:४५ बजे", friendly("2026-10-11 ≈17:45 (around quarter to six)"))

    @Test fun dateOnly() {
        assertEquals("शुक्रवार ९ अक्टूबर", friendly("2026-10-09 time missing (Friday)"))
        assertEquals("आज, रविवार ११ अक्टूबर", friendly("2026-10-11 time missing (today)"))
    }

    @Test fun partOfDayOnly() {
        assertEquals("आज, रविवार ११ अक्टूबर · सुबह", friendly("2026-10-11 morning (this morning)"))
        assertEquals("कल, शनिवार १० अक्टूबर · दोपहर", friendly("2026-10-10 afternoon (yesterday afternoon)"))
        assertEquals("कल, शनिवार १० अक्टूबर · शाम", friendly("2026-10-10 evening (yesterday evening)"))
        assertEquals("कल, शनिवार १० अक्टूबर · रात", friendly("2026-10-10 night (last night)"))
    }

    @Test fun aTimeWithNoDateSaysTheDayWasNotGiven() {
        assertEquals("लगभग सुबह १० बजे · दिन नहीं बताया", friendly("date missing ≈10:00 (around 10 AM)"))
        assertEquals("शाम ५:१५ बजे · दिन नहीं बताया", friendly("date missing 17:15 (quarter past five)"))
        assertEquals("रात · दिन नहीं बताया", friendly("date missing night (at night)"))
    }

    @Test fun nothingKnownFallsBackToTheStoredPhrase() =
        assertEquals("sometime", friendly("date missing time missing (sometime)"))

    @Test fun textThatIsNotInTheValidatorsFormatIsReturnedUnchanged() {
        assertEquals("some free text", friendly("some free text"))
        assertEquals("आज सुबह", friendly("आज सुबह"))
        assertEquals("", friendly(""))
    }

    @Test fun everyMonthHasItsHindiName() {
        val months = listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
        months.forEachIndexed { i, name ->
            val text = "2026-%02d-15 time missing (x)".format(i + 1)
            assertTrue(friendly(text).endsWith(" १५ $name"), "month ${i + 1}: ${friendly(text)}")
        }
    }

    @Test fun everyWeekdayHasItsHindiName() {
        // 2026-10-05 is a Monday; none of these seven days is today or yesterday for the 31 December used here.
        val weekdays = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
        val numbers = listOf("५", "६", "७", "८", "९", "१०", "११")
        weekdays.forEachIndexed { i, name ->
            val text = "2026-10-%02d time missing (x)".format(5 + i)
            assertEquals("$name ${numbers[i]} अक्टूबर", HindiDate.friendly(text, LocalDate.parse("2026-12-31")))
        }
    }

    @Test fun firstAndLastDayOfAMonthUseDevanagariNumerals() {
        assertEquals("गुरुवार १ जनवरी", HindiDate.friendly("2026-01-01 time missing (x)", LocalDate.parse("2026-06-01")))
        assertEquals("गुरुवार ३१ दिसंबर", HindiDate.friendly("2026-12-31 time missing (x)", LocalDate.parse("2026-06-01")))
    }

    @Test fun noLatinDigitsRemainInAParsedValue() {
        val shown = friendly("2026-10-03 09:05 (last Saturday)")
        assertTrue(shown.none { it in '0'..'9' }, shown)
    }

    @Test fun theDayAndMonthAreTheWordsTheVoiceSays() {
        val spoken = HindiPhrases.whenWords(DateTimeDisplay.spoken("2026-10-03 09:30 (last Saturday)", today))!!
        val shown = friendly("2026-10-03 09:30 (last Saturday)")
        assertTrue("शनिवार" in spoken && "शनिवार" in shown)
        assertTrue("अक्टूबर" in spoken && "अक्टूबर" in shown)
        assertTrue("सुबह" in spoken && "सुबह" in shown)
    }

    @Test fun theEnglishPathIsUntouched() {
        assertEquals("Today, Sun 11 Oct · about 10:00 AM", DateTimeDisplay.friendly("2026-10-11 ≈10:00 (around 10 AM)", today))
        assertEquals("Sat 3 Oct · 9:05 AM", DateTimeDisplay.friendly("2026-10-03 09:05 (last Saturday)", today))
    }
}

package com.megathon.incident.session

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class HindiSavedAtTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(ist).toInstant().toEpochMilli()

    @Test
    fun anAfternoonTimeReadsWithHindiMonthDevanagariNumeralsAndPartOfDay() {
        assertEquals("११ अक्टूबर, दोपहर ३:४५ बजे", HindiSavedAt.format(ms(2026, 10, 11, 15, 45)))
    }

    @Test
    fun theTimeIsTakenInIndiaNotInTheJvmZone() {
        // 2026-10-11 22:00 UTC is 03:30 on the 12th in Kolkata.
        val utc = LocalDateTime.of(2026, 10, 11, 22, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("१२ अक्टूबर, रात ३:३० बजे", HindiSavedAt.format(utc))
    }

    @Test
    fun partsOfDayFollowTheSameBoundariesAsTheEnglishDateText() {
        assertEquals("१ जनवरी, सुबह ५:०५ बजे", HindiSavedAt.format(ms(2026, 1, 1, 5, 5)))
        assertEquals("१ जनवरी, सुबह ११:५९ बजे", HindiSavedAt.format(ms(2026, 1, 1, 11, 59)))
        assertEquals("१ जनवरी, दोपहर १२:०० बजे", HindiSavedAt.format(ms(2026, 1, 1, 12, 0)))
        assertEquals("१ जनवरी, दोपहर ४:५९ बजे", HindiSavedAt.format(ms(2026, 1, 1, 16, 59)))
        assertEquals("१ जनवरी, शाम ५:०० बजे", HindiSavedAt.format(ms(2026, 1, 1, 17, 0)))
        assertEquals("१ जनवरी, शाम ८:५९ बजे", HindiSavedAt.format(ms(2026, 1, 1, 20, 59)))
        assertEquals("१ जनवरी, रात ९:०० बजे", HindiSavedAt.format(ms(2026, 1, 1, 21, 0)))
    }

    @Test
    fun midnightIsTwelveAtNight() {
        assertEquals("३१ दिसंबर, रात १२:०७ बजे", HindiSavedAt.format(ms(2026, 12, 31, 0, 7)))
    }

    @Test
    fun everyMonthHasItsHindiName() {
        val names = (1..12).map { HindiSavedAt.format(ms(2026, it, 15, 12, 0)).substringAfter(' ').substringBefore(',') }
        assertEquals(
            listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"),
            names,
        )
    }

    @Test
    fun digitsAreWrittenInDevanagariAndOtherTextIsLeftAlone() {
        assertEquals("०:१२", HindiSavedAt.digits("0:12"))
        assertEquals("१२३४५६७८९० abc", HindiSavedAt.digits("1234567890 abc"))
    }
}

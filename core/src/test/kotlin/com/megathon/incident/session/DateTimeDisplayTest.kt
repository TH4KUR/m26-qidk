package com.megathon.incident.session

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DateTimeDisplayTest {
    private val today = LocalDate.parse("2026-10-11")

    @Test fun parsesTheValidatorsFormat() {
        val p = assertNotNull(DateTimeDisplay.parse("date missing ≈10:00 (around 10 AM)"))
        assertNull(p.date)
        assertEquals("10:00", p.clock)
        assertEquals(true, p.approximate)
        assertEquals("around 10 AM", p.phrase)
    }

    @Test fun parsesAFullDateAndTime() {
        val p = assertNotNull(DateTimeDisplay.parse("2026-10-10 14:30 (yesterday at 2:30 PM)"))
        assertEquals(LocalDate.parse("2026-10-10"), p.date)
        assertEquals("14:30", p.clock)
        assertEquals(false, p.approximate)
    }

    @Test fun partOfDayIsKeptAsWords() {
        val p = assertNotNull(DateTimeDisplay.parse("2026-10-11 morning (this morning)"))
        assertEquals("morning", p.partOfDay)
        assertNull(p.clock)
    }

    @Test fun unparseableTextReturnsNull() = assertNull(DateTimeDisplay.parse("some free text"))

    @Test fun friendlyUsesTodayAndYesterday() {
        assertEquals("Today, Sun 11 Oct · about 10:00 AM", DateTimeDisplay.friendly("2026-10-11 ≈10:00 (around 10 AM)", today))
        assertEquals("Yesterday, Sat 10 Oct · 2:30 PM", DateTimeDisplay.friendly("2026-10-10 14:30 (yesterday at 2:30 PM)", today))
        assertEquals("Sat 3 Oct · 9:05 AM", DateTimeDisplay.friendly("2026-10-03 09:05 (last Saturday)", today))
    }

    @Test fun friendlyNamesWhatIsMissing() {
        assertEquals("About 10:00 AM · day not given", DateTimeDisplay.friendly("date missing ≈10:00 (around 10 AM)", today))
        assertEquals("Today, Sun 11 Oct · morning", DateTimeDisplay.friendly("2026-10-11 morning (this morning)", today))
    }

    @Test fun spokenReadsADayAndATimeInWords() {
        assertEquals("today at about 10 in the morning", DateTimeDisplay.spoken("2026-10-11 ≈10:00 (around 10 AM)", today))
        assertEquals("yesterday at 2:30 in the afternoon", DateTimeDisplay.spoken("2026-10-10 14:30 (yesterday at 2:30 PM)", today))
        assertEquals("on Saturday 3 October at 9:05 in the morning", DateTimeDisplay.spoken("2026-10-03 09:05 (last Saturday)", today))
    }

    @Test fun spokenNamesTheHourWithoutAnAmPmOrA24HourClock() {
        assertEquals("today at 8 in the evening", DateTimeDisplay.spoken("2026-10-11 20:00 (8 in the evening)", today))
        assertEquals("today at 12 in the afternoon", DateTimeDisplay.spoken("2026-10-11 12:00 (noon)", today))
        assertEquals("today at 5 in the evening", DateTimeDisplay.spoken("2026-10-11 17:00 (five)", today))
        assertEquals("today at 9:15 at night", DateTimeDisplay.spoken("2026-10-11 21:15 (quarter past nine)", today))
        assertEquals("today at 12:30 at night", DateTimeDisplay.spoken("2026-10-11 00:30 (half past midnight)", today))
    }

    @Test fun spokenSaysWhatIsMissingByLeavingItOut() {
        assertEquals("at about 10 in the morning", DateTimeDisplay.spoken("date missing ≈10:00 (around 10 AM)", today))
        assertEquals("today in the morning", DateTimeDisplay.spoken("2026-10-11 morning (this morning)", today))
        assertEquals("yesterday at night", DateTimeDisplay.spoken("2026-10-10 night (last night)", today))
        assertEquals("on Friday 9 October", DateTimeDisplay.spoken("2026-10-09 time missing (Friday)", today))
        assertEquals("sometime", DateTimeDisplay.spoken("date missing time missing (sometime)", today))
    }

    @Test fun spokenLeavesUnparseableTextAsIs() = assertEquals("this morning", DateTimeDisplay.spoken("this morning", today))

    @Test fun unparseableTextIsShownAsIs() = assertEquals("some free text", DateTimeDisplay.friendly("some free text", today))

    @Test fun withDateCompletesTheValueInTheSameFormat() {
        assertEquals(
            "2026-10-11 ≈10:00 (around 10 AM)",
            DateTimeDisplay.withDate("date missing ≈10:00 (around 10 AM)", today),
        )
    }
}

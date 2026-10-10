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
        assertEquals("Today · about 10:00 AM", DateTimeDisplay.friendly("2026-10-11 ≈10:00 (around 10 AM)", today))
        assertEquals("Yesterday · 2:30 PM", DateTimeDisplay.friendly("2026-10-10 14:30 (yesterday at 2:30 PM)", today))
        assertEquals("3 Oct · 9:05 AM", DateTimeDisplay.friendly("2026-10-03 09:05 (last Saturday)", today))
    }

    @Test fun friendlyNamesWhatIsMissing() {
        assertEquals("About 10:00 AM · day not given", DateTimeDisplay.friendly("date missing ≈10:00 (around 10 AM)", today))
        assertEquals("Today · morning", DateTimeDisplay.friendly("2026-10-11 morning (this morning)", today))
    }

    @Test fun unparseableTextIsShownAsIs() = assertEquals("some free text", DateTimeDisplay.friendly("some free text", today))

    @Test fun withDateCompletesTheValueInTheSameFormat() {
        assertEquals(
            "2026-10-11 ≈10:00 (around 10 AM)",
            DateTimeDisplay.withDate("date missing ≈10:00 (around 10 AM)", today),
        )
    }
}

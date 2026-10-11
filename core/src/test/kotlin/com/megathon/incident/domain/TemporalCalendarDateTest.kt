package com.megathon.incident.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Found by replaying the model's time phrases for an independent set of 114 initial reports: five of them named a calendar
 * date ("on 9 October 2026 at about 11 AM") and came back as "date missing"; the others were an offset from a clock time and a
 * day count with a clock time. Captures are Sunday 2026-10-11 08:00 IST.
 */
class TemporalCalendarDateTest {
    private val resolver = TemporalResolver()
    private val captured = Instant.parse("2026-10-11T08:00:00+05:30")
    private fun r(text: String) = assertNotNull(resolver.resolve(text, captured), "expected a value for '$text'")

    @Test fun dayMonthYearWithClock() {
        val v = r("on 9 October 2026 at about 11 AM")
        assertEquals(LocalDate.of(2026, 10, 9), v.date)
        assertEquals(LocalTime.of(11, 0), v.time)
    }

    @Test fun ordinalAndMonthFirst() {
        assertEquals(LocalDate.of(2026, 10, 9), r("9th October at 11 AM").date)
        assertEquals(LocalDate.of(2026, 10, 9), r("October 9 at 11 AM").date)
        assertEquals(LocalDate.of(2026, 10, 9), r("on the 9th of October around 11 AM").date)
        assertEquals(LocalDate.of(2026, 10, 9), r("9 Oct at 11 AM").date)
    }

    @Test fun withoutYearItIsTheMostRecentPastOccurrence() {
        assertEquals(LocalDate.of(2026, 10, 9), r("9 October at 11 AM").date)
        // 20 December has not come yet in 2026: the one that was is last year's.
        assertEquals(LocalDate.of(2025, 12, 20), r("on 20 December at 6 PM").date)
    }

    @Test fun aDateAloneIsStillADate() {
        val v = r("on 9 October 2026")
        assertEquals(LocalDate.of(2026, 10, 9), v.date)
        assertNull(v.time)
    }

    @Test fun notADateWhenNoMonthName() {
        // "may" the verb and a bare number are not a calendar date.
        assertNull(resolver.resolve("it may be 9 people", captured))
    }

    @Test fun daysAgoWithAClock() {
        val v = r("three days ago at about 3 PM")
        assertEquals(LocalDate.of(2026, 10, 8), v.date)
        assertEquals(LocalTime.of(15, 0), v.time)
    }

    @Test fun anOffsetBeforeAClockTime() {
        val v = r("about five minutes before 9 AM today")
        assertEquals(LocalDate.of(2026, 10, 11), v.date)
        assertEquals(LocalTime.of(8, 55), v.time)
    }

    @Test fun anOffsetAfterAClockTime() {
        val v = r("two hours after the 9 AM class today")
        assertEquals(LocalDate.of(2026, 10, 11), v.date)
        assertEquals(LocalTime.of(11, 0), v.time)
    }
}

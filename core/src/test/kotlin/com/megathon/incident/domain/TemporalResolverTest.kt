package com.megathon.incident.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemporalResolverTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val resolver = TemporalResolver(ist)

    // 2026-10-11 04:30 IST
    private val earlyMorning = Instant.parse("2026-10-10T23:00:00Z")
    // 2026-10-11 15:00 IST
    private val afternoon = Instant.parse("2026-10-11T09:30:00Z")

    private fun r(text: String, at: Instant = afternoon) = assertNotNull(resolver.resolve(text, at), "expected a value for '$text'")

    @Test
    fun around10AmKeepsApproximateTimeAndLeavesDateMissing() {
        val v = r("around 10 AM")
        assertEquals(LocalTime.of(10, 0), v.time)
        assertTrue(v.approximate)
        assertNull(v.date)
        assertTrue(v.needsClarification)
    }

    @Test
    fun laterTodayTimeIsNeverMovedToYesterday() {
        val v = r("around 10 AM", earlyMorning)
        assertNull(v.date)
    }

    @Test
    fun explicitPmTime() {
        val v = r("at 10:30 pm")
        assertEquals(LocalTime.of(22, 30), v.time)
        assertFalse(v.approximate)
    }

    @Test
    fun yesterdayEveningGivesPreviousDateAndPartOfDayWithoutInventingAClockTime() {
        val v = r("yesterday evening")
        assertEquals(LocalDate.of(2026, 10, 10), v.date)
        assertEquals(PartOfDay.EVENING, v.partOfDay)
        assertNull(v.time)
    }

    @Test
    fun yesterdayUsesTheCaptureDateInIstNotUtc() {
        // 2026-10-10T18:40Z is 2026-10-11 00:10 IST, so "yesterday" is 2026-10-10.
        val v = r("yesterday at 3 pm", Instant.parse("2026-10-10T18:40:00Z"))
        assertEquals(LocalDate.of(2026, 10, 10), v.date)
        assertEquals(LocalTime.of(15, 0), v.time)
    }

    @Test
    fun hoursAgoResolvesExactlyFromCaptureInstant() {
        val v = r("2 hours ago")
        assertEquals(LocalDate.of(2026, 10, 11), v.date)
        assertEquals(LocalTime.of(13, 0), v.time)
        assertFalse(v.approximate)
        assertFalse(v.needsClarification)
    }

    @Test
    fun numberWordsWork() = assertEquals(LocalTime.of(13, 0), r("two hours ago").time)

    @Test
    fun minutesAgo() = assertEquals(LocalTime.of(14, 40), r("20 minutes ago").time)

    @Test
    fun halfAnHourAgo() = assertEquals(LocalTime.of(14, 30), r("half an hour ago").time)

    @Test
    fun justNowIsTheCaptureTime() {
        val v = r("it happened just now")
        assertEquals(LocalDate.of(2026, 10, 11), v.date)
        assertEquals(LocalTime.of(15, 0), v.time)
    }

    @Test
    fun thisMorningIsTodayWithPartOfDay() {
        val v = r("this morning")
        assertEquals(LocalDate.of(2026, 10, 11), v.date)
        assertEquals(PartOfDay.MORNING, v.partOfDay)
    }

    @Test
    fun clockTimeWithoutAmPmIsAmbiguous() {
        val v = r("at 10")
        assertEquals(LocalTime.of(10, 0), v.time)
        assertTrue(v.ambiguous)
    }

    @Test
    fun hindiAajSubahNauBaje() {
        val v = r("aaj subah 9 baje")
        assertEquals(LocalDate.of(2026, 10, 11), v.date)
        assertEquals(LocalTime.of(9, 0), v.time)
        assertFalse(v.ambiguous)
    }

    @Test
    fun hindiRaatGivesPmHour() = assertEquals(LocalTime.of(22, 0), r("raat 10 baje").time)

    @Test
    fun hindiBajeWithoutPartOfDayIsAmbiguous() = assertTrue(r("10 baje").ambiguous)

    @Test
    fun hindiKalIsAmbiguousAndNeverResolvedToADate() {
        val v = r("kal raat")
        assertNull(v.date)
        assertTrue(v.ambiguous)
        assertEquals(PartOfDay.NIGHT, v.partOfDay)
    }

    @Test
    fun hindiHoursAgo() = assertEquals(LocalTime.of(13, 0), r("do ghante pehle").time)

    @Test
    fun hindiDevanagariAbhi() = assertEquals(LocalTime.of(15, 0), r("अभी").time)

    @Test
    fun abhiBhiMeansStillNotNow() = assertNull(resolver.resolve("abhi bhi leak ho raha hai", afternoon))

    @Test
    fun devanagariAbhiBhiMeansStillNotNow() = assertNull(resolver.resolve("अभी भी पानी बह रहा है", afternoon))

    @Test
    fun bareNightWordInProseIsNotATime() = assertNull(resolver.resolve("the night guard saw it", afternoon))

    @Test
    fun lastNightIsYesterdayWithNightPartOfDay() {
        val v = r("last night")
        assertEquals(LocalDate.of(2026, 10, 10), v.date)
        assertEquals(PartOfDay.NIGHT, v.partOfDay)
    }

    @Test
    fun hoursAgoCrossesMidnightIntoThePreviousDate() {
        // 2026-10-11 00:30 IST minus 2 h is 2026-10-10 22:30.
        val v = r("2 hours ago", Instant.parse("2026-10-10T19:00:00Z"))
        assertEquals(LocalDate.of(2026, 10, 10), v.date)
        assertEquals(LocalTime.of(22, 30), v.time)
    }

    @Test
    fun clockTimeOverTwelveIsNotAmbiguous() {
        val v = r("at 15:45")
        assertEquals(LocalTime.of(15, 45), v.time)
        assertFalse(v.ambiguous)
    }

    @Test
    fun invalidHourIsIgnored() = assertNull(resolver.resolve("at 45", afternoon))

    @Test
    fun textWithoutTimeReturnsNull() = assertNull(resolver.resolve("in the lobby near the lift", afternoon))

    @Test
    fun keepsTheOriginalSourceText() = assertEquals("around 10 AM", r("around 10 AM").sourceText)

    // ---- how Whisper actually writes times -----------------------------------------------------

    @Test
    fun numberWordHourWithAm() {
        val v = r("around ten am")
        assertEquals(LocalTime.of(10, 0), v.time)
        assertTrue(v.approximate)
    }

    @Test fun numberWordHourWithPm() = assertEquals(LocalTime.of(22, 0), r("at ten pm").time)

    @Test fun dottedMeridiem() = assertEquals(LocalTime.of(10, 0), r("around 10 a.m.").time)

    @Test fun numberWordOClockIsAmbiguous() {
        val v = r("at two o'clock")
        assertEquals(LocalTime.of(2, 0), v.time)
        assertTrue(v.ambiguous)
    }

    @Test fun hindiNumberWordBaje() = assertEquals(LocalTime.of(9, 0), r("subah nau baje").time)

    @Test fun aNumberWordThatIsNotAnHourIsIgnored() = assertNull(resolver.resolve("one of the pipes near the lab", afternoon))

    // ---- weeks (Monday-start ISO weeks; capture is Sunday 2026-10-11 15:00 IST) --------------------

    private val mondayMorning = Instant.parse("2026-10-12T04:30:00Z") // Monday 2026-10-12 10:00 IST

    @Test fun lastWeekIsAMondayToSundayRangeWithNoDate() {
        val v = r("last week")
        assertNull(v.date)
        assertNull(v.time)
        assertEquals(LocalDate.of(2026, 9, 28), v.from)
        assertEquals(LocalDate.of(2026, 10, 4), v.to)
        assertTrue(v.approximate)
        assertTrue(v.needsClarification)
    }

    @Test fun lastWeekInHinglishAndDevanagariGivesTheSameRange() {
        for (phrase in listOf("pichhle hafte", "pichle hafte", "पिछले हफ्ते", "पिछले हफ़्ते")) {
            val v = r(phrase)
            assertEquals(LocalDate.of(2026, 9, 28), v.from, phrase)
            assertEquals(LocalDate.of(2026, 10, 4), v.to, phrase)
            assertNull(v.date, phrase)
        }
    }

    @Test fun thisWeekRunsFromMondayToToday() {
        for (phrase in listOf("earlier this week", "this week", "is hafte", "इस हफ्ते")) {
            val v = r(phrase)
            assertNull(v.date, phrase)
            assertEquals(LocalDate.of(2026, 10, 5), v.from, phrase)
            assertEquals(LocalDate.of(2026, 10, 11), v.to, phrase)
            assertTrue(v.needsClarification, phrase)
        }
    }

    @Test fun onAMondayThisWeekIsJustTodayAndLastWeekIsTheSevenDaysBefore() {
        val thisWeek = r("this week", mondayMorning)
        assertEquals(LocalDate.of(2026, 10, 12), thisWeek.from)
        assertEquals(LocalDate.of(2026, 10, 12), thisWeek.to)
        val lastWeek = r("last week", mondayMorning)
        assertEquals(LocalDate.of(2026, 10, 5), lastWeek.from)
        assertEquals(LocalDate.of(2026, 10, 11), lastWeek.to)
    }

    @Test fun lastWeekAcrossTheYearBoundary() {
        val newYearsDay = Instant.parse("2026-01-01T04:30:00Z") // Thursday
        val v = r("last week", newYearsDay)
        assertEquals(LocalDate.of(2025, 12, 22), v.from)
        assertEquals(LocalDate.of(2025, 12, 28), v.to)
    }

    @Test fun aWeekAgoIsASingleApproximateDateWithoutATime() {
        for (phrase in listOf("a week ago", "one week ago", "ek hafte pehle", "एक हफ्ते पहले")) {
            val v = r(phrase)
            assertEquals(LocalDate.of(2026, 10, 4), v.date, phrase)
            assertNull(v.from, phrase)
            assertNull(v.time, phrase)
            assertTrue(v.approximate, phrase)
            assertTrue(v.needsClarification, phrase)
        }
        assertEquals(LocalDate.of(2026, 9, 27), r("2 weeks ago").date)
    }

    @Test fun aPartOfDayIsKeptNextToTheWeekRange() {
        val v = r("last week in the evening")
        assertEquals(PartOfDay.EVENING, v.partOfDay)
        assertEquals(LocalDate.of(2026, 9, 28), v.from)
        assertNull(v.time)
    }

    @Test fun aDurationIsNotAPointInTime() {
        assertNull(resolver.resolve("it has been leaking for a week", afternoon))
    }

    @Test fun anExplicitDayBeatsAWeekWord() {
        val v = r("yesterday, earlier this week")
        assertEquals(LocalDate.of(2026, 10, 10), v.date)
        assertNull(v.from)
    }
}

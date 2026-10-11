package com.megathon.incident.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Classes of gap found by an independent set of sentences after the first corpus: Roman spelling variants of Hindi number
 * words, spoken English clock forms, a spoken hour that needs an anchor, end times, a range of offsets, months, and
 * "X after Y". Model-authored expectations; captures are on Sunday 2026-10-11 (IST).
 */
class TemporalGapClassesTest {
    private val resolver = TemporalResolver()
    private fun at(hhmm: String) = Instant.parse("2026-10-11T${hhmm}:00+05:30")
    private val morning = at("08:00")
    private val afternoon = at("13:30")
    private val lateNight = at("23:50")
    private val today = LocalDate.of(2026, 10, 11)
    private val yesterday = LocalDate.of(2026, 10, 10)

    private fun r(text: String, at: Instant = morning) = assertNotNull(resolver.resolve(text, at), "expected a value for '$text'")

    // ---- 1. Roman spelling variants of Hindi number words ---------------------------------------------------------------

    @Test fun dusIsDas() {
        val v = r("parso raat dus baje chori hui")
        assertEquals(LocalDate.of(2026, 10, 9), v.date)
        assertEquals(LocalTime.of(22, 0), v.time)
        assertEquals(PartOfDay.NIGHT, v.partOfDay)
        assertFalse(v.ambiguous)
    }

    @Test fun hourSpellings() {
        for ((phrase, hour) in listOf(
            "subah dus baje" to 10, "subah dass baje" to 10, "subah nao baje" to 9, "subah tin baje" to 3, "dopahar tin baje" to 15,
            "subah panch baje" to 5, "subah pach baje" to 5, "subah paanch baje" to 5, "subah chhah baje" to 6, "subah che baje" to 6,
            "subah chah baje" to 6, "subah giyarah baje" to 11, "subah gyaarah baje" to 11, "subah baarah baje" to 12, "subah aat baje" to 8,
        )) assertEquals(LocalTime.of(hour % 24, 0), r(phrase).time, phrase)
    }

    @Test fun fractionSpellings() {
        assertEquals(LocalTime.of(10, 30), r("sade das baje").time)
        assertEquals(LocalTime.of(10, 30), r("saade das baje").time)
        assertEquals(LocalTime.of(3, 30), r("sadhe teen baje").time)
        assertEquals(LocalTime.of(10, 15), r("sawa das baje").time)
        assertEquals(LocalTime.of(9, 45), r("paone das baje").time)
        assertEquals(LocalTime.of(1, 30), r("dhed baje").time)
        assertEquals(LocalTime.of(1, 30), r("derh baje").time)
        assertEquals(LocalTime.of(2, 30), r("dhaai baje").time)
        assertEquals(LocalTime.of(2, 30), r("dhayi baje").time)
    }

    @Test fun offsetSpellings() {
        assertEquals(LocalTime.of(7, 50), r("dus minute pehle").time)
        assertEquals(LocalTime.of(7, 50), r("dass minute pehle").time)
        assertEquals(LocalTime.of(7, 30), r("adha ghanta pehle").time)
        assertEquals(LocalTime.of(7, 30), r("aadhe ghante pehle").time)
        assertEquals(LocalTime.of(5, 30), r("dhaai ghante pehle").time)
        assertEquals(LocalTime.of(6, 30), r("dhed ghante pehle").time)
    }

    @Test fun findsTheVariantSpellingsToo() {
        assertEquals("parso raat dus baje", resolver.findTimePhrase("parso raat dus baje chori hui"))
        assertEquals("sade das baje", resolver.findTimePhrase("sade das baje pump band hua"))
        assertEquals("dus minute pehle", resolver.findTimePhrase("dus minute pehle pipe phata"))
    }

    // ---- 2. spoken English clock forms ----------------------------------------------------------------------------------

    @Test fun hourAndMinuteWordsWithAPartOfDay() {
        val v = r("ten thirty last night the generator failed", lateNight)
        assertEquals(yesterday, v.date)
        assertEquals(LocalTime.of(22, 30), v.time)
        assertEquals(PartOfDay.NIGHT, v.partOfDay)
        assertEquals(LocalTime.of(8, 45), r("eight forty five this morning the alarm rang").time)
        assertEquals(LocalTime.of(22, 45), r("it was ten forty five pm").time)
        assertEquals(LocalTime.of(8, 45), r("at eight forty five").time)
    }

    @Test fun minutesPastAndTo() {
        val v = r("Smoke started at ten past eight this morning", afternoon)
        assertEquals(today, v.date)
        assertEquals(LocalTime.of(8, 10), v.time)
        assertEquals(LocalTime.of(8, 40), r("at twenty to nine this morning").time)
        assertEquals(LocalTime.of(8, 45), r("at quarter to nine this morning").time)
        assertEquals(LocalTime.of(18, 5), r("five past six in the evening").time)
        assertEquals(LocalTime.of(8, 10), r("at 10 past 8 am").time)
        assertEquals(LocalTime.of(8, 25), r("at twenty five past eight am").time)
        assertEquals(LocalTime.of(7, 30), r("half past seven this morning").time)
    }

    @Test fun aRangeOfPeopleIsNotAClockTime() {
        assertNull(resolver.resolve("ten to twelve people were injured", morning))
        assertNull(resolver.resolve("five to six workers were nearby", morning))
    }

    @Test fun findsTheSpokenClockForms() {
        assertEquals("ten thirty last night", resolver.findTimePhrase("Ten thirty last night the generator failed")?.lowercase())
        assertEquals("at ten past eight this morning", resolver.findTimePhrase("Smoke started at ten past eight this morning")?.lowercase())
    }

    // ---- 3. a spoken hour needs an anchor ---------------------------------------------------------------------------------

    @Test fun spokenHourWithAPartOfDayOrDay() {
        val v = r("It was last night at about eleven")
        assertEquals(yesterday, v.date)
        assertEquals(LocalTime.of(23, 0), v.time)
        assertTrue(v.approximate)
        val w = r("this morning around five", afternoon)
        assertEquals(LocalTime.of(5, 0), w.time)
        assertTrue(w.approximate)
        assertEquals(LocalTime.of(18, 0), r("yesterday evening around six").time)
    }

    @Test fun aStrayHourWordIsNeverATime() {
        assertNull(resolver.resolve("one of the pipes near the lab", morning))
        assertNull(resolver.resolve("we saw it around one of the pipes", morning))
        assertNull(r("we saw it around one of the pipes this morning").time)
        assertNull(r("around five workers this morning").time)
        assertNull(r("at two pipes were leaking this morning").time)
    }

    @Test fun findsASpokenHourOnlyWithAnAnchor() {
        assertEquals("last night at about eleven", resolver.findTimePhrase("It was last night at about eleven")?.lowercase())
        assertEquals("this morning around five", resolver.findTimePhrase("this morning around five")?.lowercase())
        assertNull(resolver.findTimePhrase("we saw it around one of the pipes"))
    }

    // ---- 4. end times, ranges of offsets, months -------------------------------------------------------------------------

    @Test fun anEndTimeIsNotTheIncidentTime() {
        for (s in listOf(
            "aaj subah 10 baje tak kaam chala tha", "आज सुबह दस बजे तक काम चला था", "work continued until 10", "it ran till 5 pm", "the leak lasted up to noon",
        )) assertNull(resolver.resolve(s, afternoon), s)
        assertNull(resolver.findTimePhrase("aaj subah 10 baje tak kaam chala tha"))
    }

    @Test fun theStartOfAFromToIsStillUsed() {
        val v = r("kal raat 8 baje se aaj subah 10 baje tak paani aaya tha")
        assertEquals(yesterday, v.date)
        assertEquals(LocalTime.of(20, 0), v.time)
    }

    @Test fun anEndDayDoesNotHideTheIncident() {
        val v = r("pipe kal shaam ko phata aur aaj subah tak leak karta raha")
        assertEquals(yesterday, v.date)
        assertEquals(PartOfDay.EVENING, v.partOfDay)
    }

    @Test fun aRangeOfOffsetsIsTheMidpointAndApproximate() {
        for (s in listOf("do teen ghante pehle", "two or three hours ago", "2-3 hours ago", "2 se 3 ghante pehle", "दो तीन घंटे पहले")) {
            val v = r(s)
            assertEquals(LocalTime.of(5, 30), v.time, s)
            assertTrue(v.approximate, s)
        }
        val days = r("do teen din pehle")
        assertEquals(LocalDate.of(2026, 10, 9), days.date)
        assertTrue(days.approximate)
        assertEquals(LocalTime.of(6, 0), r("a couple of hours ago").time)
        assertTrue(r("a couple of hours ago").approximate)
    }

    @Test fun lastMonthIsARangeNotADate() {
        for (s in listOf("last month", "pichhle mahine", "पिछले महीने", "previous month")) {
            val v = r(s)
            assertNull(v.date, s)
            assertEquals(LocalDate.of(2026, 9, 1), v.from, s)
            assertEquals(LocalDate.of(2026, 9, 30), v.to, s)
            assertTrue(v.approximate, s)
            assertTrue(v.needsClarification, s)
        }
        assertEquals(LocalDate.of(2026, 10, 1), r("is mahine").from)
        assertEquals(LocalDate.of(2026, 10, 11), r("this month").to)
    }

    @Test fun monthsAgoIsOneApproximateDate() {
        for (s in listOf("three months ago", "teen mahine pehle", "3 महीने पहले")) {
            val v = r(s)
            assertEquals(LocalDate.of(2026, 7, 11), v.date, s)
            assertNull(v.time, s)
            assertTrue(v.approximate, s)
        }
    }

    @Test fun findsMonthPhrases() {
        assertEquals("Last month", resolver.findTimePhrase("Last month a pipe burst near the lab"))
        assertEquals("Pichhle mahine", resolver.findTimePhrase("Pichhle mahine pipe phata tha"))
        assertEquals("three months ago", resolver.findTimePhrase("The crack appeared three months ago"))
    }

    // ---- found by probing variants of the classes above (after the first version of this file) ------------------------------

    @Test fun aRangeOfDaysDoesNotLeaveAStrayClockHour() {
        val v = r("do ya teen din pehle pipe phata tha")
        assertNull(v.time)
        assertEquals(LocalDate.of(2026, 10, 9), v.date)
        assertNull(r("a couple of days ago the lamp broke").time)
    }

    @Test fun minitIsMinute() {
        assertEquals(LocalTime.of(7, 40), r("bees minit pehle blast hua").time)
        assertEquals("bees minit pehle", resolver.findTimePhrase("bees minit pehle blast hua"))
    }

    @Test fun aPhraseKeepsAPartOfDayAndDayThatFollowIt() {
        assertEquals(
            "around ten past three in the afternoon yesterday",
            resolver.findTimePhrase("around ten past three in the afternoon yesterday the crane tipped"),
        )
    }

    // ---- 5. "X after Y": Y happened first ---------------------------------------------------------------------------------

    @Test fun theClauseAfterAfterIsTheIncidentWhenTheFirstIsAResponse() {
        val s = "We called the electrician at 1 PM after the lights went out at noon"
        assertEquals(LocalTime.of(12, 0), r(s, afternoon).time)
        assertEquals("at noon", resolver.findTimePhrase(s))
    }

    @Test fun afterMidnightIsNotAClauseBreak() = assertEquals(today, r("late last night after midnight").date)
}

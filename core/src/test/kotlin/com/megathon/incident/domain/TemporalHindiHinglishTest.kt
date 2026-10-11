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
 * Targeted tests for the Hindi / Hinglish / tense / weekday / midnight behaviour of [TemporalResolver]. The model-authored
 * expectations are a draft for a native reviewer. Captures are on Sunday 2026-10-11 (IST).
 */
class TemporalHindiHinglishTest {
    private val resolver = TemporalResolver()
    private fun at(hhmm: String) = Instant.parse("2026-10-11T${hhmm}:00+05:30")
    private val morning = at("08:00")
    private val afternoon = at("13:30")
    private val lateNight = at("23:50")
    private val justAfterMidnight = at("00:10")
    private val today = LocalDate.of(2026, 10, 11)
    private val yesterday = LocalDate.of(2026, 10, 10)

    private fun r(text: String, at: Instant = morning) = assertNotNull(resolver.resolve(text, at), "expected a value for '$text'")

    // ---- fractions and number words ------------------------------------------------------------------------------------

    @Test fun saadheDasBaje() = assertEquals(LocalTime.of(10, 30), r("saadhe das baje").time)
    @Test fun savaDasBaje() = assertEquals(LocalTime.of(10, 15), r("sava das baje").time)
    @Test fun pauneDasBajeIsNineFortyFive() = assertEquals(LocalTime.of(9, 45), r("paune das baje").time)
    @Test fun pauneEkBajeIsTwelveFortyFive() = assertEquals(LocalTime.of(12, 45), r("paune ek baje").time)
    @Test fun dedhBaje() = assertEquals(LocalTime.of(1, 30), r("dedh baje").time)
    @Test fun dhaiBaje() = assertEquals(LocalTime.of(2, 30), r("dhai baje").time)
    @Test fun devanagariFractions() {
        assertEquals(LocalTime.of(10, 30), r("साढ़े दस बजे").time)
        assertEquals(LocalTime.of(10, 15), r("सवा दस बजे").time)
        assertEquals(LocalTime.of(9, 45), r("पौने दस बजे").time)
        assertEquals(LocalTime.of(1, 30), r("डेढ़ बजे").time)
        assertEquals(LocalTime.of(2, 30), r("ढाई बजे").time)
    }
    @Test fun fractionalClockWithoutAmPmIsStillAmbiguous() = assertTrue(r("saadhe das baje").ambiguous)
    @Test fun devanagariDigitsAndNumberWords() {
        assertEquals(LocalTime.of(10, 0), r("सुबह १० बजे").time)
        assertEquals(LocalTime.of(23, 0), r("रात ग्यारह बजे").time)
        assertEquals(LocalTime.of(0, 0), r("रात बारह बजे").time)
    }

    @Test fun fractionalOffsets() {
        assertEquals(LocalTime.of(7, 30), r("aadha ghanta pehle").time)
        assertEquals(LocalTime.of(6, 30), r("dedh ghante pehle").time)
        assertEquals(LocalTime.of(5, 30), r("dhai ghante pehle").time)
        assertEquals(LocalTime.of(7, 30), r("आधा घंटा पहले").time)
        assertEquals(LocalTime.of(7, 40), r("20 minute pehle").time)
        assertEquals(LocalTime.of(7, 40), r("बीस मिनट पहले").time)
        assertEquals(LocalTime.of(7, 40), r("२० मिनट पहले").time)
    }

    @Test fun aroundAnOffsetIsApproximate() = assertTrue(r("lagbhag teen ghante pehle").approximate)
    @Test fun anExactOffsetIsNotApproximate() = assertFalse(r("do ghante pehle").approximate)

    @Test fun kuchDerPehleIsTodayApproximateWithNoClockTime() {
        val v = r("kuch der pehle", afternoon)
        assertEquals(today, v.date)
        assertNull(v.time)
        assertTrue(v.approximate)
    }

    @Test fun daysAgoIsADateWithoutATime() {
        for (phrase in listOf("do din pehle", "2 days ago", "दो दिन पहले", "two days ago")) {
            val v = r(phrase)
            assertEquals(LocalDate.of(2026, 10, 9), v.date, phrase)
            assertNull(v.time, phrase)
        }
    }

    @Test fun englishQuarterAndSpokenMinutes() {
        assertEquals(LocalTime.of(7, 15), r("at quarter past seven").time)
        assertEquals(LocalTime.of(6, 45), r("at quarter to seven").time)
        assertEquals(LocalTime.of(10, 30), r("at ten thirty").time)
        assertEquals(LocalTime.of(14, 30), r("at two thirty pm").time)
    }

    @Test fun noonAndMidnight() {
        assertEquals(LocalTime.of(12, 0), r("at noon").time)
        assertEquals(LocalTime.of(0, 0), r("at midnight").time)
    }

    // ---- days, tense --------------------------------------------------------------------------------------------------

    @Test fun parsoAndNarsoStayAmbiguousWithoutATense() {
        for (phrase in listOf("parso shaam", "नरसों रात")) {
            val v = r(phrase)
            assertNull(v.date, phrase)
            assertTrue(v.ambiguous, phrase)
        }
    }

    @Test fun pastTenseSettlesKal() {
        val v = r("kal shaam chhe baje lab mein paani leak hua")
        assertEquals(yesterday, v.date)
        assertEquals(LocalTime.of(18, 0), v.time)
        assertFalse(v.ambiguous)
        assertEquals(yesterday, r("कल रात गेट पर किसी ने चोरी की कोशिश की").date)
    }

    @Test fun pastTenseSettlesParsoAndNarso() {
        assertEquals(LocalDate.of(2026, 10, 9), r("parso shaam parking mein takkar ho gayi").date)
        assertEquals(LocalDate.of(2026, 10, 8), r("नरसों रात को ताला टूटा हुआ मिला").date)
    }

    @Test fun anIncompleteSentenceWithNoVerbKeepsKalAmbiguous() {
        val v = r("kal subah line number teen par shutdown")
        assertNull(v.date)
        assertTrue(v.ambiguous)
    }

    @Test fun futureStatementsAreNotAnIncidentTime() {
        for (sentence in listOf(
            "kal subah main site ka inspection karunga", "आज शाम पांच बजे मरम्मत का काम होगा", "aaj shaam paanch baje repair ka kaam hoga",
            "The inspection is scheduled for tomorrow at 10 am in the plant room.", "We will check the boiler at 9 am.",
        )) assertNull(resolver.resolve(sentence, morning), sentence)
    }

    @Test fun aPastAnchorSurvivesAFutureVerbElsewhere() = assertEquals(yesterday, r("yesterday evening it started and we will fix it", morning).date)

    @Test fun dayBeforeYesterdayIsTwoDaysBack() = assertEquals(LocalDate.of(2026, 10, 9), r("the day before yesterday").date)

    @Test fun devanagariKalIsNotInsideNikal() = assertNull(resolver.resolve("पानी निकल रहा है", morning))

    @Test fun todaysBareClockUsesTheCaptureInstantToChooseAmOrPm() {
        // 13:30: 22:00 has not happened yet.
        val v = r("aaj 10 baje lab mein pipe phata", afternoon)
        assertEquals(LocalTime.of(10, 0), v.time)
        assertFalse(v.ambiguous)
        // 23:50: both 10:00 and 22:00 are in the past, so it stays ambiguous.
        assertTrue(r("aaj 10 baje lab mein pipe phata", lateNight).ambiguous)
    }

    // ---- weekdays -----------------------------------------------------------------------------------------------------

    @Test fun weekdayIsTheMostRecentOneBeforeToday() {
        for ((phrase, date) in listOf(
            "on Tuesday" to LocalDate.of(2026, 10, 6), "somvaar ko" to LocalDate.of(2026, 10, 5), "पिछले शुक्रवार" to LocalDate.of(2026, 10, 9),
            "pichhle shanivaar" to LocalDate.of(2026, 10, 10), "mangalvaar subah nau baje" to LocalDate.of(2026, 10, 6),
        )) assertEquals(date, r(phrase).date, phrase)
    }

    @Test fun weekdayOfLastWeek() {
        assertEquals(LocalDate.of(2026, 9, 30), r("पिछले हफ्ते बुधवार को").date)
        assertEquals(LocalDate.of(2026, 9, 30), r("last week on Wednesday").date)
    }

    @Test fun weekdayKeepsItsPartOfDayAndClock() {
        val v = r("on Monday at 9 am")
        assertEquals(LocalDate.of(2026, 10, 5), v.date)
        assertEquals(LocalTime.of(9, 0), v.time)
        assertEquals(PartOfDay.AFTERNOON, r("last Friday afternoon").partOfDay)
    }

    // ---- midnight -----------------------------------------------------------------------------------------------------

    @Test fun lastNightAtOneAmIsTheEarlyHoursOfTheNextCalendarDay() {
        for (phrase in listOf("at 1 am last night", "kal raat ek baje ke aaspaas ho gaya", "कल रात एक बजे के आसपास बंद हो गया")) {
            val v = r(phrase)
            assertEquals(today, v.date, phrase)
            assertEquals(LocalTime.of(1, 0), v.time, phrase)
        }
    }

    @Test fun lastNightBeforeMidnightStaysOnYesterday() = assertEquals(yesterday, r("at 11:45 pm last night").date)

    @Test fun lateLastNightAfterMidnightIsToday() {
        val v = r("late last night after midnight")
        assertEquals(today, v.date)
        assertEquals(PartOfDay.NIGHT, v.partOfDay)
        assertNull(v.time)
    }

    @Test fun twentyMinutesAgoJustAfterMidnightIsYesterdayEvening() {
        val v = r("20 minute pehle", justAfterMidnight)
        assertEquals(yesterday, v.date)
        assertEquals(LocalTime.of(23, 50), v.time)
    }

    // ---- two times: the incident one wins --------------------------------------------------------------------------------

    @Test fun theIncidentClauseBeatsAResponseClause() {
        assertEquals(PartOfDay.EVENING, r("Yesterday evening a pipe burst, we called maintenance at 7 PM today.", lateNight).partOfDay)
        assertEquals(yesterday, r("Yesterday evening a pipe burst, we called maintenance at 7 PM today.", lateNight).date)
        assertEquals(LocalTime.of(9, 0), r("The boiler tripped around 9 am and I informed the supervisor at 11 am.").time)
        val said = r("I reported it at 9 am but the leak actually started last night.")
        assertEquals(yesterday, said.date)
        assertNull(said.time)
        val hindi = r("कल शाम पाइप फटा, हमने आज सुबह नौ बजे प्लंबर बुलाया", lateNight)
        assertEquals(yesterday, hindi.date)
        assertEquals(PartOfDay.EVENING, hindi.partOfDay)
        assertNull(hindi.time)
    }

    @Test fun theFirstCompleteClauseWinsOverALaterTimeThatIsNotAResponse() {
        // found by probing after the corpus: "aaj" in the second clause used to win over the incident's "kal shaam".
        val v = r("pipe kal shaam ko phata aur aaj subah tak leak karta raha")
        assertEquals(yesterday, v.date)
        assertEquals(PartOfDay.EVENING, v.partOfDay)
    }

    @Test fun aSingleResponseClauseIsStillUsedWhenThereIsNoOther() = assertEquals(LocalTime.of(19, 0), r("we called maintenance at 7 pm").time)

    @Test fun aSplitExpressionAcrossACommaStillResolvesAsOne() {
        val v = r("yesterday, in the evening", morning)
        assertEquals(yesterday, v.date)
        assertEquals(PartOfDay.EVENING, v.partOfDay)
    }

    // ---- findTimePhrase -----------------------------------------------------------------------------------------------

    private fun found(sentence: String) = resolver.findTimePhrase(sentence)

    @Test fun findsHindiAndHinglishPhrasesAsTheWordsThemselves() {
        assertEquals("कल शाम छह बजे", found("कल शाम छह बजे लैब में पानी लीक हुआ"))
        assertEquals("Kal shaam chhe baje", found("Kal shaam chhe baje lab mein paani leak hua"))
        assertEquals("साढ़े दस बजे", found("साढ़े दस बजे जनरेटर बंद हो गया"))
        assertEquals("Dedh baje", found("Dedh baje mere haath mein chot lag gayi"))
        assertEquals("पौने दस बजे के आसपास", found("पौने दस बजे के आसपास पाइप से पानी रिसने लगा"))
        assertEquals("Do ghante pehle", found("Do ghante pehle kamre mein aag lag gayi thi"))
        assertEquals("आधा घंटा पहले", found("आधा घंटा पहले पाइप फट गया"))
        assertEquals("परसों शाम", found("परसों शाम को पार्किंग में गाड़ी से टक्कर हो गई"))
        assertEquals("pichhle shukravaar dopahar", found("Pichhle shukravaar dopahar ko seedhi se ek worker gir gaya")?.lowercase())
        assertEquals("pichhle hafte", found("Pichhle hafte chhat se paani tapak raha tha")?.lowercase())
    }

    @Test fun findsEnglishPartsOfDayWeekdaysAndDays() {
        assertEquals("Yesterday evening", found("Yesterday evening a pipe burst in the corridor."))
        assertEquals("the day before yesterday", found("The day before yesterday there was a small fire.")?.lowercase())
        assertEquals("On Tuesday", found("On Tuesday the conveyor belt jammed."))
        assertEquals("three days ago", found("The railing came loose three days ago and nobody fixed it."))
        assertEquals("at half past eleven at night", found("Sparks came out at half past eleven at night near the bay."))
    }

    @Test fun findPicksTheIncidentClauseNotTheResponse() {
        assertEquals("yesterday evening", found("Yesterday evening a pipe burst, we called maintenance at 7 PM today.")?.lowercase())
        assertEquals("कल शाम", found("कल शाम पाइप फटा, हमने आज सुबह नौ बजे प्लंबर बुलाया"))
        assertEquals("last night", found("I reported it at 9 am but the leak started last night.")?.lowercase())
    }

    @Test fun findIgnoresFutureClausesAndNonTimes() {
        assertNull(found("कल सुबह मैं साइट का निरीक्षण करूंगा"))
        assertNull(found("The inspection is scheduled for tomorrow at 10 am in the plant room."))
        assertNull(found("I was at 3 meters from the pipe when it burst."))
        assertNull(found("The guard works at night near the south gate."))
        assertNull(found("गोदाम के पीछे वाले गेट पर दो रात के गार्ड तैनात थे"))
    }

    @Test fun resolveInContextUsesTheSentenceTense() {
        val sentence = "Kal shaam chhe baje lab mein paani leak hua aur humne valve band kiya"
        val phrase = assertNotNull(found(sentence))
        assertTrue(r(phrase).ambiguous, "the phrase alone has no tense")
        val v = assertNotNull(resolver.resolveInContext(phrase, sentence, morning))
        assertEquals(yesterday, v.date)
        assertFalse(v.ambiguous)
    }

    // ---- not times ----------------------------------------------------------------------------------------------------

    @Test fun measurementsAndOrdinalsAreNotClockTimes() {
        assertNull(resolver.resolve("I was at 3 meters from the pipe", morning))
        assertNull(resolver.resolve("it is at 3rd floor", morning))
    }
}

package com.megathon.incident.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PartOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * A time expression resolved as far as the words allow. Precision is preserved: a missing date stays
 * null, a part of day never becomes an invented clock time, and "kal" stays ambiguous.
 *
 * A week ("last week", "this week") is a range, never a date: [date] stays null and [from]..[to]
 * (inclusive; ISO weeks start on Monday) carries what was said, so the planner can ask "which day?".
 */
data class TemporalValue(
    val date: LocalDate?,
    val time: LocalTime?,
    val partOfDay: PartOfDay?,
    val approximate: Boolean,
    val ambiguous: Boolean,
    val sourceText: String,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    /** The date + clock-time policy (design §4) is not yet satisfied: the app must ask. */
    val needsClarification: Boolean get() = date == null || time == null || ambiguous
}

/**
 * Deterministic English + Hindi/Hinglish time resolver. Resolves against the *capture* instant of the
 * utterance in the session zone, never the later inference time and never the (wrong) kit clock string.
 */
class TemporalResolver(private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {

    fun resolve(text: String, capturedAt: Instant): TemporalValue? {
        val t = spokenHoursAsDigits(EnumNormalizer.normalize(text) ?: return null)
        val now = capturedAt.atZone(zone)

        relativeOffset(t)?.let { minutes ->
            val then = now.minusMinutes(minutes)
            return TemporalValue(then.toLocalDate(), then.toLocalTime().truncatedTo(ChronoUnit.MINUTES), null, false, false, text)
        }
        if (isNow(t)) {
            return TemporalValue(now.toLocalDate(), now.toLocalTime().truncatedTo(ChronoUnit.MINUTES), null, false, false, text)
        }

        val today = now.toLocalDate()
        val lastNight = has(t, "last night")
        val tonight = has(t, "tonight")
        val day = when {
            has(t, "yesterday") || lastNight -> Day.YESTERDAY
            has(t, "today") || has(t, "aaj") || has(t, "आज") || has(t, "this morning") ||
                has(t, "this afternoon") || has(t, "this evening") || tonight -> Day.TODAY
            has(t, "kal") || has(t, "कल") -> Day.KAL
            else -> null
        }
        val partOfDay = when {
            lastNight || tonight -> PartOfDay.NIGHT
            has(t, "morning") || has(t, "subah") || has(t, "सुबह") -> PartOfDay.MORNING
            has(t, "afternoon") || has(t, "dopahar") || has(t, "दोपहर") -> PartOfDay.AFTERNOON
            has(t, "evening") || has(t, "shaam") || has(t, "शाम") -> PartOfDay.EVENING
            has(t, "night") || has(t, "raat") || has(t, "रात") -> PartOfDay.NIGHT
            else -> null
        }
        val clock = clockTime(t)

        // A week is a range, not a day; an explicit day word wins over it.
        val week = if (day == null) weekRange(t, today) else null
        if (week != null) {
            val time = clock?.let { c ->
                val (hour, ambiguousClock) = to24h(c, partOfDay)
                if (ambiguousClock) null else LocalTime.of(hour, c.minute)
            }
            return TemporalValue(
                date = week.date, time = time, partOfDay = partOfDay, approximate = true,
                ambiguous = false, sourceText = text, from = week.from, to = week.to,
            )
        }

        // A part of day on its own ("the night guard") is not a time.
        if (clock == null && day == null) return null

        val date = when (day) {
            Day.YESTERDAY -> today.minusDays(1)
            Day.TODAY -> today
            Day.KAL, null -> null
        }
        var ambiguous = day == Day.KAL
        var time: LocalTime? = null
        if (clock != null) {
            val (hour, ambiguousClock) = to24h(clock, partOfDay)
            time = LocalTime.of(hour, clock.minute)
            ambiguous = ambiguous || ambiguousClock
        }
        return TemporalValue(date, time, partOfDay, approximate = isApproximate(t), ambiguous = ambiguous, sourceText = text)
    }

    /**
     * The first time phrase the speaker said, as the words themselves ("around 10 AM", "about fifteen minutes ago",
     * "at 8:30 tonight", "last night"), or null. Used only when the model forgot to give one. A bare "now" is not a
     * time phrase here, and after "no wait" / "I mean" / "sorry" only the corrected wording counts.
     */
    fun findTimePhrase(text: String): String? {
        // A correction ("no wait, 10 PM") may sit in a later sentence than the first time: the words after the last one win.
        CORRECTION.findAll(text.lowercase()).lastOrNull()?.let { c ->
            firstPhraseIn(text.substring(c.range.last + 1))?.let { return it }
        }
        return firstPhraseIn(text)
    }

    private fun firstPhraseIn(text: String): String? {
        for (sentence in text.split(Regex("(?<=[.!?;])\\s+"))) {
            var s = sentence
            CORRECTION.findAll(s.lowercase()).lastOrNull()?.let { s = s.substring(it.range.last + 1) }
            val lower = s.lowercase()
            val spans = FINDERS.flatMap { r -> r.findAll(lower).map { it.range }.toList() }
            if (spans.isEmpty()) continue
            val first = spans.minOf { it.first }
            // Join neighbours that are part of the same expression ("8:30" + "tonight"), not a later sentence part.
            val joined = spans.filter { it.first - first <= 30 }
            val start = joined.minOf { it.first }
            val end = joined.maxOf { it.last }
            return s.substring(start, end + 1).trim(' ', ',', '.')
        }
        return null
    }

    /** Whisper writes "ten am" and "nau baje"; the clock patterns expect digits. */
    private fun spokenHoursAsDigits(t: String): String =
        HOUR_WORDS.replace(t) { m -> (HOUR_NUMBER[m.groupValues[1]] ?: m.groupValues[1]).toString() + " " + m.groupValues[2] }

    private enum class Day { YESTERDAY, TODAY, KAL }

    private data class Clock(val hour: Int, val minute: Int, val meridiem: Meridiem?, val explicit24h: Boolean)
    private enum class Meridiem { AM, PM }

    private fun has(text: String, phrase: String) = EnumNormalizer.containsPhrase(text, phrase)

    private fun isNow(t: String): Boolean {
        if (has(t, "abhi bhi") || has(t, "अभी भी")) return false
        return has(t, "just now") || has(t, "right now") || has(t, "now") || has(t, "abhi") ||
            has(t, "अभी") || has(t, "just happened")
    }

    private class Week(val date: LocalDate?, val from: LocalDate?, val to: LocalDate?)

    /** "N weeks ago" is one approximate date; "last/this week" is a Monday-start range with no date. */
    private fun weekRange(t: String, today: LocalDate): Week? {
        WEEKS_AGO.find(t)?.let { m ->
            val token = m.groupValues[1]
            val n = token.toLongOrNull() ?: NUMBER_WORDS[token] ?: return null
            return Week(today.minusWeeks(n), null, null)
        }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when {
            LAST_WEEK.any { has(t, it) } -> Week(null, monday.minusWeeks(1), monday.minusDays(1))
            THIS_WEEK.any { has(t, it) } -> Week(null, monday, today)
            else -> null
        }
    }

    private fun isApproximate(t: String) = APPROX.any { has(t, it) }

    private fun relativeOffset(t: String): Long? {
        val m = OFFSET.find(t) ?: return null
        val token = m.groupValues[1]
        val unit = m.groupValues[2]
        if (token == "half an") return 30L
        val number = token.toLongOrNull() ?: NUMBER_WORDS[token] ?: return null
        return number * if (unit in HOUR_UNITS) 60L else 1L
    }

    private fun clockTime(t: String): Clock? {
        MERIDIEM.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (hour in 1..12 && minute in 0..59) {
                val mer = if (m.groupValues[3].startsWith("p")) Meridiem.PM else Meridiem.AM
                return Clock(hour, minute, mer, explicit24h = false)
            }
        }
        COLON.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour in 0..23 && minute in 0..59) return Clock(hour, minute, null, explicit24h = hour >= 13 || hour == 0)
        }
        HALF_PAST.find(t)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: HOUR_NUMBER[m.groupValues[1]] ?: return@let
            if (hour in 1..12) return Clock(hour, 30, null, explicit24h = false)
        }
        NUMBER_PART_OF_DAY.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            if (hour in 1..12) return Clock(hour, 0, null, explicit24h = false)
        }
        BAJE.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            if (hour in 0..23) return Clock(hour, 0, null, explicit24h = hour >= 13 || hour == 0)
        }
        BARE_HOUR.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            if (hour in 0..23) return Clock(hour, 0, null, explicit24h = hour >= 13 || hour == 0)
        }
        return null
    }

    /** Returns the 24-hour hour and whether the reading is still ambiguous. */
    private fun to24h(c: Clock, partOfDay: PartOfDay?): Pair<Int, Boolean> {
        c.meridiem?.let { mer ->
            val h = c.hour % 12
            return (if (mer == Meridiem.PM) h + 12 else h) to false
        }
        if (c.explicit24h) return c.hour to false
        return when (partOfDay) {
            PartOfDay.MORNING -> c.hour to false
            PartOfDay.AFTERNOON, PartOfDay.EVENING -> (if (c.hour < 12) c.hour + 12 else c.hour) to false
            PartOfDay.NIGHT -> when {
                c.hour in 6..11 -> c.hour + 12
                c.hour == 12 -> 0
                else -> c.hour
            } to false
            null -> c.hour to true
        }
    }

    private companion object {
        val HOUR_NUMBER = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
            "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
            "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5, "chhe" to 6, "saat" to 7, "aath" to 8,
            "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        )
        val HOUR_WORDS = Regex(
            "(?<![\\p{L}])(" + HOUR_NUMBER.keys.joinToString("|") + ")\\s*(a\\.?m\\.?|p\\.?m\\.?|o'clock|oclock|baje|बजे)",
        )
        val APPROX = listOf("around", "about", "approximately", "approx", "roughly", "lagbhag", "kareeb", "करीब", "लगभग", "तकरीबन")

        val HOUR_UNITS = setOf("hour", "hours", "hr", "hrs", "ghante", "ghanta", "ghantey", "ghanto", "घंटे", "घंटा")

        val NUMBER_WORDS = mapOf(
            "a" to 1L, "an" to 1L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L,
            "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L,
            "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L,
            "nineteen" to 19L, "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "forty five" to 45L, "forty-five" to 45L, "fifty" to 50L,
            "ek" to 1L, "do" to 2L, "teen" to 3L, "char" to 4L, "paanch" to 5L, "chhe" to 6L, "chhah" to 6L,
            "saat" to 7L, "aath" to 8L, "nau" to 9L, "das" to 10L,
            "एक" to 1L, "दो" to 2L, "तीन" to 3L, "चार" to 4L,
        )

        const val NB = "(?<![\\p{L}\\p{N}])"

        private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

        val LAST_WEEK = listOf(
            "last week", "previous week", "pichhle hafte", "pichle hafte", "pichhle hafta", "pichle hafta",
            "पिछले हफ्ते", "पिछले हफ़्ते", "पिछले हफ्ता", "पिछले सप्ताह",
        ).map(::nfc)
        val THIS_WEEK = listOf(
            "this week", "is hafte", "is hafta", "इस हफ्ते", "इस हफ़्ते", "इस हफ्ता", "इस सप्ताह",
        ).map(::nfc)
        val WEEKS_AGO = Regex(
            nfc(NB + "(\\d{1,2}|a|an|one|two|three|four|ek|do|teen|char|एक|दो|तीन|चार)\\s*" +
                "(?:weeks?|hafte|hafta|हफ्ते|हफ़्ते|हफ्ता|सप्ताह)\\s*(?:ago|pehle|pahle|पहले)"),
        )

        val OFFSET = Regex(
            NB + "(\\d{1,3}|half an|an|a|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty[ -]five|forty|fifty|" +
                "ek|do|teen|char|paanch|chhe|chhah|saat|aath|nau|das)\\s*" +
                "(hours?|hrs?|minutes?|mins?|ghante|ghanta|ghantey|ghanto|मिनट|घंटे|घंटा)\\s*(ago|pehle|pahle|पहले)",
        )
        val MERIDIEM = Regex(NB + "(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\p{L}])")
        val COLON = Regex(NB + "(\\d{1,2}):(\\d{2})(?![\\p{N}])")
        val BAJE = Regex(NB + "(\\d{1,2})\\s*(?:baje|बजे|o'clock|oclock)")
        val CORRECTION = Regex("(?:no wait|no, wait|i mean|sorry|actually|rather)[,\\s]+")
        private const val PRE = "(?:(?:at|around|about|approximately|approx|roughly|since|by)\\s+)?"
        private const val DAYWORD = "(?:yesterday|last night|tonight|today|this morning|this afternoon|this evening)"
        val FINDERS: List<Regex> by lazy {
            listOf(
                Regex(PRE + OFFSET.pattern, RegexOption.IGNORE_CASE),
                Regex(PRE + MERIDIEM.pattern, RegexOption.IGNORE_CASE),
                Regex(PRE + COLON.pattern),
                Regex(PRE + BAJE.pattern),
                Regex(PRE + HALF_PAST.pattern, RegexOption.IGNORE_CASE),
                Regex(PRE + "(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s+(?:a\\.?m\\.?|p\\.?m\\.?|o'clock)"),
                Regex(PRE + NUMBER_PART_OF_DAY.pattern),
                Regex(NB + DAYWORD + "(?![\\p{L}])"),
            )
        }
        val HALF_PAST = Regex(NB + "half past (\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)(?![\\p{L}\\p{N}])")
        // "11 this morning", "8 tonight", "9 at night"; never "2 night guards" or "3 morning shift workers".
        val NUMBER_PART_OF_DAY = Regex(
            NB + "(\\d{1,2})\\s+(?:this\\s+(?:morning|afternoon|evening|night)|tonight|in\\s+the\\s+(?:morning|afternoon|evening)|at\\s+night)(?![\\p{L}])",
        )
        val BARE_HOUR = Regex(
            "(?<![\\p{L}\\p{N}])(?:at|around|about|approximately|approx|roughly)\\s+(\\d{1,2})(?![\\p{N}:])" +
                "(?!\\s*(?:hours?|minutes?|mins?|people|persons|pm|am))",
        )
    }
}

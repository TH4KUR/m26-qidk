package com.megathon.incident.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class PartOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * A time expression resolved as far as the words allow. Precision is preserved: a missing date stays
 * null, a part of day never becomes an invented clock time, and "kal" stays ambiguous.
 */
data class TemporalValue(
    val date: LocalDate?,
    val time: LocalTime?,
    val partOfDay: PartOfDay?,
    val approximate: Boolean,
    val ambiguous: Boolean,
    val sourceText: String,
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
            "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L, "twelve" to 12L,
            "ek" to 1L, "do" to 2L, "teen" to 3L, "char" to 4L, "paanch" to 5L, "chhe" to 6L, "chhah" to 6L,
            "saat" to 7L, "aath" to 8L, "nau" to 9L, "das" to 10L,
        )

        const val NB = "(?<![\\p{L}\\p{N}])"

        val OFFSET = Regex(
            NB + "(\\d{1,3}|half an|an|a|one|two|three|four|five|six|seven|eight|nine|ten|twelve|" +
                "ek|do|teen|char|paanch|chhe|chhah|saat|aath|nau|das)\\s*" +
                "(hours?|hrs?|minutes?|mins?|ghante|ghanta|ghantey|ghanto|मिनट|घंटे|घंटा)\\s*(ago|pehle|pahle|पहले)",
        )
        val MERIDIEM = Regex(NB + "(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\p{L}])")
        val COLON = Regex(NB + "(\\d{1,2}):(\\d{2})(?![\\p{N}])")
        val BAJE = Regex(NB + "(\\d{1,2})\\s*(?:baje|बजे|o'clock|oclock)")
        val BARE_HOUR = Regex(
            "(?<![\\p{L}\\p{N}])(?:at|around|about|approximately|approx|roughly)\\s+(\\d{1,2})(?![\\p{N}:])" +
                "(?!\\s*(?:hours?|minutes?|mins?|people|persons|pm|am))",
        )
    }
}

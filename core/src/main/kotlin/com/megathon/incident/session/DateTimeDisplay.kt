package com.megathon.incident.session

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads and rewrites the DATE_TIME text produced by `PayloadValidator`:
 * `"<yyyy-MM-dd | date missing> <≈?HH:mm | morning.. | time missing> (<spoken phrase>)"`.
 * Display only: it never invents a date; [withDate] is the one deliberate completion.
 */
object DateTimeDisplay {
    data class Parts(val date: LocalDate?, val clock: String?, val approximate: Boolean, val partOfDay: String?, val phrase: String)

    private val FORMAT = Regex("""^(\d{4}-\d{2}-\d{2}|date missing) (≈?\d{2}:\d{2}|morning|afternoon|evening|night|time missing) \((.*)\)$""", RegexOption.DOT_MATCHES_ALL)
    private val MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun parse(text: String): Parts? {
        val m = FORMAT.matchEntire(text.trim()) ?: return null
        val (d, c, phrase) = m.destructured
        val date = if (d == "date missing") null else LocalDate.parse(d)
        val approximate = c.startsWith("≈")
        val clock = c.removePrefix("≈").takeIf { it.contains(':') }
        val partOfDay = c.takeIf { clock == null && it != "time missing" }
        return Parts(date, clock, approximate, partOfDay, phrase)
    }

    fun friendly(text: String, today: LocalDate): String {
        val p = parse(text) ?: return text
        val time = p.clock?.let { (if (p.approximate) "about " else "") + clock12(it) } ?: p.partOfDay
        val day = when (p.date) {
            null -> null
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> p.date.format(MONTH)
        }
        return when {
            day == null && time == null -> p.phrase
            day == null -> time!!.replaceFirstChar { it.uppercase() } + " · day not given"
            time == null -> day
            else -> "$day · $time"
        }
    }

    /** `date missing …` becomes the given date; any other text is returned unchanged. */
    fun withDate(text: String, date: LocalDate): String =
        if (text.startsWith("date missing")) date.toString() + text.removePrefix("date missing") else text

    /** Replaces the date part whether it was given or missing. */
    fun replaceDate(text: String, date: LocalDate): String =
        text.replaceFirst(Regex("""^(\d{4}-\d{2}-\d{2}|date missing)"""), date.toString())

    fun clock12(hhmm: String): String {
        val t = LocalTime.parse(hhmm)
        val h = ((t.hour + 11) % 12) + 1
        return "%d:%02d %s".format(h, t.minute, if (t.hour < 12) "AM" else "PM")
    }
}

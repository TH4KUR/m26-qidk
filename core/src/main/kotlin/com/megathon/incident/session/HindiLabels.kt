package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Severity
import com.megathon.incident.tts.HindiPhrases
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Hindi counterpart of [Labels]: what the screens show for a field label, an incident type, a severity, a status word and a
 * date when the app language is Hindi.
 *
 * The incident-type and severity words are the ones [HindiPhrases] speaks, so what is heard equals what is shown. The stored
 * report keeps its canonical values; only what is drawn changes. A value that is not one of ours (free text the reporter gave)
 * comes back as it was.
 */
object HindiLabels {
    fun field(f: Field): String = when (f) {
        Field.INCIDENT_TYPE -> "क्या हुआ"
        Field.LOCATION -> "कहाँ"
        Field.DATE_TIME -> "कब"
        Field.SEVERITY -> "कितना गंभीर"
        Field.PERSONS_INVOLVED -> "शामिल लोग"
        Field.EQUIPMENT_ASSET -> "औज़ार / सामान"
        Field.IMMEDIATE_ACTIONS -> "क्या किया गया"
        Field.WITNESSES -> "गवाह"
        Field.DESCRIPTION -> "सारांश"
        Field.RECOMMENDED_FOLLOW_UP -> "सुझाया गया अगला कदम"
    }

    /** The incident type by its wire value, e.g. `water_leak_flood`. */
    fun type(wire: String?): String {
        val known = IncidentType.fromWire(wire) ?: return wire.orEmpty()
        return HindiPhrases.typeName(Labels.type(known.wire)) ?: Labels.type(known.wire)
    }

    /** The severity by its wire value, e.g. `moderate`. "No harm" is a stated level, not a missing one. */
    fun severity(wire: String?): String = when (val known = Severity.fromWire(wire)) {
        null -> wire.orEmpty()
        Severity.NONE -> "कोई नुकसान नहीं"
        else -> HindiPhrases.severityName(Labels.severity(known.wire)) ?: Labels.severity(known.wire)
    }

    /** The status pill word (Heard, Confirmed, Suggested, Needed, None, Not sure); null for [UiStatus.EMPTY]. */
    fun status(s: UiStatus): String? = when (s) {
        UiStatus.HEARD -> "सुना गया"
        UiStatus.CONFIRMED -> "पुष्टि हुई"
        UiStatus.SUGGESTED -> "सुझाया गया"
        UiStatus.NEEDED -> "ज़रूरी"
        UiStatus.NONE -> "कोई नहीं"
        UiStatus.UNKNOWN -> "पक्का नहीं"
        UiStatus.EMPTY -> null
    }

    /** The value as a screen shows it: type and severity by name, the date through [HindiDate], anything else as given. */
    fun display(f: Field, text: String?, today: LocalDate): String? = when {
        text.isNullOrBlank() -> null
        f == Field.INCIDENT_TYPE -> type(text)
        f == Field.SEVERITY -> severity(text)
        f == Field.DATE_TIME -> HindiDate.friendly(text, today)
        else -> text
    }
}

/**
 * The stored date and time of an incident (`2026-10-11 ≈10:00 (around 10 AM)` and its relatives) written for a Hindi reader:
 * Hindi month and weekday names, Devanagari numerals and a part-of-day word with बजे. The stored value is never changed.
 * It reads the value with [DateTimeDisplay.parse], so the English and Hindi forms always agree on what the text says.
 *
 * Shapes: `आज, रविवार ११ अक्टूबर · लगभग सुबह १० बजे` (today, "लगभग" for an approximate time), `कल, ...` for yesterday, no
 * आज/कल for any other date, `सुबह १०:३० बजे` when there are minutes, `शुक्रवार ९ अक्टूबर` when only the date is known,
 * `आज, ... · सुबह` for a part of day alone, `सुबह १० बजे · दिन नहीं बताया` when the day is missing. Text that is not in the
 * validator's format is returned unchanged, and so is the stored phrase when neither a day nor a time is known.
 *
 * Part-of-day words, by the 24-hour clock (the hour is then written on a 12-hour face: 0 is १२, 13 is १):
 * 5-11 सुबह, 12-15 दोपहर, 16-19 शाम, 20-4 रात. So 00:00 is "रात १२ बजे" and 12:00 is "दोपहर १२ बजे".
 */
object HindiDate {
    private val WEEKDAYS = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार") // Monday first
    private val MONTHS = listOf(
        "जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
    )
    private val PART_OF_DAY = mapOf("morning" to "सुबह", "afternoon" to "दोपहर", "evening" to "शाम", "night" to "रात")

    fun friendly(text: String, today: LocalDate): String {
        val p = DateTimeDisplay.parse(text) ?: return text
        val time = when {
            p.clock != null -> (if (p.approximate) "लगभग " else "") + clock(p.clock)
            p.partOfDay != null -> PART_OF_DAY[p.partOfDay] ?: p.partOfDay
            else -> null
        }
        val day = p.date?.let { date ->
            val written = "${WEEKDAYS[date.dayOfWeek.value - 1]} ${numerals(date.dayOfMonth)} ${MONTHS[date.monthValue - 1]}"
            when (date) {
                today -> "आज, $written"
                today.minusDays(1) -> "कल, $written"
                else -> written
            }
        }
        return when {
            day == null && time == null -> p.phrase
            day == null -> "$time · दिन नहीं बताया"
            time == null -> day
            else -> "$day · $time"
        }
    }

    /** "सुबह १० बजे", "दोपहर २:३० बजे". */
    private fun clock(hhmm: String): String {
        val t = LocalTime.parse(hhmm)
        val part = when (t.hour) {
            in 5..11 -> "सुबह"
            in 12..15 -> "दोपहर"
            in 16..19 -> "शाम"
            else -> "रात"
        }
        val hour = ((t.hour + 11) % 12) + 1
        val face = if (t.minute == 0) numerals(hour) else numerals(hour) + ":" + numerals(t.minute).padStart(2, '०')
        return "$part $face बजे"
    }

    private fun numerals(n: Int): String = n.toString().map { '०' + (it - '0') }.joinToString("")
}

/**
 * The Hindi of the English sentences the core generates for the screen: priority reasons, suggestion reasons, field hints,
 * the questions and their answer labels, undo labels and short messages. A sentence that has no Hindi yet comes back unchanged.
 *
 * CONTRACT: returns [english] until the table is filled in. Spoken lines still go through
 * [com.megathon.incident.tts.HindiPhrases]; the two must agree, so what is heard equals what is shown.
 */
object HindiUiText {
    fun of(english: String): String = english
}

package com.megathon.incident.session

import com.megathon.incident.domain.Field
import java.time.LocalDate

/**
 * The Hindi counterpart of [Labels]: what the screens show for a field label, an incident type, a severity, a status word and a
 * date when the app language is Hindi.
 *
 * CONTRACT: the names and signatures here are fixed so the screens can be ported in parallel. Until the Hindi table is filled
 * in, every function returns the English text, so a screen never shows a raw key or a blank. The stored report keeps its
 * canonical values; only what is drawn changes.
 */
object HindiLabels {
    fun field(f: Field): String = Labels.fieldLabel(f)

    /** The incident type by its wire value, e.g. `water_leak_flood`. */
    fun type(wire: String?): String = Labels.type(wire)

    /** The severity by its wire value, e.g. `moderate`. */
    fun severity(wire: String?): String = Labels.severity(wire)

    /** The status pill word (Heard, Confirmed, Suggested, Needed, None, Not sure); null for [UiStatus.EMPTY]. */
    fun status(s: UiStatus): String? = when (s) {
        UiStatus.HEARD -> "Heard"
        UiStatus.CONFIRMED -> "Confirmed"
        UiStatus.SUGGESTED -> "Suggested"
        UiStatus.NEEDED -> "Needed"
        UiStatus.NONE -> "None"
        UiStatus.UNKNOWN -> "Not sure"
        UiStatus.EMPTY -> null
    }

    /** The value as a screen shows it: type and severity by name, the date through [HindiDate], anything else as given. */
    fun display(f: Field, text: String?, today: LocalDate): String? = Labels.display(f, text, today)
}

/**
 * The stored date and time of an incident (`2026-10-11 ≈10:00 (around 10 AM)` and its relatives) written for a Hindi reader:
 * Hindi month and weekday names, Devanagari numerals and a part-of-day word. The stored value is never changed.
 *
 * CONTRACT: returns the English friendly form until the formatter is filled in.
 */
object HindiDate {
    fun friendly(text: String, today: LocalDate): String = DateTimeDisplay.friendly(text, today)
}

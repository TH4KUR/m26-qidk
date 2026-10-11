package com.megathon.incident.session

import java.time.Instant
import java.time.ZoneId

/**
 * The "when was this saved" time of a report written for a Hindi reader: day and Hindi month name, a part-of-day word, a
 * 12-hour clock, all in Devanagari numerals (`११ अक्टूबर, दोपहर ३:४५ बजे`). Pure: the zone is a parameter, there is no clock.
 *
 * The part-of-day boundaries are the same as the English date text uses ([DateTimeDisplay]): 05-11 morning, 12-16 afternoon,
 * 17-20 evening, otherwise night.
 */
object HindiSavedAt {
    val INDIA: ZoneId = ZoneId.of("Asia/Kolkata")

    private val MONTH = listOf(
        "जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
    )

    fun format(epochMs: Long, zone: ZoneId = INDIA): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        val part = when (t.hour) {
            in 5..11 -> "सुबह"
            in 12..16 -> "दोपहर"
            in 17..20 -> "शाम"
            else -> "रात"
        }
        val hour = ((t.hour + 11) % 12) + 1
        val clock = "%d:%02d".format(hour, t.minute)
        return digits("${t.dayOfMonth} ${MONTH[t.monthValue - 1]}, $part $clock बजे")
    }

    /** Western digits written as Devanagari digits; every other character is left as it is. */
    fun digits(text: String): String = buildString(text.length) {
        for (ch in text) append(if (ch in '0'..'9') '०' + (ch - '0') else ch)
    }
}

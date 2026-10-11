package com.megathon.incident.tts

/**
 * Prepares text for a neural voice (Piper). Only the Piper path uses this; the Android voice gets the text unchanged.
 *
 * English: times ("10:30" to "ten thirty"), emergency numbers digit by digit ("112" to "one one two"), codes
 * ("Block B-2" to "Block B two"), ordinals, decimals, other numbers as words, and symbols stripped or spelled.
 * Other languages: symbols stripped and emergency numbers split into digits; the voice's own rules read the rest.
 */
object TtsTextNormalizer {
    /** Numbers a person says one digit at a time (national emergency and helpline numbers). */
    private val emergency = setOf("100", "101", "102", "108", "112", "911", "999", "1070", "1077", "1091", "1098")

    private val ones = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val ordinalOnes = listOf(
        "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh",
        "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth",
    )
    private val ordinalTens = listOf("", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth", "eightieth", "ninetieth")

    private val timeRe = Regex("""\b(\d{1,2}):(\d{2})(?:\s?([AaPp])\.?[Mm]\.?)?(?!\d)""")
    private val decimalRe = Regex("""\b(\d+)\.(\d+)\b""")
    private val ordinalRe = Regex("""\b(\d{1,2})(?:st|nd|rd|th)\b""")
    private val codeRe = Regex("""\b([A-Z]{1,3})-?(\d{1,4})\b""")
    private val placeNumberRe = Regex(
        """\b(room|gate|bay|block|zone|ward|wing|lab|platform|tower|building|unit|bus|line|extension|ext|number|no)\.?\s+(\d{3,4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val thousandsRe = Regex("""(?<=\d),(?=\d{3}(?!\d))""")
    private val digitRangeRe = Regex("""(?<=\d)-(?=\d)""")
    private val numberRe = Regex("""\d+""")
    private val hashNumberRe = Regex("""#(?=\d)""")
    private val wordHyphenRe = Regex("""(?<=[\p{L}\p{M}])[-‐‑](?=[\p{L}\p{M}])""")

    fun normalize(text: String, language: String = "en"): String {
        var s = text
        val english = language.trim().lowercase().substringBefore('-').substringBefore('_').let { it.isEmpty() || it == "en" }
        if (english) {
            s = timeRe.replace(s) { spokenTime(it) }
            s = decimalRe.replace(s) { m -> "${number(m.groupValues[1])} point ${digits(m.groupValues[2])}" }
            s = ordinalRe.replace(s) { m -> ordinal(m.groupValues[1].toInt()) }
            s = codeRe.replace(s) { m -> spokenCode(m.groupValues[1], m.groupValues[2]) }
            s = placeNumberRe.replace(s) { m -> "${m.groupValues[1]} ${digits(m.groupValues[2])}" }
            s = thousandsRe.replace(s, "")
            s = digitRangeRe.replace(s, " to ")
            s = hashNumberRe.replace(s, "number ")
            s = s.replace("%", " percent").replace("&", " and ").replace("@", " at ")
            s = numberRe.replace(s) { number(it.value) }
        } else {
            s = numberRe.replace(s) { if (it.value in emergency) it.value.toCharArray().joinToString(" ") else it.value }
        }
        s = wordHyphenRe.replace(s, " ")
        s = s.replace('—', ',').replace('–', ',').replace("(", ", ").replace(")", ", ")
        s = strip(s)
        s = s.replace(Regex("""\s+"""), " ")
        s = s.replace(Regex("""\s+([.,;:!?।])"""), "$1")
        s = s.replace(Regex("""(,\s*){2,}"""), ", ").replace(Regex(""",\s*([.;:!?।])"""), "$1")
        return s.trim().trimStart(',', ' ').trim()
    }

    /** Keeps letters, digits, marks (Devanagari and Telugu vowel signs are marks), whitespace and sentence punctuation. */
    private fun strip(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp)
            val isMark = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()
            val keep = Character.isLetterOrDigit(cp) || Character.isWhitespace(cp) || isMark || cp in KEPT_PUNCTUATION
            when {
                cp == RIGHT_QUOTE -> out.append('\'')
                keep -> out.appendCodePoint(cp)
                else -> out.append(' ')
            }
        }
        return out.toString()
    }

    private const val RIGHT_QUOTE = 0x2019
    private val KEPT_PUNCTUATION = ".,;:!?'।".map { it.code }.toSet()

    private fun spokenTime(m: MatchResult): String {
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 23 || min > 59) return m.value
        val meridian = m.groupValues[3].lowercase().takeIf { it.isNotEmpty() }?.let { if (it == "a") " A M" else " P M" }.orEmpty()
        val twentyFourHour = meridian.isEmpty() && (h == 0 || h >= 13)
        return when {
            min == 0 && twentyFourHour -> if (h == 0) "midnight" else "${number(h.toString())} hundred"
            min == 0 -> "${number(h.toString())} o'clock$meridian"
            min < 10 -> "${number(h.toString())} oh ${ones[min]}$meridian"
            else -> "${number(h.toString())} ${number(min.toString())}$meridian"
        }
    }

    private fun spokenCode(letters: String, digits: String): String {
        val l = letters.toCharArray().joinToString(" ")
        val n = if (digits.length <= 2 && !(digits.length == 2 && digits[0] == '0')) number(digits) else digits(digits)
        return "$l $n"
    }

    private fun digits(d: String): String = d.map { ones[it - '0'] }.joinToString(" ")

    /** An integer written in digits, as English words; long or zero-led numbers (phone-like) and emergency numbers digit by digit. */
    private fun number(d: String): String {
        if (d in emergency) return digits(d)
        if (d.length > 1 && d[0] == '0') return digits(d)
        if (d.length >= 7) return digits(d)
        return words(d.toInt())
    }

    private fun words(n: Int): String = when {
        n < 20 -> ones[n]
        n < 100 -> tens[n / 10] + if (n % 10 > 0) " ${ones[n % 10]}" else ""
        n < 1000 -> "${ones[n / 100]} hundred" + if (n % 100 > 0) " and ${words(n % 100)}" else ""
        else -> "${words(n / 1000)} thousand" + if (n % 1000 > 0) " ${words(n % 1000)}" else ""
    }

    private fun ordinal(n: Int): String = when {
        n < 20 -> ordinalOnes[n]
        n % 10 == 0 -> ordinalTens[n / 10]
        else -> "${tens[n / 10]} ${ordinalOnes[n % 10]}"
    }
}

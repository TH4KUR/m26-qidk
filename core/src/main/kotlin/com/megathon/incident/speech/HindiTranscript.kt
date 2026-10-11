package com.megathon.incident.speech

/**
 * Makes a Hindi transcript readable by the time logic, which is written for digits ("10 बजे", "2 घंटे पहले") and already knows
 * the day and part-of-day words (आज, कल, सुबह, दोपहर, शाम, रात, अभी). A Hindi recogniser writes the hour as a word ("दस बजे"),
 * so without this the time is invisible and the app asks "When was that?" about something the worker already said.
 *
 * Only Devanagari patterns are touched, so English and romanised text pass through unchanged. Used in the Hindi lane only.
 */
object HindiTranscript {
    private const val L = "(?<![\\p{L}\\p{M}])" // not in the middle of a word
    private const val R = "(?![\\p{L}\\p{M}])"

    /** ढ़ is written as one letter (U+095D), as ढ plus a dot (U+0922 U+093C), or without the dot. */
    private const val DHA = "(?:ढ़|ढ़?)"

    private val HOURS = mapOf(
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5, "पांच" to 5, "छह" to 6, "छः" to 6, "छे" to 6, "सात" to 7,
        "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11, "बारह" to 12,
    )
    private val NUMBERS = HOURS + mapOf(
        "तेरह" to 13, "चौदह" to 14, "पंद्रह" to 15, "पन्द्रह" to 15, "बीस" to 20, "पच्चीस" to 25, "तीस" to 30, "पैंतीस" to 35,
        "चालीस" to 40, "पैंतालीस" to 45, "पचास" to 50,
    )
    private val DEVANAGARI_DIGITS = "०१२३४५६७८९"

    private fun alt(words: Collection<String>) = words.sortedByDescending { it.length }.joinToString("|")
    private val hourAlt = alt(HOURS.keys)
    private val numberAlt = alt(NUMBERS.keys)

    private val DOUBLE_HALF = Regex("${L}ढाई\\s+बजे")
    private val ONE_AND_HALF = Regex("${L}डे$DHA\\s+बजे")
    private val HALF_PAST = Regex("${L}सा${DHA}े\\s+($hourAlt)\\s+बजे")
    private val QUARTER_PAST = Regex("${L}सवा\\s+($hourAlt)\\s+बजे")
    private val QUARTER_TO = Regex("${L}पौने\\s+($hourAlt)\\s+बजे")
    private val HOUR_BAJE = Regex("$L($hourAlt)\\s+बजे")
    private val HALF_HOUR_AGO = Regex("${L}आधे\\s+घंटे\\s+पहले")
    private val AGO = Regex("$L($numberAlt)\\s+(घंटे|घंटा|मिनट)\\s+पहले")

    fun normalize(text: String): String {
        if (text.isBlank()) return text
        var s = text.map { c -> DEVANAGARI_DIGITS.indexOf(c).let { if (it >= 0) '0' + it else c } }.joinToString("")
        s = DOUBLE_HALF.replace(s, "2:30 बजे")
        s = ONE_AND_HALF.replace(s, "1:30 बजे")
        s = HALF_PAST.replace(s) { "${HOURS.getValue(it.groupValues[1])}:30 बजे" }
        s = QUARTER_PAST.replace(s) { "${HOURS.getValue(it.groupValues[1])}:15 बजे" }
        s = QUARTER_TO.replace(s) { m -> "${(HOURS.getValue(m.groupValues[1]) + 10) % 12 + 1}:45 बजे" }
        s = HOUR_BAJE.replace(s) { "${HOURS.getValue(it.groupValues[1])} बजे" }
        s = HALF_HOUR_AGO.replace(s, "30 मिनट पहले")
        s = AGO.replace(s) { "${NUMBERS.getValue(it.groupValues[1])} ${it.groupValues[2]} पहले" }
        return s
    }

    private val DAY_PART = "(?:(?:आज|कल|परसों)\\s+)?(?:(?:सुबह|दोपहर|शाम|रात)\\s+)?"
    private val CLOCK_PHRASE = Regex("$L$DAY_PART(?:(?:लगभग|करीब|तकरीबन)\\s+)?\\d{1,2}(?::\\d{2})?\\s+बजे")
    private val OFFSET_PHRASE = Regex("$L\\d{1,3}\\s+(?:घंटे|घंटा|मिनट)\\s+पहले")
    private val DAY_PART_PHRASE = Regex("$L(?:आज|कल|परसों)\\s+(?:सुबह|दोपहर|शाम|रात)$R")
    private val NOW_PHRASE = Regex("${L}अभी$R(?!\\s+भी)")

    /**
     * The time the reporter said, in their own words ("आज सुबह 10 बजे", "2 घंटे पहले", "कल रात", "अभी"), or null. Expects
     * [normalize]d text. Used when the language model left the time out, like the English "right now" rule.
     */
    fun timePhrase(text: String): String? {
        for (pattern in listOf(CLOCK_PHRASE, OFFSET_PHRASE, DAY_PART_PHRASE, NOW_PHRASE)) {
            pattern.find(text)?.let { return it.value.trim() }
        }
        return null
    }
}

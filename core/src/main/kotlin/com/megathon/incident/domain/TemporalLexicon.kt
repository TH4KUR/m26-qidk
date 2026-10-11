package com.megathon.incident.domain

import java.text.Normalizer
import java.time.DayOfWeek

/** ClauseTense of the clause a time expression sits in. A future clause is not an incident time. */
internal enum class ClauseTense { PAST, FUTURE, NONE }

/**
 * Word lists and small text rewrites for [TemporalResolver]: Devanagari and Roman Hindi (Hinglish) time words, number words,
 * fractions ("saadhe das", "dedh", "dhai", "sava", "paune"), weekdays, tense markers and response verbs.
 *
 * [canonicalize] rewrites a time text into the one Roman/ASCII form the resolver's patterns already understand
 * ("साढ़े दस बजे" and "saadhe das baje" both become "10:30 baje"); it never invents a value, it only renames.
 * Everything here is lower-case NFC. The model-written Hindi spellings are a draft for a native reviewer.
 */
internal object TemporalLexicon {
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
    private const val LB = "(?<![\\p{L}\\p{M}\\p{N}])"
    private const val RB = "(?![\\p{L}\\p{M}\\p{N}])"
    private fun alt(words: Collection<String>) = words.map(::nfc).distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private fun word(vararg words: String) = Regex(LB + "(?:" + alt(words.toList()) + ")" + RB)

    /** Whole-word match, boundaries aware of Devanagari combining marks (so "कल" is not inside "निकल"). */
    fun hasWord(text: String, phrase: String) = Regex(LB + Regex.escape(nfc(phrase.lowercase())) + RB).containsMatchIn(text)

    // ---- vocabulary: canonical Roman form -> other spellings (Roman variants and Devanagari) -----------------------------

    private val DAY_WORDS = mapOf(
        "kal" to listOf("कल"), "aaj" to listOf("आज", "aj"),
        "parso" to listOf("parson", "parsoon", "parsu", "parsho", "परसों", "परसो"), "narso" to listOf("narson", "narsoon", "नरसों", "नरसो"),
    )
    private val POD_WORDS = mapOf(
        "subah" to listOf("subha", "subeh", "savere", "सुबह", "सवेरे"), "dopahar" to listOf("dophar", "dupahar", "dopehar", "दोपहर"),
        "shaam" to listOf("sham", "saam", "शाम"), "raat" to listOf("रात"),
    )
    private val UNIT_WORDS = mapOf(
        "baje" to listOf("बजे"), "pehle" to listOf("pahle", "pehele", "पहले"),
        "ghante" to listOf("ghantey", "ghanto", "घंटे", "घण्टे", "घंटों"), "ghanta" to listOf("घंटा", "घण्टा"),
        "minute" to listOf("mint", "minat", "minit", "minut", "मिनट"), "din" to listOf("दिन"),
        "mahine" to listOf("mahina", "mahino", "महीने", "महीना", "महिने", "माह"),
        "hafte" to listOf("हफ्ते", "हफ़्ते", "सप्ताह"), "hafta" to listOf("हफ्ता", "हफ़्ता"),
        "pichhle" to listOf("pichle", "pichhla", "पिछले"), "is" to listOf("इस"),
    )
    private val APPROX_WORDS = mapOf(
        "lagbhag" to listOf("लगभग"), "kareeb" to listOf("kareib", "qareeb", "करीब"), "taqriban" to listOf("takriban", "तकरीबन", "तक़रीबन"),
        "aaspaas" to listOf("aas paas", "aas-paas", "आसपास", "आस-पास", "आस पास"),
    )
    private val MISC_WORDS = mapOf(
        "abhi" to listOf("अभी"), "abhi bhi" to listOf("अभी भी"),
        "last night" to listOf("बीती रात", "beeti raat", "biti raat", "bheeti raat"),
        "kuch" to listOf("कुछ"), "thodi" to listOf("थोड़ी", "थोडी"), "der" to listOf("देर"),
    )
    private val FRACTIONS = mapOf(
        "aadha" to listOf("aadhe", "adha", "adhe", "aadhi", "आधा", "आधे", "आधी"),
        "dedh" to listOf("derh", "dhed", "dedha", "dhedh", "डेढ़", "डेढ"), "dhai" to listOf("dhaai", "dhayi", "dhaayi", "dhae", "ढाई"),
        "sava" to listOf("sawa", "savaa", "sawaa", "सवा"),
        "saadhe" to listOf("saade", "sade", "sadhe", "saadhey", "saadey", "sarhe", "साढ़े", "साढे", "साड़े"),
        "paune" to listOf("paone", "pone", "paun", "paunay", "pauney", "पौने"),
    )
    private val WEEKDAYS: Map<DayOfWeek, List<String>> = mapOf(
        DayOfWeek.MONDAY to listOf("monday", "somvaar", "somvar", "somwar", "सोमवार"),
        DayOfWeek.TUESDAY to listOf("tuesday", "mangalvaar", "mangalvar", "mangalwar", "मंगलवार"),
        DayOfWeek.WEDNESDAY to listOf("wednesday", "budhvaar", "budhvar", "budhwar", "बुधवार"),
        DayOfWeek.THURSDAY to listOf("thursday", "guruvaar", "guruvar", "guruwar", "brihaspativar", "veervar", "गुरुवार", "बृहस्पतिवार", "वीरवार"),
        DayOfWeek.FRIDAY to listOf("friday", "shukravaar", "shukravar", "shukrawar", "शुक्रवार"),
        DayOfWeek.SATURDAY to listOf("saturday", "shanivaar", "shanivar", "shaniwar", "शनिवार"),
        DayOfWeek.SUNDAY to listOf("sunday", "ravivaar", "ravivar", "raviwar", "itwaar", "itwar", "रविवार", "इतवार"),
    )

    /** canonical Roman number word -> value, with its other spellings. */
    private val NUMBERS: List<Triple<String, Int, List<String>>> = listOf(
        Triple("ek", 1, listOf("एक")), Triple("do", 2, listOf("दो")), Triple("teen", 3, listOf("tin", "tiin", "तीन")),
        Triple("char", 4, listOf("chaar", "चार")), Triple("paanch", 5, listOf("panch", "paach", "pach", "पांच", "पाँच")),
        Triple("chhe", 6, listOf("chhah", "che", "chah", "chha", "cheh", "chhey", "chhai", "छह", "छः", "छे", "छ")), Triple("saat", 7, listOf("सात")),
        Triple("aath", 8, listOf("aat", "ath", "आठ")), Triple("nau", 9, listOf("nao", "naun", "नौ")), Triple("das", 10, listOf("dus", "dass", "dasa", "दस")),
        Triple("gyarah", 11, listOf("gyaarah", "giyarah", "gyara", "gyarha", "igyarah", "ग्यारह")),
        Triple("barah", 12, listOf("baarah", "bara", "baraah", "barha", "बारह")),
        Triple("terah", 13, listOf("तेरह")), Triple("chaudah", 14, listOf("चौदह")), Triple("pandrah", 15, listOf("pandra", "पंद्रह", "पन्द्रह")),
        Triple("solah", 16, listOf("सोलह")), Triple("satrah", 17, listOf("सत्रह")), Triple("atharah", 18, listOf("athaarah", "अठारह")),
        Triple("unnis", 19, listOf("unees", "उन्नीस")), Triple("bees", 20, listOf("bis", "बीस")),
        Triple("pachchis", 25, listOf("pacchis", "pachis", "pachees", "पच्चीस")), Triple("tees", 30, listOf("तीस")), Triple("paintees", 35, listOf("पैंतीस")),
        Triple("chalis", 40, listOf("chaalis", "चालीस")), Triple("paintalis", 45, listOf("paitalis", "paintaalis", "पैंतालीस")),
        Triple("pachaas", 50, listOf("pachas", "पचास")),
    )
    private val NUMBER_VALUE: Map<String, Int> = NUMBERS.associate { it.first to it.second }

    private val CANON: Map<String, String> = buildMap {
        fun put(m: Map<String, List<String>>) = m.forEach { (canon, others) -> others.forEach { this[nfc(it.lowercase())] = canon } }
        put(DAY_WORDS); put(POD_WORDS); put(UNIT_WORDS); put(APPROX_WORDS); put(MISC_WORDS); put(FRACTIONS)
        WEEKDAYS.forEach { (d, names) -> names.forEach { this[nfc(it)] = d.name.lowercase() } }
        NUMBERS.forEach { (canon, _, others) -> others.forEach { this[nfc(it)] = canon } }
    }
    private val CANON_REGEX = Regex(LB + "(?:" + alt(CANON.keys) + ")" + RB)

    private val DEVANAGARI_DIGITS = ('०'..'९')

    // ---- canonical form -----------------------------------------------------------------------------------------------

    private val NUMW = NUMBER_VALUE.keys.sortedByDescending { it.length }.joinToString("|")
    private val NUM = "(\\d{1,2}|$NUMW)"
    private val FRAC_CLOCK = Regex("$LB(saadhe|sava|paune)\\s+$NUM\\s+baje$RB")
    private val DEDH_DHAI_CLOCK = Regex("$LB(dedh|dhai)\\s+baje$RB")
    private val FRAC_OFFSET = Regex("$LB(aadha|dedh|dhai|sava|paune)\\s+(?:ghante|ghanta)\\s+pehle$RB")
    private val NUM_BEFORE_UNIT = Regex("$LB($NUMW)\\s+(?=(?:minutes?|mins?|ghante|ghanta|baje|din|hafte|hafta|mahine)$RB)")
    private val EN_HOUR = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve"
    private val EN_HOUR_VALUE = EN_HOUR.split('|').withIndex().associate { it.value to it.index + 1 }
    private fun countOf(token: String): Int = token.toIntOrNull() ?: NUMBER_VALUE[token] ?: EN_HOUR_VALUE[token] ?: 0

    // "do teen ghante pehle", "two or three hours ago", "2-3 hours ago": the midpoint, and approximate ("around").
    private val RANGE_OFFSET = Regex(
        "$LB(\\d{1,2}|$NUMW|$EN_HOUR)\\s*(?:-|–|to|or|se|ya)?\\s*(\\d{1,2}|$NUMW|$EN_HOUR)\\s+" +
            "(hours?|hrs?|ghante|ghanta|minutes?|mins?|din|days?)\\s+(?=(?:ago|pehle)$RB)",
    )
    private val COUPLE_OF = Regex("${LB}a couple of (hours?|hrs?|minutes?|mins?|days?)$RB")
    private val QUARTER_PAST = Regex("${LB}quarter\\s+past\\s+(\\d{1,2}|$EN_HOUR)$RB")
    private val QUARTER_TO = Regex("${LB}quarter\\s+to\\s+(\\d{1,2}|$EN_HOUR)$RB")
    private val EN_MIN = "fifteen|twenty five|twenty-five|twenty|thirty five|thirty-five|thirty|forty five|forty-five|forty|fifty five|fifty-five|fifty"

    private val HOUR_MIN_AFTER_AMPM = Regex("$LB($EN_HOUR)\\s+($EN_MIN)\\s+(?=a\\.?m\\.?$RB|p\\.?m\\.?$RB)")
    private val NOON = Regex("$LB(?:(at|until|till|by|up to|upto)\\s+)?(?:noon|midday)$RB")
    private val MIDNIGHT = Regex("(?<!after )(?<!past )$LB(?:(at|until|till|by|up to|upto)\\s+)?midnight(?!\\s+ke baad)$RB")

    // Spoken English clock forms. A bare "ten thirty" is a time only with a preposition before it or a part of day / am / pm after.
    private val EN_MINUTE_VALUES = buildMap {
        put("fifteen", 15); put("twenty", 20); put("twenty five", 25); put("twenty-five", 25); put("thirty", 30); put("thirty five", 35)
        put("thirty-five", 35); put("forty", 40); put("forty five", 45); put("forty-five", 45); put("fifty", 50); put("fifty five", 55)
        put("fifty-five", 55)
        listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine").forEachIndexed { i, w -> put("oh $w", i + 1); put("o $w", i + 1) }
    }
    private val EN_MIN_ALT = EN_MINUTE_VALUES.keys.sortedByDescending { it.length }.joinToString("|")
    private const val PREP = "(?:at|around|about|by|since|until|till|from)"
    private const val ANCHOR_AFTER =
        "(?:a\\.?m\\.?|p\\.?m\\.?|last night|tonight|this (?:morning|afternoon|evening|night)|yesterday|today|in the (?:morning|afternoon|evening|night)|at night|o'clock)"
    private val HOUR_MIN_PREP = Regex("$LB($PREP)\\s+(\\d{1,2}|$EN_HOUR)\\s+($EN_MIN_ALT)$RB")
    private val HOUR_MIN_ANCHOR = Regex("$LB(\\d{1,2}|$EN_HOUR)\\s+($EN_MIN_ALT)\\s+(?=$ANCHOR_AFTER$RB)")
    private val SMALL_MIN = mapOf("five" to 5, "ten" to 10, "twenty" to 20, "twenty five" to 25, "twenty-five" to 25, "quarter" to 15, "a quarter" to 15)
    private val SMALL_ALT = (SMALL_MIN.keys + listOf("5", "10", "20", "25")).sortedByDescending { it.length }.joinToString("|")
    private val MIN_PAST = Regex("$LB($SMALL_ALT) past (\\d{1,2}|$EN_HOUR)$RB")
    private val MIN_TO_PREP = Regex("$LB($PREP) ($SMALL_ALT) to (\\d{1,2}|$EN_HOUR)$RB")
    private val MIN_TO_ANCHOR = Regex("$LB($SMALL_ALT) to (\\d{1,2}|$EN_HOUR)\\s+(?=$ANCHOR_AFTER$RB)")
    private fun smallMinutes(token: String) = token.toIntOrNull() ?: SMALL_MIN.getValue(token)

    // A spoken hour ("at about eleven", "around five") is a time only when the text also names a part of day or a day, and the
    // word is not the start of a count ("around five workers", "around one of the pipes").
    private val EN_ANCHOR_ANY = Regex(
        "${LB}(?:last night|tonight|yesterday|today|morning|afternoon|evening|night|monday|tuesday|wednesday|thursday|friday|saturday|sunday)$RB",
    )
    private const val NOT_A_COUNT =
        "(?!\\s+(?:of|or|to|more|other|another)\\b)(?!\\s+(?!(?:was|is|has|this|its|his|as|us|yes)\\b)[a-z]+s\\b)"
    private val SPOKEN_HOUR = Regex("$LB((?:(?:at|around|about|since)\\s+)+)($EN_HOUR)$RB$NOT_A_COUNT")

    /** Finders for a spoken hour, to be used only for a clause that names a part of day or a day (see [anchoredFinders]). */
    private val SPOKEN_HOUR_FINDER = Regex("$LB(?:(?:at|around|about|since)\\s+)+(?:$EN_HOUR)$RB$NOT_A_COUNT")
    fun anchoredFinders(clause: String): List<Regex> = if (EN_ANCHOR_ANY.containsMatchIn(clause)) listOf(SPOKEN_HOUR_FINDER) else emptyList()

    private fun hourOf(token: String): Int = token.toIntOrNull() ?: EN_HOUR_VALUE[token] ?: NUMBER_VALUE[token] ?: 0

    fun canonicalize(t: String): String {
        var s = nfc(t)
        s = s.map { c -> if (c in DEVANAGARI_DIGITS) ('0' + (c - '०')) else c }.joinToString("")
        s = CANON_REGEX.replace(s) { CANON[it.value] ?: it.value }
        s = FRAC_CLOCK.replace(s) { m ->
            val h = hourOf(m.groupValues[2])
            when (m.groupValues[1]) {
                "saadhe" -> "$h:30 baje"
                "sava" -> "$h:15 baje"
                else -> "${if (h == 1) 12 else h - 1}:45 baje"
            }
        }
        s = DEDH_DHAI_CLOCK.replace(s) { m -> if (m.groupValues[1] == "dedh") "1:30 baje" else "2:30 baje" }
        s = FRAC_OFFSET.replace(s) { m ->
            val minutes = when (m.groupValues[1]) { "aadha" -> 30; "dedh" -> 90; "dhai" -> 150; "sava" -> 75; else -> 45 }
            "$minutes minutes ago"
        }
        s = RANGE_OFFSET.replace(s) { m ->
            val a = countOf(m.groupValues[1])
            val b = countOf(m.groupValues[2])
            if (b <= a) m.value else when (m.groupValues[3]) {
                "hour", "hours", "hr", "hrs", "ghante", "ghanta" -> "around ${(a + b) * 30} minutes "
                "minute", "minutes", "min", "mins" -> "around ${(a + b) / 2} minutes "
                else -> "around ${(a + b) / 2} days "
            }
        }
        s = COUPLE_OF.replace(s) { m -> "around 2 ${m.groupValues[1]}" }
        s = NUM_BEFORE_UNIT.replace(s) { m -> NUMBER_VALUE.getValue(m.groupValues[1]).toString() + " " }
        s = NOON.replace(s) { m -> m.groupValues[1].ifEmpty { "at" } + " 12:00 pm" }
        s = MIDNIGHT.replace(s) { m -> m.groupValues[1].ifEmpty { "at" } + " 12:00 am" }
        s = QUARTER_PAST.replace(s) { "${hourOf(it.groupValues[1])}:15" }
        s = QUARTER_TO.replace(s) { m -> hourOf(m.groupValues[1]).let { h -> "${if (h == 1) 12 else h - 1}:45" } }
        s = MIN_PAST.replace(s) { m -> "${hourOf(m.groupValues[2])}:%02d".format(smallMinutes(m.groupValues[1])) }
        s = MIN_TO_PREP.replace(s) { m -> hourOf(m.groupValues[3]).let { h -> "${m.groupValues[1]} ${if (h == 1) 12 else h - 1}:%02d".format(60 - smallMinutes(m.groupValues[2])) } }
        s = MIN_TO_ANCHOR.replace(s) { m -> hourOf(m.groupValues[2]).let { h -> "${if (h == 1) 12 else h - 1}:%02d ".format(60 - smallMinutes(m.groupValues[1])) } }
        s = HOUR_MIN_PREP.replace(s) { m -> "${m.groupValues[1]} ${hourOf(m.groupValues[2])}:%02d".format(EN_MINUTE_VALUES.getValue(m.groupValues[3])) }
        s = HOUR_MIN_ANCHOR.replace(s) { m -> "${hourOf(m.groupValues[1])}:%02d ".format(EN_MINUTE_VALUES.getValue(m.groupValues[2])) }
        s = HOUR_MIN_AFTER_AMPM.replace(s) { m -> "${hourOf(m.groupValues[1])}:%02d ".format(EN_MINUTE_VALUES.getValue(m.groupValues[2])) }
        if (EN_ANCHOR_ANY.containsMatchIn(s)) s = SPOKEN_HOUR.replace(s) { m -> m.groupValues[1] + hourOf(m.groupValues[2]) }
        return s
    }

    // ---- day arithmetic words -----------------------------------------------------------------------------------------

    private val WEEKDAY_REGEX = Regex(LB + "(" + DayOfWeek.entries.joinToString("|") { it.name.lowercase() } + ")" + RB)

    /** The weekday named in a canonical text, or null. */
    fun weekday(t: String): DayOfWeek? = WEEKDAY_REGEX.find(t)?.let { DayOfWeek.valueOf(it.groupValues[1].uppercase()) }

    private val EN_COUNT = mapOf("a" to 1L, "an" to 1L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L)
    private val DAYS_AGO = Regex("$LB(\\d{1,3}|${EN_COUNT.keys.joinToString("|")})\\s*(?:days?|din)\\s*(?:ago|pehle)$RB")

    /** "3 days ago", "do din pehle" (canonical: "2 din pehle") as a number of days, or null. */
    fun daysAgo(t: String): Long? = DAYS_AGO.find(t)?.groupValues?.get(1)?.let { it.toLongOrNull() ?: EN_COUNT[it] }

    private val SHORT_WHILE = Regex(
        "${LB}(?:kuch der pehle|thodi der pehle|thodi der|a while ago|a short while ago|a little while ago|a few minutes ago|few minutes ago)$RB",
    )

    /** "kuch der pehle": some time ago today; no clock time can be said. */
    fun shortWhileAgo(t: String) = SHORT_WHILE.containsMatchIn(t)

    private val LAST_MONTH = Regex("${LB}(?:last|previous) month$RB|${LB}pichhle mahine$RB")
    private val THIS_MONTH = Regex("${LB}(?:this month|is mahine)$RB")
    private val MONTHS_AGO = Regex("$LB(\\d{1,2}|a|an|one|two|three|four|five|six)\\s*(?:months?|mahine)\\s*(?:ago|pehle)$RB")
    fun lastMonth(t: String) = LAST_MONTH.containsMatchIn(t)
    fun thisMonth(t: String) = THIS_MONTH.containsMatchIn(t)

    /** "3 months ago", "teen mahine pehle" (canonical "3 mahine pehle"): the number of months, or null. */
    fun monthsAgo(t: String): Long? = MONTHS_AGO.find(t)?.groupValues?.get(1)?.let { it.toLongOrNull() ?: EN_COUNT[it] ?: EN_HOUR_VALUE[it]?.toLong() }

    private val STRONG_NOW = Regex("${LB}(?:abhi abhi|just now|right now|just happened)$RB")
    fun strongNow(t: String) = STRONG_NOW.containsMatchIn(t)

    private val AFTER_MIDNIGHT = Regex("${LB}(?:after midnight|past midnight|midnight ke baad|aadhi raat ke baad)$RB|आधी रात के बाद")
    fun afterMidnight(t: String) = AFTER_MIDNIGHT.containsMatchIn(t)

    // ---- end times ("10 baje tak", "until 10") -----------------------------------------------------------------------

    /** A time that says when something stopped or by when, not when the incident happened. Matches both scripts and canonical text. */
    private val END_TIME: Regex by lazy {
        val en = "(?:\\d{1,2}(?::\\d{2})?(?:\\s*[ap]\\.?m\\.?)?|noon|midnight|midday|$EN_HOUR)"
        Regex(
            LB + "(?:" +
                "(?:(?:$S_DAY)\\s+)?(?:(?:$S_POD)\\s+(?:(?:ke|के)\\s+)?)?(?:(?:$S_FRAC)\\s+)?(?:$S_NUM)(?::\\d{2})?\\s*(?:$S_BAJE)\\s+(?:tak|तक)" +
                "|(?:$S_DAY)(?:\\s+(?:$S_POD))?\\s+(?:tak|तक)" +
                "|(?:until|till|til|up to|upto|by)\\s+(?:at\\s+)?(?:(?:yesterday|today|tonight|this)\\s+)?$en(?:\\s+(?:this\\s+)?(?:morning|afternoon|evening|night))?" +
                ")" + RB,
            RegexOption.IGNORE_CASE,
        )
    }

    fun endTimeRanges(text: String): List<IntRange> = END_TIME.findAll(text).map { it.range }.toList()
    fun withoutEndTimes(t: String): String = END_TIME.replace(t, " ")

    // ---- tense, clauses, response verbs ---------------------------------------------------------------------------------

    private val PAST_MARKERS = word(
        "tha", "thi", "hua", "hui", "hue", "gaya", "gayi", "gaye", "gai", "laga", "lagi", "lage", "mila", "mili", "mile",
        "aaya", "aayi", "aaye", "phata", "phati", "phate", "toota", "tooti", "toote", "tuta", "gira", "giri", "gire", "nikla", "nikli",
        "baja", "baji", "diya", "diye", "kiya", "kiye", "liya", "liye", "dekha", "dikha", "dikhi", "bulaya", "bataya",
        "ne", "maine", "humne", "hamne", "usne", "unhone", "inhone", "tumne", "aapne", "kisine",
        "था", "थी", "हुआ", "हुई", "हुए", "गया", "गई", "गयी", "गए", "गये", "लगा", "लगी", "लगे", "मिला", "मिली", "मिले",
        "आया", "आई", "आए", "फटा", "फटी", "फटे", "टूटा", "टूटी", "टूटे", "गिरा", "गिरी", "गिरे", "निकला", "निकली",
        "बजा", "बजी", "दिया", "दिए", "किया", "किए", "लिया", "लिए", "देखा", "दिखा", "दिखी", "बुलाया", "बताया",
        "ने", "मैंने", "हमने", "उसने", "उन्होंने", "इन्होंने", "तुमने", "आपने", "किसीने",
    )
    private val FUTURE_MARKERS = word(
        "hoga", "hogi", "honge", "hoge", "karunga", "karungi", "karenge", "karega", "karegi", "karoge", "jaunga", "jaungi", "jayenge",
        "jayega", "jayegi", "jaoge", "aaunga", "aaungi", "aayega", "aayegi", "aayenge", "dunga", "dungi", "denge", "dega", "degi",
        "lenge", "lunga", "milega", "milegi", "dekhenge", "dekhunga", "bhejenge", "bhejunga", "bulayenge", "bulaunga", "batayenge",
        "bataunga", "rahega", "rahegi", "lagega", "chalega", "padega", "tomorrow", "will", "shall", "scheduled", "planned", "upcoming",
        "होगा", "होगी", "होंगे", "होगे", "करूंगा", "करूँगा", "करूंगी", "करेंगे", "करेगा", "करेगी", "जाऊंगा", "जाऊँगा", "जाएगा", "जाएगी",
        "जाएंगे", "जाएँगे", "आऊंगा", "आएगा", "आएगी", "देंगे", "दूंगा", "देगा", "मिलेगा", "देखेंगे", "भेजेंगे", "बुलाऊंगा", "बताऊंगा",
        "रहेगा", "लगेगा", "चलेगा", "पड़ेगा",
    )
    private val FUTURE_PHRASES = Regex("${LB}(?:going to|next week|next month|karna hai|karni hai)$RB")
    private val PAST_ANCHOR = Regex(
        "${LB}(?:yesterday|last night|ago|pehle|pahle|day before yesterday|pichhle|pichle|last week|previous week)$RB|पहले|पिछले",
    )

    private val SINCE = Regex("$LB(?:kal|parso|narso|subah|dopahar|shaam|raat|baje|कल|परसों|सुबह|दोपहर|शाम|रात|बजे)\\s+(?:se|से)$RB")

    fun tenseOf(clause: String): ClauseTense = when {
        PAST_MARKERS.containsMatchIn(clause) || SINCE.containsMatchIn(clause) -> ClauseTense.PAST
        FUTURE_MARKERS.containsMatchIn(clause) || FUTURE_PHRASES.containsMatchIn(clause) -> ClauseTense.FUTURE
        else -> ClauseTense.NONE
    }

    /** A future clause that carries no past anchor ("yesterday", "pehle") is not an incident time. */
    fun hasPastAnchor(clause: String) = PAST_ANCHOR.containsMatchIn(clause)

    /** ClauseTense of clause [i]: its own, else the next clause's (the verb often follows), else the previous one's. */
    fun tenseAt(clauses: List<String>, i: Int): ClauseTense {
        val own = tenseOf(clauses[i])
        if (own != ClauseTense.NONE) return own
        clauses.getOrNull(i + 1)?.let { tenseOf(it).takeIf { t -> t != ClauseTense.NONE }?.let { t -> return t } }
        clauses.getOrNull(i - 1)?.let { tenseOf(it).takeIf { t -> t != ClauseTense.NONE }?.let { t -> return t } }
        return ClauseTense.NONE
    }

    private val RESPONSE = Regex(
        "${LB}(?:called|informed|notified|alerted|phoned|messaged|emailed|escalated|dispatched|summoned|arrived|told|reported|" +
            "bulaya|bulayi|bulaye|bataya|batayi|batai|suchit|soochit|pahunche|pahunchi|" +
            "(?:phone|call|inform|report)\\s+(?:kiya|kar|ki)|khabar\\s+(?:di|kiya)|sent for)$RB" +
            "|बुलाया|बुलाई|बुलाए|बताया|बताई|सूचित|फोन किया|फोन कर|कॉल किया|कॉल कर|खबर दी|सूचना दी|रिपोर्ट किया|पहुंचे|पहुँचे|पहुंची",
    )

    /** The clause is about something done in response (called, informed, reported, bulaya, bataya, phone kiya). */
    fun isResponse(clause: String) = RESPONSE.containsMatchIn(clause)

    private val CLAUSE_SPLIT = Regex(
        "\\s*(?:[,;!?।|]|\\.(?=\\s|$)|\\s+(?:and|but|then|aur|और|lekin|magar|phir|फिर|लेकिन|मगर)\\s+|\\s+after\\s+(?=(?:the|a|an|we|i|they|he|she|it|someone|everyone|my|our|that|this)\\s))\\s*",
    )

    /** The clause ranges of [text], in order; the separators are not part of any range. */
    fun clauseRanges(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        for (m in CLAUSE_SPLIT.findAll(text)) {
            if (m.range.first > start) out += start until m.range.first
            start = m.range.last + 1
        }
        if (start < text.length) out += start until text.length
        return out.filter { text.substring(it).isNotBlank() }
    }

    fun clauses(text: String): List<String> = clauseRanges(text).map { text.substring(it) }

    // ---- findTimePhrase finders (surface text, both scripts) -------------------------------------------------------------

    private fun surface(m: Map<String, List<String>>) = alt(m.keys + m.values.flatten())
    private val S_DAY = surface(DAY_WORDS)
    private val S_POD = surface(POD_WORDS)
    private val S_BAJE = alt(listOf("baje", "bajey", "बजे"))
    private val S_PEHLE = alt(listOf("pehle", "pahle", "पहले"))
    private val S_APPROX = surface(APPROX_WORDS.filterKeys { it != "aaspaas" })
    private val S_AASPAAS = surface(APPROX_WORDS.filterKeys { it == "aaspaas" })
    private val S_FRAC = surface(FRACTIONS)
    private val S_NUM = alt(NUMBERS.flatMap { listOf(it.first) + it.third }) + "|[0-9\\u0966-\\u096F]{1,2}"
    private val S_UNITS = surface(UNIT_WORDS.filterKeys { it in setOf("ghante", "ghanta", "minute", "din", "hafte", "hafta", "mahine") }) + "|min|mins"
    private val S_WEEKDAY = alt(WEEKDAYS.values.flatten())
    private val S_WEEKWORD = alt(listOf("hafte", "hafta", "हफ्ते", "हफ़्ते", "हफ्ता", "सप्ताह", "week", "mahine", "mahina", "महीने", "महीना", "माह", "month"))
    private val EN_POD = "(?:\\s+(?:morning|afternoon|evening|night))?"
    private val HI_POD = "(?:\\s+(?:$S_POD|morning|afternoon|evening|night))?"
    private val KO = "(?:(?:ko|को|ke|के)\\s+)?"
    private val S_KAREEB = alt(listOf("kareeb", "kareib", "qareeb", "करीब"))

    private val S_DEDH_DHAI = surface(FRACTIONS.filterKeys { it == "dedh" || it == "dhai" })
    private val HI_CLOCK_CORE = "(?:(?:$S_FRAC)\\s+)?(?:$S_NUM)\\s*(?:$S_BAJE)|(?:$S_DEDH_DHAI)\\s+(?:$S_BAJE)"

    /**
     * Words that only complete a time found by [FINDERS] ("half past eleven" + "at night", "last night" + "after midnight"); on their
     * own they are not a time ("the guard works at night"), so they extend a phrase but never start one.
     */
    val MODIFIER_FINDERS: List<Regex> by lazy {
        listOf(
            Regex(LB + "(?:at night|in the (?:morning|afternoon|evening|night)|(?:after|past) midnight|in the early hours)" + RB),
            Regex(LB + "(?:midnight ke baad|aadhi raat ke baad)" + RB),
            Regex(LB + "(?:$S_POD)(?:\\s+(?:ko|को|mein|में))?" + RB),
            Regex("आधी रात के बाद"),
        )
    }

    /** Finders for Hindi/Hinglish/Devanagari and the English forms the older finders in [TemporalResolver] lack. */
    val FINDERS: List<Regex> by lazy {
        listOf(
            // "kal shaam chhe baje", "रात ११ बजे के आसपास", "saadhe das baje", "dedh baje", "lagbhag das baje"
            Regex(LB + "(?:(?:$S_DAY)\\s+$KO)?(?:(?:$S_POD)\\s+$KO)?(?:(?:$S_APPROX)\\s+)?(?:$HI_CLOCK_CORE)(?:(?:\\s+(?:ke|के))?\\s+(?:$S_AASPAAS|$S_KAREEB))?" + RB),
            // "do ghante pehle", "aadha ghanta pehle", "२० मिनट पहले", "lagbhag teen ghante pehle", "do din pehle"
            Regex(LB + "(?:(?:$S_APPROX)\\s+)?(?:(?:$S_NUM|$S_FRAC)(?:\\s*(?:-|–|se|से|ya|या)\\s*|\\s+))?(?:$S_NUM|$S_FRAC)\\s+(?:$S_UNITS)\\s+(?:$S_PEHLE)" + RB),
            Regex(LB + "(?:kuch|thodi|कुछ|थोड़ी)\\s+(?:der|देर)\\s+(?:$S_PEHLE)" + RB),
            Regex(LB + "(?:abhi|अभी)\\s+(?:abhi|अभी)" + RB),
            // "kal shaam", "aaj subah", "parso", "नरसों रात"
            Regex(LB + "(?:$S_DAY)$HI_POD" + RB),
            // "pichhle shukravaar dopahar", "somvaar", "रविवार"
            Regex(LB + "(?:(?:pichhle|pichle|पिछले|last|this|on)\\s+)?(?:$S_WEEKDAY)(?:\\s+(?:ko|को))?$HI_POD" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:pichhle|pichle|is|पिछले|इस|last|this|previous)\\s+(?:$S_WEEKWORD)" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?<!after )(?<!past )(?:at\\s+)?(?:noon|midday|midnight)(?!\\s+ke baad)" + RB),
            Regex(LB + "(?:बीती|beeti|biti|bheeti)\\s+(?:$S_POD)" + RB),
            // English additions
            Regex(LB + "(?:the\\s+)?day\\s+before\\s+yesterday$EN_POD" + RB),
            Regex(LB + "(?:yesterday|last night|tonight|today|this morning|this afternoon|this evening)$EN_POD" + RB),
            Regex(LB + "(?:\\d{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:days?|weeks?|months?)\\s+ago" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:\\d{1,2}|$EN_HOUR)\\s*(?:-|–|or|to)\\s*(?:\\d{1,2}|$EN_HOUR)\\s+(?:hours?|hrs?|minutes?|mins?|days?)\\s+ago" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "a couple of (?:hours?|hrs?|minutes?|mins?|days?) ago" + RB, RegexOption.IGNORE_CASE),
            // spoken English clock forms: "ten thirty last night", "at ten past eight", "twenty to nine this morning"
            Regex(LB + "$PREP\\s+(?:\\d{1,2}|$EN_HOUR)\\s+(?:$EN_MIN_ALT)(?:\\s+(?:a\\.?m\\.?|p\\.?m\\.?))?" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:\\d{1,2}|$EN_HOUR)\\s+(?:$EN_MIN_ALT)(?=\\s+$ANCHOR_AFTER)" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:$PREP\\s+)?(?:$SMALL_ALT)\\s+past\\s+(?:\\d{1,2}|$EN_HOUR)" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:$PREP\\s+(?:$SMALL_ALT)\\s+to\\s+(?:\\d{1,2}|$EN_HOUR)|(?:$SMALL_ALT)\\s+to\\s+(?:\\d{1,2}|$EN_HOUR)(?=\\s+$ANCHOR_AFTER))" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "quarter\\s+(?:past|to)\\s+(?:\\d{1,2}|$EN_HOUR)" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:at|around|about|by|since)\\s+(?:\\d{1,2}|$EN_HOUR)\\s+(?:$EN_MIN)(?:\\s+(?:a\\.?m\\.?|p\\.?m\\.?))?" + RB, RegexOption.IGNORE_CASE),
            Regex(LB + "(?:$EN_HOUR)\\s+(?:$EN_MIN)\\s+(?:a\\.?m\\.?|p\\.?m\\.?)" + RB, RegexOption.IGNORE_CASE),
        )
    }
}

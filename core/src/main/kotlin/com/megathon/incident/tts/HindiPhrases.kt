package com.megathon.incident.tts

/** A stretch of text and the language code of the voice that should read it (`hi` or `en`). */
data class SpokenSegment(val text: String, val language: String)

/**
 * The Hindi version of what the app says aloud. Pure text in, pure text out.
 *
 * Everything the live session speaks is built from a small set of English sentences (the planner's questions, the advisor's
 * two lines, the saved-report summary), so each is translated by an exact table or a fixed template. Values the reporter said
 * (a place, in whatever script it was heard) are never translated: they are placed into the Hindi sentence as they are. A
 * place written only in Latin letters is its own `en` segment, so the Hindi voice is not asked to read it.
 *
 * A sentence this does not know, or a time it cannot say correctly (9:05 has no short Hindi form), is returned unchanged as an
 * `en` segment: it is spoken in English, never guessed at. Adjacent segments in the same language are joined.
 */
object HindiPhrases {
    fun segments(english: String): List<SpokenSegment> {
        val pieces = english.trim().split(Regex("(?<=[.?!])\\s+")).filter { it.isNotBlank() }.flatMap(::translate)
        return pieces.fold(mutableListOf()) { out, seg ->
            val last = out.lastOrNull()
            if (last != null && last.language == seg.language) out[out.lastIndex] = SpokenSegment(last.text + " " + seg.text, seg.language)
            else out += seg
            out
        }
    }

    private fun translate(sentence: String): List<SpokenSegment> {
        FIXED[sentence]?.let { return listOf(hi(it)) }

        Regex("""^Severity is (.+)\.$""").matchEntire(sentence)?.let { m ->
            SEVERITY[m.groupValues[1].lowercase()]?.let { return listOf(hi("गंभीरता $it है।")) }
        }
        Regex("""^That was (.+)\.$""").matchEntire(sentence)?.let { m ->
            whenPhrase(m.groupValues[1])?.let { return listOf(hi("यह $it हुआ था।")) }
        }
        Regex("""^At (.+)\.$""").matchEntire(sentence)?.let { m ->
            return place(m.groupValues[1], "पर।")
        }
        Regex("""^(.+?) at (.+)\.$""").matchEntire(sentence)?.let { m ->
            TYPE[m.groupValues[1].lowercase()]?.let { return place(m.groupValues[2], "में $it।") }
        }
        Regex("""^(.+)\.$""").matchEntire(sentence)?.let { m ->
            TYPE[m.groupValues[1].lowercase()]?.let { return listOf(hi("$it।")) }
        }
        Regex("""^What is (your|the) (.+)'s name\?$""").matchEntire(sentence)?.let { m ->
            ROLE[m.groupValues[2].lowercase()]?.let { role ->
                return listOf(hi("${if (m.groupValues[1] == "your") "आपके" else "उस"} $role का नाम क्या है?"))
            }
        }
        Regex("""^Was it the (.+)\?$""").matchEntire(sentence)?.let { m -> return spokenItem("क्या यह", m.groupValues[1], "था?") }
        // Not a sentence the app builds. Text already in Devanagari is for the Hindi voice; anything else stays English.
        return listOf(SpokenSegment(sentence, if (sentence.any { it.code in 0x0900..0x097F }) "hi" else "en"))
    }

    /** "<place> <tail>", the place spoken by the voice for its own script. */
    private fun place(value: String, tail: String): List<SpokenSegment> {
        val latinOnly = value.any { it in 'A'..'Z' || it in 'a'..'z' } && value.none { it.code in 0x0900..0x097F }
        return if (latinOnly) listOf(SpokenSegment(value, "en"), hi(tail)) else listOf(hi("$value $tail"))
    }

    /** "<before> <item> <after>": a Latin-letter item name is its own `en` segment, so the Hindi voice is not asked to read it. */
    private fun spokenItem(before: String, value: String, after: String): List<SpokenSegment> {
        val latinOnly = value.any { it in 'A'..'Z' || it in 'a'..'z' } && value.none { it.code in 0x0900..0x097F }
        return if (latinOnly) listOf(hi(before), SpokenSegment(value, "en"), hi(after)) else listOf(hi("$before $value $after"))
    }

    /** The words after "That was": [day] [part of day] [about] [clock], or null when a part cannot be said correctly. */
    private fun whenPhrase(text: String): String? {
        var rest = text.trim()
        var day: String? = null
        when {
            rest.startsWith("today") -> { day = "आज"; rest = rest.removePrefix("today") }
            rest.startsWith("yesterday") -> { day = "कल"; rest = rest.removePrefix("yesterday") }
            else -> Regex("""^on (\w+) (\d{1,2}) (\w+)""").find(rest)?.let { m ->
                val weekday = WEEKDAY[m.groupValues[1]] ?: return null
                val number = DAY_NUMBER.getOrNull(m.groupValues[2].toInt()) ?: return null
                val month = MONTH[m.groupValues[3]] ?: return null
                day = "$weekday, $number $month को"
                rest = rest.removeRange(m.range)
            }
        }
        rest = rest.trim()

        var part: String? = null
        var clock: String? = null
        val withClock = Regex("""^at (about )?(\d{1,2})(?::(\d{2}))? (in the morning|in the afternoon|in the evening|at night)$""").matchEntire(rest)
        when {
            rest.isEmpty() -> if (day == null) return null
            withClock != null -> {
                val (about, hour, minute, partText) = withClock.destructured
                clock = clockWords(hour.toInt(), minute.ifEmpty { "0" }.toInt())?.let { (if (about.isNotEmpty()) "लगभग " else "") + it } ?: return null
                part = PART.getValue(partText)
            }
            rest == "at night" -> part = "रात"
            else -> Regex("""^in the (morning|afternoon|evening)$""").matchEntire(rest)?.let { part = PART.getValue("in the ${it.groupValues[1]}") } ?: return null
        }

        // A bare part of day with no day and no clock reads "रात में".
        val words = mutableListOf<String>()
        day?.let { words += it }
        if (part != null && (clock != null || day != null)) words += part
        clock?.let { words += it }
        if (words.isEmpty() && part != null) words += "$part में"
        return words.joinToString(" ")
    }

    /** "ढाई बजे", "सवा नौ बजे", "पौने बारह बजे"; null for minutes with no short Hindi form. */
    private fun clockWords(hour: Int, minute: Int): String? {
        if (hour !in 1..12) return null
        val h = HOUR[hour]
        return when (minute) {
            0 -> "$h बजे"
            15 -> "सवा $h बजे"
            30 -> when (hour) { 1 -> "डेढ़ बजे"; 2 -> "ढाई बजे"; else -> "साढ़े $h बजे" }
            45 -> "पौने ${HOUR[hour % 12 + 1]} बजे"
            else -> null
        }
    }

    private fun hi(text: String) = SpokenSegment(text, "hi")

    private val FIXED = mapOf(
        "What kind of incident is it?" to "यह किस तरह की घटना है?",
        "Where is it?" to "यह कहाँ है?",
        "Where did it happen?" to "यह कहाँ हुआ?",
        "Was that today?" to "क्या यह आज हुआ था?",
        "Which day was that?" to "यह किस दिन हुआ था?",
        "When was that?" to "यह कब हुआ था?",
        "How bad is it?" to "यह कितना गंभीर है?",
        "Can you tell me more?" to "क्या आप कुछ और बता सकते हैं?",
        // The cross-questions asked after the four mandatory answers (OptionalAsks). Model-authored Hindi: a native speaker should review it.
        "Did anyone see it happen?" to "क्या किसी ने इसे होते हुए देखा?",
        "Who was hurt?" to "किसे चोट लगी?",
        "Who is unwell?" to "कौन बीमार है?",
        "Do you know who it was?" to "क्या आप जानते हैं कि यह कौन था?",
        "Which tool, machine or item was involved?" to "इसमें कौन सा औज़ार, मशीन या सामान शामिल था?",
        "Is anyone hurt or in danger right now?" to "क्या अभी कोई घायल है या खतरे में है?",
        // The number is said one digit at a time, as the English line does (see TtsTextNormalizer's emergency numbers).
        "If anyone is in danger, call 112 now." to "अगर कोई खतरे में है, तो अभी एक एक दो पर कॉल करें।",
        "Your report is saved on this device." to "आपकी रिपोर्ट इस डिवाइस पर सेव हो गई है।",
        "No harm done." to "कोई नुकसान नहीं हुआ।",
        "I didn't catch that." to "मुझे ठीक से सुनाई नहीं दिया।",
        "I didn't quite get that." to "मुझे ठीक से समझ नहीं आया।",
        "Could you say it again?" to "क्या आप दोबारा बोल सकते हैं?",
        "Could you say it again, a little closer to the microphone?" to "क्या आप माइक के थोड़ा पास आकर दोबारा बोल सकते हैं?",
    )

    private val TYPE = mapOf(
        "water leak or flood" to "पानी का रिसाव या बाढ़",
        "water leak" to "पानी का रिसाव",
        "fire or smoke" to "आग या धुआँ",
        "electrical problem" to "बिजली की समस्या",
        "electrical" to "बिजली की समस्या",
        "spill or chemical" to "रसायन या तरल का फैलाव",
        "spill" to "तरल का फैलाव",
        "injury" to "चोट",
        "illness" to "बीमारी",
        "near miss" to "बाल-बाल बचने की घटना",
        "property damage" to "संपत्ति को नुकसान",
        "equipment failure" to "उपकरण में खराबी",
        "security issue" to "सुरक्षा संबंधी समस्या",
        "environmental issue" to "पर्यावरण संबंधी समस्या",
        "something else" to "कुछ और",
    )

    /** Who a "What is your <role>'s name?" question is about. */
    private val ROLE = mapOf(
        "colleague" to "सहकर्मी", "coworker" to "सहकर्मी", "co-worker" to "सहकर्मी", "student" to "छात्र", "visitor" to "आगंतुक",
        "patient" to "मरीज़", "guest" to "मेहमान", "customer" to "ग्राहक", "worker" to "कर्मचारी", "staff member" to "कर्मचारी",
        "professor" to "प्रोफेसर", "teacher" to "शिक्षक", "driver" to "ड्राइवर", "technician" to "तकनीशियन", "cleaner" to "सफाईकर्मी",
        "person" to "व्यक्ति",
    )

    private val SEVERITY = mapOf("minor" to "मामूली", "moderate" to "मध्यम", "severe" to "गंभीर", "critical" to "अत्यंत गंभीर")

    private val PART = mapOf(
        "in the morning" to "सुबह", "in the afternoon" to "दोपहर", "in the evening" to "शाम", "at night" to "रात",
    )

    private val HOUR = listOf("", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस", "ग्यारह", "बारह")

    private val DAY_NUMBER = listOf(
        "", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह",
        "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस",
        "अट्ठाईस", "उनतीस", "तीस", "इकतीस",
    )

    private val WEEKDAY = mapOf(
        "Monday" to "सोमवार", "Tuesday" to "मंगलवार", "Wednesday" to "बुधवार", "Thursday" to "गुरुवार",
        "Friday" to "शुक्रवार", "Saturday" to "शनिवार", "Sunday" to "रविवार",
    )

    private val MONTH = mapOf(
        "January" to "जनवरी", "February" to "फरवरी", "March" to "मार्च", "April" to "अप्रैल", "May" to "मई", "June" to "जून",
        "July" to "जुलाई", "August" to "अगस्त", "September" to "सितंबर", "October" to "अक्टूबर", "November" to "नवंबर",
        "December" to "दिसंबर",
    )
}

package com.megathon.incident.session

import com.megathon.incident.domain.Field
import java.text.Normalizer

/**
 * A short spoken answer to an open optional question ("Meera and Ravi", "no one", "pata nahi") answers THAT field, so the
 * reporter does not have to phrase it as a full report sentence the first-pass prompt can read. Deterministic, English plus
 * common Hindi/Hinglish forms. Anything that does not look like an answer is [Answer.NotAnAnswer] and goes through the normal
 * first pass, so a real new sentence is never swallowed. Names are never invented: every token comes from what was said.
 */
internal object AnswerParser {
    sealed interface Answer {
        data class Value(val text: String) : Answer
        data object None : Answer
        data object Skip : Answer
        data object NotAnAnswer : Answer
    }

    private const val MAX_WORDS = 8
    private const val MAX_NAMES = 6
    private const val MAX_ITEM_WORDS = 4

    private val NONE = setOf(
        "no one", "nobody", "none", "nope", "no", "nothing", "no tool", "no tools", "no item", "no items", "no one saw it", "no one saw",
        "nobody saw it", "nobody saw", "no one was there", "koi nahi", "koi nahin", "koi nahi tha", "koi nahin tha", "nahi", "nahin",
        "कोई नहीं", "कोई नहीं था", "नहीं", "नही",
    )
    private val SKIP = setOf(
        "skip", "pass", "next", "idk", "i don't know", "i dont know", "dont know", "don't know", "not sure", "no idea", "pata nahi", "pata nahin",
        "पता नहीं", "पता नही", "chhodo", "chhod do", "छोड़ो", "छोड़ दो",
    )

    private val LEAD = Regex(
        "^(?:i think it was|i think it is|i think|i guess|it was|it is|it's|its|that was|that is|that's|this was|maybe|probably|uh|um|erm|yes|yeah|yep|" +
            "haan|han|हाँ|हां|ji|the name is|her name is|his name is|their name is|name is|uska naam|unka naam|उसका नाम|उनका नाम|naam|नाम|wo|woh|vo|वो|वह)\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val TRAIL = Regex(
        "\\s+(?:hai|hain|tha|thi|the|है|हैं|था|थी|थे|saw it|saw|ne dekha tha|ne dekha|ने देखा)$",
        RegexOption.IGNORE_CASE,
    )
    private val ARTICLE = Regex("^(?:the|a|an|my|our|some|one|ek|ye|yeh|woh|wo|वो|यह|ये|एक)\\s+", RegexOption.IGNORE_CASE)
    private val SPLIT = Regex("\\s*(?:,|&|\\band\\b|\\baur\\b|और)\\s*", RegexOption.IGNORE_CASE)
    private val EDGE = Regex("^[\\s.,!?;:\"'“”‘’।]+|[\\s.,!?;:\"'“”‘’।]+$")
    private val NAME_TOKEN = Regex("^[\\p{L}\\p{M}]{2,}$")
    private val ITEM_TOKEN = Regex("^[\\p{L}\\p{M}\\p{N}-]+$")

    /** Words that are never a person's name: pronouns, roles, quantities. A role is not a name (spec: never invent names). */
    private val NOT_NAMES = setOf(
        "he", "she", "they", "him", "her", "them", "we", "you", "it", "someone", "somebody", "everyone", "everybody", "people", "person", "man",
        "woman", "boy", "girl", "guard", "security", "nurse", "doctor", "student", "colleague", "friend", "worker", "staff", "visitor", "the", "an",
        "three", "two", "four", "five", "many", "few", "all", "did", "was", "were", "yes", "ok", "okay", "who", "what",
    )

    fun parse(field: Field, said: String): Answer {
        if (field != Field.PERSONS_INVOLVED && field != Field.WITNESSES && field != Field.EQUIPMENT_ASSET) return Answer.NotAnAnswer
        val text = Normalizer.normalize(said, Normalizer.Form.NFC).replace(EDGE, "").replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.split(' ').size > MAX_WORDS) return Answer.NotAnAnswer
        val key = text.lowercase()
        if (key in NONE) return Answer.None
        if (key in SKIP) return Answer.Skip
        return if (field == Field.EQUIPMENT_ASSET) item(text) else names(text)
    }

    private fun strip(text: String, article: Boolean): String {
        var s = text
        while (true) {
            val before = s
            s = s.replace(LEAD, "").replace(TRAIL, "").replace(EDGE, "")
            if (article) s = s.replace(ARTICLE, "")
            if (s == before) return s
        }
    }

    private fun names(text: String): Answer {
        val parts = strip(text, article = false).split(SPLIT).map { it.replace(EDGE, "") }.filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > MAX_NAMES) return Answer.NotAnAnswer
        val out = parts.map { part ->
            val tokens = part.split(' ')
            if (tokens.size > 3 || tokens.any { !NAME_TOKEN.matches(it) || it.lowercase() in NOT_NAMES }) return Answer.NotAnAnswer
            tokens.joinToString(" ") { t -> if (t.all { it.isLowerCase() && it.code < 128 }) t.replaceFirstChar(Char::uppercaseChar) else t }
        }
        return Answer.Value(out.joinToString(", "))
    }

    private fun item(text: String): Answer {
        val s = strip(text, article = true)
        val tokens = s.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > MAX_ITEM_WORDS || tokens.any { !ITEM_TOKEN.matches(it) }) return Answer.NotAnAnswer
        return Answer.Value(tokens.joinToString(" "))
    }
}

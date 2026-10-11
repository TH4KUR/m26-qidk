package com.megathon.incident.tts

/**
 * Splits text into sentence-sized pieces so the voice can start speaking after the first one, and so a stop request
 * is noticed between pieces. A sentence over [maxChars] is broken at commas, then at spaces.
 */
object TtsChunker {
    const val DEFAULT_MAX_CHARS = 220

    private val sentenceEnd = Regex("(?<=[.!?।])\\s+")

    fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> {
        val sentences = text.trim().split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }
        return sentences.flatMap { if (it.length <= maxChars) listOf(it) else breakLong(it, maxChars) }
    }

    private fun breakLong(sentence: String, maxChars: Int): List<String> {
        val parts = sentence.split(Regex("(?<=,)\\s+")).flatMap { part ->
            if (part.length <= maxChars) listOf(part) else part.split(' ').filter { it.isNotEmpty() }
        }
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (part in parts) {
            if (current.isNotEmpty() && current.length + 1 + part.length > maxChars) {
                out += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(part)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}

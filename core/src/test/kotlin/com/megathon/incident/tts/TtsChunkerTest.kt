package com.megathon.incident.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtsChunkerTest {
    @Test
    fun oneSentenceIsOneChunk() {
        assertEquals(listOf("Is anyone hurt?"), TtsChunker.split("Is anyone hurt?"))
    }

    @Test
    fun splitsAtSentenceEnds() {
        assertEquals(
            listOf("Water is leaking.", "Is anyone hurt?", "Tell me where."),
            TtsChunker.split("Water is leaking. Is anyone hurt? Tell me where."),
        )
    }

    @Test
    fun hindiFullStopIsASentenceEnd() {
        assertEquals(listOf("पानी रिस रहा है।", "क्या कोई घायल है?"), TtsChunker.split("पानी रिस रहा है। क्या कोई घायल है?"))
    }

    @Test
    fun aNumberWithADecimalPointIsNotSplit() {
        assertEquals(listOf("It is 3.5 metres deep."), TtsChunker.split("It is 3.5 metres deep."))
    }

    @Test
    fun blankInputGivesNoChunks() {
        assertTrue(TtsChunker.split("   ").isEmpty())
    }

    @Test
    fun aVeryLongSentenceIsBrokenAtCommasThenSpaces() {
        val long = (1..40).joinToString(", ") { "word$it" } + "."
        val chunks = TtsChunker.split(long, maxChars = 80)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 80 }, chunks.toString())
        fun words(s: String) = s.replace(",", "").replace(".", "").split(" ").filter { it.isNotEmpty() }
        assertEquals(words(long), words(chunks.joinToString(" ")))
    }

    @Test
    fun aWordLongerThanTheLimitIsKeptWhole() {
        assertEquals(listOf("x".repeat(50)), TtsChunker.split("x".repeat(50), maxChars = 10))
    }
}

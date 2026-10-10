package com.megathon.incident.tam

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CueMatchingTest {
    private fun hits(text: String, vararg phrases: String) = CueMatching.find(text, phrases.toList()).map { it.phrase }

    @Test fun latinPhrasesMatchWholeWordsOnly() {
        assertEquals(listOf("tha"), hits("paani leak tha", "tha"))
        assertTrue(hits("thanda paani", "tha").isEmpty())
    }

    @Test fun devanagariFireWordDoesNotMatchInsideAheadOrOtherWords() {
        assertEquals(listOf("आग"), hits("घर में आग लगी है", "आग"))
        assertTrue(hits("आगे जाकर बाएं मुड़ो", "आग").isEmpty())
        assertTrue(hits("आगामी सोमवार", "आग").isEmpty())
    }

    @Test fun devanagariMatraWordsAreNotSplit() {
        assertTrue(hits("विद्यार्थी आए", "थी").isEmpty())
        assertTrue(hits("स्थान खाली है", "था").isEmpty())
        assertTrue(hits("मतलब क्या है", "मत").isEmpty())
    }

    @Test fun outOfControlIsNotUnderControl() {
        assertTrue(hits("आग बेकाबू है", "काबू").isEmpty())
        assertEquals(listOf("काबू"), hits("आग काबू में है", "काबू"))
    }

    @Test fun devanagariMultiWordPhrasesStillMatch() {
        assertEquals(listOf("अभी भी"), hits("पानी अभी भी रिस रहा है", "अभी भी"))
        assertEquals(listOf("रहा है"), hits("पानी रिस रहा है", "रहा है"))
    }

    @Test fun punctuationEndsADevanagariWord() {
        assertEquals(listOf("आग"), hits("आग, धुआं और लपटें", "आग"))
        assertEquals(listOf("नहीं"), hits("कोई घायल नहीं।", "नहीं"))
    }
}

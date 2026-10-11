package com.megathon.incident.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PiperVoicesTest {
    @Test
    fun englishIsTheDefault() {
        assertEquals("en", PiperVoices.forLanguage(null)?.language)
        assertEquals("en", PiperVoices.forLanguage("")?.language)
        assertEquals("en", PiperVoices.forLanguage("en")?.language)
    }

    @Test
    fun regionAndCaseAreIgnored() {
        assertEquals("en", PiperVoices.forLanguage("en-IN")?.language)
        assertEquals("en", PiperVoices.forLanguage("EN_us")?.language)
        assertEquals("hi", PiperVoices.forLanguage("hi-IN")?.language)
        assertEquals("hi", PiperVoices.forLanguage("HI")?.language)
    }

    @Test
    fun teluguAndUnknownLanguagesHaveNoPiperVoice() {
        assertNull(PiperVoices.forLanguage("te"))
        assertNull(PiperVoices.forLanguage("te-IN"))
        assertNull(PiperVoices.forLanguage("fr"))
    }

    @Test
    fun theEnglishVoiceIsTheLessacMediumFolder() {
        val v = assertNotNull(PiperVoices.forLanguage("en"))
        assertEquals("vits-piper-en_US-lessac-medium", v.folder)
        assertEquals("en_US-lessac-medium.onnx", v.modelFile)
        assertEquals("en-us", v.espeakVoice)
        assertTrue(v.modelSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(v.modelBytes > 10_000_000)
    }

    @Test
    fun theHindiVoiceIsTheRohanInt8Folder() {
        val v = assertNotNull(PiperVoices.forLanguage("hi"))
        assertEquals("vits-piper-hi_IN-rohan-medium-int8", v.folder)
        assertEquals("hi_IN-rohan-medium.onnx", v.modelFile)
    }

    @Test
    fun everyVoiceHasADistinctFolderAndLabel() {
        val all = PiperVoices.all
        assertEquals(all.size, all.map { it.folder }.toSet().size)
        assertTrue(all.all { it.label.contains("Piper") })
    }
}

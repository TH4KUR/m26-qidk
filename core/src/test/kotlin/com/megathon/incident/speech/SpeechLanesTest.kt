package com.megathon.incident.speech

import com.megathon.incident.engine.EngineSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechLanesTest {
    @Test
    fun noLanguageMeansEnglishWithNothingToExplain() {
        for (code in listOf(null, "", "   ")) {
            val lane = SpeechLanes.select(code)
            assertEquals(RecognitionEngine.WHISPER_SMALL, lane.engine, "code=$code")
            assertEquals("en", lane.language)
            assertFalse(lane.experimental)
            assertNull(lane.notice)
        }
    }

    @Test
    fun englishInAnyCaseOrRegionStaysOnTheExistingEngineWithTheExistingCode() {
        for (code in listOf("en", "EN", " en ", "en-IN", "en_US", "en-us")) {
            val lane = SpeechLanes.select(code)
            assertEquals(RecognitionEngine.WHISPER_SMALL, lane.engine, "code=$code")
            assertEquals("en", lane.language, "code=$code")
            assertFalse(lane.experimental)
            assertNull(lane.notice)
        }
    }

    @Test
    fun theEnglishLaneIsExactlyWhatTheAppDidBeforeTheHindiLaneExisted() {
        // The recognition call passed the literal "en" to the Whisper engine and showed no note. Nothing about that may move.
        assertEquals(SpeechLane.ENGLISH, SpeechLanes.select("en"))
        assertEquals("en", SpeechLane.ENGLISH.language)
        assertEquals(RecognitionEngine.WHISPER_SMALL, SpeechLane.ENGLISH.engine)
        assertFalse(SpeechLane.ENGLISH.experimental)
        assertNull(SpeechLane.ENGLISH.notice)
        // And a selection that never mentions a language is the English lane.
        assertEquals(SpeechLane.ENGLISH, SpeechLanes.select(EngineSelection().language))
    }

    @Test
    fun hindiRoutesToTheNemotronEngineAndIsMarkedExperimental() {
        for (code in listOf("hi", "HI", " hi ", "hi-IN", "hi_IN")) {
            val lane = SpeechLanes.select(code)
            assertEquals(RecognitionEngine.NEMOTRON_HINDI, lane.engine, "code=$code")
            assertEquals("hi", lane.language, "code=$code")
            assertTrue(lane.experimental)
            assertNull(lane.notice)
        }
    }

    @Test
    fun teluguIsNotSupportedAndNeverReachesTheHindiModel() {
        for (code in listOf("te", "TE", "te-IN", "te_IN", "tel")) {
            val lane = SpeechLanes.select(code)
            assertEquals(RecognitionEngine.WHISPER_SMALL, lane.engine, "code=$code must not use the Hindi model")
            assertEquals("en", lane.language, "code=$code")
            assertFalse(lane.experimental)
            val notice = assertNotNull(lane.notice, "code=$code needs a reason")
            assertTrue("Telugu" in notice, notice)
            assertTrue("not supported" in notice, notice)
        }
    }

    @Test
    fun unknownCodesFallBackToEnglishAndSayWhy() {
        for (code in listOf("fr", "xx", "hindi", "ta", "zh-CN", "h", "1")) {
            val lane = SpeechLanes.select(code)
            assertEquals(RecognitionEngine.WHISPER_SMALL, lane.engine, "code=$code")
            assertEquals("en", lane.language)
            val notice = assertNotNull(lane.notice, "code=$code needs a reason")
            assertTrue(code in notice, notice)
        }
    }

    @Test
    fun onlyTheHindiLaneUsesTheHindiEngine() {
        val hindi = listOf("hi", "hi-IN").map { SpeechLanes.select(it) }
        val others = listOf(null, "en", "te", "fr", "").map { SpeechLanes.select(it) }
        assertTrue(hindi.all { it.engine == RecognitionEngine.NEMOTRON_HINDI })
        assertTrue(others.none { it.engine == RecognitionEngine.NEMOTRON_HINDI })
    }

    @Test
    fun theSelectionCarriesTheRequestedLanguageAndDefaultsToEnglish() {
        assertEquals("en", EngineSelection().language)
        assertEquals("hi", EngineSelection(language = "hi").language)
    }
}

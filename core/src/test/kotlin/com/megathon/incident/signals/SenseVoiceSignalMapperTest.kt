package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SenseVoiceSignalMapperTest {
    @Test
    fun anAngryVoiceBecomesAnEmotionSignal() {
        val signals = SenseVoiceSignalMapper.map(emotion = "<|ANGRY|>", event = "<|Speech|>", segmentId = 3)
        assertEquals(listOf(Signal("emotion", "angry", Source.SENSEVOICE, 3)), signals)
    }

    @Test
    fun theFourEmotionsInTheSpecAndFearAreKept() {
        listOf("HAPPY" to "happy", "SAD" to "sad", "ANGRY" to "angry", "NEUTRAL" to "neutral", "FEARFUL" to "fearful")
            .forEach { (tag, label) ->
                assertEquals(label, SenseVoiceSignalMapper.map("<|$tag|>", null).single().label, tag)
            }
    }

    @Test
    fun unknownOrMissingEmotionIsNoSignalNotAGuess() {
        assertTrue(SenseVoiceSignalMapper.map("<|EMO_UNKNOWN|>", "<|Speech|>").isEmpty())
        assertTrue(SenseVoiceSignalMapper.map(null, null).isEmpty())
        assertTrue(SenseVoiceSignalMapper.map("", "").isEmpty())
    }

    @Test
    fun soundEventsUseTheWordsThePriorityRulesMatchOn() {
        assertEquals("crying", SenseVoiceSignalMapper.map(null, "<|Cry|>").single().label)
        assertEquals("coughing", SenseVoiceSignalMapper.map(null, "<|Cough|>").single().label)
        assertEquals("sneezing", SenseVoiceSignalMapper.map(null, "<|Sneeze|>").single().label)
        assertEquals("laughter", SenseVoiceSignalMapper.map(null, "<|Laughter|>").single().label)
        assertEquals("breathing", SenseVoiceSignalMapper.map(null, "<|Breath|>").single().label)
        assertEquals("event", SenseVoiceSignalMapper.map(null, "<|Cry|>").single().kind)
    }

    @Test
    fun speechMusicAndApplauseAreNotSignals() {
        assertTrue(SenseVoiceSignalMapper.map(null, "<|Speech|>").isEmpty())
        assertTrue(SenseVoiceSignalMapper.map(null, "<|BGM|>").isEmpty())
        assertTrue(SenseVoiceSignalMapper.map(null, "<|Applause|>").isEmpty())
    }

    @Test
    fun emotionAndEventTogetherGiveBothSignalsEmotionFirst() {
        val signals = SenseVoiceSignalMapper.map("<|SAD|>", "<|Cry|>")
        assertEquals(listOf("emotion" to "sad", "event" to "crying"), signals.map { it.kind to it.label })
        assertTrue(signals.all { it.source == Source.SENSEVOICE })
    }

    @Test
    fun tagsWithoutBracketsOrInOtherCaseStillMatch() {
        assertEquals("angry", SenseVoiceSignalMapper.map("angry", null).single().label)
        assertEquals("crying", SenseVoiceSignalMapper.map(null, "cry").single().label)
    }
}

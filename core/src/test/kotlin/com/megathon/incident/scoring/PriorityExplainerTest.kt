package com.megathon.incident.scoring

import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.priorityForScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriorityExplainerTest {
    private val explainer = PriorityExplainer.default()

    private fun emotion(label: String) = Signal("emotion", label, Source.SENSEVOICE)
    private fun event(label: String) = Signal("event", label, Source.SENSEVOICE)

    @Test
    fun withoutVoiceTagsTheVoiceAddsNothing() {
        val i = explainer.explain("there is a fire in the lab", emptyList())
        assertEquals(i.wordsOnlyScore, i.withVoiceScore)
        assertEquals(0, i.voicePoints)
        assertTrue(i.terms.none { it.kind != VoiceTermKind.WORDS })
    }

    @Test
    fun aDangerSoundRaisesTheScoreAndTheTermShowsTheRuleAndThePoints() {
        // Rule list: gunshot -> impact at least 5, immediacy at least 4; risk matrix 5 x 4 = 20.
        val i = explainer.explain("ok", listOf(event("gunshot")))
        assertEquals(20, i.withVoiceScore)
        assertEquals(5, i.impact)
        assertEquals(4, i.immediacy)
        assertEquals("URGENT", i.level)
        val t = i.terms.single { it.kind == VoiceTermKind.SOUND }
        assertEquals("gunshot", t.label)
        assertTrue(t.counted)
        assertEquals(5, t.minImpact)
        assertEquals(4, t.minImmediacy)
        assertEquals(i.withVoiceScore - i.wordsOnlyScore, t.points)
        assertTrue(t.points!! > 0)
        assertEquals(i.voicePoints, t.points)
    }

    @Test
    fun anAngryVoiceCountsAsDistressAndLiftsImmediacyByOne() {
        val calm = explainer.explain("the printer is out of paper", emptyList())
        val angry = explainer.explain("the printer is out of paper", listOf(emotion("angry")))
        assertEquals(calm.immediacy + 1, angry.immediacy)
        val t = angry.terms.single { it.kind == VoiceTermKind.EMOTION }
        assertTrue(t.counted)
        assertTrue(t.points!! > 0)
        assertEquals(angry.withVoiceScore - calm.withVoiceScore, t.points)
    }

    @Test
    fun aHappyVoiceIsListedButChangesNothing() {
        val i = explainer.explain("the printer is out of paper", listOf(emotion("happy")))
        val t = i.terms.single { it.kind == VoiceTermKind.EMOTION }
        assertFalse(t.counted)
        assertEquals(0, t.points)
        assertEquals(0, i.voicePoints)
    }

    @Test
    fun aSoundWithNoRuleIsListedButChangesNothing() {
        val i = explainer.explain("the printer is out of paper", listOf(event("coughing")))
        val t = i.terms.single { it.kind == VoiceTermKind.SOUND }
        assertFalse(t.counted)
        assertNull(t.minImpact)
        assertEquals(0, t.points)
    }

    @Test
    fun eachSignalIsCreditedWithWhatTheScoreLosesWithoutIt() {
        val i = explainer.explain("ok", listOf(event("gunshot"), emotion("angry")))
        val gun = i.terms.single { it.label == "gunshot" }
        val angry = i.terms.single { it.label == "angry" }
        // Without the gunshot only the angry voice is left: the default impact 3 and immediacy 2 + 1 (distress) = 9 against 20.
        assertEquals(20 - 9, gun.points)
        // Without the angry voice the gunshot alone already gives 5 x 4 = 20.
        assertEquals(0, angry.points)
    }

    @Test
    fun theWordsAreShownAsTheirOwnTermsWithoutQuotingThem() {
        val i = explainer.explain("there is a fire in the lab", emptyList())
        val words = i.terms.filter { it.kind == VoiceTermKind.WORDS }
        assertTrue(words.isNotEmpty())
        assertTrue(words.none { it.label.contains("\"") && it.label.contains("lab") })
        assertTrue(words.all { it.points == null && it.source == "TEXT_CUE" })
    }

    @Test
    fun signalsThatAreNotAudioTagsAreIgnored() {
        val i = explainer.explain("ok", listOf(Signal("something", "x", Source.RULE)))
        assertTrue(i.terms.none { it.kind != VoiceTermKind.WORDS })
    }

    @Test
    fun theFormulaAndTheLevelCutoffsAreWrittenOutAndMatchTheRealOnes() {
        val i = explainer.explain("ok", emptyList())
        assertTrue(i.formula.contains("impact") && i.formula.contains("immediacy"))
        for (score in listOf(1, 4, 5, 9, 10, 14, 15, 20)) {
            val level = priorityForScore(score)
            assertTrue(i.thresholds.contains(level.name), "$level")
        }
        // The cut-offs 5, 10 and 15 are the ones priorityForScore uses.
        assertEquals(Priority.LOW, priorityForScore(4))
        assertTrue(i.thresholds.contains("5") && i.thresholds.contains("10") && i.thresholds.contains("15"))
    }

    @Test
    fun theReplayIsDeterministicAndKeepsNoMoreThanItWasGiven() {
        val a = explainer.explain("the lift is stuck", listOf(emotion("fearful")))
        val b = explainer.explain("the lift is stuck", listOf(emotion("fearful")))
        assertEquals(a, b)
    }
}

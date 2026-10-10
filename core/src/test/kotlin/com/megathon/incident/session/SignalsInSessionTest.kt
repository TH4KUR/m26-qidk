package com.megathon.incident.session

import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.signals.SenseVoiceSignalMapper
import com.megathon.incident.testing.FakeLlmEngine
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Signals from the audio travel with an utterance into the advisor; the advisor's rules alone decide what they mean. */
class SignalsInSessionTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val calm = FakeLlmEngine.DECK_TRANSCRIPT
    private val fireAlarm = Signal("event", "fire alarm", Source.YAMNET, null)

    private fun session() = Pipeline.newSession(FakeLlmEngine(), InMemoryReportStore(), clock)

    private fun ReportSession.say(text: String, signals: List<Signal> = emptyList()) =
        runBlocking { onUtterance(text, null, signals) }

    private fun ReportSession.level(): Priority = assertNotNull(ui.value.priority).level

    @Test
    fun aDangerSoundRaisesThePriorityThroughTheAdvisor() {
        val without = session().also { it.say(calm) }
        val with = session().also { it.say(calm, listOf(fireAlarm)) }
        assertTrue(with.level() > without.level(), "with the alarm ${with.level()}, without ${without.level()}")
        assertTrue(with.ui.value.priority!!.reasons.any { it.contains("fire alarm", ignoreCase = true) })
    }

    @Test
    fun anUnknownEmotionAddsNothing() {
        val baseline = session().also { it.say(calm) }
        val mapped = SenseVoiceSignalMapper.map(emotion = "<|EMO_UNKNOWN|>", event = "<|Speech|>")
        assertTrue(mapped.isEmpty())
        val unknown = session().also {
            it.say(calm, mapped + Signal("emotion", "bewildered", Source.SENSEVOICE, null))
        }
        assertEquals(baseline.level(), unknown.level())
        assertEquals(baseline.ui.value.priority!!.reasons, unknown.ui.value.priority!!.reasons)
    }

    @Test
    fun aCalmEmotionDoesNotMakeAnythingMoreUrgent() {
        val baseline = session().also { it.say(calm) }
        val neutral = session().also { it.say(calm, SenseVoiceSignalMapper.map("<|NEUTRAL|>", "<|Speech|>")) }
        assertEquals(baseline.level(), neutral.level())
    }

    @Test
    fun signalsNeverLowerAnAlreadyRaisedPriority() {
        val s = session()
        s.say(calm, listOf(fireAlarm))
        val raised = s.level()
        assertTrue(raised > session().also { it.say(calm) }.level())

        s.say("Minor, nobody was hurt.")
        assertTrue(s.level() >= raised, "a calm turn lowered it to ${s.level()}")

        s.say("It is fine now.", SenseVoiceSignalMapper.map("<|NEUTRAL|>", "<|Speech|>"))
        assertTrue(s.level() >= raised, "a neutral voice lowered it to ${s.level()}")
    }
}

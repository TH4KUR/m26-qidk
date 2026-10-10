package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeSpeechEngine
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Speaker
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandsFreeLoopTest {
    private val rate = 16_000
    private val log = CopyOnWriteArrayList<String>()

    private fun tone(ms: Int) = ShortArray(rate * ms / 1000) { (4000 * sin(2 * PI * 220.0 * it / rate)).toInt().toShort() }
    private fun silence(ms: Int) = ShortArray(rate * ms / 1000)

    private fun mic(vararg parts: ShortArray): Flow<ShortArray> =
        flowOf(*parts.flatMap { part -> part.toList().chunked(320).map { it.toShortArray() } }.toTypedArray())

    private val gate = object : MicGate {
        override fun mute() { log += "mute" }
        override fun unmute() { log += "unmute" }
    }

    private val speaker = object : Speaker {
        override suspend fun speak(text: String) { log += "speak:$text" }
    }

    private fun loop(
        engine: SpeechEngine = FakeSpeechEngine(defaultText = "water leak in conference room two"),
        language: () -> String = { "en" },
        handler: TurnHandler = TurnHandler { t, _ -> log += "handle:${t.text}"; TurnReply("Got it.") },
    ) = HandsFreeLoop(engine, speaker, gate, handler, language, graceMs = 0)

    @Test
    fun anUtteranceIsHandledAndTheReplyIsSpokenWithTheMicMuted() = runBlocking {
        loop().run(mic(silence(500), tone(1000), silence(1500)))

        assertEquals(
            listOf("handle:water leak in conference room two", "mute", "speak:Got it.", "unmute"),
            log.toList(),
        )
    }

    @Test
    fun silenceNeverCallsTheEngineOrTheHandler() = runBlocking {
        val engine = FakeSpeechEngine(defaultText = "should not be asked")
        loop(engine = engine).run(mic(silence(3000)))

        assertTrue(engine.languageRequests.isEmpty())
        assertTrue(log.isEmpty())
    }

    @Test
    fun aBlankTranscriptSpeaksThePromptAndNeverCallsTheHandler() = runBlocking {
        loop(engine = FakeSpeechEngine(defaultText = "   ")).run(mic(silence(500), tone(1000), silence(1500)))

        assertEquals(listOf("mute", "speak:Sorry, I didn't catch that.", "unmute"), log.toList())
    }

    @Test
    fun aVoiceCommandIsParsedAndPassedToTheHandler() = runBlocking {
        val seen = mutableListOf<VoiceCommand?>()
        val engine = FakeSpeechEngine(defaultText = "Submit the report.")
        loop(engine = engine, handler = TurnHandler { _, command -> seen += command; TurnReply() })
            .run(mic(silence(500), tone(1000), silence(1500)))

        assertEquals(listOf<VoiceCommand?>(VoiceCommand.Submit), seen)
        assertTrue(log.isEmpty(), "an empty reply must not make the app speak: $log")
    }

    @Test
    fun theLanguageToggleIsReadForEveryUtterance() = runBlocking {
        val engine = FakeSpeechEngine(defaultText = "text")
        var language = "en"
        loop(engine = engine, language = { language }, handler = TurnHandler { _, _ -> language = "hi"; TurnReply() })
            .run(mic(silence(500), tone(800), silence(1200), tone(800), silence(1200)))

        assertEquals(listOf("en", "hi"), engine.languageRequests)
    }

    @Test
    fun anEngineFailureIsSpokenAndTheLoopKeepsRunning() = runBlocking {
        var calls = 0
        val flaky = object : SpeechEngine {
            override val name = "flaky"
            override suspend fun transcribe(pcm: ShortArray, language: String): Transcript {
                if (calls++ == 0) error("NPU graph failed")
                return Transcript("second try", language, 1, Compute.SIMULATED)
            }
        }
        val loop = loop(engine = flaky)
        loop.run(mic(silence(500), tone(800), silence(1200), tone(800), silence(1200)))

        assertEquals(
            listOf(
                "mute", "speak:Sorry, I couldn't process that. Please say it again.", "unmute",
                "handle:second try", "mute", "speak:Got it.", "unmute",
            ),
            log.toList(),
        )
        assertEquals(listOf("speech engine flaky: NPU graph failed"), loop.errors)
    }

    @Test
    fun aHandlerFailureDoesNotEndTheLoop() = runBlocking {
        var calls = 0
        val loop = loop(handler = TurnHandler { _, _ -> if (calls++ == 0) error("boom") else TurnReply("Recovered.") })
        loop.run(mic(silence(500), tone(800), silence(1200), tone(800), silence(1200)))

        assertTrue("speak:Recovered." in log, log.toString())
        assertEquals(listOf("turn handler: boom"), loop.errors)
    }

    @Test
    fun whenTheMicStopsAnUnfinishedUtteranceIsStillProcessed() = runBlocking {
        // Tap-to-talk: the user taps stop in the middle of speech, so the stream ends with no trailing silence.
        loop().run(mic(silence(500), tone(900)))

        assertEquals("handle:water leak in conference room two", log.first())
    }
}

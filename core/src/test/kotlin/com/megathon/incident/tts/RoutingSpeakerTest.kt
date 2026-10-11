package com.megathon.incident.tts

import com.megathon.incident.testing.Compute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingSpeakerTest {
    private class FakeBackend(
        override val compute: Compute,
        private val name: String,
        var unavailable: String? = null,
        var failure: Throwable? = null,
        override val offline: Boolean? = true,
    ) : TtsBackend {
        val spoken = mutableListOf<Pair<String, String>>()
        var stops = 0
        var shutdowns = 0
        var unavailableChecks = 0
        override fun label(language: String) = "$name/$language"
        override suspend fun unavailableReason(language: String): String? { unavailableChecks++; return unavailable }
        override suspend fun speak(text: String, language: String) {
            failure?.let { throw it }
            spoken += language to text
        }
        override fun stop() { stops++ }
        override fun shutdown() { shutdowns++ }
    }

    private val android = FakeBackend(Compute.CPU, "Android TTS")
    private val piper = FakeBackend(Compute.CPU, "Piper")

    @Test
    fun withoutAPrimaryTheFallbackSpeaksAndIsReported() = runBlocking {
        val s = RoutingSpeaker(primary = null, fallback = android)
        s.speak("Hello.")
        assertEquals(listOf("en" to "Hello."), android.spoken)
        val last = assertNotNull(s.last)
        assertEquals("Android TTS/en", last.engine)
        assertEquals(Compute.CPU, last.compute)
        assertNull(last.fallbackReason)
    }

    @Test
    fun theLabelNamesTheEngineThatActuallySpoke() = runBlocking {
        val s = RoutingSpeaker(piper, android)
        s.speak("Hello.")
        assertEquals("Piper/en", s.last?.engine)
        assertEquals(Compute.CPU, s.last?.compute)
        assertTrue(android.spoken.isEmpty())
    }

    @Test
    fun anUnavailableVoiceFallsBackAndSaysWhy() = runBlocking {
        piper.unavailable = "no Piper voice for te"
        val s = RoutingSpeaker(piper, android, language = { "te" })
        s.speak("Hello.")
        assertTrue(piper.spoken.isEmpty())
        assertEquals(listOf("te" to "Hello."), android.spoken)
        assertEquals("Android TTS/te", s.last?.engine)
        assertEquals("no Piper voice for te", s.last?.fallbackReason)
    }

    @Test
    fun aFailureBeforeAnyAudioFallsBackAndSaysWhy() = runBlocking {
        piper.failure = TtsBackendException("could not load the native library", permanent = true)
        val s = RoutingSpeaker(piper, android)
        s.speak("Hello.")
        assertEquals("Android TTS/en", s.last?.engine)
        assertEquals("could not load the native library", s.last?.fallbackReason)
        assertEquals(listOf("en" to "Hello."), android.spoken)
    }

    @Test
    fun aPermanentFailureIsNotRetriedOnTheNextUtterance() = runBlocking {
        piper.failure = TtsBackendException("library failed", permanent = true)
        val s = RoutingSpeaker(piper, android)
        s.speak("One.")
        piper.failure = null
        s.speak("Two.")
        assertEquals(2, android.spoken.size)
        assertTrue(piper.spoken.isEmpty())
        assertEquals(1, piper.unavailableChecks)
        assertEquals("library failed", s.last?.fallbackReason)
    }

    @Test
    fun aTransientFailureIsRetriedNextTime() = runBlocking {
        piper.failure = TtsBackendException("synthesis returned no audio", permanent = false)
        val s = RoutingSpeaker(piper, android)
        s.speak("One.")
        piper.failure = null
        s.speak("Two.")
        assertEquals(listOf("en" to "Two."), piper.spoken)
        assertEquals("Piper/en", s.last?.engine)
    }

    @Test
    fun aFailureAfterAudioStartedIsNotSpokenAgain() = runBlocking {
        piper.failure = TtsBackendException("died on sentence 3", spokeSomething = true)
        val s = RoutingSpeaker(piper, android)
        s.speak("One. Two. Three.")
        assertTrue(android.spoken.isEmpty())
        assertEquals("Piper/en", s.last?.engine)
    }

    @Test
    fun anUnexpectedErrorFromThePrimaryAlsoFallsBack() = runBlocking {
        piper.failure = UnsatisfiedLinkError("dlopen failed")
        val s = RoutingSpeaker(piper, android)
        s.speak("Hello.")
        assertEquals("Android TTS/en", s.last?.engine)
        assertTrue(s.last?.fallbackReason.orEmpty().contains("dlopen failed"))
    }

    @Test
    fun cancellationIsNotAFailure() {
        piper.failure = CancellationException("stop")
        val s = RoutingSpeaker(piper, android)
        assertFailsWith<CancellationException> { runBlocking { s.speak("Hello.") } }
        assertTrue(android.spoken.isEmpty())
    }

    @Test
    fun theLanguageIsAskedForEachUtterance() = runBlocking {
        var lang = "en"
        val s = RoutingSpeaker(piper, android, language = { lang })
        s.speak("One.")
        lang = "hi"
        s.speak("Two.")
        assertEquals(listOf("en" to "One.", "hi" to "Two."), piper.spoken)
    }

    @Test
    fun blankTextIsNotSpokenAndNothingIsReported() = runBlocking {
        val s = RoutingSpeaker(piper, android)
        s.speak("   ")
        assertTrue(piper.spoken.isEmpty() && android.spoken.isEmpty())
        assertNull(s.last)
    }

    @Test
    fun stopAndShutdownReachBothEngines() {
        val s = RoutingSpeaker(piper, android)
        s.stop()
        s.shutdown()
        assertEquals(1, piper.stops); assertEquals(1, android.stops)
        assertEquals(1, piper.shutdowns); assertEquals(1, android.shutdowns)
    }

    @Test
    fun theProofStageNamesTheEngineAndItsCompute() {
        val piperSpoke = SpokenWith("Piper TTS (sherpa-onnx)", Compute.CPU, true)
        assertEquals(
            com.megathon.incident.session.StageRecord("Speak", "Piper TTS (sherpa-onnx)", Compute.CPU, 120L),
            piperSpoke.toStage(120L),
        )
        assertNull(piperSpoke.note())
    }

    @Test
    fun theProofNoteSaysWhyPiperDidNotSpeak() {
        val fellBack = SpokenWith("Android TTS (system voice)", Compute.CPU, true, "voice files are missing")
        val note = assertNotNull(fellBack.note())
        assertTrue(note.contains("Piper"), note)
        assertTrue(note.contains("voice files are missing"), note)
        assertTrue(note.contains("Android TTS (system voice)"), note)
    }

    @Test
    fun offlineComesFromTheEngineThatSpoke() = runBlocking {
        val sys = FakeBackend(Compute.CPU, "Android TTS", offline = false)
        val s = RoutingSpeaker(primary = null, fallback = sys)
        s.speak("Hello.")
        assertEquals(false, s.last?.offline)
    }
}

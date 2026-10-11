package com.megathon.incident.tts

import com.megathon.incident.engine.EngineSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class TtsKindTest {
    @Test
    fun defaultIsAndroid() {
        assertEquals(TtsKind.ANDROID, EngineSelection().tts)
        assertEquals(TtsKind.ANDROID, TtsKind.parse(null))
    }

    @Test
    fun piperIsParsedCaseInsensitively() {
        assertEquals(TtsKind.PIPER, TtsKind.parse("PIPER"))
        assertEquals(TtsKind.PIPER, TtsKind.parse(" piper "))
        assertEquals(TtsKind.ANDROID, TtsKind.parse("android"))
    }

    @Test
    fun theNeuralVoiceSpeaksSlowerThanItsNaturalRateByDefault() {
        // 1.0 is the model's natural rate and was reported as too fast; below 1.0 is slower. A switch lets it be tuned by ear.
        assertEquals(0.8f, EngineSelection().ttsSpeed)
    }

    @Test
    fun supertonicIsParsedCaseInsensitively() {
        assertEquals(TtsKind.SUPERTONIC, TtsKind.parse("SUPERTONIC"))
        assertEquals(TtsKind.SUPERTONIC, TtsKind.parse(" supertonic "))
    }

    @Test
    fun theFactoryHandsOutOnlyWhatIsWiredAndNeverFakesIt() {
        val backend = object : TtsBackend {
            override val compute = com.megathon.incident.testing.Compute.CPU
            override val offline: Boolean? = true
            override fun label(language: String) = "x"
            override suspend fun unavailableReason(language: String): String? = null
            override suspend fun speak(text: String, language: String) = Unit
            override fun stop() = Unit
            override fun shutdown() = Unit
        }
        val f = com.megathon.incident.engine.EngineFactory(isDebug = false, ttsProviders = mapOf(TtsKind.ANDROID to { backend }))
        kotlin.test.assertSame(backend, f.tts(TtsKind.ANDROID))
        kotlin.test.assertFailsWith<com.megathon.incident.engine.EngineUnavailableException> { f.tts(TtsKind.PIPER) }
    }

    @Test
    fun unknownValuesAndTheDebugCheckProbeStayAndroid() {
        assertEquals(TtsKind.ANDROID, TtsKind.parse("check"))
        assertEquals(TtsKind.ANDROID, TtsKind.parse("kokoro"))
        assertEquals(TtsKind.ANDROID, TtsKind.parse(""))
    }
}

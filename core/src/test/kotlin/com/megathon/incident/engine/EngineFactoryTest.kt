package com.megathon.incident.engine

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.testing.FakeSpeechEngine
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class EngineFactoryTest {
    private val debug = EngineFactory(isDebug = true)
    private val release = EngineFactory(isDebug = false)

    @Test
    fun defaultSelectionIsFake() = assertIs<EngineSelection>(EngineSelection()).let {
        assertSame(SpeechKind.FAKE, it.speech)
        assertSame(LlmKind.FAKE, it.llm)
    }

    @Test
    fun debugBuildHandsOutFakes() {
        assertIs<FakeSpeechEngine>(debug.speech(SpeechKind.FAKE))
        assertIs<FakeLlmEngine>(debug.llm(LlmKind.FAKE))
    }

    @Test
    fun releaseBuildRefusesSimulatedEngines() {
        assertFailsWith<SimulatedEngineInReleaseException> { release.speech(SpeechKind.FAKE) }
        assertFailsWith<SimulatedEngineInReleaseException> { release.llm(LlmKind.FAKE) }
    }

    @Test
    fun releaseBuildRefusesASimulatedResult() {
        assertFailsWith<SimulatedEngineInReleaseException> { release.checkResult(Compute.SIMULATED) }
    }

    @Test
    fun resultsFromRealHardwareAreAccepted() {
        Compute.values().filter { it != Compute.SIMULATED }.forEach { release.checkResult(it) }
        debug.checkResult(Compute.SIMULATED)
    }

    @Test
    fun realEnginesThatAreNotWiredYetSayWhy() {
        assertFailsWith<EngineUnavailableException> { debug.speech(SpeechKind.SHERPA_QNN) }
        assertFailsWith<EngineUnavailableException> { debug.llm(LlmKind.GENIEX) }
    }

    @Test
    fun aRegisteredRealProviderIsUsedAndAllowedInRelease() {
        val real = object : LlmEngine {
            override val name = "GENIEX"
            override suspend fun complete(request: LlmRequest) = LlmResult("{}", 1, Compute.NPU)
        }
        val factory = EngineFactory(isDebug = false, llmProviders = mapOf(LlmKind.GENIEX to { real }))
        assertSame(real, factory.llm(LlmKind.GENIEX))
    }
}

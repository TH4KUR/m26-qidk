package com.megathon.incident.testing

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.signals.CompositeSignalSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SignalSourcesTest {
    private val alarm = Signal("event", "fire alarm", Source.YAMNET)
    private val angry = Signal("emotion", "angry", Source.SENSEVOICE)
    private val pcm = shortArrayOf(1, 2, 3)

    private fun source(name: String, compute: Compute, vararg signals: Signal) = object : SignalSource {
        override val name = name
        override val compute = compute
        override suspend fun signals(pcm: ShortArray) = signals.toList()
    }

    @Test
    fun theFakeSignalSourceIsLabelledSimulated() {
        val fake = FakeSignalSource(mapOf("clip" to listOf(alarm)), clipId = { "clip" })
        assertEquals(Compute.SIMULATED, fake.compute)
        assertEquals("FAKE", fake.name)
        assertEquals(listOf(alarm), SpeechEngineContractTestRunner.run { fake.signals(pcm) })
    }

    @Test
    fun anUnknownClipHasNoSignals() {
        val fake = FakeSignalSource(emptyMap())
        assertEquals(emptyList(), SpeechEngineContractTestRunner.run { fake.signals(pcm) })
    }

    @Test
    fun aCompositeMergesSourcesAndDropsDuplicateSignals() {
        val composite = CompositeSignalSource(
            listOf(source("a", Compute.NPU, alarm), source("b", Compute.NPU, alarm, angry)),
        )
        assertEquals(listOf(alarm, angry), SpeechEngineContractTestRunner.run { composite.signals(pcm) })
    }

    @Test
    fun aCompositeOfOneComputeReportsThatCompute() {
        val composite = CompositeSignalSource(listOf(source("a", Compute.CPU), source("b", Compute.CPU)))
        assertEquals(Compute.CPU, composite.compute)
    }

    @Test
    fun aCompositeOfMixedComputeIsHybridSoTheProofStripDoesNotOverclaim() {
        val composite = CompositeSignalSource(listOf(source("a", Compute.NPU), source("b", Compute.CPU)))
        assertEquals(Compute.HYBRID, composite.compute)
    }

    @Test
    fun anySimulatedPartMakesTheWholeCompositeSimulated() {
        val composite = CompositeSignalSource(listOf(source("a", Compute.NPU), source("b", Compute.SIMULATED)))
        assertEquals(Compute.SIMULATED, composite.compute)
    }

    @Test
    fun aSourceThatFailsDoesNotLoseTheOthersSignals() {
        val broken = object : SignalSource {
            override val name = "broken"
            override val compute = Compute.CPU
            override suspend fun signals(pcm: ShortArray): List<Signal> = error("model not loaded")
        }
        val composite = CompositeSignalSource(listOf(broken, source("ok", Compute.CPU, alarm)))
        assertEquals(listOf(alarm), SpeechEngineContractTestRunner.run { composite.signals(pcm) })
        assertEquals(listOf("broken: model not loaded"), composite.failures)
    }
}

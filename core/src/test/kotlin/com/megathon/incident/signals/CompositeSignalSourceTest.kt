package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SignalSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositeSignalSourceTest {
    private val pcm = shortArrayOf(1, 2, 3)

    private fun slow(name: String, ms: Long, signal: Signal) = object : SignalSource {
        override val name = name
        override val compute = Compute.CPU
        override suspend fun signals(pcm: ShortArray): List<Signal> {
            delay(ms)
            return listOf(signal)
        }
    }

    @Test
    fun independentSourcesRunAtTheSameTimeSoLatencyIsTheMaxNotTheSum() = runBlocking {
        val a = Signal("event", "fire alarm", Source.YAMNET)
        val b = Signal("emotion", "angry", Source.SENSEVOICE)
        val composite = CompositeSignalSource(listOf(slow("a", 300, a), slow("b", 300, b)))

        val startedAt = System.nanoTime()
        val signals = composite.signals(pcm)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(listOf(a, b), signals, "results keep the order of the sources")
        assertTrue(elapsedMs < 500, "took $elapsedMs ms; sequential would be about 600")
    }

    @Test
    fun aNativeErrorInOneSourceCostsOnlyThatSource() = runBlocking {
        val ok = Signal("event", "siren", Source.YAMNET)
        val broken = object : SignalSource {
            override val name = "native"
            override val compute = Compute.NPU
            override suspend fun signals(pcm: ShortArray): List<Signal> = throw UnsatisfiedLinkError("libtflite.so")
        }
        val composite = CompositeSignalSource(listOf(broken, slow("ok", 1, ok)))

        assertEquals(listOf(ok), composite.signals(pcm))
        assertEquals(listOf("native: libtflite.so"), composite.failures)
    }

    @Test
    fun theFailureListKeepsOnlyTheMostRecentEntries() = runBlocking {
        val failing = object : SignalSource {
            override val name = "failing"
            override val compute = Compute.CPU
            override suspend fun signals(pcm: ShortArray): List<Signal> = error("model not loaded")
        }
        val composite = CompositeSignalSource(listOf(failing))
        repeat(30) { composite.signals(pcm) }

        assertEquals(20, composite.failures.size)
    }
}

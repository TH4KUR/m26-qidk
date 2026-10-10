package com.megathon.incident.signals

import com.megathon.incident.domain.Source
import com.megathon.incident.testing.Compute
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The glue between the front end, a model runner and the mappers. The real runners (ONNX Runtime, sherpa-onnx) live in `:app`. */
class ModelSignalSourcesTest {
    private val classNames = List(521) { "class $it" }.toMutableList().also {
        it[0] = "Speech"; it[394] = "Fire alarm"; it[390] = "Siren"
    }

    private fun threeSeconds(): ShortArray = ShortArray(48_000) { (8000 * sin(2 * PI * 880 * it / 16_000)).toInt().toShort() }

    private fun logitsFor(alarm: Float, speech: Float = -9f) = FloatArray(521) { -20f }.also { it[394] = alarm; it[0] = speech }

    @Test
    fun yamNetSourceTurnsWindowsOfAudioIntoHazardSignals() = runBlocking {
        val calls = AtomicInteger()
        val source = YamNetSignalSource(YamNetModel { patch ->
            assertEquals(96 * 64, patch.size)
            calls.incrementAndGet()
            logitsFor(alarm = 8f)
        }, classNames, Compute.CPU)

        val signals = source.signals(threeSeconds())

        assertEquals(listOf("fire alarm"), signals.map { it.label })
        assertEquals(Source.YAMNET, signals.single().source)
        // 3 s is 298 frames: patches start at 0, 48, ..., 192 (a patch needs 96 frames) = 5 windows.
        assertEquals(5, calls.get())
        assertEquals("YamNet", source.name)
        assertEquals(Compute.CPU, source.compute)
    }

    @Test
    fun theStrongestWindowDecidesNotTheAverage() = runBlocking {
        val window = AtomicInteger()
        val source = YamNetSignalSource(YamNetModel { logitsFor(alarm = if (window.getAndIncrement() == 3) 9f else -12f) }, classNames, Compute.CPU)
        assertEquals(listOf("fire alarm"), source.signals(threeSeconds()).map { it.label })
    }

    @Test
    fun nothingHazardousMeansNoSignals() = runBlocking {
        val source = YamNetSignalSource(YamNetModel { logitsFor(alarm = -12f, speech = 6f) }, classNames, Compute.CPU)
        assertTrue(source.signals(threeSeconds()).isEmpty())
    }

    @Test
    fun emptyAudioNeverReachesTheModel() = runBlocking {
        val calls = AtomicInteger()
        val source = YamNetSignalSource(YamNetModel { calls.incrementAndGet(); logitsFor(9f) }, classNames, Compute.CPU)
        assertTrue(source.signals(ShortArray(0)).isEmpty())
        assertEquals(0, calls.get())
    }

    @Test
    fun theModelRunsOffTheCallersThread() = runBlocking {
        val threads = CopyOnWriteArrayList<String>()
        val source = YamNetSignalSource(YamNetModel { threads += Thread.currentThread().name; logitsFor(-12f) }, classNames, Compute.CPU)
        source.signals(threeSeconds())
        assertTrue(threads.isNotEmpty() && threads.none { it == Thread.currentThread().name }, "ran on: $threads")
    }

    @Test
    fun aModelWithTheWrongNumberOfClassesIsRejectedNotMisread() {
        val source = YamNetSignalSource(YamNetModel { FloatArray(100) }, classNames, Compute.CPU)
        assertFailsWith<IllegalArgumentException> { runBlocking { source.signals(threeSeconds()) } }
    }

    @Test
    fun senseVoiceSourceMapsEmotionAndEventTags() = runBlocking {
        val source = SenseVoiceSignalSource(
            SenseVoiceRecognizer { SenseVoiceResult("help", "<|en|>", "<|SAD|>", "<|Cry|>") },
            Compute.NPU,
        )
        val signals = source.signals(threeSeconds())
        assertEquals(listOf("emotion" to "sad", "event" to "crying"), signals.map { it.kind to it.label })
        assertTrue(signals.all { it.source == Source.SENSEVOICE })
        assertEquals("SenseVoice", source.name)
        assertEquals(Compute.NPU, source.compute)
    }

    @Test
    fun theUsualUnknownEmotionAndPlainSpeechGiveNoSignals() = runBlocking {
        val source = SenseVoiceSignalSource(
            SenseVoiceRecognizer { SenseVoiceResult("it is eleven o'clock", "<|en|>", "<|EMO_UNKNOWN|>", "<|Speech|>") },
            Compute.CPU,
        )
        assertTrue(source.signals(threeSeconds()).isEmpty())
    }

    @Test
    fun senseVoiceSkipsEmptyAudioAndRunsOffTheCallersThread() = runBlocking {
        val threads = CopyOnWriteArrayList<String>()
        val source = SenseVoiceSignalSource(
            SenseVoiceRecognizer { threads += Thread.currentThread().name; SenseVoiceResult("", "", "", "") },
            Compute.CPU,
        )
        assertTrue(source.signals(ShortArray(0)).isEmpty())
        assertTrue(threads.isEmpty(), "empty audio must not reach the recognizer")
        source.signals(threeSeconds())
        assertTrue(threads.none { it == Thread.currentThread().name }, "ran on: $threads")
    }
}

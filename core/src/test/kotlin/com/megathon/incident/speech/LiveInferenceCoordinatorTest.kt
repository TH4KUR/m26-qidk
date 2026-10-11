package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.testing.Transcript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RATE = 16_000

/** Replies with the scripted texts in call order, optionally holding every call until [gate] opens. */
private class ScriptedEngine(private val script: List<String>, var gate: CompletableDeferred<Unit>? = null, private val failCalls: Set<Int> = emptySet()) : SpeechEngine {
    override val name = "scripted"
    val calls = CopyOnWriteArrayList<ShortArray>()
    @Volatile var running = 0
    @Volatile var maxRunning = 0

    override suspend fun transcribe(pcm: ShortArray, language: String): Transcript {
        val index = calls.size
        calls += pcm.copyOf()
        running++; maxRunning = maxOf(maxRunning, running)
        try {
            gate?.await()
            if (index in failCalls) error("engine failed")
            return Transcript(script.getOrElse(index) { "" }, language, 1, Compute.SIMULATED)
        } finally {
            running--
        }
    }
}

class LiveInferenceCoordinatorTest {
    private fun quiet(ms: Int) = ShortArray(RATE * ms / 1000) { if (it % 2 == 0) 20 else -20 }
    private fun speech(ms: Int) = ShortArray(RATE * ms / 1000) { if (it / 20 % 2 == 0) 3000 else -3000 }

    /** Feeds 100 ms chunks through ONE reused buffer, like the microphone read loop does. */
    private fun feed(c: LiveInferenceCoordinator, pcm: ShortArray) {
        val buffer = ShortArray(RATE / 10)
        for (at in pcm.indices step buffer.size) {
            val n = minOf(buffer.size, pcm.size - at)
            pcm.copyInto(buffer, 0, at, at + n)
            c.offer(buffer, n)
        }
        buffer.fill(0)
    }

    private suspend fun until(what: String, condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) yield()
    }

    private fun CoroutineScope.start(c: LiveInferenceCoordinator): Job = launch { c.run() }

    @Test
    fun continuousSpeechWithoutAPausePreviewsBeforeStop() = runBlocking {
        val engine = ScriptedEngine(listOf("one two three four five six", "five six seven eight nine ten", "nine ten eleven twelve"))
        val snapshots = CopyOnWriteArrayList<LiveInferenceCoordinator.Snapshot>()
        val c = LiveInferenceCoordinator(engine, "en", onSnapshot = { snapshots += it })
        val worker = start(c)
        feed(c, quiet(300) + speech(6_000))
        until("first preview") { snapshots.isNotEmpty() }
        assertTrue(snapshots.last().stable.startsWith("one two"), snapshots.last().toString())
        feed(c, speech(6_000))
        until("second preview") { snapshots.size >= 2 }
        val done = c.finish()
        worker.join()
        assertTrue(engine.calls.all { it.size <= 6 * RATE }, "no window may exceed the cap")
        assertEquals("one two three four five six seven eight nine ten eleven twelve", done.text)
        assertFalse(done.fullPass)
    }

    @Test
    fun theFirstWindowStartsAtTheVeryFirstSample() = runBlocking {
        val engine = ScriptedEngine(listOf("hello"))
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        val soft = ShortArray(RATE / 2) { 90 } // a soft first word, below the speech threshold
        feed(c, soft + speech(4_000))
        until("window") { engine.calls.isNotEmpty() }
        assertContentEquals(soft, engine.calls.first().copyOfRange(0, soft.size))
        c.cancel(); worker.join()
    }

    @Test
    fun aReusedMicrophoneBufferCannotChangeQueuedAudio() = runBlocking {
        val engine = ScriptedEngine(listOf("hello"), gate = CompletableDeferred())
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        val pcm = quiet(300) + speech(4_000)
        feed(c, pcm) // the helper zeroes the shared buffer afterwards
        until("window") { engine.calls.isNotEmpty() }
        assertContentEquals(pcm.copyOfRange(0, engine.calls.first().size), engine.calls.first())
        engine.gate!!.complete(Unit)
        c.cancel(); worker.join()
    }

    @Test
    fun stopDecodesTheRemainingTailAndTheRecordingIsReturnedOnce() = runBlocking {
        val engine = ScriptedEngine(listOf("the pump is leaking in room", "in room three"))
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        val first = quiet(300) + speech(4_000)
        val tail = speech(1_200) // shorter than the preview trigger
        val pcm = first + tail
        feed(c, first)
        until("preview") { engine.calls.size == 1 }
        feed(c, tail)
        val done = c.finish()
        worker.join()
        assertEquals("the pump is leaking in room three", done.text)
        assertEquals(pcm.size, done.pcm.size)
        assertEquals(2, engine.calls.size)
        val again = c.finish()
        assertEquals(2, engine.calls.size, "finishing twice must not decode again")
        assertEquals(done.text, again.text)
    }

    @Test
    fun anUnmatchedSharedRegionMakesStopDecodeTheWholeRecordingOnce() = runBlocking {
        val engine = ScriptedEngine(listOf("a fire in lab one", "fine at lab won there is smoke", "a fire in lab one and there is smoke"))
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        feed(c, quiet(300) + speech(5_000))
        until("first") { engine.calls.size == 1 }
        feed(c, speech(4_000))
        until("second") { engine.calls.size == 2 }
        val done = c.finish()
        worker.join()
        assertTrue(done.fullPass)
        assertEquals("a fire in lab one and there is smoke", done.text)
        assertEquals(9_300 * RATE / 1000, engine.calls.last().size)
    }

    @Test
    fun aFailedPreviewDoesNotBreakTheRecordingAndStopRecoversWithAFullPass() = runBlocking {
        val engine = ScriptedEngine(listOf("", "everything said"), failCalls = setOf(0))
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        feed(c, quiet(300) + speech(4_000))
        until("failing preview") { engine.calls.isNotEmpty() }
        val done = c.finish()
        worker.join()
        assertTrue(done.fullPass)
        assertEquals("everything said", done.text)
    }

    @Test
    fun silenceNeverCallsTheEngine() = runBlocking {
        val engine = ScriptedEngine(listOf("invented"))
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        feed(c, quiet(5_000))
        val done = c.finish()
        worker.join()
        assertEquals(0, engine.calls.size)
        assertEquals("", done.text)
    }

    @Test
    fun onlyOneInferenceRunsAtATimeAndSpeculativeWorkCoalesces() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val engine = ScriptedEngine(listOf("one two three", "three four five six", "six seven"), gate = gate)
        val lane = Mutex()
        val c = LiveInferenceCoordinator(engine, "en", lane = lane)
        val worker = start(c)
        feed(c, quiet(300) + speech(4_000))
        until("first call") { engine.calls.size == 1 }
        feed(c, speech(12_000)) // many triggers while the accelerator is busy
        gate.complete(Unit)
        val done = c.finish()
        worker.join()
        assertEquals(1, engine.maxRunning)
        assertTrue(done.fullPass, "a backlog beyond one window is decoded as one whole-recording pass")
        assertTrue(engine.calls.size <= 3, "queued previews must not pile up: ${engine.calls.size}")
    }

    @Test
    fun theThirtySecondCapKeepsExactlyThirtySeconds() = runBlocking {
        val engine = ScriptedEngine(emptyList())
        val c = LiveInferenceCoordinator(engine, "en")
        val worker = start(c)
        feed(c, quiet(300) + speech(32_000))
        val done = c.finish()
        worker.join()
        assertEquals(30 * RATE, done.pcm.size)
    }

    @Test
    fun cancelDiscardsEverythingAndEmitsNothingMore() = runBlocking {
        val engine = ScriptedEngine(listOf("hello there"), gate = CompletableDeferred())
        val snapshots = CopyOnWriteArrayList<LiveInferenceCoordinator.Snapshot>()
        val c = LiveInferenceCoordinator(engine, "en", onSnapshot = { snapshots += it })
        val worker = start(c)
        feed(c, quiet(300) + speech(4_000))
        until("call") { engine.calls.isNotEmpty() }
        c.cancel()
        engine.gate!!.complete(Unit)
        worker.join()
        yield()
        assertTrue(snapshots.isEmpty())
    }
}

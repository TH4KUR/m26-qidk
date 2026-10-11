package com.megathon.incident.speech

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A worker stand-in: [handler] maps each request line to the reply line (or null for silence). */
private class FakeChannel(
    private val ready: String? = """{"v":1,"ready":true,"init_ms":900}""",
    private val log: String = "",
    private val handler: (String) -> String?,
) : WorkerChannel {
    private val lines = LinkedBlockingQueue<String>()
    val sent = CopyOnWriteArrayList<String>()
    @Volatile var destroyed = false

    init { ready?.let(lines::put) }

    override fun send(line: String) {
        sent += line
        if (destroyed) return
        handler(line)?.let(lines::put)
    }

    override fun readLine(): String? = lines.take().takeIf { it != EOF }

    override fun sawLog(marker: String) = log.contains(marker)

    override fun destroy() { destroyed = true; lines.put(EOF) }

    fun die() = lines.put(EOF)

    private companion object { const val EOF = "\u0000EOF" }
}

private fun idOf(request: String) = Regex("\"id\":(\\d+)").find(request)!!.groupValues[1].toInt()
private fun ok(request: String, text: String = "hello") = """{"v":1,"id":${idOf(request)},"text":"$text","decode_ms":120}"""

class ResidentWorkerClientTest {
    private fun client(timeoutMs: Long = 2_000, startupMs: Long = 2_000, make: (Int) -> FakeChannel): Pair<ResidentWorkerClient, MutableList<FakeChannel>> {
        val made = mutableListOf<FakeChannel>()
        val c = ResidentWorkerClient({ make(made.size).also { made += it } }, requestTimeoutMs = timeoutMs, startupTimeoutMs = startupMs)
        return c to made
    }

    @Test
    fun twoSequentialCallsShareOneWorkerAndRecognizer() = runBlocking {
        val (c, made) = client { FakeChannel(handler = ::ok) }
        assertEquals("hello", c.transcribe("/a.wav", "en").text)
        assertEquals("hello", c.transcribe("/b.wav", "en").text)
        assertEquals(1, made.size, "one worker, so one recognizer creation")
        assertEquals(1, c.launches)
        c.close()
    }

    @Test
    fun everyRequestCarriesItsOwnIncreasingIdAndThePath() = runBlocking {
        val (c, made) = client { FakeChannel(handler = ::ok) }
        c.transcribe("/a.wav", "en"); c.transcribe("/b.wav", "en")
        val ids = made.single().sent.map(::idOf)
        assertEquals(listOf(1, 2), ids)
        assertTrue("/b.wav" in made.single().sent[1] && "\"cmd\":\"transcribe\"" in made.single().sent[1])
        c.close()
    }

    @Test
    fun aReplyWithTheWrongIdFailsAndTheWorkerIsReplaced() = runBlocking {
        val (c, made) = client { n -> FakeChannel { if (n == 0) """{"v":1,"id":${idOf(it) + 5},"text":"x","decode_ms":1}""" else ok(it, "second") } }
        assertFailsWith<WorkerException> { c.transcribe("/a.wav", "en") }
        assertTrue(made[0].destroyed)
        assertEquals("second", c.transcribe("/b.wav", "en").text)
        assertEquals(2, made.size)
        c.close()
    }

    @Test
    fun aMalformedReplyFailsVisibly() = runBlocking {
        val (c, made) = client { FakeChannel { "this is not json" } }
        val e = assertFailsWith<WorkerException> { c.transcribe("/a.wav", "en") }
        assertTrue("malformed" in e.message.orEmpty(), e.message)
        assertTrue(made.single().destroyed)
    }

    @Test
    fun aWorkerThatDiesFailsTheCallAndTheNextCallRecovers() = runBlocking {
        val (c, made) = client { n -> FakeChannel { if (n == 0) { null } else ok(it, "again") }.also { if (n == 0) Thread { Thread.sleep(50); it.die() }.start() } }
        val e = assertFailsWith<WorkerException> { c.transcribe("/a.wav", "en") }
        assertTrue("ended" in e.message.orEmpty(), e.message)
        assertEquals("again", c.transcribe("/b.wav", "en").text)
        assertEquals(2, made.size)
        c.close()
    }

    @Test
    fun aTimeoutDropsTheWorkerSoALateReplyCannotAnswerAnotherRequest() = runBlocking {
        val (c, made) = client(timeoutMs = 150) { n -> FakeChannel { if (n == 0) null else ok(it, "fresh") } }
        val e = assertFailsWith<WorkerException> { c.transcribe("/slow.wav", "en") }
        assertTrue("timed out" in e.message.orEmpty(), e.message)
        assertTrue(made[0].destroyed)
        assertEquals("fresh", c.transcribe("/next.wav", "en").text)
        assertEquals(2, made.size)
        c.close()
    }

    @Test
    fun aRequestLevelErrorKeepsTheWorkerAlive() = runBlocking {
        val (c, made) = client { FakeChannel { """{"v":1,"id":${idOf(it)},"error":"cannot read /a.wav"}""".takeIf { _ -> "/a.wav" in it } ?: ok(it) } }
        val e = assertFailsWith<WorkerException> { c.transcribe("/a.wav", "en") }
        assertTrue("cannot read" in e.message.orEmpty())
        assertEquals("hello", c.transcribe("/b.wav", "en").text)
        assertEquals(1, made.size, "an unreadable file is not a reason to reload the models")
        c.close()
    }

    @Test
    fun aWorkerThatFailsToStartReportsWhyAndIsRetriedLater() = runBlocking {
        val (c, made) = client { n -> FakeChannel(ready = if (n == 0) """{"v":1,"ready":false,"error":"no context binary"}""" else """{"v":1,"ready":true,"init_ms":5}""", handler = ::ok) }
        val e = assertFailsWith<WorkerException> { c.transcribe("/a.wav", "en") }
        assertTrue("no context binary" in e.message.orEmpty())
        assertEquals("hello", c.transcribe("/a.wav", "en").text)
        assertEquals(2, made.size)
        c.close()
    }

    @Test
    fun aWorkerThatNeverSaysReadyTimesOutAtStartup() = runBlocking {
        val (c, made) = client(startupMs = 150) { FakeChannel(ready = null, handler = ::ok) }
        val e = assertFailsWith<WorkerException> { c.transcribe("/a.wav", "en") }
        assertTrue("start" in e.message.orEmpty(), e.message)
        assertTrue(made.single().destroyed)
    }

    @Test
    fun concurrentCallsAreSerialized() = runBlocking {
        val inFlight = AtomicInteger()
        val most = AtomicInteger()
        val (c, _) = client { FakeChannel {
            val now = inFlight.incrementAndGet(); most.updateAndGet { m -> maxOf(m, now) }
            Thread.sleep(20); inFlight.decrementAndGet(); ok(it)
        } }
        (1..4).map { async(kotlinx.coroutines.Dispatchers.Default) { c.transcribe("/$it.wav", "en") } }.awaitAll()
        assertEquals(1, most.get())
        c.close()
    }

    @Test
    fun npuIsReportedOnlyWhenTheWorkerLogShowsTheHexagonProvider() = runBlocking {
        val (withLog, _) = client { FakeChannel(log = "created HtpProvider ok", handler = ::ok) }
        val (without, _) = client { FakeChannel(log = "created CpuProvider", handler = ::ok) }
        assertTrue(withLog.transcribe("/a.wav", "en").hexagonEvidence)
        assertFalse(without.transcribe("/a.wav", "en").hexagonEvidence)
        withLog.close(); without.close()
    }

    @Test
    fun closeAsksTheWorkerToQuitAndReleasesIt() = runBlocking {
        val (c, made) = client { FakeChannel(handler = ::ok) }
        c.transcribe("/a.wav", "en")
        c.close()
        assertTrue(made.single().sent.last().contains("\"cmd\":\"quit\""))
        assertTrue(made.single().destroyed)
    }
}

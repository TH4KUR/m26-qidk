package com.megathon.incident.speech

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PacedReplayTest {
    private class FakeClock {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        suspend fun sleep(ms: Long) { sleeps += ms; now += ms }
    }

    private fun pcm(ms: Int) = ShortArray(16 * ms) { (it % 1000).toShort() }

    @Test
    fun chunksArriveAtRealTimeSpacing() = runBlocking {
        val clock = FakeClock()
        PacedReplay.feed(pcm(1_000), chunkMs = 100, speed = 1.0, now = { clock.now }, sleep = clock::sleep) { _, _ -> }
        assertEquals(9, clock.sleeps.size, "ten chunks, the first is due at once")
        assertTrue(clock.sleeps.all { it == 100L })
    }

    @Test
    fun speedScalesTheSpacing() = runBlocking {
        val clock = FakeClock()
        PacedReplay.feed(pcm(1_000), chunkMs = 100, speed = 2.0, now = { clock.now }, sleep = clock::sleep) { _, _ -> }
        assertEquals(450L, clock.sleeps.sum())
    }

    @Test
    fun everySampleIsDeliveredOnceInOrderThroughOneReusedBuffer() = runBlocking {
        val clock = FakeClock()
        val source = pcm(1_050)
        val seen = mutableListOf<Short>()
        var buffer: ShortArray? = null
        var reused = true
        PacedReplay.feed(source, chunkMs = 100, now = { clock.now }, sleep = clock::sleep) { chunk, n ->
            if (buffer == null) buffer = chunk else if (buffer !== chunk) reused = false
            seen += chunk.take(n)
        }
        assertEquals(source.toList(), seen)
        assertTrue(reused, "the microphone reuses its read buffer, so the replay must too")
        assertSame(buffer, buffer)
    }

    @Test
    fun timeSpentInTheSinkCountsAgainstTheSchedule() = runBlocking {
        val clock = FakeClock()
        PacedReplay.feed(pcm(500), chunkMs = 100, now = { clock.now }, sleep = clock::sleep) { _, _ -> clock.now += 30 }
        assertTrue(clock.sleeps.all { it == 70L }, clock.sleeps.toString())
    }
}

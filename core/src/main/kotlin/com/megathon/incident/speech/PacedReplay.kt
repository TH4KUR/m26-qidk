package com.megathon.incident.speech

import kotlinx.coroutines.delay

/**
 * Plays recorded PCM into a consumer at the pace it was spoken, the way the microphone read loop delivers it: fixed-size chunks
 * through ONE reused buffer. For comparing batch and live processing on the same clip without anyone speaking; an instant file
 * feed would not show what incremental decoding buys. [speed] above 1 compresses time (a debugging aid, not for measurements).
 */
object PacedReplay {
    suspend fun feed(
        pcm: ShortArray,
        sampleRate: Int = 16_000,
        chunkMs: Int = 100,
        speed: Double = 1.0,
        now: () -> Long = { System.nanoTime() / 1_000_000 },
        sleep: suspend (Long) -> Unit = { delay(it) },
        sink: (ShortArray, Int) -> Unit,
    ) {
        require(speed > 0.0 && chunkMs > 0) { "speed and chunkMs must be positive" }
        val chunk = ShortArray(sampleRate * chunkMs / 1000)
        val started = now()
        var index = 0
        var at = 0
        while (at < pcm.size) {
            val wait = started + (index * chunkMs / speed).toLong() - now() // an absolute schedule: slow work in the sink is not added to every wait
            if (wait > 0) sleep(wait)
            val n = minOf(chunk.size, pcm.size - at)
            pcm.copyInto(chunk, 0, at, at + n)
            sink(chunk, n)
            at += n
            index++
        }
    }
}

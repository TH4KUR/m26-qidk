package com.megathon.incident.testing

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SpeechEngineContractTest {
    private val sample = shortArrayOf(1, 2, 3)
    private val silence = ShortArray(160)

    @Test
    fun speechSampleProducesNonEmptySimulatedTranscript() {
        val engine = FakeSpeechEngine(
            scriptedTexts = mapOf("sample" to "Fire alarm heard in room two."),
            clipId = { "sample" },
        )

        val transcript = runSuspend { engine.transcribe(sample, "en") }

        assertTrue(transcript.text.isNotBlank())
        assertEquals("en", transcript.language)
        assertEquals(Compute.SIMULATED, transcript.compute)
        assertTrue(transcript.elapsedMs >= 0)
    }

    @Test
    fun silenceProducesEmptyTranscript() {
        val engine = FakeSpeechEngine(defaultText = "This must not be returned for silence.")

        val transcript = runSuspend { engine.transcribe(silence, "en") }

        assertEquals("", transcript.text)
    }

    @Test
    fun requestedLanguageIsPreserved() {
        val engine = FakeSpeechEngine(defaultText = "नमस्ते")

        val transcript = runSuspend { engine.transcribe(sample, "hi") }

        assertEquals("hi", transcript.language)
        assertEquals(listOf("hi"), engine.languageRequests)
    }

    @Test
    fun delayedFakeSuspendsWithoutBlockingCallerThread() {
        val engine = FakeSpeechEngine(defaultText = "sample", delayMs = 100)
        val completion = AtomicReference<Result<Transcript>?>(null)
        val done = CountDownLatch(1)
        val startedAt = System.nanoTime()
        val operation: suspend () -> Transcript = {
            engine.transcribe(sample, "en")
        }

        operation.startCoroutine(object : Continuation<Transcript> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Transcript>) {
                completion.set(result)
                done.countDown()
            }
        })

        val callerElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        assertTrue(callerElapsedMs < 80, "caller was blocked for ${callerElapsedMs} ms")
        assertTrue(done.await(1, TimeUnit.SECONDS))
        assertNotNull(completion.get())
        assertFalse(completion.get()!!.isFailure)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        val result = AtomicReference<Result<T>?>(null)
        val done = CountDownLatch(1)

        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(value: Result<T>) {
                result.set(value)
                done.countDown()
            }
        })

        check(done.await(1, TimeUnit.SECONDS)) { "suspend operation did not complete" }
        return result.get()!!.getOrThrow()
    }
}

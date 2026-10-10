package com.megathon.incident.testing

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal object SpeechEngineContractTestRunner {
    /** Runs a short suspend block; it must finish within a second. */
    fun <T> run(block: suspend () -> T): T = run(1, block)

    /** For the contract suite, which makes several calls and may wait on a deliberately slow engine. */
    fun <T> runLong(block: suspend () -> T): T = run(30, block)

    private fun <T> run(timeoutSeconds: Long, block: suspend () -> T): T {
        val result = AtomicReference<Result<T>?>(null)
        val done = CountDownLatch(1)

        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(value: Result<T>) {
                result.set(value)
                done.countDown()
            }
        })

        check(done.await(timeoutSeconds, TimeUnit.SECONDS)) { "suspend operation did not complete" }
        return result.get()!!.getOrThrow()
    }
}

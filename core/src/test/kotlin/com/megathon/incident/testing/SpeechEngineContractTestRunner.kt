package com.megathon.incident.testing

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal object SpeechEngineContractTestRunner {
    fun <T> run(block: suspend () -> T): T {
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

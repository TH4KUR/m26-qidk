package com.megathon.incident.speech

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** Marks work done only to show progress early (previews). It is skipped, not run, once the reporter has tapped Stop. */
object Speculative : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<Speculative>
}

/**
 * The one lane every accelerator call goes through (speech, language model, voice tags, warm-up). Separate processes do not
 * mean separate NPUs, and one GenieX instance must never run two generations at once, so calls take turns here.
 * [stopSpeculation] (Stop was tapped) makes queued [Speculative] work give up when its turn comes; a call that is already
 * running is never interrupted, because a native call cannot be cancelled underneath itself.
 */
class AcceleratorLane {
    private val mutex = Mutex()

    @Volatile
    private var speculation = true

    /** The raw lock, for components that take a lane [Mutex] directly (the ASR coordinator). Prefer [run]. */
    val lock: Mutex get() = mutex

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        if (coroutineContext[Speculative.Key] != null && !speculation) throw CancellationException("speculative work skipped after Stop")
        block()
    }

    fun stopSpeculation() { speculation = false }

    fun startSpeculation() { speculation = true }
}

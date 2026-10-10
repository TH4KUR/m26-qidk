package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SignalSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs several sources over the same audio at the same time (YamNet for sounds, SenseVoice for voice), so the
 * latency is the slowest source, not the sum. A source that fails (an `Error` from a native library included)
 * costs only its own signals; the most recent failures are kept in [failures] for the proof strip and the log.
 * The reported compute never overclaims: mixed means HYBRID, and any simulated part makes it SIMULATED.
 */
class CompositeSignalSource(private val sources: List<SignalSource>) : SignalSource {
    override val name: String = sources.joinToString("+") { it.name }

    override val compute: Compute = when {
        sources.any { it.compute == Compute.SIMULATED } -> Compute.SIMULATED
        sources.map { it.compute }.distinct().size > 1 -> Compute.HYBRID
        else -> sources.firstOrNull()?.compute ?: Compute.CPU
    }

    private val failed = ArrayDeque<String>()

    val failures: List<String> get() = synchronized(failed) { failed.toList() }

    override suspend fun signals(pcm: ShortArray): List<Signal> = coroutineScope {
        sources.map { source -> async { signalsOf(source, pcm) } }.awaitAll().flatten().distinct()
    }

    private suspend fun signalsOf(source: SignalSource, pcm: ShortArray): List<Signal> =
        try {
            source.signals(pcm)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            synchronized(failed) {
                failed += "${source.name}: ${error.message}"
                while (failed.size > MAX_FAILURES) failed.removeFirst()
            }
            emptyList()
        }

    private companion object {
        const val MAX_FAILURES = 20
    }
}

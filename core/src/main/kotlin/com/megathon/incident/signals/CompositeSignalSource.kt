package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.SignalSource
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs several sources over the same audio (YamNet for sounds, SenseVoice for voice). A source that fails
 * costs only its own signals, and the failure is kept in [failures] for the proof strip and the log.
 * The reported compute never overclaims: mixed means HYBRID, and any simulated part makes it SIMULATED.
 */
class CompositeSignalSource(private val sources: List<SignalSource>) : SignalSource {
    override val name: String = sources.joinToString("+") { it.name }

    override val compute: Compute = when {
        sources.any { it.compute == Compute.SIMULATED } -> Compute.SIMULATED
        sources.map { it.compute }.distinct().size > 1 -> Compute.HYBRID
        else -> sources.firstOrNull()?.compute ?: Compute.CPU
    }

    private val failed = CopyOnWriteArrayList<String>()

    val failures: List<String> get() = failed.toList()

    override suspend fun signals(pcm: ShortArray): List<Signal> =
        sources.flatMap { source ->
            try {
                source.signals(pcm)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed += "${source.name}: ${error.message}"
                emptyList()
            }
        }.distinct()
}

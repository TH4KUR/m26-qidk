package com.megathon.incident.testing

import com.megathon.incident.domain.Signal

/**
 * Returns canned signals by clip id. The Signal model itself is owned by C.
 */
class FakeSignalSource(
    private val cannedByClipId: Map<String, List<Signal>>,
    private val clipId: (ShortArray) -> String = { fingerprint(it) },
) : SignalSource {
    override val name: String = "FAKE"
    override val compute: Compute = Compute.SIMULATED

    override suspend fun signals(pcm: ShortArray): List<Signal> =
        cannedByClipId[clipId(pcm)].orEmpty().toList()

    private companion object {
        fun fingerprint(pcm: ShortArray): String =
            "${pcm.size}:${pcm.contentHashCode()}"
    }
}

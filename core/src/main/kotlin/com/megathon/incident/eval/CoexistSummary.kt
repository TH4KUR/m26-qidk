package com.megathon.incident.eval

import kotlinx.serialization.Serializable

/** One iteration of the debug Whisper + LLM coexistence loop. A stage that never ran has a null time. */
@Serializable
data class CoexistLoop(
    val index: Int,
    val sttMs: Long?,
    val llmMs: Long?,
    val totalMs: Long,
    val ok: Boolean,
    val error: String?,
    /** App process PSS (MB) right after the loop. */
    val pssMb: Int,
    /** Exit status of the speech child process; null when unknown (the engine only exposes it on failure). */
    val childExit: Int?,
    val transcript: String?,
)

@Serializable
data class TimingStats(val min: Long, val median: Double, val max: Long)

@Serializable
data class CoexistSummary(
    val n: Int,
    val failures: Int,
    /** Over the loops where that stage ran. */
    val stt: TimingStats?,
    val llm: TimingStats?,
    val total: TimingStats?,
    val pssFirstMb: Int?,
    val pssLastMb: Int?,
    val pssMaxMb: Int?,
    /** Last minus first loop; positive means the process grew over the run. */
    val pssGrowthMb: Int?,
) {
    companion object {
        fun median(values: List<Long>): Double? {
            if (values.isEmpty()) return null
            val s = values.sorted()
            val mid = s.size / 2
            return if (s.size % 2 == 1) s[mid].toDouble() else (s[mid - 1] + s[mid]) / 2.0
        }

        private fun stats(values: List<Long>): TimingStats? =
            median(values)?.let { TimingStats(values.min(), it, values.max()) }

        fun of(loops: List<CoexistLoop>): CoexistSummary {
            val pss = loops.map { it.pssMb }
            return CoexistSummary(
                n = loops.size,
                failures = loops.count { !it.ok },
                stt = stats(loops.mapNotNull { it.sttMs }),
                llm = stats(loops.mapNotNull { it.llmMs }),
                total = stats(loops.map { it.totalMs }),
                pssFirstMb = pss.firstOrNull(),
                pssLastMb = pss.lastOrNull(),
                pssMaxMb = pss.maxOrNull(),
                pssGrowthMb = if (pss.isEmpty()) null else pss.last() - pss.first(),
            )
        }
    }
}

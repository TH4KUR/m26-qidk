package com.megathon.incident.scoring

import com.megathon.incident.session.StageRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One stage of a saved report as it was logged: where it ran and how long it took. No text. */
@Serializable
data class LogStage(val stage: String, val engine: String, val compute: String, val ms: Long? = null)

/**
 * One saved report as one line of the on-device log. Numbers, engine names and compute only: no transcript, no names, no
 * places. [audio] has one entry per spoken turn of the report.
 */
@Serializable
data class SessionLogRecord(
    val v: Int = 1,
    val atEpochMs: Long,
    val reportId: String,
    val stages: List<LogStage> = emptyList(),
    val audio: List<AudioScoreLine> = emptyList(),
) {
    companion object {
        fun of(reportId: String, atEpochMs: Long, stages: List<StageRecord>, scores: List<AudioScore>): SessionLogRecord =
            SessionLogRecord(
                atEpochMs = atEpochMs,
                reportId = reportId,
                stages = stages.map { LogStage(it.stage, it.engine, it.compute.name, it.ms) },
                audio = scores.map { it.toLine() },
            )
    }
}

/** The `sessions.jsonl` line format: one compact JSON object per line. */
object SessionLogCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(record: SessionLogRecord): String = json.encodeToString(SessionLogRecord.serializer(), record)

    /** Every line that reads; blank and damaged lines are skipped, never fatal. */
    fun parse(text: String): List<SessionLogRecord> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { runCatching { json.decodeFromString(SessionLogRecord.serializer(), it) }.getOrNull() }
            .toList()
}

/** The median of one stage's times over every saved report; [medianMs] is null when no time was recorded. */
data class StageMedian(val stage: String, val count: Int, val medianMs: Long?)

data class ComputeCount(val compute: String, val count: Int)

/** One saved report as one line: its audio score (mean over its turns), the middle times of two stages, and where it ran. */
data class RecentRow(
    val reportId: String,
    val atEpochMs: Long,
    val audioScore: Int?,
    val hearMs: Long?,
    val readMs: Long?,
    val compute: String,
)

data class GlobalSummary(
    val reports: Int,
    /** Hear (speech), Read (model), Tags (voice tags), Speak (voice), in that order, whether or not they have times. */
    val stageMedians: List<StageMedian>,
    /** How many stage records ran on each kind of compute, most first. */
    val computeMix: List<ComputeCount>,
    /** Mean of each scored report's mean capture score; null when no report was scored. */
    val averageAudioScore: Double?,
    val scoredReports: Int,
    val recent: List<RecentRow>,
)

/** Aggregates the log over every saved report on the device. Pure. */
object SessionLogAggregator {
    val STAGES = listOf("Hear", "Read", "Tags", "Speak")
    private val COMPUTE_ORDER = listOf("NPU", "CPU", "HYBRID", "SIMULATED")

    fun aggregate(records: List<SessionLogRecord>, lastN: Int = 10): GlobalSummary {
        // The same report saved twice counts once, and the later line wins.
        val unique = LinkedHashMap<String, SessionLogRecord>()
        for (r in records) unique[r.reportId] = r
        val all = unique.values.toList()

        val stages = all.flatMap { it.stages }
        val medians = STAGES.map { name ->
            val times = stages.filter { it.stage == name }.mapNotNull { it.ms }
            StageMedian(name, times.size, median(times))
        }
        val mix = stages.groupingBy { it.compute }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { COMPUTE_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } })
            .map { ComputeCount(it.key, it.value) }

        val perReport = all.mapNotNull { reportScore(it) }
        val recent = all.sortedByDescending { it.atEpochMs }.take(lastN).map { r ->
            RecentRow(
                reportId = r.reportId,
                atEpochMs = r.atEpochMs,
                audioScore = reportScore(r)?.let { Math.round(it).toInt() },
                hearMs = median(r.stages.filter { it.stage == "Hear" }.mapNotNull { it.ms }),
                readMs = median(r.stages.filter { it.stage == "Read" }.mapNotNull { it.ms }),
                compute = r.stages.map { it.compute }.distinct()
                    .sortedBy { COMPUTE_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.joinToString("+"),
            )
        }
        return GlobalSummary(
            reports = all.size,
            stageMedians = medians,
            computeMix = mix,
            averageAudioScore = if (perReport.isEmpty()) null else perReport.average(),
            scoredReports = perReport.size,
            recent = recent,
        )
    }

    /** A report's audio score: the mean of its turns' capture scores; null when none of its turns was measured. */
    private fun reportScore(r: SessionLogRecord): Double? =
        r.audio.mapNotNull { it.capture }.takeIf { it.isNotEmpty() }?.average()

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else Math.round((s[mid - 1] + s[mid]) / 2.0)
    }
}

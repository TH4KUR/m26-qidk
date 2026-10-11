package com.megathon.incident.scoring

import com.megathon.incident.testing.Compute
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The thresholds of the capture-quality score, in one place so the rules shown on screen, the documentation and the
 * arithmetic cannot drift apart. Levels are dBFS (0 is full scale).
 */
object CaptureThresholds {
    const val PEAK_WEIGHT = 25
    const val PEAK_ZERO_DBFS = -48.0   // at or below this the peak term scores 0
    const val PEAK_FULL_DBFS = -24.0   // at or above this it scores full marks

    const val MEAN_WEIGHT = 25
    const val MEAN_ZERO_LOW_DBFS = -60.0
    const val MEAN_FULL_LOW_DBFS = -42.0  // the kit microphone's typical mean for normal speech is about -39
    const val MEAN_FULL_HIGH_DBFS = -12.0
    const val MEAN_ZERO_HIGH_DBFS = -3.0

    const val CLIP_WEIGHT = 20
    const val CLIP_ZERO_FRACTION = 0.01   // 1 % of samples at full scale scores 0
    const val CLIP_LEVEL = 32767          // |sample| at or above this counts as clipped

    const val DURATION_WEIGHT = 15
    const val DURATION_ZERO_S = 0.5
    const val DURATION_FULL_S = 2.0

    const val SILENCE_WEIGHT = 15
    const val SILENCE_FULL_FRACTION = 0.30
    const val SILENCE_ZERO_FRACTION = 0.80
    const val SILENCE_FRAME_MS = 20
    const val SILENCE_BELOW_DBFS = -50.0  // the same floor the microphone level meter uses
}

/**
 * One line of the score table: what was measured, the rule it is judged by, and the points it earned of its weight.
 * [measured] is null (and [valueText] says "not measured") when the clip had no samples.
 */
@Serializable
data class ScoreTerm(
    val id: String,
    val label: String,
    val measured: Double?,
    val valueText: String,
    val rule: String,
    val points: Int,
    val weight: Int,
)

/** The capture-quality part: five terms and their total out of 100, or no total when there was no audio. */
@Serializable
data class CaptureQuality(val terms: List<ScoreTerm>, val total: Int?)

/** What the speech engine reported for the clip. [compute] and [processingMs] are null when the engine did not say. */
data class RecognitionInput(val engine: String?, val compute: Compute?, val processingMs: Long?, val transcript: String)

/**
 * Recognition facts. [confidence] is always "not measured": none of the speech engines in this app returns a confidence
 * number, and one is never estimated here. [realTimeFactor] is audio milliseconds over processing milliseconds, so 5.0
 * means the clip was recognised five times faster than it was spoken.
 */
@Serializable
data class RecognitionFacts(
    val engine: String?,
    val compute: String?,
    val processingMs: Long?,
    val audioMs: Long,
    val realTimeFactor: Double?,
    val words: Int,
    val confidence: String = AudioScore.NOT_MEASURED,
)

/** The numbers a log line keeps: no words, no names, no places. */
@Serializable
data class AudioScoreLine(
    val capture: Int? = null,
    val peakDbfs: Double? = null,
    val meanDbfs: Double? = null,
    val clippedPct: Double? = null,
    val silencePct: Double? = null,
    val durationS: Double? = null,
    val engine: String? = null,
    val compute: String? = null,
    val processingMs: Long? = null,
    val realTimeFactor: Double? = null,
    val words: Int = 0,
    /** The 1..20 priority score the rules give from the words and the voice tags, and from the words alone. */
    val rulesScore: Int? = null,
    val wordsOnlyScore: Int? = null,
)

/**
 * How one recorded clip was scored. A heuristic, written out term by term: it describes how well the device captured
 * the voice and how the voice tags fed the priority rules. It is not a transcription accuracy and not a measure of how
 * truthful or how serious the report is.
 */
@Serializable
data class AudioScore(
    val capture: CaptureQuality,
    val recognition: RecognitionFacts,
    /** How the voice (and the words) fed the priority; null when no rule replay was available. */
    val influence: PriorityInfluence?,
    val note: String = AudioScore.HEURISTIC_NOTE,
) {
    fun toLine(): AudioScoreLine {
        fun term(id: String) = capture.terms.firstOrNull { it.id == id }?.measured
        return AudioScoreLine(
            capture = capture.total,
            peakDbfs = term("peak")?.round1(),
            meanDbfs = term("mean")?.round1(),
            clippedPct = term("clipping")?.let { (it * 1000).roundToInt() / 10.0 },
            silencePct = term("silence")?.let { (it * 1000).roundToInt() / 10.0 },
            durationS = term("duration")?.round1(),
            engine = recognition.engine,
            compute = recognition.compute,
            processingMs = recognition.processingMs,
            realTimeFactor = recognition.realTimeFactor?.let { (it * 10).roundToInt() / 10.0 },
            words = recognition.words,
            rulesScore = influence?.withVoiceScore,
            wordsOnlyScore = influence?.wordsOnlyScore,
        )
    }

    companion object {
        const val NOT_MEASURED = "not measured"
        const val HEURISTIC_NOTE =
            "A heuristic written out in the open: it rates how well the device captured the voice and shows how the voice tags " +
                "fed the priority rules. It is not a measure of speech accuracy or of how serious the report is."

        /**
         * Pure and deterministic: the same samples and facts always give the same score.
         *
         * @param samples 16-bit mono PCM of the clip
         * @param recognition what the speech engine reported; null when no engine ran
         * @param influence the rule replay from [PriorityExplainer]; null when it was not available
         */
        fun of(samples: ShortArray, sampleRate: Int, recognition: RecognitionInput?, influence: PriorityInfluence?): AudioScore {
            val audioMs = if (sampleRate > 0) samples.size * 1000L / sampleRate else 0L
            val ms = recognition?.processingMs
            val facts = RecognitionFacts(
                engine = recognition?.engine,
                compute = recognition?.compute?.name,
                processingMs = ms,
                audioMs = audioMs,
                realTimeFactor = if (ms != null && ms > 0 && audioMs > 0) audioMs.toDouble() / ms else null,
                words = recognition?.let { countWords(it.transcript) } ?: 0,
            )
            return AudioScore(capture(samples, sampleRate), facts, influence)
        }

        /** Words with at least one letter or digit, split on white space (Hindi and English alike). */
        fun countWords(text: String): Int = text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

        private fun Double.round1() = (this * 10).roundToInt() / 10.0

        /** Locale-independent: a score table must read the same on every device. */
        private fun String.fmt(vararg a: Any): String = java.lang.String.format(java.util.Locale.ROOT, this, *a)

        private fun capture(samples: ShortArray, sampleRate: Int): CaptureQuality {
            if (samples.isEmpty() || sampleRate <= 0) return unmeasured()
            val t = CaptureThresholds
            var peak = 0
            var sumSquares = 0.0
            var clipped = 0
            for (s in samples) {
                val v = s.toInt()
                val a = abs(v)
                if (a > peak) peak = a
                if (a >= t.CLIP_LEVEL) clipped++
                sumSquares += v.toDouble() * v
            }
            val peakDb = db(peak / 32768.0)
            val meanDb = db(sqrt(sumSquares / samples.size) / 32768.0)
            val clipFraction = clipped.toDouble() / samples.size
            val seconds = samples.size.toDouble() / sampleRate

            val frame = maxOf(1, sampleRate * t.SILENCE_FRAME_MS / 1000)
            var frames = 0
            var silent = 0
            var start = 0
            while (start < samples.size) {
                val end = minOf(samples.size, start + frame)
                var sq = 0.0
                for (i in start until end) sq += samples[i].toDouble() * samples[i]
                if (db(sqrt(sq / (end - start)) / 32768.0) < t.SILENCE_BELOW_DBFS) silent++
                frames++
                start = end
            }
            val silenceFraction = silent.toDouble() / frames

            fun pts(weight: Int, fraction: Double) = (weight * fraction.coerceIn(0.0, 1.0)).roundToInt()

            val terms = listOf(
                ScoreTerm(
                    "peak", "Peak level", peakDb, "%.1f dBFS".fmt(peakDb),
                    "${t.PEAK_WEIGHT} points at or above ${t.PEAK_FULL_DBFS.toInt()} dBFS, falling in a straight line to 0 at ${t.PEAK_ZERO_DBFS.toInt()} dBFS",
                    pts(t.PEAK_WEIGHT, (peakDb - t.PEAK_ZERO_DBFS) / (t.PEAK_FULL_DBFS - t.PEAK_ZERO_DBFS)), t.PEAK_WEIGHT,
                ),
                ScoreTerm(
                    "mean", "Average level (RMS)", meanDb, "%.1f dBFS".fmt(meanDb),
                    "${t.MEAN_WEIGHT} points from ${t.MEAN_FULL_LOW_DBFS.toInt()} to ${t.MEAN_FULL_HIGH_DBFS.toInt()} dBFS; fewer below " +
                        "${t.MEAN_FULL_LOW_DBFS.toInt()} (0 at ${t.MEAN_ZERO_LOW_DBFS.toInt()}); fewer above ${t.MEAN_FULL_HIGH_DBFS.toInt()} (0 at ${t.MEAN_ZERO_HIGH_DBFS.toInt()})",
                    pts(
                        t.MEAN_WEIGHT,
                        when {
                            meanDb < t.MEAN_FULL_LOW_DBFS -> (meanDb - t.MEAN_ZERO_LOW_DBFS) / (t.MEAN_FULL_LOW_DBFS - t.MEAN_ZERO_LOW_DBFS)
                            meanDb > t.MEAN_FULL_HIGH_DBFS -> (t.MEAN_ZERO_HIGH_DBFS - meanDb) / (t.MEAN_ZERO_HIGH_DBFS - t.MEAN_FULL_HIGH_DBFS)
                            else -> 1.0
                        },
                    ),
                    t.MEAN_WEIGHT,
                ),
                ScoreTerm(
                    "clipping", "Clipped samples", clipFraction, "%.2f %%".fmt(clipFraction * 100),
                    "${t.CLIP_WEIGHT} points at 0 %, falling to 0 at ${(t.CLIP_ZERO_FRACTION * 100).toInt()} % of samples at full scale",
                    pts(t.CLIP_WEIGHT, 1.0 - clipFraction / t.CLIP_ZERO_FRACTION), t.CLIP_WEIGHT,
                ),
                ScoreTerm(
                    "duration", "Length", seconds, "%.1f s".fmt(seconds),
                    "${t.DURATION_WEIGHT} points at ${t.DURATION_FULL_S.toInt()} s or longer, falling to 0 at ${t.DURATION_ZERO_S} s",
                    pts(t.DURATION_WEIGHT, (seconds - t.DURATION_ZERO_S) / (t.DURATION_FULL_S - t.DURATION_ZERO_S)), t.DURATION_WEIGHT,
                ),
                ScoreTerm(
                    "silence", "Silence", silenceFraction, "%.0f %%".fmt(silenceFraction * 100),
                    "${t.SILENCE_WEIGHT} points at ${(t.SILENCE_FULL_FRACTION * 100).toInt()} % or less of ${t.SILENCE_FRAME_MS} ms frames below " +
                        "${t.SILENCE_BELOW_DBFS.toInt()} dBFS, falling to 0 at ${(t.SILENCE_ZERO_FRACTION * 100).toInt()} %",
                    pts(t.SILENCE_WEIGHT, (t.SILENCE_ZERO_FRACTION - silenceFraction) / (t.SILENCE_ZERO_FRACTION - t.SILENCE_FULL_FRACTION)), t.SILENCE_WEIGHT,
                ),
            )
            return CaptureQuality(terms, terms.sumOf { it.points })
        }

        /** Every term shown with "not measured" and no total: a clip with no samples has nothing to score. */
        private fun unmeasured(): CaptureQuality {
            val t = CaptureThresholds
            val none = NOT_MEASURED
            return CaptureQuality(
                listOf(
                    ScoreTerm("peak", "Peak level", null, none, "no audio was captured", 0, t.PEAK_WEIGHT),
                    ScoreTerm("mean", "Average level (RMS)", null, none, "no audio was captured", 0, t.MEAN_WEIGHT),
                    ScoreTerm("clipping", "Clipped samples", null, none, "no audio was captured", 0, t.CLIP_WEIGHT),
                    ScoreTerm("duration", "Length", null, none, "no audio was captured", 0, t.DURATION_WEIGHT),
                    ScoreTerm("silence", "Silence", null, none, "no audio was captured", 0, t.SILENCE_WEIGHT),
                ),
                total = null,
            )
        }

        private fun db(x: Double): Double = if (x <= 0.0) -120.0 else maxOf(-120.0, 20 * log10(x))
    }
}

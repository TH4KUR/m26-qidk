package com.megathon.incident.eval

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Field-level scorer for incident reports, a port of tools/laptop_eval/score.py (rules in
 * docs/eval-guide.md). Both run core/src/test/resources/fixtures/eval/scorer_cases.jsonl.
 * Gold items and predictions are JSON, so the same files feed the laptop contest, the kit run
 * and the Judge Bench screen.
 */
object EvalScorer {
    val FIELDS = listOf(
        "incident_type", "severity", "location", "date_time",
        "persons_involved", "equipment_asset", "immediate_actions", "witnesses",
    )
    val MANDATORY = listOf("incident_type", "location", "date_time", "severity") // spec D6

    private val ENUM_FIELDS = setOf("incident_type", "severity")
    private val LIST_FIELDS = setOf("persons_involved", "witnesses")
    private val FILLED = setOf("REPORTED", "USER_CONFIRMED", "USER_EDITED")
    private val ANSWERED = FILLED + setOf("EXPLICIT_NONE", "UNKNOWN")
    val SPECIAL_STATUSES = listOf("EXPLICIT_NONE", "UNKNOWN", "SUGGESTED")

    /** Same stop-words as C's GroundingValidator. */
    private val STOP_WORDS = setOf("the", "a", "an", "of", "in", "at", "near", "around", "to", "on", "is", "was", "and")
    private val NUMBER_WORDS = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen " +
        "fifteen sixteen seventeen eighteen nineteen twenty").split(" ").withIndex().associate { it.value to it.index.toString() }
    private const val TEXT_MATCH = 0.8
    private val PARTS_OF_DAY = setOf("morning", "afternoon", "evening", "night")
    private val DISPLAY = Regex(
        "^\\s*(\\d{4}-\\d{2}-\\d{2}|date missing)\\s+≈?\\s*(\\d{1,2}:\\d{2}|morning|afternoon|evening|night|time missing)",
        RegexOption.IGNORE_CASE,
    )

    // ---- normalisation ---------------------------------------------------------------------

    internal fun tokens(text: String): List<String> {
        val t = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (isLetterMarkOrNumber(cp)) cur.appendCodePoint(cp)
            else if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
            i += Character.charCount(cp)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.map { NUMBER_WORDS[it] ?: it }
    }

    private fun isLetterMarkOrNumber(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
        Character.OTHER_LETTER, Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    private fun content(text: String): Set<String> {
        val toks = tokens(text)
        return toks.filterNot { it in STOP_WORDS }.toSet().ifEmpty { toks.toSet() }
    }

    private fun dice(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else 2.0 * (a intersect b).size / (a.size + b.size)

    private fun names(parts: List<String>): Set<String> =
        parts.filter { tokens(it).isNotEmpty() }.map { content(it).sorted().joinToString(" ") }.toSet()

    private fun splitNames(text: String): List<String> = text.split(Regex(",|&|\\band\\b"))

    // ---- date and time ---------------------------------------------------------------------

    private data class TimeParts(val date: LocalDate?, val time: String?, val partOfDay: String?)

    private fun predTimeParts(pred: JsonObject): TimeParts? {
        (pred["parts"] as? JsonObject)?.let { p ->
            val pod = (p.str("partOfDay") ?: p.str("part_of_day"))?.lowercase()
            return TimeParts(p.str("date")?.let(LocalDate::parse), p.str("time"), pod)
        }
        val m = DISPLAY.find(pred.str("text") ?: "") ?: return null
        val (d, tod) = m.destructured
        val date = if (d.equals("date missing", ignoreCase = true)) null else LocalDate.parse(d)
        val t = tod.lowercase()
        return when {
            t in PARTS_OF_DAY -> TimeParts(date, null, t)
            t == "time missing" -> TimeParts(date, null, null)
            else -> {
                val (h, mi) = t.split(":")
                TimeParts(date, "%02d:%s".format(h.toInt(), mi), null)
            }
        }
    }

    private fun dateTimeMatches(gold: JsonObject, pred: JsonObject, capturedAt: String?): Boolean {
        val p = predTimeParts(pred) ?: return false
        gold.int("minutes_ago")?.let { minutes ->
            if (capturedAt == null || p.date == null || p.time == null) return false
            val expected = OffsetDateTime.parse(capturedAt).minusMinutes(minutes.toLong())
            val got = OffsetDateTime.of(p.date, LocalTime.parse(p.time), expected.offset)
            return abs(ChronoUnit.SECONDS.between(expected, got)) <= 60
        }
        val offset = gold.int("date_offset_days")
        val dateOk = if (offset == null) p.date == null else {
            val ref = capturedAt?.let { OffsetDateTime.parse(it).toLocalDate() }
            p.date != null && ref != null && ChronoUnit.DAYS.between(ref, p.date) == offset.toLong()
        }
        val goldTime = gold.str("time")
        val goldPod = gold.str("part_of_day")
        val timeOk = when {
            goldTime != null -> p.time == goldTime
            goldPod != null -> p.time == null && p.partOfDay == goldPod.lowercase()
            else -> p.time == null
        }
        return dateOk && timeOk
    }

    // ---- per-slot outcome ------------------------------------------------------------------

    private fun isFilled(pred: JsonObject?): Boolean =
        pred != null && pred.str("status") in FILLED && (!pred.str("text").isNullOrBlank() || pred["parts"] is JsonObject)

    private fun matches(field: String, gold: JsonObject, pred: JsonObject, capturedAt: String?): Boolean {
        val text = pred.str("text") ?: ""
        if (field == "date_time") {
            if (pred.bool("raw")) return dice(content(text), content(gold.str("text") ?: "")) >= TEXT_MATCH
            return dateTimeMatches(gold, pred, capturedAt)
        }
        val alts = (gold["alts"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        if (field in ENUM_FIELDS) {
            return text.trim().lowercase() in (listOf(gold.str("value")!!) + alts).map { it.lowercase() }
        }
        if (field in LIST_FIELDS) {
            return names(splitNames(text)) == names(gold["value"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
        return (listOf(gold.str("value")!!) + alts).any { dice(content(text), content(it)) >= TEXT_MATCH }
    }

    fun outcome(field: String, gold: JsonObject, pred: JsonObject?, capturedAt: String? = null): Outcome {
        val goldHasValue = gold.str("status") in FILLED
        val filled = isFilled(pred)
        return when {
            goldHasValue && !filled -> Outcome.MISS
            goldHasValue -> if (matches(field, gold, pred!!, capturedAt)) Outcome.CORRECT else Outcome.WRONG
            filled -> Outcome.HALLUCINATION
            else -> Outcome.CORRECT_EMPTY
        }
    }

    // ---- aggregation -----------------------------------------------------------------------

    private fun askedFields(pred: JsonObject?): Set<String> {
        if (pred == null) return emptySet()
        (pred["asked"] as? JsonArray)?.let { turns ->
            return turns.flatMap { t -> t.jsonArray.map { it.jsonPrimitive.content.lowercase() } }.toSet()
        }
        val fields = pred["report"]?.jsonObject?.get("fields") as? JsonObject ?: JsonObject(emptyMap())
        return MANDATORY.filter { f -> (fields[f.uppercase()] as? JsonObject)?.str("status") !in ANSWERED }.toSet()
    }

    fun score(golds: List<JsonObject>, preds: List<JsonObject>): ScoreReport {
        val byId = preds.associateBy { it.str("gold_id") }
        val counts = FIELDS.associateWith { IntArray(Outcome.entries.size) }
        val agree = SPECIAL_STATUSES.associateWith { IntArray(2) }
        var missingPreds = 0
        var fuHit = 0; var fuAsked = 0; var fuMissing = 0

        for (item in golds) {
            val pred = byId[item.str("id")]
            if (pred == null) missingPreds++
            val fields = pred?.get("report")?.jsonObject?.get("fields") as? JsonObject
            val capturedAt = pred?.str("captured_at")
            val goldFields = item["gold"]!!.jsonObject
            for (f in FIELDS) {
                val gold = goldFields[f] as? JsonObject ?: MISSING_GOLD
                val pv = fields?.get(f.uppercase()) as? JsonObject
                counts.getValue(f)[outcome(f, gold, pv, capturedAt).ordinal]++
                agree[gold.str("status")]?.let { a ->
                    a[1]++
                    if ((pv?.str("status") ?: "MISSING") == gold.str("status")) a[0]++
                }
            }
            val asked = askedFields(pred)
            val missing = (item["missing_mandatory"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
            fuHit += (asked intersect missing).size
            fuAsked += asked.size
            fuMissing += missing.size
        }

        fun total(subset: List<String>) = FieldScore.of(IntArray(Outcome.entries.size) { o -> subset.sumOf { counts.getValue(it)[o] } })
        return ScoreReport(
            nItems = golds.size,
            nMissingPredictions = missingPreds,
            fields = FIELDS.associateWith { FieldScore.of(counts.getValue(it)) },
            overall = total(FIELDS),
            mandatory = total(MANDATORY),
            followUpPrecision = Ratio(fuHit, fuAsked),
            followUpRecall = Ratio(fuHit, fuMissing),
            statusAgreement = SPECIAL_STATUSES.associateWith { Ratio(agree.getValue(it)[0], agree.getValue(it)[1]) },
        )
    }

    private val MISSING_GOLD = buildJsonObject { put("status", "MISSING") }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull
    private fun JsonObject.bool(key: String): Boolean = str(key) == "true"
}

enum class Outcome(val wire: String) {
    CORRECT("correct"), WRONG("wrong"), MISS("miss"), HALLUCINATION("hallucination"), CORRECT_EMPTY("correct_empty")
}

/** A ratio that keeps its counts. No denominator means no value (never 1.0). */
data class Ratio(val num: Int, val den: Int) {
    val value: Double? get() = if (den == 0) null else num.toDouble() / den

    /** Wilson 95% score interval. */
    val ci95: Pair<Double, Double>?
        get() {
            if (den == 0) return null
            val z = 1.96
            val p = num.toDouble() / den
            val centre = (p + z * z / (2 * den)) / (1 + z * z / den)
            val half = z * sqrt(p * (1 - p) / den + z * z / (4.0 * den * den)) / (1 + z * z / den)
            return maxOf(0.0, centre - half) to minOf(1.0, centre + half)
        }

    override fun toString(): String = value?.let { "$num/$den (${Math.round(it * 100)}%)" } ?: "n/a (0/0)"
}

data class FieldScore(val correct: Int, val wrong: Int, val miss: Int, val hallucination: Int, val correctEmpty: Int) {
    val goldValues get() = correct + wrong + miss
    val filled get() = correct + wrong + hallucination
    val precision get() = Ratio(correct, filled)
    val recall get() = Ratio(correct, goldValues)
    val hallucinationRate get() = Ratio(hallucination, hallucination + correctEmpty)

    companion object {
        internal fun of(c: IntArray) = FieldScore(c[0], c[1], c[2], c[3], c[4])
    }
}

data class ScoreReport(
    val nItems: Int,
    val nMissingPredictions: Int,
    val fields: Map<String, FieldScore>,
    val overall: FieldScore,
    val mandatory: FieldScore,
    val followUpPrecision: Ratio,
    val followUpRecall: Ratio,
    val statusAgreement: Map<String, Ratio>,
)

/**
 * One pipeline result for one gold item, in the shape the scorer reads (the same JSON the Python
 * contest writes to predictions.jsonl).
 */
data class Prediction(
    val goldId: String,
    val report: IncidentReport,
    val asked: List<List<Field>>? = null,
    val capturedAt: String? = null,
    val transcript: String = "",
    val latencyMs: Map<String, Long> = emptyMap(),
    val compute: String = "",
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("gold_id", goldId)
        capturedAt?.let { put("captured_at", it) }
        asked?.let { a -> put("asked", JsonArray(a.map { turn -> JsonArray(turn.map { JsonPrimitive(it.name) }) })) }
        put("transcript", transcript)
        put("latency_ms", JsonObject(latencyMs.mapValues { JsonPrimitive(it.value) }))
        put("compute", compute)
        put("report", JSON.encodeToJsonElement(IncidentReport.serializer(), report))
    }

    companion object {
        internal val JSON = Json { ignoreUnknownKeys = true }

        fun fromJson(o: JsonObject): Prediction {
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            return Prediction(
                goldId = s("gold_id")!!,
                report = JSON.decodeFromJsonElement(IncidentReport.serializer(), o["report"]!!),
                asked = (o["asked"] as? JsonArray)?.map { t -> t.jsonArray.map { Field.valueOf(it.jsonPrimitive.content) } },
                capturedAt = s("captured_at"),
                transcript = s("transcript") ?: "",
                latencyMs = (o["latency_ms"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content.toLong() }.orEmpty(),
                compute = s("compute") ?: "",
            )
        }
    }
}

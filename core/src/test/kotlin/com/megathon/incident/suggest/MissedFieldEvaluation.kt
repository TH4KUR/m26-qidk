package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.io.File
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

/**
 * Measurement, not a unit test: [MissedFieldRules] chips over E's gold, "dev" split only (holdout rows are
 * dropped before anything else is read). Skipped unless MISSED_EVAL_GOLD (a .jsonl) and MISSED_EVAL_OUT are set.
 *
 * Setup: the transcript is the item's turns joined; the state has only the gold location as REPORTED (so the
 * place guard is exercised), and equipment and actions MISSING: the case where the model emitted neither.
 * Matching is E's `score.py` text rule: content tokens (same stop and number words), dice >= 0.8 against the
 * gold value or any alt. Outcomes: right chip, wrong chip, missed (gold has a value); false alarm, right
 * no-chip (gold has none).
 */
class MissedFieldEvaluation {

    private val engine = RuleSuggestionEngine(
        SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
        Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
    )

    private enum class Outcome { RIGHT_CHIP, WRONG_CHIP, MISSED, FALSE_ALARM, RIGHT_NO_CHIP }

    @Test
    fun measure() {
        val goldPath = System.getenv("MISSED_EVAL_GOLD")
        val outPath = System.getenv("MISSED_EVAL_OUT")
        assumeTrue("set MISSED_EVAL_GOLD and MISSED_EVAL_OUT to run", goldPath != null && outPath != null)

        val dev = File(goldPath!!).readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
            .filter { it["split"]?.jsonPrimitive?.content == "dev" }

        val rows = mutableListOf<List<String>>()
        val counts = mutableMapOf<Pair<String, Outcome>, Int>()
        for (item in dev) {
            val id = item["id"]!!.jsonPrimitive.content
            val transcript = item["turns"]!!.jsonArray.joinToString(" ") { it.jsonPrimitive.content }
            val gold = item["gold"]!!.jsonObject
            var state = ReportState.empty(id, 0)
            gold.field("location")?.takeIf { filled(it) }?.let { loc ->
                val v = loc["value"]!!.jsonPrimitive.content
                state = state.copy(report = state.report.with(Field.LOCATION, FieldValue(v, ValueStatus.REPORTED, v)))
            }
            val chips = engine.suggest(state, transcript).filter { it.original == "" }
            for ((key, field) in listOf("equipment_asset" to Field.EQUIPMENT_ASSET, "immediate_actions" to Field.IMMEDIATE_ACTIONS)) {
                val g = gold.field(key)
                val chip = chips.firstOrNull { it.field == field }?.proposed
                val candidates = g?.takeIf { filled(it) }?.let { listOf(it["value"]!!.jsonPrimitive.content) + (it["alts"]?.jsonArray?.map { a -> a.jsonPrimitive.content } ?: emptyList()) }
                val outcome = when {
                    candidates != null && chip == null -> Outcome.MISSED
                    candidates != null -> if (candidates.any { dice(content(chip!!), content(it)) >= 0.8 }) Outcome.RIGHT_CHIP else Outcome.WRONG_CHIP
                    chip != null -> Outcome.FALSE_ALARM
                    else -> Outcome.RIGHT_NO_CHIP
                }
                counts.merge(key to outcome, 1, Int::plus)
                rows += listOf(id, key, candidates?.first() ?: "", chip ?: "", outcome.name)
            }
        }

        val report = buildString {
            appendLine("dev items: ${dev.size} (holdout not read)")
            appendLine()
            appendLine("| field | " + Outcome.entries.joinToString(" | ") { it.name } + " |")
            appendLine("|---|" + Outcome.entries.joinToString("") { "---|" })
            for (f in listOf("equipment_asset", "immediate_actions")) {
                appendLine("| $f | " + Outcome.entries.joinToString(" | ") { (counts[f to it] ?: 0).toString() } + " |")
            }
            appendLine()
            appendLine("| id | field | gold | chip | outcome |")
            appendLine("|---|---|---|---|---|")
            for (r in rows) appendLine("| " + r.joinToString(" | ") { it.replace("|", "/") } + " |")
        }
        File(outPath!!).writeText(report)
        println(report)
    }

    private fun JsonObject.field(key: String) = this[key]?.jsonObject

    private fun filled(o: JsonObject) = o["status"]?.jsonPrimitive?.content in setOf("REPORTED", "USER_CONFIRMED", "USER_EDITED") &&
        o["value"] != null

    // ---- E's score.py text rule ---------------------------------------------------------------------

    private fun tokens(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()) {
            if (Character.isLetterOrDigit(ch) || Character.getType(ch).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt() }) cur.append(ch)
            else if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.map { NUMBER_WORDS[it] ?: it }
    }

    private fun content(text: String): Set<String> {
        val toks = tokens(text)
        return toks.filter { it !in STOP_WORDS }.toSet().ifEmpty { toks.toSet() }
    }

    private fun dice(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else 2.0 * (a intersect b).size / (a.size + b.size)

    private companion object {
        val STOP_WORDS = setOf("the", "a", "an", "of", "in", "at", "near", "around", "to", "on", "is", "was", "and")
        val NUMBER_WORDS = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen " +
            "fifteen sixteen seventeen eighteen nineteen twenty").split(" ").withIndex().associate { (i, w) -> w to i.toString() }
    }
}

package com.megathon.incident.domain

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Measuring harness for time_corpus_v1.jsonl (model-authored, not native-reviewed; see time/README.md).
 * It FAILS NOTHING by itself except when the corpus does not load: it writes the tables and every miss to
 * build/time-corpus/report.txt so a change to the resolver can be judged by numbers.
 *
 * Modes: `sentence` resolves the whole report sentence; `phrase` is what the app does when the model forgot
 * date_time_text (findTimePhrase, then resolve the phrase); `context` resolves the found phrase with the sentence
 * as tense context (resolveInContext), which is what the app would do if the validator passed the transcript.
 */
class TemporalCorpusTest {
    private val resolver = TemporalResolver()

    private data class Expect(
        val date: LocalDate?, val time: LocalTime?, val pod: String?, val approx: Boolean, val ambiguous: Boolean,
        val from: LocalDate?, val to: LocalDate?,
    )

    private data class Item(val id: String, val lang: String, val category: String, val capturedAt: OffsetDateTime, val sentence: String, val expected: Expect?)

    private enum class Outcome { CORRECT, WRONG, NULL_MISSED, SPURIOUS }

    private fun load(resource: String): List<Item> {
        val text = javaClass.getResourceAsStream("/time/$resource")!!.bufferedReader(Charsets.UTF_8).readText()
        return text.lines().filter { it.isNotBlank() }.map { line ->
            val o = Json.parseToJsonElement(line).jsonObject
            fun s(k: String) = o[k]!!.jsonPrimitive.content
            val e = o["expected"]
            Item(
                s("id"), s("lang"), s("category"), OffsetDateTime.parse(s("capturedAt")), s("sentence"),
                if (e == null || e is JsonNull) null else expect(e.jsonObject),
            )
        }
    }

    private fun expect(o: JsonObject): Expect {
        fun s(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        return Expect(
            s("date")?.let(LocalDate::parse), s("time")?.let(LocalTime::parse), s("partOfDay"),
            o["approximate"]!!.jsonPrimitive.boolean, o["ambiguous"]!!.jsonPrimitive.boolean,
            s("from")?.let(LocalDate::parse), s("to")?.let(LocalDate::parse),
        )
    }

    private fun describe(v: TemporalValue?) =
        if (v == null) "null" else "date=${v.date} time=${v.time} pod=${v.partOfDay} approx=${v.approximate} amb=${v.ambiguous}" +
            (if (v.from != null) " range=${v.from}..${v.to}" else "")

    private fun describe(e: Expect?) =
        if (e == null) "null" else "date=${e.date} time=${e.time} pod=${e.pod} approx=${e.approx} amb=${e.ambiguous}" +
            (if (e.from != null) " range=${e.from}..${e.to}" else "")

    private fun fieldsEqual(v: TemporalValue, e: Expect) = mapOf(
        "date" to (v.date == e.date), "time" to (v.time == e.time), "pod" to (v.partOfDay?.name == e.pod),
        "approx" to (v.approximate == e.approx), "amb" to (v.ambiguous == e.ambiguous),
        "range" to (v.from == e.from && v.to == e.to),
    )

    private fun judge(v: TemporalValue?, e: Expect?): Outcome = when {
        e == null -> if (v == null) Outcome.CORRECT else Outcome.SPURIOUS
        v == null -> Outcome.NULL_MISSED
        fieldsEqual(v, e).values.all { it } -> Outcome.CORRECT
        else -> Outcome.WRONG
    }

    private fun modes(): Map<String, (Item) -> TemporalValue?> {
        val m = linkedMapOf<String, (Item) -> TemporalValue?>()
        m["sentence"] = { resolver.resolve(it.sentence, it.capturedAt.toInstant()) }
        m["phrase"] = { i -> resolver.findTimePhrase(i.sentence)?.let { resolver.resolve(it, i.capturedAt.toInstant()) } }
        extraModes(m)
        return m
    }

    /** Hook for the context mode once the resolver has it. */
    private fun extraModes(m: MutableMap<String, (Item) -> TemporalValue?>) {
        m["context"] = { i -> resolver.findTimePhrase(i.sentence)?.let { resolver.resolveInContext(it, i.sentence, i.capturedAt.toInstant()) } }
    }

    @Test
    fun measureCorpus() {
        val main = load("time_corpus_v1.jsonl")
        assertTrue(main.size >= 120, "corpus has ${main.size} items")
        assertTrue(main.map { it.id }.toSet().size == main.size, "ids are unique")
        // Written AFTER the fixes, from phrasings the fixes were not built around: the honest out-of-sample check.
        val probe = load("time_corpus_probe_v1.jsonl")
        assertTrue(probe.map { it.id }.toSet().size == probe.size, "probe ids are unique")

        val sb = StringBuilder()
        section("time_corpus_v1", main, sb)
        sb.appendLine()
        sb.appendLine("##########################################################")
        section("time_corpus_probe_v1", probe, sb)
        val out = File("build/time-corpus/report.txt")
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
    }

    /**
     * A regression gate, not part of the measurement: after the fixes the whole-sentence mode is correct on every item, and the
     * app-shaped context mode misses only the two bare "right now" / "just now" items, which findTimePhrase leaves to ReportSession
     * by design. A new miss here means a resolver change made a case worse. (The corpus is the author's own; see time/README.md.)
     */
    @Test
    fun sentenceAndContextModesStayCorrect() {
        val items = load("time_corpus_v1.jsonl") + load("time_corpus_probe_v1.jsonl")
        val modes = modes()
        val sentenceMisses = items.filter { judge(modes.getValue("sentence")(it), it.expected) != Outcome.CORRECT }.map { it.id }
        assertTrue(sentenceMisses.isEmpty(), "sentence mode misses: $sentenceMisses")
        val contextMisses = items.filter { judge(modes.getValue("context")(it), it.expected) != Outcome.CORRECT }.map { it.id }
        assertTrue(contextMisses.toSet() == setOf("en-013", "en-015"), "context mode misses: $contextMisses")
    }

    private fun section(name: String, items: List<Item>, sb: StringBuilder) {
        sb.appendLine("$name: ${items.size} items (" + items.groupingBy { it.lang }.eachCount().entries.joinToString { "${it.key}=${it.value}" } + ")")
        for ((mode, run) in modes()) {
            val results = items.map { it to runCatching { run(it) } }
            sb.appendLine()
            sb.appendLine("=== mode: $mode ===")
            val judged = results.map { (i, r) -> Triple(i, r.getOrNull(), if (r.isFailure) Outcome.WRONG else judge(r.getOrNull(), i.expected)) }
            fun table(title: String, key: (Item) -> String) {
                sb.appendLine("-- by $title: n correct wrong null-when-value-expected spurious-value-when-null-expected")
                for ((k, group) in judged.groupBy { key(it.first) }.toSortedMap()) {
                    val c = group.groupingBy { it.third }.eachCount()
                    sb.appendLine(
                        "%-22s %3d %4d %4d %4d %4d".format(
                            k, group.size, c[Outcome.CORRECT] ?: 0, c[Outcome.WRONG] ?: 0, c[Outcome.NULL_MISSED] ?: 0, c[Outcome.SPURIOUS] ?: 0,
                        ),
                    )
                }
            }
            table("language") { it.lang }
            table("language x category") { "${it.lang}/${it.category}" }
            table("category") { it.category }
            val total = judged.groupingBy { it.third }.eachCount()
            sb.appendLine(
                "TOTAL %d correct=%d wrong=%d null_missed=%d spurious=%d".format(
                    judged.size, total[Outcome.CORRECT] ?: 0, total[Outcome.WRONG] ?: 0, total[Outcome.NULL_MISSED] ?: 0, total[Outcome.SPURIOUS] ?: 0,
                ),
            )
            // Field-level accuracy over the items that expected a value and got one.
            val fieldHits = linkedMapOf<String, Int>().withDefault { 0 }
            var both = 0
            for ((i, v, _) in judged) {
                val e = i.expected ?: continue
                if (v == null) continue
                both++
                fieldsEqual(v, e).forEach { (f, ok) -> if (ok) fieldHits[f] = fieldHits.getValue(f) + 1 }
            }
            sb.appendLine("field accuracy over $both value-vs-value items: " + listOf("date", "time", "pod", "approx", "amb", "range").joinToString { "$it=${fieldHits.getValue(it)}" })
            sb.appendLine("-- misses")
            for ((i, v, o) in judged) {
                if (o == Outcome.CORRECT) continue
                sb.appendLine("[${o.name}] ${i.id} ${i.category} @${i.capturedAt.toLocalTime()} \"${i.sentence}\"")
                sb.appendLine("      got      ${describe(v)}")
                sb.appendLine("      expected ${describe(i.expected)}")
            }
        }
    }
}

package com.megathon.incident.suggest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * Measurement, not a unit test: runs the snapper over E's gold set and writes counts to a report.
 * Skipped unless SNAPPER_EVAL_GOLD (comma-separated .jsonl files), SNAPPER_EVAL_SITE (a site file)
 * and SNAPPER_EVAL_OUT (report path) are set. Uses only the "dev" split, so holdout cases stay unseen.
 *
 * Two sets per gold location/equipment value:
 *  - clean: the value as typed in the gold. A chip here is only right if it names the same place.
 *  - synthetic: digits and single letters spoken as words ("2" -> "to", "C" -> "see"), the way speech
 *    recognition often writes them. The right answer is the gold place. Labelled synthetic: these are
 *    generated, not real recognition errors.
 */
class SnapperEvaluation {

    private data class Case(val id: String, val kind: EntryKind, val heard: String, val goldNames: List<String>)

    private enum class Outcome { CORRECT_SNAP, CORRECT_NO_CHIP, WRONG_SNAP, MISSED, UNKNOWN_PLACE_NO_CHIP, UNKNOWN_PLACE_SNAPPED }

    @Test
    fun measure() {
        val goldPaths = System.getenv("SNAPPER_EVAL_GOLD")
        val sitePath = System.getenv("SNAPPER_EVAL_SITE")
        val outPath = System.getenv("SNAPPER_EVAL_OUT")
        assumeTrue("set SNAPPER_EVAL_GOLD, SNAPPER_EVAL_SITE and SNAPPER_EVAL_OUT to run", goldPaths != null && sitePath != null && outPath != null)

        val site = SiteDirectory.fromJson(File(sitePath!!).readText())
        val snapper = GazetteerSnapper(site, Homophones.default)
        val cases = goldPaths!!.split(',').flatMap { readCases(File(it.trim())) }

        val clean = cases.map { it to judge(site, snapper, it) }
        val synthetic = cases.mapNotNull { c -> spokenForm(c.heard)?.let { c.copy(heard = it) } }.map { it to judge(site, snapper, it) }

        val report = buildString {
            appendLine("cases: ${cases.size} values (dev split only) from ${cases.map { it.id }.distinct().size} gold items")
            appendLine()
            section("Clean values (as typed in the gold)", clean)
            section("Synthetic mishearings (digits/letters spoken as words)", synthetic)
        }
        File(outPath!!).writeText(report)
        println(report)
    }

    private fun StringBuilder.section(title: String, rows: List<Pair<Case, Pair<Outcome, String?>>>) {
        appendLine("## $title: n = ${rows.size}")
        for (o in Outcome.entries) appendLine("- $o: ${rows.count { it.second.first == o }}")
        appendLine()
        appendLine("| id | kind | heard | gold | outcome | proposed |")
        appendLine("|---|---|---|---|---|---|")
        for ((c, r) in rows) appendLine("| ${c.id} | ${c.kind} | ${c.heard} | ${c.goldNames.joinToString(" / ")} | ${r.first} | ${r.second ?: ""} |")
        appendLine()
    }

    /** Outcome plus the proposed name, judged against the site entry the gold value (or an alt) names. */
    private fun judge(site: SiteDirectory, snapper: GazetteerSnapper, c: Case): Pair<Outcome, String?> {
        val goldEntry = c.goldNames.firstNotNullOfOrNull { site.lookup(it)?.takeIf { e -> e.kind == c.kind } }
        val snap = snapper.snap(c.heard, c.kind)
        return when {
            goldEntry == null && snap == null -> Outcome.UNKNOWN_PLACE_NO_CHIP to null
            goldEntry == null -> Outcome.UNKNOWN_PLACE_SNAPPED to snap!!.entry.name
            // No chip is needed when the right name is already in what was heard ("near the Football Ground").
            snap == null && SiteDirectory.key(goldEntry.name) in SiteDirectory.key(c.heard) -> Outcome.CORRECT_NO_CHIP to null
            snap == null -> Outcome.MISSED to null
            snap.entry.id == goldEntry.id -> Outcome.CORRECT_SNAP to snap.entry.name
            else -> Outcome.WRONG_SNAP to snap.entry.name
        }
    }

    private fun readCases(file: File): List<Case> = file.readLines().filter { it.isNotBlank() }.flatMap { line ->
        val item = Json.parseToJsonElement(line).jsonObject
        if (item["split"]?.jsonPrimitive?.content != "dev") return@flatMap emptyList()
        val id = item["id"]!!.jsonPrimitive.content
        val gold = item["gold"]!!.jsonObject
        listOfNotNull(
            caseFor(id, EntryKind.PLACE, gold["location"]?.jsonObject),
            caseFor(id, EntryKind.ASSET, gold["equipment_asset"]?.jsonObject),
        )
    }

    private fun caseFor(id: String, kind: EntryKind, field: JsonObject?): Case? {
        val value = field?.get("value") ?: return null
        if (value is JsonArray) return null
        val heard = value.jsonPrimitive.content
        val alts = (field["alts"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        return Case(id, kind, heard, listOf(heard) + alts)
    }

    /** "Seminar Hall 2" -> "seminar hall to", "Vindhya C4" -> "vindhya see four"; null when nothing changes. */
    private fun spokenForm(value: String): String? {
        val out = value.lowercase().split(' ').flatMap { word ->
            when {
                word.length == 1 && word[0].isLetter() -> listOf(LETTERS[word] ?: word)
                word.all(Char::isDigit) -> word.map { SPOKEN_DIGITS.getValue(it) }
                word.length == 2 && word[0].isLetter() && word[1].isDigit() -> listOf(LETTERS[word.take(1)] ?: word.take(1), SPOKEN_DIGITS.getValue(word[1]))
                else -> listOf(word)
            }
        }.joinToString(" ")
        return out.takeIf { it != value.lowercase() }
    }

    private companion object {
        val SPOKEN_DIGITS = mapOf('0' to "oh", '1' to "one", '2' to "to", '3' to "three", '4' to "for", '5' to "five",
            '6' to "six", '7' to "seven", '8' to "ate", '9' to "nine")
        val LETTERS = mapOf("c" to "see")
    }
}

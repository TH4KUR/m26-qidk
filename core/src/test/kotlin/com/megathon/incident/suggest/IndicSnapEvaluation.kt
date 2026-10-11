package com.megathon.incident.suggest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * Measurement, not a unit test: the snapper on the spoken place phrase (`location_said`) of D's Hindi/Telugu/Hinglish
 * draft set (`data/indic_draft_v1`, model-authored, unreviewed), "dev" split only. Skipped unless INDIC_EVAL_SET and
 * INDIC_EVAL_OUT are set. Outcomes per language: right chip (names `site_name`), wrong chip, right no-chip (the English
 * name is already said), missed, false alarm (a chip for a place not on the site).
 */
class IndicSnapEvaluation {

    private enum class Outcome { RIGHT_CHIP, WRONG_CHIP, RIGHT_NO_CHIP, MISSED, FALSE_ALARM }

    @Test
    fun measure() {
        val setPath = System.getenv("INDIC_EVAL_SET")
        val outPath = System.getenv("INDIC_EVAL_OUT")
        assumeTrue("set INDIC_EVAL_SET and INDIC_EVAL_OUT to run", setPath != null && outPath != null)

        val snapper = GazetteerSnapper(SiteDirectory.loadDefault(), Homophones.default)
        val dev = File(setPath!!).readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
            .filter { it["split"]?.jsonPrimitive?.content == "dev" }

        val rows = dev.map { item ->
            val id = item["id"]!!.jsonPrimitive.content
            val said = item["location_said"]!!.jsonPrimitive.content
            val site = item["site_name"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content
            val snap = snapper.snap(said, EntryKind.PLACE)
            val outcome = when {
                site == null -> if (snap == null) Outcome.RIGHT_NO_CHIP else Outcome.FALSE_ALARM
                snap == null -> if (SiteDirectory.key(site) in SiteDirectory.key(said)) Outcome.RIGHT_NO_CHIP else Outcome.MISSED
                snap.entry.name == site -> Outcome.RIGHT_CHIP
                else -> Outcome.WRONG_CHIP
            }
            listOf(id.take(2), id, said, site ?: "(not on site)", snap?.proposed ?: "", outcome.name)
        }

        val report = buildString {
            appendLine("dev items: ${rows.size} (test split not read)")
            appendLine()
            appendLine("| language | n | " + Outcome.entries.joinToString(" | ") { it.name } + " |")
            appendLine("|---|---|" + Outcome.entries.joinToString("") { "---|" })
            for ((lang, rs) in rows.groupBy { it[0] }) {
                appendLine("| $lang | ${rs.size} | " + Outcome.entries.joinToString(" | ") { o -> rs.count { it[5] == o.name }.toString() } + " |")
            }
            appendLine()
            appendLine("| id | said | site | proposed | outcome |")
            appendLine("|---|---|---|---|---|")
            for (r in rows) appendLine("| ${r[1]} | ${r[2]} | ${r[3]} | ${r[4]} | ${r[5]} |")
        }
        File(outPath!!).writeText(report)
        println(report)
    }
}

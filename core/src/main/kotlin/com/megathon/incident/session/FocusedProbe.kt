package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.GroundingValidator
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.suggest.EntryKind
import com.megathon.incident.suggest.SiteDirectory
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A second, narrow look at the fields the first pass most often leaves empty: the tool or item involved and what was done.
 * One short call per field, in plain words, run after the turn (never inside it) so it adds nothing to the turn time. It only
 * ever proposes a chip (the reporter's tap is the only thing that changes a field), and a proposal must be grounded in the
 * worker's own words: the quote has to be in the transcript, a plan or a hedge is dropped, and a failing, slow or confused
 * model costs the chip, never the session.
 */
class FocusedProbe(
    private val llm: LlmEngine,
    private val tam: TamDetector = TamDetector(TamCueLists.loadDefault()),
    private val timeoutMs: Long = 8_000,
    private val sites: SiteDirectory? = runCatching { SiteDirectory.loadDefault() }.getOrNull(),
    private val itemPrompt: String = resource("/prompts/probe_item_v1.txt"),
    private val actionPrompt: String = resource("/prompts/probe_action_v1.txt"),
) {
    /** The fields this probe looks at, in order. [skip] are fields something else already proposes a chip for. */
    suspend fun propose(report: IncidentReport, transcript: String, skip: Set<Field> = emptySet()): List<Suggestion> {
        val said = transcript.trim().takeLast(MAX_CHARS)
        if (said.isEmpty()) return emptyList()
        val chips = mutableListOf<Suggestion>()
        for (field in ORDER) {
            if (field in skip || report.isAnswered(field)) continue
            val system = if (field == Field.EQUIPMENT_ASSET) itemPrompt else actionPrompt
            val text = try {
                withTimeoutOrNull(timeoutMs) { llm.complete(LlmRequest(system, "Worker: \"$said\"")).text }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            chip(field, text, said, report)?.let(chips::add)
        }
        return chips
    }

    private fun chip(field: Field, reply: String, said: String, report: IncidentReport): Suggestion? {
        val obj = parseObject(reply) ?: return null
        val key = if (field == Field.EQUIPMENT_ASSET) "item" else "action"
        val value = obj[key]?.string()?.trim()?.trimEnd('.', ',', ';')?.takeIf { it.isNotEmpty() } ?: return null
        val quote = obj["quote"]?.string()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!GroundingValidator.isGrounded(value, quote, said)) return null
        // A plan, advice or a hedge is not something that happened: the same check the rule chips use.
        val clauses = runCatching { tam.analyze(quote) }.getOrDefault(emptyList())
        if (clauses.any { it.tam.modality != Modality.ASSERTED || it.hedged }) return null
        return when (field) {
            Field.EQUIPMENT_ASSET -> {
                val lower = value.lowercase()
                if (value.split(Regex("\\s+")).size > MAX_ITEM_WORDS || lower in INCIDENT_WORDS) return null
                if (lower == report.value(Field.LOCATION).text?.lowercase()) return null
                if (looksLikePlace(value)) return null
                // Seen on every set the same way (kit, synthetic, phone): a name, a role or a body part named as the thing involved.
                val named = listOf(Field.PERSONS_INVOLVED, Field.WITNESSES).flatMap { report.value(it).text.orEmpty().split(',') }
                    .map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (lower in named || named.any { n -> n in lower.split(' ') } || lower in NOT_ITEMS) return null
                Suggestion(field, "", value, "I may have missed it: you said \"$quote\". Add \"$value\" as a tool or item?", Source.LLM)
            }
            else -> Suggestion(field, "", value, "I may have missed it: you said \"$quote\". Add \"$value\" as an action taken?", Source.LLM)
        }
    }

    /** A campus PLACE (not an asset such as a projector) or something that ends in a place word is where it happened, never what it happened to. */
    private fun looksLikePlace(value: String): Boolean = PLACE_NOUN.containsMatchIn(value) || sites?.lookup(value)?.kind == EntryKind.PLACE

    private fun parseObject(reply: String): JsonObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { Json.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    companion object {
        private val ORDER = listOf(Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS)
        private const val MAX_CHARS = 1200
        private const val MAX_ITEM_WORDS = 4
        private val PLACE_NOUN = Regex("(?i)\\b(room|hall|block|floor|hostel|building|canteen|library|gate|corridor|lobby|campus|ground|office|lab)\\b(?:\\s*[\\w-]{1,3})?$")
        /** Roles and body parts are who or where it hurt, never the tool or item involved. */
        private val NOT_ITEMS = setOf(
            "guard", "security guard", "security", "nurse", "doctor", "student", "colleague", "coworker", "visitor", "worker", "staff", "driver",
            "person", "man", "woman", "boy", "girl", "people", "someone", "somebody", "head", "hand", "hands", "arm", "leg", "finger", "fingers",
            "eye", "eyes", "ankle", "foot", "feet", "knee", "back", "shoulder", "neck", "face", "wrist", "chest",
        )
        private val INCIDENT_WORDS = setOf(
            "leak", "water leak", "fire", "smoke", "spill", "fall", "accident", "incident", "injury", "flood", "sparks", "spark", "sparked",
        )

        private fun resource(path: String): String =
            FocusedProbe::class.java.getResourceAsStream(path)?.use { String(it.readBytes(), Charsets.UTF_8).trim() }
                ?: error("missing resource $path")
    }
}

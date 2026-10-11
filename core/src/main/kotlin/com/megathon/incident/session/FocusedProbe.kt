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
    private val peoplePrompt: String = resource("/prompts/probe_people_v1.txt"),
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
        chips += peopleChips(report, said, skip)
        return chips
    }

    /**
     * Named people the first pass left out: one call for both people fields. A name has to be in the worker's own words,
     * capitalised, not the worker, a role, a body part, a place or the location; a person who was hurt stays involved,
     * anyone else present is a witness. One chip per field, and only for a field that is empty and nothing else proposes.
     */
    private suspend fun peopleChips(report: IncidentReport, said: String, skip: Set<Field>): List<Suggestion> {
        val open = PEOPLE.filter { it !in skip && !report.isAnswered(it) }
        if (open.isEmpty()) return emptyList()
        val text = try {
            withTimeoutOrNull(timeoutMs) { llm.complete(LlmRequest(peoplePrompt, "Worker: \"$said\"")).text }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val obj = parseObject(text) ?: return emptyList()
        fun names(key: String): List<String> = when (val v = obj[key]) {
            is kotlinx.serialization.json.JsonArray -> v.mapNotNull { it.string()?.trim()?.trimEnd('.', ',', ';') }
            is JsonPrimitive -> listOfNotNull(v.string()?.trim()?.trimEnd('.', ',', ';'))
            else -> emptyList()
        }.filter { it.isNotEmpty() }
        val place = report.value(Field.LOCATION).let { listOfNotNull(it.text, it.evidence) }.joinToString(" ").lowercase()
        val taken = listOf(Field.PERSONS_INVOLVED, Field.WITNESSES).flatMap { f -> report.value(f).text.orEmpty().split(',') }
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        fun usable(name: String): Boolean {
            val words = name.split(Regex("\\s+"))
            if (words.size > 3 || !name[0].isUpperCase()) return false
            val lower = name.lowercase()
            if (lower in taken || lower in NOT_PEOPLE || words.any { it.lowercase() in NOT_PEOPLE }) return false
            if (!Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(said)) return false
            if (place.isNotEmpty() && Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(lower) + "(?![\\p{L}\\p{N}])").containsMatchIn(place)) return false
            return !looksLikePlace(name)
        }
        val hurt = Regex("(?i)\\b(slip|slipped|fell|fall|tripped|hurt|injured|cut|burned|burnt|fainted|collapsed|hit|trapped|bleeding|got|caught)\\b")
        fun involvedByWords(name: String) = said.split(Regex("(?<=[.!?;])\\s+")).any { it.contains(name, ignoreCase = true) && hurt.containsMatchIn(it) }
        val involved = names("persons_involved").filter(::usable).distinct()
        val witnesses = names("witnesses").filter(::usable).distinct()
        // Present and hurt is involved; present and not hurt is a witness, never both.
        val asWitness = witnesses.filter { !involvedByWords(it) && it !in involved }
        val asInvolved = (involved + witnesses.filter { involvedByWords(it) }).distinct()
        val chips = mutableListOf<Suggestion>()
        if (Field.WITNESSES in open && asWitness.isNotEmpty()) {
            chips += Suggestion(
                Field.WITNESSES, "", asWitness.joinToString(", "),
                "I may have missed it: ${asWitness.joinToString(" and ") { "\"$it\"" }} saw it or was there. Add as a witness?", Source.LLM,
            )
        }
        if (Field.PERSONS_INVOLVED in open && asInvolved.isNotEmpty()) {
            chips += Suggestion(
                Field.PERSONS_INVOLVED, "", asInvolved.joinToString(", "),
                "I may have missed it: ${asInvolved.joinToString(" and ") { "\"$it\"" }} is named as someone it happened to. Add as involved?", Source.LLM,
            )
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
        private val PEOPLE = listOf(Field.WITNESSES, Field.PERSONS_INVOLVED)
        /** Never a name: the worker, pronouns, roles, body parts and question words. */
        private val NOT_PEOPLE: Set<String> by lazy {
            setOf(
                "i", "me", "my", "we", "us", "our", "he", "she", "they", "him", "her", "them", "you", "someone", "somebody", "nobody",
                "everyone", "friend", "friends", "colleague", "colleagues", "manager", "supervisor", "the", "a", "an", "none", "no", "one",
            ) + NOT_ITEMS
        }
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

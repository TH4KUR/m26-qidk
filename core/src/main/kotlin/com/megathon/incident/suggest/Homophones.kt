package com.megathon.incident.suggest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * Words that sound alike ("weak"/"week", "to"/"two"/"2"), stored as groups so every lookup is
 * symmetric. One table for the whole of `:core`: the suggestion engine (D) and the near-miss
 * grounding rule (C) both read it. Pure lookup; deciding what a match means is the caller's job.
 *
 * Guarantees (tested): input is NFC + lower case; [variants] never contains the token itself;
 * [sameSound] is reflexive and symmetric, and compares multi-word text token by token with
 * equal token counts.
 */
class HomophoneTable(groups: List<List<String>>) {

    private val groupOf: Map<String, Set<String>>

    init {
        val map = mutableMapOf<String, Set<String>>()
        for (raw in groups) {
            val group = raw.map(::normalize).toSet()
            require(group.size >= 2) { "group $raw needs at least two different words" }
            for (token in group) {
                require(token.isNotEmpty() && SEPARATORS.find(token) == null) { "'$token' in $raw must be one word" }
                require(token !in map) { "'$token' is in two groups: ${map[token]} and $group" }
                map[token] = group
            }
        }
        groupOf = map
    }

    /** Every word in the table. */
    fun tokens(): Set<String> = groupOf.keys

    /** The other words that sound like [token]; empty when the table does not know it. */
    fun variants(token: String): Set<String> {
        val t = normalize(token)
        return groupOf[t]?.minus(t) ?: emptySet()
    }

    /** True when [a] and [b] have the same number of words and each pair is equal or sounds alike. */
    fun sameSound(a: String, b: String): Boolean {
        val ta = words(a)
        val tb = words(b)
        if (ta.size != tb.size) return false
        return ta.indices.all { i -> ta[i] == tb[i] || tb[i] in (groupOf[ta[i]] ?: emptySet()) }
    }

    companion object {
        private val SEPARATORS = Regex("[^\\p{L}\\p{M}\\p{N}]+")
        private val json = Json { ignoreUnknownKeys = true }

        private fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().trim()

        private fun words(text: String): List<String> =
            normalize(text).split(SEPARATORS).filter { it.isNotEmpty() }

        fun fromJson(text: String): HomophoneTable = HomophoneTable(json.decodeFromString<HomophoneDoc>(text).groups)
    }
}

/** The shipped table, loaded once from `suggest/homophones.json`. */
object Homophones {
    private const val PATH = "/suggest/homophones.json"

    val default: HomophoneTable by lazy {
        val stream = Homophones::class.java.getResourceAsStream(PATH) ?: error("missing resource $PATH")
        HomophoneTable.fromJson(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    fun variants(token: String): Set<String> = default.variants(token)

    fun sameSound(a: String, b: String): Boolean = default.sameSound(a, b)
}

@Serializable
private data class HomophoneDoc(val groups: List<List<String>>)

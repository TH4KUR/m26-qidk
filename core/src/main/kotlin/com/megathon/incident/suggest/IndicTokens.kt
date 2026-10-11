package com.megathon.incident.suggest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * Hindi/Telugu lane (A/020, experimental, D19): English readings of words as a recogniser may write them in
 * Devanagari or Telugu script ("सेमिनार" -> seminar, "బి" -> b), and Hindi/Telugu number words in script and Roman
 * ("दो", "rendu" -> 2). [GazetteerSnapper] tries these readings like sound-alikes, so a reading only counts when the
 * whole window then names a site entry exactly. The table is model-authored and unreviewed (`label_source`).
 */
class IndicTokenTable(tokens: Map<String, List<String>>) {

    private val readings: Map<String, List<String>> = tokens.entries.associate { (k, v) -> normalize(k) to v.map(::normalize) }

    /** English readings of [token]; empty when the table does not know it. */
    fun english(token: String): List<String> = readings[normalize(token)] ?: emptyList()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().trim()

        fun fromJson(text: String): IndicTokenTable = IndicTokenTable(json.decodeFromString<IndicTokenDoc>(text).tokens)

        val EMPTY = IndicTokenTable(emptyMap())
    }
}

/** The shipped table, loaded once from `suggest/indic_tokens.json`. */
object IndicTokens {
    private const val PATH = "/suggest/indic_tokens.json"

    val default: IndicTokenTable by lazy {
        val stream = IndicTokens::class.java.getResourceAsStream(PATH) ?: error("missing resource $PATH")
        IndicTokenTable.fromJson(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}

@Serializable
private data class IndicTokenDoc(val tokens: Map<String, List<String>>)

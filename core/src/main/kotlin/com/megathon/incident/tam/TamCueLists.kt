package com.megathon.incident.tam

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

@Serializable
internal data class CueFile(
    val language: String = "",
    val note: String = "",
    val ongoing: List<String> = emptyList(),
    val progressive: List<String> = emptyList(),
    val completed: List<String> = emptyList(),
    val resolution: List<String> = emptyList(),
    val planned: List<String> = emptyList(),
    val hedged: List<String> = emptyList(),
    val negated: List<String> = emptyList(),
    val past: List<String> = emptyList(),
    val present: List<String> = emptyList(),
    val future: List<String> = emptyList(),
    val clauseBreaks: List<String> = emptyList(),
)

/**
 * Cue phrases per category, merged across languages: Hinglish speech mixes English and Hindi in one
 * sentence, so the detector always works on the union. Lists live in the `tam` resource folder
 * (`cues_en.json`, `cues_hi.json`).
 */
data class TamCueLists(
    /** Explicit continuation: "still", "since morning", "abhi bhi". */
    val ongoing: List<String>,
    /** Plain progressive markers (Hindi "raha hai"); ongoing, but not an escalation by themselves. */
    val progressive: List<String>,
    val completed: List<String>,
    /** The subset of completion cues meaning the problem itself ended (not "called", "informed"). */
    val resolution: List<String>,
    val planned: List<String>,
    val hedged: List<String>,
    val negated: List<String>,
    val past: List<String>,
    val present: List<String>,
    val future: List<String>,
    val clauseBreaks: List<String>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val DEFAULT_FILES = listOf("/tam/cues_en.json", "/tam/cues_hi.json")

        fun fromJson(vararg documents: String): TamCueLists {
            val files = documents.map { json.decodeFromString(CueFile.serializer(), it) }
            fun merged(pick: (CueFile) -> List<String>) = files.flatMap(pick).map { Normalizer.normalize(it, Normalizer.Form.NFC).lowercase().trim() }.filter { it.isNotEmpty() }.distinct()
            return TamCueLists(
                ongoing = merged { it.ongoing }, progressive = merged { it.progressive }, completed = merged { it.completed }, resolution = merged { it.resolution }, planned = merged { it.planned },
                hedged = merged { it.hedged }, negated = merged { it.negated }, past = merged { it.past },
                present = merged { it.present }, future = merged { it.future }, clauseBreaks = merged { it.clauseBreaks },
            )
        }

        fun loadDefault(): TamCueLists {
            val docs = DEFAULT_FILES.map { path ->
                val stream = TamCueLists::class.java.getResourceAsStream(path)
                    ?: error("missing cue list resource $path")
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            return fromJson(*docs.toTypedArray())
        }
    }
}

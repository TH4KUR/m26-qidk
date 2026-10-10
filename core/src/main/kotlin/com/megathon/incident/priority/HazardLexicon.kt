package com.megathon.incident.priority

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One hazard family. [impact] is 1..5; phrases are lower-case English, romanised Hindi or Devanagari. */
@Serializable
data class HazardDef(
    val label: String,
    val impact: Int,
    val phrases: List<String>,
    /** Longer phrases that contain a trigger word but are not the hazard ("fire drill"). */
    val notWhen: List<String> = emptyList(),
    /** In present or unknown tense this hazard is happening now (fire, gas, an armed robber). */
    val presentIsOngoing: Boolean = false,
    /** The hazard is itself about people (a fight, an injury), so a people cue must not add +1 again. */
    val peopleIntrinsic: Boolean = false,
)

/** A hazard that only counts when a required hazard and one of [andAnyOf] share a clause. */
@Serializable
data class CompoundDef(
    val label: String,
    val requires: List<String>,
    val andAnyOf: List<String>,
    val impact: Int,
    val forceOngoing: Boolean = false,
    val peopleIntrinsic: Boolean = false,
)

/** A sound event (YamNet) raising the minimum impact and immediacy. Never lowers anything. */
@Serializable
data class SignalDef(val label: String, val matches: List<String>, val minImpact: Int, val minImmediacy: Int)

@Serializable
data class LexiconFile(
    val defaultImpact: Int = 2,
    val hazards: List<HazardDef> = emptyList(),
    val compounds: List<CompoundDef> = emptyList(),
    val peopleCues: List<String> = emptyList(),
    val resolutionCues: List<String> = emptyList(),
    val lowUrgencyCues: List<String> = emptyList(),
    val distressCues: List<String> = emptyList(),
    val distressEmotions: List<String> = emptyList(),
    val signals: List<SignalDef> = emptyList(),
)

/** Editable data (`resources/priority/hazards.json`); a teammate can tune it without touching code. */
class HazardLexicon(val file: LexiconFile) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val DEFAULT_RESOURCE = "/priority/hazards.json"

        fun fromJson(document: String) = HazardLexicon(json.decodeFromString(LexiconFile.serializer(), document))

        fun loadDefault(): HazardLexicon {
            val stream = HazardLexicon::class.java.getResourceAsStream(DEFAULT_RESOURCE)
                ?: error("missing resource $DEFAULT_RESOURCE")
            return fromJson(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
    }
}

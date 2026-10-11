package com.megathon.incident.scoring

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.priorityForScore
import com.megathon.incident.priority.HazardLexicon
import com.megathon.incident.priority.PriorityRules
import com.megathon.incident.priority.UrgencyFeatureExtractor
import com.megathon.incident.priority.UrgencyInput
import com.megathon.incident.priority.UrgencyStrategies
import com.megathon.incident.priority.UrgencyStrategy
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlinx.serialization.Serializable

/** Where an audio-derived input came from: the voice emotion tag, a sound event, or the words the speech engine heard. */
enum class VoiceTermKind { EMOTION, SOUND, WORDS }

/**
 * One audio-derived input to the priority. [rule] says in words how the real rules treat it; [minImpact] and
 * [minImmediacy] are the floors the rule list sets for a sound; [points] is what the 1..20 score would lose if this
 * input were removed (null for the words, which act together and are summarised by [PriorityInfluence.wordsOnlyScore]).
 */
@Serializable
data class VoiceTerm(
    val kind: VoiceTermKind,
    val label: String,
    val source: String,
    val rule: String,
    val counted: Boolean,
    val minImpact: Int? = null,
    val minImmediacy: Int? = null,
    val points: Int? = null,
)

/**
 * How the words and the voice fed the priority, from a replay of the real rules on the same inputs. The live priority can
 * be higher (the language model's hint and the reporter's answers also count, and it never drops during a report), so this
 * is "what the rules take from the audio", not a second priority.
 */
@Serializable
data class PriorityInfluence(
    val terms: List<VoiceTerm>,
    /** 1..20 from the words alone. */
    val wordsOnlyScore: Int,
    /** 1..20 from the words and the voice tags. */
    val withVoiceScore: Int,
    val impact: Int,
    val immediacy: Int,
    val level: String,
    val strategy: String,
    val formula: String,
    val thresholds: String,
) {
    /** What the voice tags added to the words-only score. Never negative: tags only raise. */
    val voicePoints: Int get() = withVoiceScore - wordsOnlyScore
}

/**
 * Replays the app's own priority rules ([UrgencyFeatureExtractor] and the default [UrgencyStrategy]) on a transcript and
 * the voice tags, with and without each audio-derived input, so the screen can show exactly what each one contributed.
 * Pure: nothing is stored and the transcript is not kept in the result.
 */
class PriorityExplainer(
    private val extractor: UrgencyFeatureExtractor,
    private val strategy: UrgencyStrategy,
    private val lexicon: HazardLexicon,
) {
    private fun score(transcript: String, signals: List<Signal>) = strategy.assess(extractor.extract(UrgencyInput(transcript, signals)))

    fun explain(transcript: String, signals: List<Signal>): PriorityInfluence {
        val audio = signals.filter { it.kind == KIND_EMOTION || it.kind == KIND_EVENT }
        val all = score(transcript, audio)
        val wordsOnly = score(transcript, emptyList())

        val terms = mutableListOf<VoiceTerm>()
        // Words: the cues the rules found in what was said, with any quoted phrase removed so no sentence is stored.
        val words = extractor.extract(UrgencyInput(transcript))
        for (r in words.reasons.filter { it.source == Source.TEXT_CUE }) {
            val clean = QUOTED.replace(r.text, "").replace("  ", " ").trim()
            terms += VoiceTerm(
                VoiceTermKind.WORDS, clean, r.source.name, "found in the recognised words: $clean",
                counted = true,
            )
        }
        for ((kind, label, source) in audio.map { Triple(it.kind, it.label, it.source) }.distinct()) {
            val without = score(transcript, audio.filterNot { it.kind == kind && it.label == label }).score
            val points = all.score - without
            terms += if (kind == KIND_EVENT) {
                val def = lexicon.file.signals.firstOrNull { d -> d.matches.any { label.lowercase().contains(it) } }
                if (def != null) {
                    VoiceTerm(
                        VoiceTermKind.SOUND, label, source.name,
                        "${def.label} is on the rule list: impact at least ${def.minImpact}, immediacy at least ${def.minImmediacy}",
                        counted = true, minImpact = def.minImpact, minImmediacy = def.minImmediacy, points = points,
                    )
                } else {
                    VoiceTerm(
                        VoiceTermKind.SOUND, label, source.name,
                        "no priority rule uses this sound, so it changes nothing",
                        counted = false, points = points,
                    )
                }
            } else {
                val distress = lexicon.file.distressEmotions.any { label.equals(it, ignoreCase = true) }
                if (distress) {
                    VoiceTerm(
                        VoiceTermKind.EMOTION, label, source.name,
                        "a $label voice counts as distress: immediacy goes up by 1, at most 4",
                        counted = true, points = points,
                    )
                } else {
                    VoiceTerm(
                        VoiceTermKind.EMOTION, label, source.name,
                        "no priority rule uses a $label voice, so it changes nothing",
                        counted = false, points = points,
                    )
                }
            }
        }

        return PriorityInfluence(
            terms = terms,
            wordsOnlyScore = wordsOnly.score,
            withVoiceScore = all.score,
            impact = all.impact,
            immediacy = all.immediacy,
            level = priorityForScore(all.score).name,
            strategy = strategy.id,
            formula = FORMULA,
            thresholds = THRESHOLDS,
        )
    }

    companion object {
        const val KIND_EMOTION = "emotion"
        const val KIND_EVENT = "event"

        private val QUOTED = Regex("\\s*\\(\"[^\"]*\"\\)|\"[^\"]*\"")

        const val FORMULA =
            "Priority score (1 to 20) = impact (1 to 5) x immediacy (1 to 4). The words set impact and immediacy. " +
                "Voice tags can only raise them: a sound on the rule list sets a minimum impact and immediacy, and an angry, " +
                "sad or fearful voice counts as distress, which adds 1 to immediacy (at most 4). Nothing lowers the score."
        const val THRESHOLDS = "Levels: 1 to 4 LOW, 5 to 9 MEDIUM, 10 to 14 HIGH, 15 to 20 URGENT."

        /** The same wiring as the app's session: the packaged hazard list, the cue lists and the default strategy. */
        fun default(): PriorityExplainer {
            val lexicon = HazardLexicon.loadDefault()
            val extractor = UrgencyFeatureExtractor(lexicon, TamDetector(TamCueLists.loadDefault()))
            val strategy = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == UrgencyStrategies.DEFAULT_ID }
            return PriorityExplainer(extractor, strategy, lexicon)
        }
    }
}

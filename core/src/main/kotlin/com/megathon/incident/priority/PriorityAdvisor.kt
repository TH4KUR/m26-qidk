package com.megathon.incident.priority

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.LlmHint
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.TriageLane
import com.megathon.incident.domain.UrgencyAssessment
import com.megathon.incident.domain.UrgencyReason
import com.megathon.incident.domain.priorityForScore

/** A one-tap offer to lower the level after the reporter cancelled an alarm. Never applied by itself. */
data class Deescalation(val offerLevel: Priority, val reason: String)

enum class KeyAnswer { YES, NO, UNSURE }

/** What the advisor tells the app after one more chunk of speech (or one answered question). */
data class AdvisorOutput(
    val advice: PriorityAdvice,
    val score: Int,
    /** The emergency line to speak now; non-null only the first time it becomes warranted. */
    val spoken: String?,
    /** Set when the reporter cancelled an earlier alarm: show a "lower to LOW?" chip. */
    val deescalation: Deescalation? = null,
    /** Set once, when nothing in the report was recognised: ask this before anything else. */
    val keyQuestion: String? = null,
)

data class OverrideEntry(val from: Priority, val to: Priority, val reason: String)

object TriageLanes {
    private val STANDARD = listOf(Field.INCIDENT_TYPE, Field.LOCATION, Field.DATE_TIME, Field.SEVERITY)

    /** Design doc 2.3: the more urgent the incident, the fewer the questions and the less ceremony. */
    fun forLevel(level: Priority): TriageLane = when (level) {
        Priority.URGENT -> TriageLane(level, 1, listOf(Field.LOCATION), requireConfirmation = false, speak112 = true, offerSubmitNow = true)
        Priority.HIGH -> TriageLane(level, 1, listOf(Field.LOCATION, Field.INCIDENT_TYPE), requireConfirmation = false, speak112 = false, offerSubmitNow = true)
        Priority.MEDIUM -> TriageLane(level, 2, STANDARD, requireConfirmation = false, speak112 = false, offerSubmitNow = false)
        Priority.LOW -> TriageLane(
            level, 2, STANDARD + listOf(Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS, Field.WITNESSES),
            requireConfirmation = true, speak112 = false, offerSubmitNow = false,
        )
    }
}

/**
 * Live priority advice for one incident session (spec D18).
 *
 *  - Re-scores the *whole* transcript after every chunk, then keeps the highest score seen: **the
 *    automatic score only ever rises**. A calm follow-up, "nobody hurt" or a resolution never lowers it.
 *  - Only the user can lower the effective level, and that is journaled. A later, higher automatic
 *    level (new evidence) clears a stale override. A cancellation such as "false alarm" is *offered*
 *    as a chip; the user taps to apply it.
 *  - The emergency line is spoken once, when URGENT is first reached or a danger sound is heard.
 *  - A report the rules do not recognise gets one dispatch-style key question ("hurt or in danger
 *    right now?"); an unsure answer is treated as danger, as emergency dispatch protocols do.
 *  - The on-device model's [LlmHint] can raise the result, never lower it, and cannot reach URGENT
 *    alone (it is capped one step above the rules).
 */
class PriorityAdvisor(
    private val extractor: UrgencyFeatureExtractor,
    private val strategy: UrgencyStrategy,
) {
    private val transcript = StringBuilder()
    private val signals = mutableListOf<Signal>()
    private var best: UrgencyAssessment? = null
    private var emergencySpoken = false

    private var hintImpact: Int? = null
    private var hintOngoing = false

    private var floorScore = 0
    private var floorReason: UrgencyReason? = null

    /** What the reporter stated in the report (a severity, an incident type). Separate from the key-question floor. */
    private var statedFloorScore = 0
    private var statedFloorReason: UrgencyReason? = null
    private var keyQuestionAsked = false

    private var pendingDeescalation: Deescalation? = null

    private var overrideLevel: Priority? = null
    private var overrideReason: String = ""
    private var autoLevelAtOverride: Priority = Priority.LOW
    private val entries = mutableListOf<OverrideEntry>()

    /** Highest automatic 1..20 score so far (0 before the first chunk). */
    val currentScore: Int get() = best?.score ?: 0

    val journal: List<OverrideEntry> get() = entries.toList()

    private val autoLevel: Priority get() = best?.level ?: Priority.LOW

    fun onChunk(text: String, newSignals: List<Signal> = emptyList(), hint: LlmHint? = null): AdvisorOutput {
        if (text.isNotBlank()) {
            if (transcript.isNotEmpty()) transcript.append(' ')
            transcript.append(text.trim())
        }
        signals += newSignals
        hint?.impact?.let { hintImpact = maxOf(hintImpact ?: 0, it) }
        if (hint?.ongoing == true) hintOngoing = true

        val features = rescore()

        // New evidence above the level at which the user overrode retires the override.
        val ov = overrideLevel
        if (ov != null && autoLevel > autoLevelAtOverride) {
            entries += OverrideEntry(from = ov, to = autoLevel, reason = "cleared: new evidence raised the automatic level")
            overrideLevel = null
        }

        pendingDeescalation = offerDeescalation(text)

        val keyQuestion = if (
            !keyQuestionAsked && text.isNotBlank() && features.hazards.isEmpty() &&
            signals.isEmpty() && autoLevel < Priority.HIGH
        ) {
            keyQuestionAsked = true
            KEY_QUESTION
        } else null

        return output(features, keyQuestion)
    }

    /**
     * What the reporter has stated in the report raises the advice and never lowers it: a severity they said ("critical" is
     * URGENT, "severe" HIGH, "moderate" MEDIUM) and an incident type that is a hazard in itself (fire, electrical, injury,
     * chemical, security, illness: at least MEDIUM, never URGENT alone). Only filled values count (heard or confirmed);
     * a suggestion is not the reporter's word. Call after each merge; calling again with the same report changes nothing.
     */
    fun onReport(report: IncidentReport): AdvisorOutput {
        val sev = report.value(Field.SEVERITY)
        if (report.isFilled(Field.SEVERITY)) {
            when (Severity.fromWire(sev.text)) {
                Severity.CRITICAL -> setStatedFloor(15, "reporter called it critical")
                Severity.SEVERE -> setStatedFloor(10, "reporter called it severe")
                Severity.MODERATE -> setStatedFloor(5, "reporter called it moderate")
                else -> Unit
            }
        }
        val type = report.value(Field.INCIDENT_TYPE)
        if (report.isFilled(Field.INCIDENT_TYPE) && IncidentType.fromWire(type.text) in HAZARD_TYPES) {
            setStatedFloor(6, "incident type ${type.text} is a hazard in itself")
        }
        return output(rescore(), keyQuestion = null)
    }

    /** The reporter's answer to [KEY_QUESTION]. YES raises to URGENT, UNSURE to HIGH, NO changes nothing. */
    fun answerKeyQuestion(answer: KeyAnswer): AdvisorOutput {
        keyQuestionAsked = true
        when (answer) {
            KeyAnswer.YES -> setFloor(16, "reporter answered yes to \"$KEY_QUESTION\"")
            KeyAnswer.UNSURE -> setFloor(12, "reporter was unsure whether anyone is in danger; treated as danger")
            KeyAnswer.NO -> Unit
        }
        return output(rescore(), keyQuestion = null)
    }

    /** The user decides the level. Returns the new advice; the change is journaled. */
    fun override(level: Priority, reason: String): PriorityAdvice {
        val from = overrideLevel ?: autoLevel
        entries += OverrideEntry(from, level, reason)
        overrideLevel = level
        overrideReason = reason
        autoLevelAtOverride = autoLevel
        return advice()
    }

    /** Applies the pending cancellation offer, if any. */
    fun acceptDeescalation(): PriorityAdvice {
        val offer = pendingDeescalation ?: return advice()
        pendingDeescalation = null
        return override(offer.offerLevel, "reporter correction: ${offer.reason}")
    }

    fun dismissDeescalation() {
        pendingDeescalation = null
    }

    fun lane(): TriageLane = TriageLanes.forLevel(overrideLevel ?: autoLevel)

    // ---- internals -----------------------------------------------------------------------------

    private fun rescore(): UrgencyFeatures {
        val hint = if (hintImpact != null || hintOngoing) LlmHint(hintImpact, hintOngoing.takeIf { it }) else null
        val features = extractor.extract(UrgencyInput(transcript.toString(), signals.toList(), hint))
        var assessment = strategy.assess(features)
        val (floor, why) = if (statedFloorScore > floorScore) statedFloorScore to statedFloorReason else floorScore to floorReason
        if (floor > assessment.score) {
            assessment = assessment.copy(
                score = floor,
                level = priorityForScore(floor),
                reasons = assessment.reasons + listOfNotNull(why),
            )
        }
        if (best == null || assessment.score > best!!.score) best = assessment
        return features
    }

    private fun setStatedFloor(score: Int, why: String) {
        if (score > statedFloorScore) {
            statedFloorScore = score
            statedFloorReason = UrgencyReason(why, Source.TEXT_CUE)
        }
    }

    private fun setFloor(score: Int, why: String) {
        if (score > floorScore) {
            floorScore = score
            floorReason = UrgencyReason(why, Source.USER)
        }
    }

    private fun offerDeescalation(chunk: String): Deescalation? {
        if (chunk.isBlank() || overrideLevel != null || autoLevel < Priority.MEDIUM) return null
        val cue = extractor.cancelCue(chunk) ?: return null
        // "sorry, but there is also a gas leak" is a new report, not a cancellation.
        if (extractor.extract(UrgencyInput(chunk)).hazards.isNotEmpty()) return null
        return Deescalation(Priority.LOW, "reporter said \"$cue\"")
    }

    private fun output(features: UrgencyFeatures, keyQuestion: String?): AdvisorOutput {
        val dangerHeard = features.dangerSignals.isNotEmpty()
        val spoken = if (!emergencySpoken && (autoLevel == Priority.URGENT || dangerHeard)) {
            emergencySpoken = true
            EMERGENCY_LINE
        } else null
        return AdvisorOutput(advice(), currentScore, spoken, pendingDeescalation, keyQuestion)
    }

    private fun advice(): PriorityAdvice {
        val reasons = (best?.reasons ?: emptyList()).map { "${it.text} [${it.source}]" }
        val ov = overrideLevel
        return if (ov != null) {
            PriorityAdvice(ov, reasons + "reporter override: $overrideReason [USER]", overriddenByUser = true)
        } else {
            PriorityAdvice(autoLevel, reasons, overriddenByUser = false)
        }
    }

    companion object {
        const val EMERGENCY_LINE = "If anyone is in danger, call 112 now."
        const val KEY_QUESTION = "Is anyone hurt or in danger right now?"

        /** Types that are a hazard in themselves; a stated one lifts the advice to MEDIUM (see [onReport]). */
        private val HAZARD_TYPES = setOf(
            IncidentType.FIRE_SMOKE, IncidentType.ELECTRICAL, IncidentType.INJURY,
            IncidentType.SPILL_CHEMICAL, IncidentType.SECURITY, IncidentType.ILLNESS,
        )
    }
}

package com.megathon.incident.priority

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.TriageLane
import com.megathon.incident.domain.UrgencyAssessment

/** What the advisor tells the app after one more chunk of speech. */
data class AdvisorOutput(
    val advice: PriorityAdvice,
    val score: Int,
    /** The emergency line to speak now; non-null only the first time it becomes warranted. */
    val spoken: String?,
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
 *    level (new evidence) clears a stale override.
 *  - The emergency line is spoken once, when URGENT is first reached or a danger sound is heard.
 */
class PriorityAdvisor(
    private val extractor: UrgencyFeatureExtractor,
    private val strategy: UrgencyStrategy,
) {
    private val transcript = StringBuilder()
    private val signals = mutableListOf<Signal>()
    private var best: UrgencyAssessment? = null
    private var emergencySpoken = false

    private var overrideLevel: Priority? = null
    private var overrideReason: String = ""
    private var autoLevelAtOverride: Priority = Priority.LOW
    private val entries = mutableListOf<OverrideEntry>()

    /** Highest automatic 1..20 score so far (0 before the first chunk). */
    val currentScore: Int get() = best?.score ?: 0

    val journal: List<OverrideEntry> get() = entries.toList()

    private val autoLevel: Priority get() = best?.level ?: Priority.LOW

    fun onChunk(text: String, newSignals: List<Signal> = emptyList()): AdvisorOutput {
        if (text.isNotBlank()) {
            if (transcript.isNotEmpty()) transcript.append(' ')
            transcript.append(text.trim())
        }
        signals += newSignals

        val features = extractor.extract(UrgencyInput(transcript.toString(), signals.toList()))
        val assessment = strategy.assess(features)
        if (best == null || assessment.score > best!!.score) best = assessment

        // New evidence above the level at which the user overrode retires the override.
        val ov = overrideLevel
        if (ov != null && autoLevel > autoLevelAtOverride) {
            entries += OverrideEntry(from = ov, to = autoLevel, reason = "cleared: new evidence raised the automatic level")
            overrideLevel = null
        }

        val dangerHeard = features.dangerSignals.isNotEmpty()
        val spoken = if (!emergencySpoken && (autoLevel == Priority.URGENT || dangerHeard)) {
            emergencySpoken = true
            EMERGENCY_LINE
        } else null

        return AdvisorOutput(advice(), currentScore, spoken)
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

    fun lane(): TriageLane = TriageLanes.forLevel(overrideLevel ?: autoLevel)

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
    }
}

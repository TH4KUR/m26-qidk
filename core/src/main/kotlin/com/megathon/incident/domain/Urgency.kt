package com.megathon.incident.domain

import kotlinx.serialization.Serializable

/** Why a score is what it is. Shown to the user; [source] names where the evidence came from. */
@Serializable
data class UrgencyReason(val text: String, val source: Source)

/**
 * The 1-20 urgency of an incident (design doc 2). This is advice for the app's behaviour, never the
 * reporter-stated severity field (spec D18).
 *
 * @property score 1..20, `impact * immediacy`-shaped depending on [strategy]
 * @property impact 1..5 how bad the worst credible outcome is
 * @property immediacy 1..4 how soon action is needed
 */
@Serializable
data class UrgencyAssessment(
    val score: Int,
    val level: Priority,
    val impact: Int,
    val immediacy: Int,
    val reasons: List<UrgencyReason>,
    val strategy: String,
)

/** Maps the 1..20 score to the four advice levels (design doc 2.1). */
fun priorityForScore(score: Int): Priority = when {
    score >= 15 -> Priority.URGENT
    score >= 10 -> Priority.HIGH
    score >= 5 -> Priority.MEDIUM
    else -> Priority.LOW
}

/**
 * How the follow-up flow should behave at an urgency level (design doc 2.3). Produced by C, obeyed by
 * D's planner and A's UI.
 */
@Serializable
data class TriageLane(
    val level: Priority,
    val maxQuestionsPerTurn: Int,
    val mandatoryFields: List<Field>,
    val requireConfirmation: Boolean,
    val speak112: Boolean,
    val offerSubmitNow: Boolean,
)

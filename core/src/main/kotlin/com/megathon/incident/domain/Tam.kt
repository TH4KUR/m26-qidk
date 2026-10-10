package com.megathon.incident.domain

import kotlinx.serialization.Serializable

/** Spec §4b / D17. Rule-based on the CPU; every consumer must show it as a TEXT_CUE source. */
@Serializable
enum class Tense { PAST, PRESENT, FUTURE, UNKNOWN }

/** ONGOING = the state or hazard continues at the time of speaking; COMPLETED = it was done or ended. */
@Serializable
enum class Aspect { ONGOING, COMPLETED, NONE }

/** Precedence when several apply: NEGATED > PLANNED > HEDGED > ASSERTED. */
@Serializable
enum class Modality { ASSERTED, HEDGED, NEGATED, PLANNED }

@Serializable
data class Tam(val tense: Tense, val aspect: Aspect, val modality: Modality)

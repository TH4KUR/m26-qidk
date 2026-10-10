package com.megathon.incident.domain

import kotlinx.serialization.Serializable

/** Incident categories; [wire] is the exact value in the LLM contract (spec §4). */
@Serializable
enum class IncidentType(val wire: String) {
    WATER_LEAK_FLOOD("water_leak_flood"),
    FIRE_SMOKE("fire_smoke"),
    ELECTRICAL("electrical"),
    SPILL_CHEMICAL("spill_chemical"),
    INJURY("injury"),
    ILLNESS("illness"),
    NEAR_MISS("near_miss"),
    PROPERTY_DAMAGE("property_damage"),
    EQUIPMENT_FAILURE("equipment_failure"),
    SECURITY("security"),
    ENVIRONMENTAL("environmental"),
    OTHER("other");

    companion object {
        fun fromWire(value: String?): IncidentType? = entries.firstOrNull { it.wire == value }
    }
}

/** Reporter-stated severity (spec D18). `NONE` is a stated level, not the missing sentinel. */
@Serializable
enum class Severity(val wire: String) {
    NONE("none"), MINOR("minor"), MODERATE("moderate"), SEVERE("severe"), CRITICAL("critical");

    companion object {
        fun fromWire(value: String?): Severity? = entries.firstOrNull { it.wire == value }
    }
}

@Serializable
enum class Field {
    INCIDENT_TYPE, SEVERITY, LOCATION, DATE_TIME, PERSONS_INVOLVED,
    EQUIPMENT_ASSET, DESCRIPTION, IMMEDIATE_ACTIONS, WITNESSES, RECOMMENDED_FOLLOW_UP,
}

/** Spec §4b. Only REPORTED, USER_CONFIRMED and USER_EDITED count as filled. */
@Serializable
enum class ValueStatus { MISSING, UNKNOWN, EXPLICIT_NONE, REPORTED, SUGGESTED, USER_CONFIRMED, USER_EDITED }

/** Where a signal, suggestion or reason came from; shown on every chip (spec §4b). */
@Serializable
enum class Source { TEXT_CUE, SENSEVOICE, YAMNET, GAZETTEER, RULE, LLM, USER }

@Serializable
data class FieldValue(
    val text: String?,
    val status: ValueStatus,
    val evidence: String? = null,
) {
    companion object {
        val MISSING = FieldValue(null, ValueStatus.MISSING)
        fun explicitNone(evidence: String) = FieldValue(null, ValueStatus.EXPLICIT_NONE, evidence)
        fun unknown(evidence: String) = FieldValue(null, ValueStatus.UNKNOWN, evidence)
    }
}

@Serializable
data class IncidentReport(
    val id: String,
    val createdAtEpochMs: Long,
    val fields: Map<Field, FieldValue> = emptyMap(),
) {
    fun value(field: Field): FieldValue = fields[field] ?: FieldValue.MISSING

    fun with(field: Field, value: FieldValue): IncidentReport = copy(fields = fields + (field to value))

    /** Has a usable value (heard, confirmed or typed). */
    fun isFilled(field: Field): Boolean = value(field).status in FILLED

    /** The question has an answer, including "none" or "I don't know". */
    fun isAnswered(field: Field): Boolean = value(field).status in ANSWERED

    companion object {
        private val FILLED = setOf(ValueStatus.REPORTED, ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED)
        private val ANSWERED = FILLED + setOf(ValueStatus.EXPLICIT_NONE, ValueStatus.UNKNOWN)

        fun empty(id: String, nowEpochMs: Long) = IncidentReport(id, nowEpochMs)
    }
}

/** Raw `update_report` arguments exactly as the LLM proposed them (spec §4). Not yet validated. */
@Serializable
data class ExtractionPayload(
    val incident_type: String? = null,
    val severity: String? = null,
    val location: String? = null,
    val date_time_text: String? = null,
    val persons_involved: List<String> = emptyList(),
    val equipment_asset: String? = null,
    val immediate_actions: String? = null,
    val witnesses: List<String> = emptyList(),
    val evidence: Map<String, String> = emptyMap(),
    /** Optional urgency hint, 1..5. Never written to a field; it can only raise the priority advice. */
    val impact_hint: Int? = null,
    /** Optional: the model thinks the problem is still continuing. Same raise-only rule. */
    val ongoing: Boolean? = null,
)

@Serializable
data class Signal(val kind: String, val label: String, val source: Source, val segmentId: Long? = null)

@Serializable
data class Suggestion(val field: Field, val original: String, val proposed: String, val reason: String, val source: Source)

/** Declared in ascending urgency so `compareTo` orders LOW < MEDIUM < HIGH < URGENT. */
@Serializable
enum class Priority { LOW, MEDIUM, HIGH, URGENT }

@Serializable
data class PriorityAdvice(val level: Priority, val reasons: List<String>, val overriddenByUser: Boolean = false)

/** What happened to a field. The original value is always kept in [JournalEntry.before]. */
@Serializable
enum class JournalKind { HEARD, CORRECTED, SUGGESTION_ACCEPTED, SUGGESTION_REJECTED, USER_EDITED }

/**
 * One append-only change to a field (spec D16: "the original stays in the journal").
 * Type owned by C; the logic that appends entries and the storage that persists them are D's.
 */
@Serializable
data class JournalEntry(
    val atEpochMs: Long,
    val field: Field,
    val kind: JournalKind,
    val before: FieldValue,
    val after: FieldValue,
    /** Set for SUGGESTION_ACCEPTED / SUGGESTION_REJECTED so the same chip is not offered again. */
    val suggestion: Suggestion? = null,
)

/** The report plus its change journal. Pending chips are not stored: `suggest()` is recomputed. */
@Serializable
data class ReportState(val report: IncidentReport, val journal: List<JournalEntry> = emptyList()) {
    fun record(entry: JournalEntry): ReportState = copy(journal = journal + entry)

    /** True when this exact chip was already accepted or rejected, so it must not reappear. */
    fun hasDecided(s: Suggestion): Boolean = journal.any {
        (it.kind == JournalKind.SUGGESTION_ACCEPTED || it.kind == JournalKind.SUGGESTION_REJECTED) &&
            it.suggestion?.let { x -> x.field == s.field && x.original == s.original && x.proposed == s.proposed } == true
    }

    companion object {
        fun empty(id: String, nowEpochMs: Long) = ReportState(IncidentReport.empty(id, nowEpochMs))
    }
}

/**
 * The on-device language model's optional opinion on urgency. It can only raise the rules' result,
 * by at most one impact step, and cannot reach URGENT on its own (design doc, Q6).
 */
@Serializable
data class LlmHint(val impact: Int? = null, val ongoing: Boolean? = null) {
    companion object {
        /** Out-of-range values are dropped rather than clamped: a wild number is not a signal. */
        fun from(p: ExtractionPayload): LlmHint? {
            val impact = p.impact_hint?.takeIf { it in 1..5 }
            return if (impact == null && p.ongoing == null) null else LlmHint(impact, p.ongoing)
        }
    }
}

package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.priority.Deescalation
import com.megathon.incident.testing.Compute

/** What the screen shows for one field. Word + icon + colour are chosen from this, never colour alone. */
enum class UiStatus { HEARD, SUGGESTED, CONFIRMED, NEEDED, NONE, UNKNOWN, EMPTY }

data class FieldView(
    val field: Field,
    val label: String,
    val mandatory: Boolean,
    val value: String?,
    val status: UiStatus,
    /** The reporter's own words that support the value. */
    val evidence: String?,
    /** One short line of explanation: why it is suggested, what is asked. */
    val hint: String?,
    /** Filled by the assistant at submit time, not heard: labelled as such (disclosure). */
    val writtenByAssistant: Boolean,
)

/** A ready-made answer the reporter can tap instead of speaking. */
data class QuickReply(val label: String, val field: Field, val value: String, val suggested: Boolean = false)

data class Ask(val field: Field, val prompt: String, val replies: List<QuickReply>)

/**
 * At most two [Ask]s per turn (lane cap). [spoken] is what the device says aloud.
 * A [key] question is the advisor's dispatch-style "anyone hurt or in danger?": no asks, answered Yes / No / Not sure.
 */
data class Question(val asks: List<Ask>, val spoken: String, val key: Boolean = false)

/** The visible "function call" trail (spec D5): update_report, ask_user, submit_report. */
data class ToolLogEntry(val tool: String, val json: String, val atMs: Long)

/** A value that changed. The original is never lost (spec D16). */
data class JournalEntry(val field: Field, val before: String?, val after: String?, val reason: String, val atMs: Long)

/** One pipeline stage with where it really ran. [compute] comes from the engine's result, never a constant. */
data class StageRecord(val stage: String, val engine: String, val compute: Compute, val ms: Long?)

/** Something the device should say aloud. A new [id] means a new utterance. */
data class Say(val id: Int, val text: String)

sealed interface SessionState {
    data object Idle : SessionState
    data object Extracting : SessionState
    data class Asking(val question: Question) : SessionState
    data object Review : SessionState
    data class Submitted(val reportId: String) : SessionState
    data class Failed(val message: String, val retryable: Boolean) : SessionState
}

data class SessionUi(
    val state: SessionState,
    val report: IncidentReport,
    val fields: List<FieldView>,
    val transcripts: List<String>,
    val priority: PriorityAdvice?,
    val toolLog: List<ToolLogEntry>,
    val journal: List<JournalEntry>,
    val stages: List<StageRecord>,
    val canUndo: Boolean,
    val say: Say?,
    val spokenSummary: String?,
    /** The urgent lane (or high) offers sending now: the report is not yet submitted and the lane allows it. */
    val canSubmitNow: Boolean = false,
    /** Set when the reporter cancelled an alarm: a one-tap "lower to LOW?" offer. Never applied by itself. */
    val deescalation: Deescalation? = null,
)

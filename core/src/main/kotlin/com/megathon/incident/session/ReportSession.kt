package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.PayloadValidator
import com.megathon.incident.llm.ToolCallParser
import com.megathon.incident.priority.PriorityAdvisor
import com.megathon.incident.priority.SeveritySuggester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * One incident, from first words to submitted report (the state machine the screens render).
 *
 * The model only ever proposes `update_report`; what happens next is deterministic code (spec D6): validate and ground
 * the proposal, merge it, suggest a severity, rate the priority, then either ask the next question or go to review.
 * Calls must be serialized by the caller (one voice turn at a time).
 */
class ReportSession(
    private val llm: LlmEngine,
    private val validator: PayloadValidator,
    private val severityHints: SeveritySuggester,
    private val newAdvisor: () -> PriorityAdvisor,
    private val planner: QuestionPlanner,
    private val prompts: PromptBuilder,
    private val store: ReportStore,
    private val clock: Clock,
    private val zone: ZoneId,
    private val newId: () -> String,
) {
    private data class Snapshot(val report: IncidentReport, val clarify: Set<Field>, val assumed: Set<Field>, val transcriptCount: Int)

    private var advisor = newAdvisor()
    private var report = IncidentReport.empty(newId(), clock.millis())
    private var clarify: Set<Field> = emptySet()
    private var assumed: Set<Field> = emptySet()
    private var state: SessionState = SessionState.Idle
    private val transcripts = mutableListOf<String>()
    private val toolLog = mutableListOf<ToolLogEntry>()
    private val journal = mutableListOf<JournalEntry>()
    private val stages = mutableListOf<StageRecord>()
    private val asked = mutableListOf<Set<Field>>()
    private val undoStack = ArrayDeque<Snapshot>()
    private var priority: PriorityAdvice? = null
    private var say: Say? = null
    private var sayCounter = 0
    private var spokenSummary: String? = null

    private val _ui = MutableStateFlow(build())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    // ---- voice turns --------------------------------------------------------------------------------------------

    /**
     * One thing the reporter said. [stt] is how it was heard (engine, where it ran, how long). [signals] are the sound and
     * voice tags heard in the same audio; they go to the advisor, whose rules alone decide what they mean.
     */
    suspend fun onUtterance(text: String, stt: StageRecord? = null, signals: List<Signal> = emptyList()) {
        if (text.isBlank()) {
            fail("I didn't catch that. Try again, a little closer to the microphone.")
            return
        }
        val said = text.trim()
        remember() // one voice turn is one undoable step
        transcripts += said
        stt?.let(::record)
        state = SessionState.Extracting
        publish()

        var request = prompts.build(said, report)
        var result = llm.complete(request)
        var parsed = ToolCallParser.parse(result.text)
        if (parsed is ParseResult.Failure) {
            request = prompts.repair(request, (parsed as ParseResult.Failure).detail)
            result = llm.complete(request)
            parsed = ToolCallParser.parse(result.text)
        }
        record(StageRecord("Read", llm.name, result.compute, result.elapsedMs))
        if (parsed !is ParseResult.Ok) {
            fail("I couldn't make sense of that one. Please say it again.")
            return
        }
        log("update_report", result.text.trim())

        val before = report
        val validation = validator.validate(parsed.payload, transcripts.joinToString(" "), clock.instant())
        val merged = ReportMerge.apply(report, validation, MergeContext(said, clock.instant(), zone, clock.millis()))
        report = merged.report
        journal += merged.journal
        val changed = Field.entries.filter { before.value(it).text != report.value(it).text }.toSet()
        clarify = (clarify - changed) + merged.clarify
        assumed = (assumed - changed) + merged.assumed

        suggestSeverity()
        val out = advisor.onChunk(said, signals)
        priority = out.advice
        advance(prefix = out.spoken)
    }

    // ---- taps and edits -----------------------------------------------------------------------------------------

    /** The reporter tapped a ready-made answer. The model is not involved. */
    fun answer(reply: QuickReply) {
        remember()
        val value = when (reply.field) {
            Field.DATE_TIME -> FieldValue(dateAnswer(reply), ValueStatus.USER_CONFIRMED, "tapped ${reply.label}")
            else -> FieldValue(reply.value, ValueStatus.USER_CONFIRMED, "tapped ${reply.label}")
        }
        setByReporter(reply.field, value, "tapped ${reply.label}")
        if (state is SessionState.Asking) advance(prefix = null) else publish()
    }

    fun edit(field: Field, text: String) {
        remember()
        val value = if (text.isBlank()) FieldValue(null, ValueStatus.MISSING) else FieldValue(text.trim(), ValueStatus.USER_EDITED)
        setByReporter(field, value, "edited by the reporter")
        when (state) {
            is SessionState.Asking -> advance(prefix = null)
            SessionState.Idle, is SessionState.Failed -> { state = SessionState.Review; publish() }
            else -> publish()
        }
    }

    fun acceptSuggestion(field: Field) {
        val v = report.value(field)
        if (v.status != ValueStatus.SUGGESTED) return
        remember()
        setByReporter(field, v.copy(status = ValueStatus.USER_CONFIRMED), "suggestion accepted")
        if (state is SessionState.Asking) advance(prefix = null) else publish()
    }

    fun undo() {
        val snap = undoStack.removeLastOrNull() ?: return
        journal += JournalEntry(Field.INCIDENT_TYPE, null, null, "last change undone", clock.millis()).copy(field = lastChangedField(snap))
        report = snap.report
        clarify = snap.clarify
        assumed = snap.assumed
        while (transcripts.size > snap.transcriptCount) transcripts.removeAt(transcripts.lastIndex)
        if (state is SessionState.Asking) advance(prefix = null) else publish()
    }

    /** Lets the app add a stage it ran itself (for example the spoken reply) to the proof. */
    fun recordStage(stage: StageRecord) {
        record(stage)
        publish()
    }

    fun overridePriority(level: Priority, reason: String) {
        priority = advisor.override(level, reason)
        publish()
    }

    // ---- finishing ----------------------------------------------------------------------------------------------

    /** Never blocked by missing fields or pending suggestions; what is still missing is listed in the call. */
    suspend fun submit() {
        refreshDerived(force = true)
        val missing = QuestionPlanner.MANDATORY.filter { !report.isAnswered(it) }.map { it.name.lowercase() }
        store.save(report)
        log(
            "submit_report",
            JsonObject(mapOf("report_id" to JsonPrimitive(report.id), "missing" to JsonArray(missing.map(::JsonPrimitive)))).toString(),
        )
        state = SessionState.Submitted(report.id)
        spokenSummary = DerivedFields.spoken(report, today())
        speak(spokenSummary!!)
        publish()
    }

    fun reset() {
        advisor = newAdvisor()
        report = IncidentReport.empty(newId(), clock.millis())
        clarify = emptySet(); assumed = emptySet()
        transcripts.clear(); toolLog.clear(); journal.clear(); stages.clear(); asked.clear(); undoStack.clear()
        priority = null; say = null; spokenSummary = null
        state = SessionState.Idle
        publish()
    }

    // ---- internals ----------------------------------------------------------------------------------------------

    private fun advance(prefix: String?) {
        val q = planner.next(report, clarify, advisor.lane(), asked)
        if (q != null) {
            asked += q.asks.map { it.field }.toSet()
            log(
                "ask_user",
                JsonObject(
                    mapOf(
                        "fields" to JsonArray(q.asks.map { JsonPrimitive(it.field.name.lowercase()) }),
                        "spoken" to JsonPrimitive(q.spoken),
                    ),
                ).toString(),
            )
            state = SessionState.Asking(q)
            speak(listOfNotNull(prefix, q.spoken).joinToString(" "))
        } else {
            state = SessionState.Review
            prefix?.let(::speak)
        }
        publish()
    }

    private fun suggestSeverity() {
        if (report.isAnswered(Field.SEVERITY)) return
        severityHints.suggest(transcripts.joinToString(" "))?.let { report = report.with(Field.SEVERITY, it.toFieldValue()) }
    }

    private fun setByReporter(field: Field, value: FieldValue, reason: String) {
        val before = report.value(field)
        report = report.with(field, value)
        clarify = clarify - field
        assumed = assumed - field
        if (before.text != value.text) journal += JournalEntry(field, before.text, value.text, reason, clock.millis())
        log(
            "update_report",
            JsonObject(
                mapOf("by" to JsonPrimitive("reporter"), "field" to JsonPrimitive(field.name.lowercase()), "value" to JsonPrimitive(value.text.orEmpty())),
            ).toString(),
        )
    }

    private fun dateAnswer(reply: QuickReply): String {
        val now = clock.instant().atZone(zone)
        val date = if (reply.value == "yesterday") now.toLocalDate().minusDays(1) else now.toLocalDate()
        val current = report.value(Field.DATE_TIME).text
        return when {
            reply.value == "now" -> "$date ${"%02d:%02d".format(now.hour, now.minute)} (just now)"
            current != null && DateTimeDisplay.parse(current) != null -> DateTimeDisplay.replaceDate(current, date)
            else -> "$date time missing (${reply.label.lowercase()})"
        }
    }

    private fun lastChangedField(snap: Snapshot): Field =
        Field.entries.firstOrNull { report.value(it) != snap.report.value(it) } ?: Field.INCIDENT_TYPE

    private fun remember() {
        undoStack.addLast(Snapshot(report, clarify, assumed, transcripts.size))
        if (undoStack.size > 20) undoStack.removeFirst()
    }

    private fun refreshDerived(force: Boolean = false) {
        if (!force && state !is SessionState.Review && state !is SessionState.Submitted) return
        fun fill(f: Field, text: String) {
            val cur = report.value(f)
            if (cur.status == ValueStatus.USER_EDITED || cur.status == ValueStatus.USER_CONFIRMED) return
            report = report.with(f, FieldValue(text, ValueStatus.SUGGESTED, "written by the assistant"))
        }
        fill(Field.DESCRIPTION, DerivedFields.description(report, today()))
        fill(Field.RECOMMENDED_FOLLOW_UP, DerivedFields.followUp(report))
    }

    private fun fail(message: String) {
        state = SessionState.Failed(message, retryable = true)
        publish()
    }

    private fun record(s: StageRecord) {
        stages.removeAll { it.stage == s.stage }
        stages += s
    }

    private fun log(tool: String, json: String) {
        toolLog += ToolLogEntry(tool, json, clock.millis())
    }

    private fun speak(text: String) {
        say = Say(++sayCounter, text)
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private fun publish() {
        refreshDerived()
        _ui.value = build()
    }

    private fun build() = SessionUi(
        state = state,
        report = report,
        fields = fieldViews(),
        transcripts = transcripts.toList(),
        priority = priority,
        toolLog = toolLog.toList(),
        journal = journal.toList(),
        stages = stages.toList(),
        canUndo = undoStack.isNotEmpty(),
        say = say,
        spokenSummary = spokenSummary,
    )

    private fun fieldViews(): List<FieldView> {
        val today = today()
        return Labels.ORDER.map { f ->
            val v = report.value(f)
            val mandatory = f in QuestionPlanner.MANDATORY
            val assistant = f == Field.DESCRIPTION || f == Field.RECOMMENDED_FOLLOW_UP
            var value = Labels.display(f, v.text, today)
            var evidence: String? = null
            var hint: String? = null
            val status = when {
                f in clarify && v.text != null -> { hint = "Which day was this?"; UiStatus.NEEDED }
                else -> when (v.status) {
                    ValueStatus.MISSING -> if (mandatory) UiStatus.NEEDED else UiStatus.EMPTY
                    ValueStatus.UNKNOWN -> { value = "Don't know"; UiStatus.UNKNOWN }
                    ValueStatus.EXPLICIT_NONE -> { value = "None"; UiStatus.NONE }
                    ValueStatus.REPORTED -> {
                        evidence = v.evidence
                        if (f in assumed) {
                            val day = v.text?.let { DateTimeDisplay.parse(it)?.date }
                            hint = "Assumed ${if (day == today.minusDays(1)) "yesterday" else "today"}. Tap to change."
                        }
                        UiStatus.HEARD
                    }
                    ValueStatus.SUGGESTED -> {
                        if (!assistant) hint = v.evidence?.let { "Suggested because: $it" }
                        UiStatus.SUGGESTED
                    }
                    ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED -> UiStatus.CONFIRMED
                }
            }
            FieldView(f, Labels.fieldLabel(f), mandatory, value, status, evidence, hint, assistant)
        }
    }
}

package com.megathon.incident.session

import com.megathon.incident.domain.ExtractionPayload
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.PayloadValidator
import com.megathon.incident.llm.ToolCallParser
import com.megathon.incident.priority.Deescalation
import com.megathon.incident.priority.KeyAnswer
import com.megathon.incident.priority.PriorityAdvisor
import com.megathon.incident.priority.SeveritySuggester
import com.megathon.incident.suggest.SuggestionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
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
    /** D's chip engine (spec D16); null means no chips. It only proposes; the reporter's tap is the only thing that changes a field. */
    private val suggestions: SuggestionEngine? = null,
    /** The focused second look at fields the first pass left empty; it only proposes chips, and only [probeMissing] runs it. */
    private val probe: FocusedProbe? = null,
) {
    private data class Snapshot(
        val report: IncidentReport,
        val clarify: Set<Field>,
        val transcriptCount: Int,
        val chipDecisions: List<com.megathon.incident.domain.JournalEntry>,
    )

    private var advisor = newAdvisor()
    private var report = IncidentReport.empty(newId(), clock.millis())
    private var clarify: Set<Field> = emptySet()
    private var state: SessionState = SessionState.Idle
    private val transcripts = mutableListOf<String>()
    private val toolLog = mutableListOf<ToolLogEntry>()
    private val journal = mutableListOf<JournalEntry>()
    /** Accepted and rejected chips, in D's journal type: this is how a dismissed chip stays dismissed. */
    private var chipDecisions: List<com.megathon.incident.domain.JournalEntry> = emptyList()
    /** Chips from [probe]: kept until the reporter speaks again, shown only while they still apply. */
    private var probeChips: List<Suggestion> = emptyList()
    private val probeDecisions = com.megathon.incident.suggest.ChipDecisions(clock)
    private val stages = mutableListOf<StageRecord>()
    private val asked = mutableListOf<Set<Field>>()
    /** The open mandatory fields when the last question was chosen: a change means an answer made progress. */
    private var lastOpen: Set<Field> = emptySet()
    private val undoStack = ArrayDeque<Snapshot>()
    private var priority: PriorityAdvice? = null
    private var deescalation: Deescalation? = null
    private var say: Say? = null
    private var sayCounter = 0
    private var spokenSummary: String? = null
    /** The emergency line raised by a tap or an edit: spoken with the next reply, or at the next publish, never dropped. */
    private var stagedEmergency: String? = null

    private val _ui = MutableStateFlow(build())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    // ---- voice turns --------------------------------------------------------------------------------------------

    /**
     * One thing the reporter said. [stt] is how it was heard (engine, where it ran, how long). [signals] are the sound and
     * voice tags heard in the same audio; they go to the advisor, whose rules alone decide what they mean. [capturedAt] is the
     * device-clock instant the reporter stopped speaking (defaults to the session clock now).
     */
    suspend fun onUtterance(text: String, stt: StageRecord? = null, signals: List<Signal> = emptyList(), capturedAt: Instant? = null) {
        if (text.isBlank()) {
            fail("I didn't catch that. Could you say it again, a little closer to the microphone?")
            return
        }
        val said = text.trim()
        if (answerOpenQuestion(said)) return // a short answer to an open optional question answers that field
        val at = capturedAt ?: clock.instant() // when the reporter stopped speaking: "right now" and "10 minutes ago" are relative to this
        remember() // one voice turn is one undoable step
        probeChips = emptyList()
        transcripts += said
        stt?.let(::record)
        state = SessionState.Extracting
        publish()

        var request = prompts.build(said, report, earlier = transcripts.dropLast(1).takeLast(2))
        var result = llm.complete(request)
        var parsed = ToolCallParser.parse(result.text)
        if (parsed is ParseResult.Failure) {
            request = prompts.repair(request, (parsed as ParseResult.Failure).detail)
            result = llm.complete(request)
            parsed = ToolCallParser.parse(result.text)
        }
        record(StageRecord("Read", llm.name, result.compute, result.elapsedMs))
        if (parsed !is ParseResult.Ok) {
            fail("I didn't quite get that. Could you say it again?")
            return
        }
        log("update_report", result.text.trim())

        val before = report
        val validation = validator.validate(withNowPhrase(parsed.payload, said), transcripts.joinToString(" "), at)
        val merged = ReportMerge.apply(report, validation, MergeContext(said, at, zone, clock.millis()))
        report = merged.report
        journal += merged.journal
        val changed = Field.entries.filter { before.value(it).text != report.value(it).text }.toSet()
        clarify = (clarify - changed) + merged.clarify

        suggestSeverity()
        val out = advisor.onChunk(said, signals, hint = validation.hint)
        val spokenByFloor = applyStatedFloors()
        advance(prefix = out.spoken ?: spokenByFloor, keyQuestion = out.keyQuestion)
    }

    /**
     * While a single optional question (tool or item, people, witnesses) is open, a short spoken answer such as "Meera and Ravi", "no one"
     * or "pata nahi" answers it directly, without the first-pass prompt (which would not know what a bare name is for). Anything that does not
     * look like an answer returns false and is read as a normal sentence. The answer is the reporter's own: USER_CONFIRMED, evidence = their words.
     */
    private fun answerOpenQuestion(said: String): Boolean {
        val ask = (state as? SessionState.Asking)?.question?.takeIf { !it.key }?.asks?.singleOrNull() ?: return false
        if (ask.field !in OptionalAsks.ASKABLE) return false
        val value = when (val a = AnswerParser.parse(ask.field, said)) {
            AnswerParser.Answer.NotAnAnswer -> return false
            AnswerParser.Answer.Skip -> null
            AnswerParser.Answer.None -> FieldValue.explicitNone(said)
            is AnswerParser.Answer.Value -> FieldValue(a.text, ValueStatus.USER_CONFIRMED, said)
        }
        remember()
        value?.let { setByReporter(ask.field, it, "answered by voice") }
        advance(prefix = null)
        return true
    }

    /**
     * What the reporter has stated in the report (a severity, a hazard incident type) floors the advice: raise-only and
     * idempotent, so it is safe to call after every change. Returns the emergency line when this call is what made it
     * warranted: the advisor hands it out once, so the caller must speak it or it is lost.
     */
    private fun applyStatedFloors(): String? {
        val out = advisor.onReport(report)
        priority = out.advice
        deescalation = out.deescalation
        return out.spoken
    }

    /**
     * A small model often skips "right now" / "just now" as a time. It is the worker's own phrase and C's resolver reads it
     * as the capture time, so when the model left the time out and the report has none yet, take the phrase from the words.
     * Never replaces a time the model gave or one already in the report.
     */
    private fun withNowPhrase(payload: ExtractionPayload, said: String): ExtractionPayload {
        if (!payload.date_time_text.isNullOrBlank() || report.isFilled(Field.DATE_TIME)) return payload
        val phrase = NOW_PHRASE.find(said)?.value ?: return payload
        return payload.copy(date_time_text = phrase, evidence = payload.evidence + ("date_time_text" to phrase))
    }

    // ---- taps and edits -----------------------------------------------------------------------------------------

    /** The reporter tapped a ready-made answer. The model is not involved. */
    fun answer(reply: QuickReply) {
        remember()
        when (reply.value) {
            // "Skip" records nothing: the field stays as it was and, being in the asked history, is not asked again.
            QuickReply.SKIP -> Unit
            // "None" / "No one" is an answer: the field is explicitly empty, so it is never asked again either.
            QuickReply.NONE -> setByReporter(reply.field, FieldValue.explicitNone("tapped ${reply.label}"), "tapped ${reply.label}")
            else -> {
                val value = when (reply.field) {
                    Field.DATE_TIME -> FieldValue(dateAnswer(reply), ValueStatus.USER_CONFIRMED, "tapped ${reply.label}")
                    else -> FieldValue(reply.value, ValueStatus.USER_CONFIRMED, "tapped ${reply.label}")
                }
                setByReporter(reply.field, value, "tapped ${reply.label}")
            }
        }
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

    /**
     * The reporter tapped Accept on a chip from the suggestion engine: the field becomes the proposal (USER_CONFIRMED), what was
     * heard stays in the journal, and Undo brings it back. A chip made for a value that has since changed does nothing.
     */
    fun acceptSuggestion(chip: Suggestion) {
        val now = chipState()
        val after = if (chip in probeChips) probeDecisions.accept(now, chip) else (suggestions ?: return).accept(now, chip)
        if (after == now) return
        remember()
        chipDecisions = after.journal
        setByReporter(chip.field, after.report.value(chip.field), "suggestion accepted: ${chip.reason}")
        if (state is SessionState.Asking) advance(prefix = null) else publish()
    }

    /** The reporter dismissed a chip: the field is untouched and the same chip is not offered again. */
    fun rejectSuggestion(chip: Suggestion) {
        val now = chipState()
        val after = if (chip in probeChips) probeDecisions.reject(now, chip) else (suggestions ?: return).reject(now, chip)
        if (after == now) return
        remember()
        chipDecisions = after.journal
        publish()
    }

    fun undo() {
        val snap = undoStack.removeLastOrNull() ?: return
        journal += JournalEntry(Field.INCIDENT_TYPE, null, null, "last change undone", clock.millis()).copy(field = lastChangedField(snap))
        report = snap.report
        clarify = snap.clarify
        chipDecisions = snap.chipDecisions
        while (transcripts.size > snap.transcriptCount) transcripts.removeAt(transcripts.lastIndex)
        if (state is SessionState.Asking) advance(prefix = null) else publish()
    }

    /** Lets the app add a stage it ran itself (for example the spoken reply) to the proof. */
    fun recordStage(stage: StageRecord) {
        record(stage)
        publish()
    }

    /** The reporter's own decision, from the level control or the "lower to LOW?" chip. Clears any pending offer. */
    fun overridePriority(level: Priority, reason: String) {
        priority = advisor.override(level, reason)
        advisor.dismissDeescalation()
        deescalation = null
        publish()
    }

    /** The reporter tapped Yes / No / Not sure on the key question. Unsure counts as danger (the advisor decides). */
    fun answerKeyQuestion(answer: KeyAnswer) {
        if ((state as? SessionState.Asking)?.question?.key != true) return
        val out = advisor.answerKeyQuestion(answer)
        priority = out.advice
        advance(prefix = out.spoken)
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
        clarify = emptySet()
        chipDecisions = emptyList(); probeChips = emptyList(); transcripts.clear(); toolLog.clear(); journal.clear(); stages.clear(); asked.clear(); lastOpen = emptySet(); undoStack.clear()
        priority = null; deescalation = null; say = null; spokenSummary = null; stagedEmergency = null
        state = SessionState.Idle
        publish()
    }

    // ---- internals ----------------------------------------------------------------------------------------------

    /** [keyQuestion], when the advisor has one, is asked first and on its own: one spoken question, then the planner's. */
    private fun advance(prefix: String?, keyQuestion: String? = null) {
        val lead = listOfNotNull(stagedEmergency, prefix).joinToString(" ").ifEmpty { null }
        stagedEmergency = null
        val q = if (keyQuestion != null) Question(emptyList(), keyQuestion, key = true)
        else {
            // An answer that changed what is still open is progress: a field asked before may be asked again. A round of
            // questions that changed nothing is not, so the report goes to review rather than the worker being nagged.
            val open = planner.open(report, clarify, advisor.lane()).toSet()
            if (open != lastOpen) { asked.clear(); lastOpen = open }
            planner.next(report, clarify, advisor.lane(), asked, heard = transcripts.joinToString(" "), candidates = itemCandidates())
        }
        if (q != null) {
            if (!q.key) asked += q.asks.map { it.field }.toSet()
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
            speak(listOfNotNull(lead, q.spoken).joinToString(" "))
        } else {
            state = SessionState.Review
            lead?.let(::speak)
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
        if (before.text != value.text) journal += JournalEntry(field, before.text, value.text, reason, clock.millis())
        log(
            "update_report",
            JsonObject(
                mapOf("by" to JsonPrimitive("reporter"), "field" to JsonPrimitive(field.name.lowercase()), "value" to JsonPrimitive(value.text.orEmpty())),
            ).toString(),
        )
        stagedEmergency = applyStatedFloors() ?: stagedEmergency
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
        undoStack.addLast(Snapshot(report, clarify, transcripts.size, chipDecisions))
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
        stagedEmergency?.let { stagedEmergency = null; speak(it) } // a tap or edit raised it and no question followed
        refreshDerived()
        _ui.value = build()
    }

    private fun chipState() = ReportState(report, chipDecisions)

    /** A tool or item a chip already proposes: the planner turns it into a yes or no instead of an open question. */
    private fun itemCandidates(): Map<Field, String> =
        chips().filter { it.field == Field.EQUIPMENT_ASSET && it.original.isEmpty() }
            .groupBy { it.field }.mapValues { (_, c) -> c.first().proposed }

    /**
     * Recomputed from the report each time (chips are never stored). Severity is left to its own suggestion
     * ([acceptSuggestion] by field) while it is still only suggested, so the reporter is not shown it twice.
     * A failing engine costs the chips, never the session.
     */
    private fun chips(): List<Suggestion> {
        if (state is SessionState.Submitted) return emptyList()
        val found = suggestions?.let { engine ->
            try {
                engine.suggest(chipState(), transcripts.joinToString(" "))
            } catch (e: Exception) {
                emptyList()
            }
        }.orEmpty().filterNot { it.field == Field.SEVERITY && report.value(Field.SEVERITY).status == ValueStatus.SUGGESTED }
        val decided = chipState()
        // A probe chip counts only while the field is still what it was made for, nothing else proposes that field, and it was not dismissed.
        val live = probeChips.filter { c -> found.none { it.field == c.field } && (report.value(c.field).text ?: "") == c.original && !decided.hasDecided(c) }
        return found + live
    }

    /**
     * The second look (see [FocusedProbe]): call it AFTER a turn has finished, while the reply is being spoken, never inside the
     * turn, so it adds nothing to the turn time. The chips appear when it returns. If the reporter spoke or submitted meanwhile,
     * the result is dropped as stale.
     */
    suspend fun probeMissing() {
        val p = probe ?: return
        if (state is SessionState.Submitted || state is SessionState.Extracting) return
        val heard = transcripts.size
        val said = transcripts.joinToString(" ")
        if (said.isBlank()) return
        val taken = suggestions?.let { runCatching { it.suggest(chipState(), said) }.getOrNull() }.orEmpty().map { it.field }.toSet()
        val found = p.propose(report, said, skip = taken)
        if (found.isEmpty() || transcripts.size != heard || state is SessionState.Submitted) return
        probeChips = probeChips.filterNot { c -> found.any { it.field == c.field } } + found
        publish()
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
        canSubmitNow = state !is SessionState.Submitted && advisor.lane().offerSubmitNow,
        deescalation = deescalation,
        suggestions = chips(),
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
                    ValueStatus.REPORTED -> { evidence = v.evidence; UiStatus.HEARD }
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

    private companion object {
        val NOW_PHRASE = Regex("""\b(right now|just now|just happened|this very moment)\b""", RegexOption.IGNORE_CASE)
    }
}

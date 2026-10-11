package com.megathon.incident.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.megathon.incident.audio.MicRecorder
import com.megathon.incident.data.FileReportStore
import com.megathon.incident.device.NetworkMeter
import com.megathon.incident.device.NetworkProof
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.engine.EngineFactory
import com.megathon.incident.engine.EngineSelection
import com.megathon.incident.engine.EngineUnavailableException
import com.megathon.incident.engine.Engines
import com.megathon.incident.engine.LlmKind
import com.megathon.incident.engine.SimulatedEngineInReleaseException
import com.megathon.incident.engine.SpeechKind
import com.megathon.incident.priority.KeyAnswer
import com.megathon.incident.session.Pipeline
import com.megathon.incident.session.QuickReply
import com.megathon.incident.session.ReportSession
import com.megathon.incident.session.SessionState
import com.megathon.incident.session.SessionUi
import com.megathon.incident.session.StageRecord
import com.megathon.incident.suggest.Homophones
import com.megathon.incident.suggest.RuleSuggestionEngine
import com.megathon.incident.suggest.SiteDirectory
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import com.megathon.incident.speech.DemoSpeechEngine
import com.megathon.incident.speech.GenieXLlmEngine
import com.megathon.incident.speech.SenseVoiceSignalEngine
import com.megathon.incident.speech.SherpaCliSpeechEngine
import com.megathon.incident.speech.SignalReading
import com.megathon.incident.speech.AndroidTtsBackend
import com.megathon.incident.speech.PiperSpeaker
import com.megathon.incident.speech.SystemTtsSpeaker
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.tts.RoutingSpeaker
import com.megathon.incident.tts.TtsKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Screen { HOME, REPORT, CHECK, SENT }

/** Everything the screens draw. The report itself lives in [SessionUi]. */
data class AppUi(
    val screen: Screen,
    val listening: Boolean,
    val processing: Boolean,
    val elapsedMs: Long,
    val levels: List<Float>,
    val session: SessionUi,
    val selected: Field?,
    val editing: Field?,
    val justFilled: Set<Field>,
    /** What was just heard, shown while the model is still reading it. */
    val heardNow: String?,
    val speaking: Boolean,
    val message: String?,
    /** Set when the build refuses to start (a simulated engine in a release build, or an engine not wired yet). */
    val blocked: String?,
    /** What is simulated in this run, in words; null when everything is real. */
    val simulatedNote: String?,
    val simulated: Boolean = simulatedNote != null,
    val network: NetworkProof,
    val showProof: Boolean,
    val ttsOffline: Boolean?,
    val undoLabel: String?,
    val needsMicPermission: Boolean,
    /** Why the Piper voice was asked for but not heard (files missing, library failed), in words; null when not applicable. */
    val ttsNote: String? = null,
    /** The latest voice tags (emotion, sound event) SenseVoice read from the audio; null when none were read. */
    val voiceTags: SignalReading? = null,
    /** Why there are no voice tags, in words (the bundle is missing, or the last read failed); null when not a failure. */
    val voiceTagsNote: String? = null,
)

private data class Local(
    val screen: Screen = Screen.HOME,
    val listening: Boolean = false,
    val processing: Boolean = false,
    val elapsedMs: Long = 0,
    val levels: List<Float> = emptyList(),
    val selected: Field? = null,
    val editing: Field? = null,
    val justFilled: Set<Field> = emptySet(),
    val heardNow: String? = null,
    val speaking: Boolean = false,
    val message: String? = null,
    val network: NetworkProof = NetworkProof(false, false, null),
    val showProof: Boolean = false,
    val ttsOffline: Boolean? = null,
    val ttsNote: String? = null,
    val undoLabel: String? = null,
    val needsMicPermission: Boolean = false,
    val voiceTags: SignalReading? = null,
    val voiceTagsNote: String? = null,
)

class AppViewModel(app: Application, private val selection: EngineSelection) : AndroidViewModel(app) {
    private val demoSpeech = DemoSpeechEngine()
    private val factory = EngineFactory(
        isDebug = Engines.isDebuggable(app),
        speechProviders = mapOf(SpeechKind.FAKE to { demoSpeech }, SpeechKind.SHERPA_QNN to { SherpaCliSpeechEngine(app) }),
        llmProviders = mapOf(LlmKind.FAKE to { FakeLlmEngine(name = "DEMO REPLIES") }, LlmKind.GENIEX to { GenieXLlmEngine(app) }),
        ttsProviders = mapOf(
            TtsKind.ANDROID to { AndroidTtsBackend(SystemTtsSpeaker(app)) },
            // Built only when PIPER is selected, and it loads nothing until it first speaks (the native library is lazy).
            TtsKind.PIPER to { PiperSpeaker(app, requireVerified = !Engines.isDebuggable(app), language = { speechLanguage() }) },
        ),
    )
    private val recorder = MicRecorder(app)
    // Speech output: the Android voice always exists (it is the fallback); Piper only when asked for with `--es tts PIPER`.
    // The label on the proof strip comes from the engine that really spoke (RoutingSpeaker.last), never from the request.
    private val speaker = RoutingSpeaker(
        primary = if (selection.tts == TtsKind.PIPER) runCatching { factory.tts(TtsKind.PIPER) }.getOrNull() else null,
        fallback = factory.tts(TtsKind.ANDROID),
        language = { speechLanguage() },
    )
    private val meter = NetworkMeter(app)
    private val mutex = Mutex()

    // Voice tags are optional: a missing SenseVoice bundle is noticed once and then not retried every turn.
    private var tagEngine: SenseVoiceSignalEngine? = null
    private var tagEngineUnavailable: String? = null

    private var blocked: String? = null
    private val speech = runCatching { factory.speech(selection.speech) }.onFailure { blocked = explain(it) }.getOrNull()
    private val llm = runCatching { factory.llm(selection.llm) }.onFailure { blocked = blocked ?: explain(it) }.getOrNull()
    // Chips (spec D16): the bundled fictional site list, or a real one dropped into filesDir/site.json for the demo. Rules on the CPU.
    private val siteLoad = SiteDirectory.loadPreferring(java.io.File(app.filesDir, "site.json"))
    private val suggestionEngine = RuleSuggestionEngine(
        siteLoad.site, Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()), java.time.Clock.systemDefaultZone(),
    )
    private val session: ReportSession = Pipeline.newSession(llm ?: FakeLlmEngine(), FileReportStore(app), suggestions = suggestionEngine)

    private val local = MutableStateFlow(Local(network = meter.read()))
    private var timer: Job? = null
    private var clearFilled: Job? = null
    private var clearUndo: Job? = null

    val ui: StateFlow<AppUi> = combine(local, session.ui) { l, s ->
        AppUi(
            screen = l.screen, listening = l.listening, processing = l.processing, elapsedMs = l.elapsedMs, levels = l.levels,
            session = s, selected = l.selected, editing = l.editing, justFilled = l.justFilled, heardNow = l.heardNow,
            speaking = l.speaking, message = l.message, blocked = blocked,
            simulatedNote = simulatedNote(),
            network = l.network, showProof = l.showProof, ttsOffline = l.ttsOffline, ttsNote = l.ttsNote, undoLabel = l.undoLabel,
            needsMicPermission = l.needsMicPermission,
            voiceTags = l.voiceTags, voiceTagsNote = l.voiceTagsNote,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUi(
        screen = Screen.HOME, listening = false, processing = false, elapsedMs = 0, levels = emptyList(), session = session.ui.value,
        selected = null, editing = null, justFilled = emptySet(), heardNow = null, speaking = false, message = null, blocked = blocked,
        simulatedNote = simulatedNote(), network = local.value.network, showProof = false, ttsOffline = null, undoLabel = null,
        needsMicPermission = false,
    ))

    init {
        // Speak whatever the session wants said (questions, the emergency line, the summary), one at a time.
        viewModelScope.launch {
            session.ui.map { it.say }.filterNotNull().distinctUntilChangedBy { it.id }.collect { say ->
                if (local.value.listening) return@collect
                local.update { it.copy(speaking = true) }
                val started = System.nanoTime()
                speaker.speak(say.text)
                val used = speaker.last
                local.update { it.copy(speaking = false, ttsOffline = used?.offline, ttsNote = used?.note()) }
                used?.note()?.let { android.util.Log.w(TAG, "tts fallback: $it") }
                val ms = (System.nanoTime() - started) / 1_000_000
                session.recordStage(used?.toStage(ms) ?: StageRecord("Speak", AndroidTtsBackend.LABEL, Compute.CPU, ms))
            }
        }
    }

    // ---- microphone ----------------------------------------------------------------------------------------------

    /** One tap starts listening, the next tap stops. Recording never starts by itself. */
    fun onMicTapped() {
        if (blocked != null || local.value.processing) return
        if (local.value.listening) stopAndProcess() else startListening()
    }

    fun onMicPermissionResult(granted: Boolean) {
        local.update { it.copy(needsMicPermission = false) }
        if (granted) startListening() else say("Microphone access is off, so I can't hear you. You can turn it on in Settings, or type your answers.")
    }

    private fun startListening() {
        if (!recorder.hasPermission()) { local.update { it.copy(needsMicPermission = true) }; return }
        speaker.stop()
        val levels = ArrayDeque<Float>()
        val ok = recorder.start(
            viewModelScope,
            onLevel = { lv ->
                levels.addLast(lv); if (levels.size > BARS) levels.removeFirst()
                local.update { it.copy(levels = levels.toList()) }
            },
            onLimit = { stopAndProcess() },
        )
        if (!ok) { say("I couldn't open the microphone. Another app may be using it."); return }
        local.update { it.copy(listening = true, elapsedMs = 0, levels = emptyList(), message = null, speaking = false) }
        timer = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            while (true) { delay(200); local.update { it.copy(elapsedMs = System.currentTimeMillis() - t0) } }
        }
    }

    private fun stopAndProcess() {
        viewModelScope.launch {
            timer?.cancel()
            val pcm = recorder.stop()
            process(pcm, java.time.Instant.now()) // the device clock at the moment the reporter stopped speaking
        }
    }

    /** Hear -> read -> merge for one utterance of 16 kHz mono PCM. Shared by the microphone and the debug WAV hook. */
    private suspend fun process(pcm: ShortArray, capturedAt: java.time.Instant = java.time.Instant.now()) {
        mutex.withLock {
            local.update { it.copy(listening = false, processing = true, heardNow = null, message = null) }
            val engine = speech ?: return@withLock
            val before = session.ui.value.fields
            val journalBefore = session.ui.value.journal.size
            val heard = try {
                engine.transcribe(pcm, "en")
            } catch (e: Exception) {
                // Say why, in words and in the log: a real engine that fails must never be silent.
                android.util.Log.e(TAG, "speech engine failed", e)
                finishTurn(before, journalBefore, "I couldn't hear that: ${e.message?.take(160) ?: "the speech engine failed"}")
                return@withLock
            }
            android.util.Log.i(TAG, "heard [${heard.compute}] in ${heard.elapsedMs} ms: ${heard.text}")
            runCatching { factory.checkResult(heard.compute) }.onFailure { blocked = explain(it) }
            local.update { it.copy(heardNow = heard.text.ifBlank { null }) }
            val tags = if (heard.text.isBlank()) null else readVoiceTags(pcm)
            tags?.let { session.recordStage(StageRecord("Tags", "SenseVoice (sherpa-onnx, QNN)", it.compute, it.elapsedMs)) }
            session.onUtterance(heard.text, StageRecord("Hear", engine.name, heard.compute, heard.elapsedMs), tags?.signals.orEmpty(), capturedAt)
            finishTurn(before, journalBefore, null)
        }
    }

    /**
     * SenseVoice's tags for the same audio, or null. Never blocks the report: any failure is logged and the turn
     * goes on without signals. Only used with a real speech engine, so the tags always describe the audio that was heard.
     */
    private suspend fun readVoiceTags(pcm: ShortArray): SignalReading? {
        if (selection.speech == SpeechKind.FAKE) return null
        tagEngineUnavailable?.let { why ->
            local.update { it.copy(voiceTags = null, voiceTagsNote = why) }
            return null
        }
        val engine = tagEngine ?: try {
            SenseVoiceSignalEngine(getApplication<Application>()).also { tagEngine = it }
        } catch (e: EngineUnavailableException) {
            tagEngineUnavailable = e.message ?: "SenseVoice is not available"
            android.util.Log.w(TAG, "voice tags unavailable: ${e.message}")
            local.update { it.copy(voiceTags = null, voiceTagsNote = tagEngineUnavailable) }
            return null
        }
        return try {
            engine.read(pcm).also { r ->
                android.util.Log.i(TAG, "tags [${r.compute}] in ${r.elapsedMs} ms: emotion=${r.reading?.emotion} event=${r.reading?.event} -> ${r.signals.map { it.label }}")
                local.update { it.copy(voiceTags = r, voiceTagsNote = null) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "voice tags failed", e)
            local.update { it.copy(voiceTags = null, voiceTagsNote = "The last read failed: ${e.message?.take(120) ?: "unknown error"}") }
            null
        }
    }

    private val preloadStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Loads the language model in the background as soon as the screen is up, so the first turn does not pay the 9 to 13 s
     * model load. Only a real model has anything to load (the demo engine is not [Warmable]); models load one after the
     * other. A failure is logged with its reason and changes nothing else: the first real turn tries again and fails
     * visibly exactly as it did before. Started once per view model, never on a debug eval, coexist or WAV launch (those
     * time the load themselves).
     */
    fun preloadModels() {
        if (blocked != null || !preloadStarted.compareAndSet(false, true)) return
        val steps = listOfNotNull((llm as? com.megathon.incident.llm.Warmable)?.let { "language model" to it })
        if (steps.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t0 = System.nanoTime()
            com.megathon.incident.llm.ModelPreloader().run(steps).forEach {
                when (it) {
                    is com.megathon.incident.llm.PreloadOutcome.Loaded ->
                        android.util.Log.i(TAG, "preload ${it.name}: ready after ${(System.nanoTime() - t0) / 1_000_000} ms (load ${it.ms} ms)")
                    is com.megathon.incident.llm.PreloadOutcome.Failed ->
                        android.util.Log.w(TAG, "preload ${it.name} failed: ${it.reason}")
                }
            }
        }
    }

    /**
     * Debug builds only: the one way to run the held-out items. `--es holdout CONFIRM --es gold <file> --es run <name>`
     * [`--es prompt <file>`]; never together with `--es eval`, and a second run of the same prompt on the same build is refused
     * unless `--es holdout_again CONFIRM_AGAIN` is also given. The decision, the prompt hash, the build (APK hash), the gold
     * hash and the item ids are logged (tag Incident, prefix "holdout") and the run is written to the ledger before the first
     * model call, so an interrupted run still counts. Always the whole held-out set: no `only` filter.
     */
    fun debugHoldout(gold: String?, promptFile: String?, runName: String, holdout: String?, again: String?, devRequested: Boolean) {
        if (!Engines.isDebuggable(getApplication())) return
        val engine = llm ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            val promptBytes = promptFile?.let { java.io.File(it).readBytes() } ?: com.megathon.incident.session.PromptBuilder.defaultSourceBytes()
            val promptHash = com.megathon.incident.eval.ShortHash.of(promptBytes)
            val build = runCatching { com.megathon.incident.eval.ShortHash.of(java.io.File(app.applicationInfo.sourceDir).inputStream()) }.getOrDefault("unknown")
            val ledgerFile = java.io.File(app.filesDir, "eval/holdout-ledger.jsonl")
            val ledger = com.megathon.incident.eval.HoldoutLedger.parse(if (ledgerFile.exists()) ledgerFile.readText() else "")
            val decision = com.megathon.incident.eval.HoldoutGuard.decide(holdout, again, devRequested, promptHash, build, ledger)
            when (decision) {
                is com.megathon.incident.eval.HoldoutDecision.NotRequested -> return@launch
                is com.megathon.incident.eval.HoldoutDecision.Refused -> {
                    android.util.Log.e(TAG, "holdout REFUSED: ${decision.reason}")
                    return@launch
                }
                is com.megathon.incident.eval.HoldoutDecision.Allowed -> Unit
            }
            val goldFile = gold?.let { java.io.File(it) }
            if (goldFile == null || !goldFile.isFile) {
                android.util.Log.e(TAG, "holdout REFUSED: --es gold <file> is missing or not a file")
                return@launch
            }
            val goldHash = com.megathon.incident.eval.ShortHash.of(goldFile.readBytes())
            val repeat = (decision as com.megathon.incident.eval.HoldoutDecision.Allowed).repeat
            android.util.Log.i(TAG, "holdout run $runName starting: prompt=$promptHash build=$build gold=$goldHash repeat=$repeat")
            val prompts = promptFile?.let { com.megathon.incident.session.PromptBuilder(String(promptBytes, Charsets.UTF_8).trim()) }
                ?: com.megathon.incident.session.PromptBuilder.default()
            val warm: suspend () -> Long = { (engine as? GenieXLlmEngine)?.warmUp() ?: 0L }
            val out = runCatching {
                com.megathon.incident.eval.EvalRunner(app.filesDir, engine, warm).run(
                    goldFile, prompts, runName, only = null, split = com.megathon.incident.eval.EvalSplit.HOLDOUT,
                    onSelected = { ids ->
                        android.util.Log.i(TAG, "holdout run $runName items (${ids.size}): ${ids.joinToString(",")}")
                        ledgerFile.parentFile?.mkdirs()
                        ledgerFile.appendText(com.megathon.incident.eval.HoldoutRun(promptHash, build, goldHash, ids, System.currentTimeMillis()).toLine() + "\n")
                    },
                )
            }
            out.onSuccess { android.util.Log.i(TAG, "holdout run $runName done: ${it.path}") }
                .onFailure { android.util.Log.e(TAG, "holdout run $runName failed", it) }
        }
    }

    /** Debug builds only: run E's gold dev split through the real pipeline and write predictions for E's scorer. */
    fun debugEval(gold: String, promptFile: String?, runName: String, only: Set<String>?) {
        if (!Engines.isDebuggable(getApplication())) return
        val engine = llm ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val prompts = promptFile?.let { com.megathon.incident.session.PromptBuilder(java.io.File(it).readText().trim()) }
                ?: com.megathon.incident.session.PromptBuilder.default()
            val warm: suspend () -> Long = { (engine as? GenieXLlmEngine)?.warmUp() ?: 0L }
            android.util.Log.i(TAG, "eval $runName starting")
            val out = runCatching {
                com.megathon.incident.eval.EvalRunner(getApplication<Application>().filesDir, engine, warm).run(java.io.File(gold), prompts, runName, only)
            }
            out.onSuccess { android.util.Log.i(TAG, "eval $runName done: ${it.path}") }
                .onFailure { android.util.Log.e(TAG, "eval $runName failed", it) }
        }
    }

    /** Debug builds only: run a 16 kHz mono PCM16 WAV through the same path as the microphone. */
    fun debugTranscribeFile(path: String) {
        if (!Engines.isDebuggable(getApplication())) return
        viewModelScope.launch {
            val pcm = runCatching { readWav(java.io.File(path)) }.getOrElse {
                android.util.Log.e(TAG, "cannot read $path", it); say("Can't read that file: ${it.message}"); return@launch
            }
            process(pcm)
        }
    }

    /**
     * Debug builds only: Whisper (child process) and Qwen3-4B (GenieX, in process) taking turns [loops] times on one WAV,
     * to check they coexist without crashes or memory growth. Output: `<filesDir>/coexist/<timestamp>/`.
     */
    fun debugCoexist(path: String, loops: Int) {
        if (!Engines.isDebuggable(getApplication())) return
        val stt = speech
        val engine = llm
        if (stt == null || engine == null) { android.util.Log.e(TAG, "coexist: no engines (${blocked ?: "unknown"})"); return }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val out = runCatching {
                val pcm = readWav(java.io.File(path))
                com.megathon.incident.eval.CoexistRunner(getApplication<Application>().filesDir, stt, engine).run(pcm, loops)
            }
            out.onSuccess { android.util.Log.i(TAG, "coexist done: ${it.path}") }
                .onFailure { android.util.Log.e(TAG, "coexist failed", it) }
        }
    }

    private fun readWav(file: java.io.File): ShortArray {
        val b = file.readBytes()
        var i = 12
        while (i + 8 <= b.size) {
            val id = String(b, i, 4)
            val size = java.nio.ByteBuffer.wrap(b, i + 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                val n = minOf(size, b.size - (i + 8)) / 2
                val bb = java.nio.ByteBuffer.wrap(b, i + 8, n * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                return ShortArray(n) { bb.short }
            }
            i += 8 + size
        }
        error("no data chunk")
    }

    private fun finishTurn(before: List<com.megathon.incident.session.FieldView>, journalBefore: Int, error: String?) {
        val s = session.ui.value
        // A spoken correction replaced a value: say what changed and offer undo, as for a tap.
        s.journal.drop(journalBefore).lastOrNull()?.let { j ->
            undoPrompt("${com.megathon.incident.session.Labels.fieldLabel(j.field)} changed to ${j.after ?: "empty"}")
        }
        val changed = s.fields.filter { f -> before.firstOrNull { it.field == f.field }?.let { it.value != f.value || it.status != f.status } ?: true }
            .filter { it.value != null }.map { it.field }.toSet()
        val failed = s.state as? SessionState.Failed
        val screen = when {
            s.transcripts.isEmpty() && failed != null -> Screen.HOME
            else -> Screen.REPORT
        }
        local.update { it.copy(processing = false, screen = screen, justFilled = changed, message = error ?: failed?.message, heardNow = null) }
        flashFilled()
    }

    // ---- taps and edits ------------------------------------------------------------------------------------------

    fun reply(reply: QuickReply) {
        speaker.stop()
        session.answer(reply)
        local.update { it.copy(screen = Screen.REPORT, justFilled = setOf(reply.field), speaking = false, editing = null) }
        flashFilled(); undoPrompt("${session.ui.value.fields.first { it.field == reply.field }.label} set to ${reply.label}")
    }

    fun select(field: Field?) = local.update { it.copy(selected = if (it.selected == field) null else field) }
    fun startEdit(field: Field) = local.update { it.copy(editing = field, selected = field) }
    fun cancelEdit() = local.update { it.copy(editing = null) }

    fun saveEdit(field: Field, text: String) {
        session.edit(field, text)
        local.update { it.copy(editing = null, screen = Screen.REPORT, justFilled = setOf(field)) }
        flashFilled(); undoPrompt("${session.ui.value.fields.first { it.field == field }.label} changed")
    }

    fun acceptSuggestion(field: Field) {
        session.acceptSuggestion(field)
        local.update { it.copy(justFilled = setOf(field)) }
        flashFilled(); undoPrompt("${session.ui.value.fields.first { it.field == field }.label} confirmed")
    }

    /** Accept on a suggestion chip: the field becomes the proposal; Undo brings back what was heard. */
    fun acceptChip(chip: com.megathon.incident.domain.Suggestion) {
        session.acceptSuggestion(chip)
        local.update { it.copy(justFilled = setOf(chip.field)) }
        flashFilled(); undoPrompt("${session.ui.value.fields.first { it.field == chip.field }.label} set to ${chip.proposed}")
    }

    /** Dismiss on a suggestion chip: nothing changes and the same chip is not shown again. */
    fun dismissChip(chip: com.megathon.incident.domain.Suggestion) = session.rejectSuggestion(chip)

    fun undo() { session.undo(); local.update { it.copy(undoLabel = null) } }
    fun overridePriority(level: Priority, reason: String) = session.overridePriority(level, reason)

    fun answerKeyQuestion(answer: KeyAnswer) {
        speaker.stop()
        session.answerKeyQuestion(answer)
        local.update { it.copy(speaking = false) }
    }

    // ---- navigation ----------------------------------------------------------------------------------------------

    fun goCheck() { speaker.stop(); local.update { it.copy(screen = Screen.CHECK, speaking = false) } }
    fun backToReport() = local.update { it.copy(screen = Screen.REPORT) }

    fun send() {
        viewModelScope.launch {
            speaker.stop()
            session.submit()
            local.update { it.copy(screen = Screen.SENT, network = meter.read()) }
        }
    }

    fun hearAgain() {
        val text = session.ui.value.spokenSummary ?: return
        viewModelScope.launch { local.update { it.copy(speaking = true) }; speaker.speak(text); local.update { it.copy(speaking = false) } }
    }

    fun stopSpeaking() { speaker.stop(); local.update { it.copy(speaking = false) } }

    fun newReport() {
        speaker.stop()
        session.reset()
        demoSpeech.restart()
        local.update { Local(network = meter.read(), ttsOffline = it.ttsOffline, ttsNote = it.ttsNote) }
    }

    fun openProof() = local.update { it.copy(showProof = true, network = meter.read()) }
    fun closeProof() = local.update { it.copy(showProof = false) }
    fun dismissMessage() = local.update { it.copy(message = null) }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * The language the voice should speak. The app's recognition call is fixed to English in this build (the EN/HI/TE
     * toggle is not in this branch), so this is English too. When the toggle lands, return its value here.
     */
    private fun speechLanguage(): String = "en"

    /** Debug builds only: speak [text] with the selected engine and log which engine spoke, on what, and how long it took. */
    fun debugSay(text: String) {
        if (!Engines.isDebuggable(getApplication())) return
        viewModelScope.launch {
            val t0 = System.nanoTime()
            speaker.speak(text)
            val used = speaker.last
            android.util.Log.i(
                TAG,
                "tts say engine=${used?.engine} compute=${used?.compute} offline=${used?.offline} " +
                    "ms=${(System.nanoTime() - t0) / 1_000_000} fallback=${used?.fallbackReason ?: "none"}",
            )
        }
    }

    private fun say(message: String) = local.update { it.copy(message = message) }

    private fun flashFilled() {
        clearFilled?.cancel()
        clearFilled = viewModelScope.launch { delay(1800); local.update { it.copy(justFilled = emptySet()) } }
    }

    private fun undoPrompt(label: String) {
        clearUndo?.cancel()
        local.update { it.copy(undoLabel = label) }
        clearUndo = viewModelScope.launch { delay(6000); local.update { it.copy(undoLabel = null) } }
    }

    private fun simulatedNote(): String? {
        val speechFake = selection.speech == SpeechKind.FAKE
        val llmFake = selection.llm == LlmKind.FAKE
        return when {
            speechFake && llmFake -> "scripted demo, not live recognition"
            speechFake -> "speech is scripted, not live recognition"
            llmFake -> "speech is live, but the report is filled by canned replies, not a live model"
            else -> null
        }
    }

    private fun explain(t: Throwable): String = when (t) {
        is SimulatedEngineInReleaseException -> "This build can't start. It would use a simulated engine, and simulated results are blocked in release builds."
        is EngineUnavailableException -> t.message?.replaceFirstChar { it.uppercase() }.orEmpty().let { if (it.endsWith(".")) it else "$it." }
        else -> t.message ?: "This build can't start."
    }

    override fun onCleared() {
        recorder.cancel()
        speaker.shutdown()
    }

    class Factory(private val app: Application, private val selection: EngineSelection) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app, selection) as T
    }

    private companion object {
        const val BARS = 24
        const val TAG = "Incident"
    }
}

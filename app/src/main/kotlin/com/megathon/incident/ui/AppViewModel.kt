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
import com.megathon.incident.speech.AcceleratorLane
import com.megathon.incident.speech.DemoSpeechEngine
import com.megathon.incident.speech.LaneLlmEngine
import com.megathon.incident.speech.LiveInferenceCoordinator
import com.megathon.incident.speech.PacedReplay
import com.megathon.incident.speech.PreviewScheduler
import com.megathon.incident.speech.Speculative
import com.megathon.incident.speech.GenieXLlmEngine
import com.megathon.incident.speech.HindiTranscript
import com.megathon.incident.speech.SenseVoiceSignalEngine
import com.megathon.incident.speech.NemotronHindiSpeechEngine
import com.megathon.incident.speech.RecognitionEngine
import com.megathon.incident.speech.ResidentSherpaSpeechEngine
import com.megathon.incident.speech.SherpaCliSpeechEngine
import com.megathon.incident.speech.SpeechBundle
import com.megathon.incident.speech.SupertonicSpeaker
import com.megathon.incident.speech.SpeechLane
import com.megathon.incident.speech.SpeechLanes
import com.megathon.incident.speech.TranscriptScriptNote
import com.megathon.incident.speech.SignalReading
import com.megathon.incident.speech.AndroidTtsBackend
import com.megathon.incident.speech.PiperSpeaker
import com.megathon.incident.speech.SystemTtsSpeaker
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.testing.SpeechEngine
import com.megathon.incident.tts.HindiPhrases
import com.megathon.incident.tts.HindiSummary
import com.megathon.incident.tts.RoutingSpeaker
import com.megathon.incident.tts.TtsBackend
import com.megathon.incident.tts.TtsKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class Screen { HOME, REPORT, CHECK, SENT }

/** Whether the on-device models (language model and, with the resident worker, speech) are loaded. Loading moves earlier (at launch); it is not removed. */
enum class Readiness { NOT_NEEDED, LOADING, READY, FAILED }

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
    /** The transcript so far while recording is still going on (live windows); null otherwise. */
    val liveText: String? = null,
    val readiness: Readiness = Readiness.NOT_NEEDED,
    /** True while the EXPERIMENTAL Hindi recognition lane is switched on (default off). */
    val hindiLane: Boolean = false,
    /** One short line when a transcript in Devanagari sits next to English labels and replies; null otherwise. */
    val scriptNote: String? = null,
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
    val liveText: String? = null,
    val readiness: Readiness = Readiness.NOT_NEEDED,
    val hindi: Boolean = false,
)

class AppViewModel(app: Application, private val selection: EngineSelection) : AndroidViewModel(app) {
    private val demoSpeech = DemoSpeechEngine()
    private val factory = EngineFactory(
        isDebug = Engines.isDebuggable(app),
        speechProviders = mapOf(
            SpeechKind.FAKE to { demoSpeech },
            // The resident worker when it is staged in the APK (models load once); otherwise the per-utterance CLI, which stays the baseline.
            SpeechKind.SHERPA_QNN to {
                if (!ResidentSherpaSpeechEngine.preferCliBaseline && SpeechBundle(app).worker.exists()) ResidentSherpaSpeechEngine(app) else SherpaCliSpeechEngine(app)
            },
        ),
        llmProviders = mapOf(LlmKind.FAKE to { FakeLlmEngine(name = "DEMO REPLIES") }, LlmKind.GENIEX to { GenieXLlmEngine(app) }),
        ttsProviders = mapOf(
            TtsKind.ANDROID to { AndroidTtsBackend(SystemTtsSpeaker(app)) },
            // Built only when PIPER is selected, and it loads nothing until it first speaks (the native library is lazy).
            TtsKind.PIPER to { PiperSpeaker(app, requireVerified = !Engines.isDebuggable(app), language = { speechLanguage() }) },
            // One model for Hindi and English; the language is chosen per call (see speakAloud).
            TtsKind.SUPERTONIC to { SupertonicSpeaker(app, requireVerified = !Engines.isDebuggable(app), sid = selection.ttsVoice, speed = selection.ttsSpeed) },
        ),
    )
    private val recorder = MicRecorder(app)
    // Speech output: the Android voice always exists (it is the fallback); Piper only when asked for with `--es tts PIPER`.
    // The label on the proof strip comes from the engine that really spoke (RoutingSpeaker.last), never from the request.
    private val configuredVoice: TtsBackend? =
        if (selection.tts != TtsKind.ANDROID) runCatching { factory.tts(selection.tts) }.getOrNull() else null
    // The Hindi lane speaks with Supertonic whenever its model is on the device, however the app was started (the Home switch
    // changes the lane, not the launch extras). One instance: the launch extra's, when that was already Supertonic.
    private val hindiVoice: TtsBackend? by lazy {
        if (selection.tts == TtsKind.SUPERTONIC) configuredVoice else runCatching { factory.tts(TtsKind.SUPERTONIC) }.getOrNull()
    }
    /** Why the Hindi voice was not used on the last Hindi-lane utterance (shown with the proof note); null when it was used. */
    @Volatile private var hindiVoiceNote: String? = null
    private val speaker = RoutingSpeaker(
        primary = configuredVoice,
        fallback = factory.tts(TtsKind.ANDROID),
        language = { speechLanguage() },
    )
    private val meter = NetworkMeter(app)
    private val mutex = Mutex()
    /** Speech, the language model and the tag model take turns here: separate processes do not mean separate NPUs. */
    private val accelerator = AcceleratorLane()

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
    private val session: ReportSession = Pipeline.newSession(LaneLlmEngine(llm ?: FakeLlmEngine(), accelerator), FileReportStore(app), suggestions = suggestionEngine)

    // The recognition lane. English is the default and the only lane unless Hindi is asked for (`--es lang hi` or the Home
    // switch). Telugu and unknown codes resolve to English with a note. Hindi needs the real speech engine: with the
    // scripted demo engine there is nothing to recognise, so the lane stays off and says why.
    private val requestedLane = SpeechLanes.select(selection.language)
    private val laneNote: String? = when {
        requestedLane.engine == RecognitionEngine.NEMOTRON_HINDI && selection.speech == SpeechKind.FAKE ->
            "The Hindi lane needs the real speech engine. This run uses the scripted demo, so English is used."
        else -> requestedLane.notice
    }
    private var hindiEngine: NemotronHindiSpeechEngine? = null

    private val local = MutableStateFlow(
        Local(
            network = meter.read(),
            hindi = requestedLane.engine == RecognitionEngine.NEMOTRON_HINDI && selection.speech != SpeechKind.FAKE,
            message = laneNote,
        ),
    )
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
            voiceTags = l.voiceTags, voiceTagsNote = l.voiceTagsNote, liveText = l.liveText, readiness = l.readiness,
            hindiLane = l.hindi,
            scriptNote = TranscriptScriptNote.forTexts(s.transcripts + listOfNotNull(l.heardNow)),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUi(
        screen = Screen.HOME, listening = false, processing = false, elapsedMs = 0, levels = emptyList(), session = session.ui.value,
        selected = null, editing = null, justFilled = emptySet(), heardNow = null, speaking = false, message = null, blocked = blocked,
        simulatedNote = simulatedNote(), network = local.value.network, showProof = false, ttsOffline = null, undoLabel = null,
        needsMicPermission = false, hindiLane = local.value.hindi,
    ))

    /** Debug switch (`--es live off`): transcribe and extract only after Stop, as before, for a reproducible comparison. */
    // Only with the resident worker: through the per-utterance CLI every 3 s preview would reload Whisper on the shared lane.
    private var liveInference = speech is ResidentSherpaSpeechEngine
    private var live: Live? = null
    private var replaying = false // debug replay: no spoken questions, so timings are not disturbed and the room stays quiet
    private var warm: Job? = null

    // Latency evidence for the kit comparison (batch vs live): monotonic time from the Stop tap to the finished turn, logged once per turn.
    private var turnStartNs = 0L
    private var turnMode = ""

    private class Live(val id: Long, val coordinator: LiveInferenceCoordinator, val scheduler: PreviewScheduler)

    fun debugSetLiveInference(on: Boolean) {
        if (Engines.isDebuggable(getApplication())) liveInference = on && speech is ResidentSherpaSpeechEngine
    }

    init {
        warmUpModels()
        android.util.Log.i(
            TAG,
            "speech lane: requested=${selection.language} -> language=${if (local.value.hindi) "hi" else SpeechLane.ENGLISH.language} " +
                "engine=${if (local.value.hindi) RecognitionEngine.NEMOTRON_HINDI else RecognitionEngine.WHISPER_SMALL}" +
                (laneNote?.let { " note=\"$it\"" } ?: ""),
        )
        // Speak whatever the session wants said (questions, the emergency line, the summary), one at a time.
        viewModelScope.launch {
            session.ui.map { it.say }.filterNotNull().distinctUntilChangedBy { it.id }.collect { say ->
                if (local.value.listening || replaying) return@collect
                local.update { it.copy(speaking = true) }
                val started = System.nanoTime()
                speakAloud(say.text)
                val used = speaker.last
                local.update { it.copy(speaking = false, ttsOffline = used?.offline, ttsNote = used?.note() ?: hindiVoiceNote) }
                used?.note()?.let { android.util.Log.w(TAG, "tts fallback: $it") }
                val ms = (System.nanoTime() - started) / 1_000_000
                session.recordStage(used?.toStage(ms) ?: StageRecord("Speak", AndroidTtsBackend.LABEL, Compute.CPU, ms))
            }
        }
    }

    /**
     * Loads the language model at launch instead of at the first Stop. Idempotent: one load however often it is asked for.
     * Recording may start while it loads; inference waits its turn on the lane. A failure is shown and the first real
     * request tries the lazy load again.
     */
    private fun warmUpModels() {
        // Off unless live inference is on (the resident speech worker is the engine): without it the app loads models lazily at the
        // first turn exactly as before, and no launch-time label or load appears (A/036 condition 2).
        if (!liveInference) return
        val genie = llm as? GenieXLlmEngine
        val worker = speech as? ResidentSherpaSpeechEngine
        if (genie == null && worker == null) return
        if (warm != null) return
        local.update { it.copy(readiness = Readiness.LOADING) }
        warm = viewModelScope.launch {
            val started = System.nanoTime()
            try {
                // One after the other on the shared lane: two accelerator initialisations at once is not something to test at launch.
                accelerator.run { worker?.warmUp() }
                accelerator.run { genie?.warmUp() }
                android.util.Log.i(TAG, "models ready after ${(System.nanoTime() - started) / 1_000_000} ms (launch-time load)")
                local.update { it.copy(readiness = Readiness.READY) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "model warm-up failed", e)
                local.update { it.copy(readiness = Readiness.FAILED, message = "The on-device model did not load yet: ${e.message?.take(120) ?: "unknown error"}. It will be tried again when you speak.") }
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
        val started = beginLive()
        val ok = recorder.start(
            viewModelScope,
            onLevel = { lv ->
                levels.addLast(lv); if (levels.size > BARS) levels.removeFirst()
                local.update { it.copy(levels = levels.toList()) }
            },
            onLimit = { stopAndProcess() },
            onChunk = started?.let { l -> { buffer, n -> l.coordinator.offer(buffer, n) } },
        )
        if (!ok) { started?.let(::abandon); say("I couldn't open the microphone. Another app may be using it."); return }
        local.update { it.copy(listening = true, elapsedMs = 0, levels = emptyList(), message = null, speaking = false, liveText = null) }
        timer = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            while (true) { delay(200); local.update { it.copy(elapsedMs = System.currentTimeMillis() - t0) } }
        }
    }

    private fun stopAndProcess() {
        // Atomic: a tap and the 30 s limit can both arrive; only the first one commits the recording.
        if (!local.getAndUpdate { it.copy(listening = false, processing = it.listening || it.processing) }.listening) return
        viewModelScope.launch {
            timer?.cancel()
            val l = live // taken before waiting: a second Stop (the 30 s limit racing a tap) must not commit the same recording twice
            live = null
            turnStartNs = System.nanoTime()
            turnMode = if (l != null) "live" else "batch"
            val pcm = recorder.stop()
            val capturedAt = java.time.Instant.now() // the device clock at the moment the reporter stopped speaking
            if (l != null) processLive(l, capturedAt) else process(pcm, capturedAt)
        }
    }

    /** Starts incremental transcription and provisional extraction for a new recording; null in batch mode. */
    private fun beginLive(): Live? {
        val engine = speech ?: return null
        if (!liveInference) return null
        // Live windows are English-lane only: the Hindi lane has its own experimental engine and normalisation (process() below).
        if (currentLane().engine != RecognitionEngine.WHISPER_SMALL) return null
        accelerator.startSpeculation()
        val id = session.beginRecording()
        // Extraction runs on the session's own (main) dispatcher: the session is not thread-safe, only the model call waits.
        val scheduler = PreviewScheduler(viewModelScope) { text -> withContext(Speculative) { session.previewRecording(id, text) } }
        val coordinator = LiveInferenceCoordinator(engine, currentLane().language, lane = accelerator.lock, onSnapshot = snapshot@{ s ->
            if (!local.value.listening) return@snapshot // a decode that ended after Stop: the final transcript replaces it
            val text = listOf(s.stable, s.tail).filter { it.isNotBlank() }.joinToString(" ")
            local.update { it.copy(liveText = text) }
            scheduler.offer(text)
        })
        viewModelScope.launch(Dispatchers.Default) { coordinator.run() }
        return Live(id, coordinator, scheduler).also { live = it }
    }

    private fun abandon(l: Live) {
        l.scheduler.stop()
        l.coordinator.cancel()
        session.cancelRecording(l.id)
        live = null
    }

    /**
     * Stop after a live recording: pending previews are dropped, the tail is decoded, and ONE authoritative turn is committed
     * (re-using the provisional extraction only if its transcript and baseline match exactly). Questions and speech start here.
     */
    private suspend fun processLive(l: Live, capturedAt: java.time.Instant) {
        l.scheduler.stop()
        accelerator.stopSpeculation()
        mutex.withLock {
            local.update { it.copy(listening = false, processing = true, heardNow = null, message = null) }
            val engine = speech ?: return@withLock
            val before = session.ui.value.fields
            val journalBefore = session.ui.value.journal.size
            val heard = try {
                l.coordinator.finish()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "speech engine failed", e)
                session.cancelRecording(l.id)
                local.update { it.copy(liveText = null) }
                finishTurn(before, journalBefore, "I couldn't hear that: ${e.message?.take(160) ?: "the speech engine failed"}")
                return@withLock
            }
            android.util.Log.i(TAG, "heard live [${heard.compute}] ${heard.decodeCalls} decodes, ${heard.decodeMs} ms engine time, whole-recording fallback=${heard.fullPass}: ${heard.text}")
            heard.compute?.let { c -> runCatching { factory.checkResult(c) }.onFailure { blocked = explain(it) } }
            local.update { it.copy(heardNow = heard.text.ifBlank { null }, liveText = null) }
            val tags = if (heard.text.isBlank()) null else readVoiceTags(heard.pcm)
            tags?.let { session.recordStage(StageRecord("Tags", "SenseVoice (sherpa-onnx, QNN)", it.compute, it.elapsedMs)) }
            val stage = heard.compute?.let { StageRecord("Hear", "${engine.name} (live windows)", it, heard.decodeMs) }
            session.onUtterance(heard.text, stage, tags?.signals.orEmpty(), capturedAt, recording = l.id)
            if (heard.text.isBlank()) session.cancelRecording(l.id)
            finishTurn(before, journalBefore, null)
            probeMissingAfterTurn()
        }
    }

    /**
     * The focused second look at empty tool/item and action fields: after the turn is on screen, while the reply is spoken, holding the
     * model lock so a new turn simply waits for it. Failure costs the chips, never the turn. Shared by the batch and the live path.
     */
    private suspend fun probeMissingAfterTurn() {
        try {
            session.probeMissing()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "probe failed", e)
        }
    }

    /** Hear -> read -> merge for one utterance of 16 kHz mono PCM. Shared by the microphone and the debug WAV hook. */
    private suspend fun process(pcm: ShortArray, capturedAt: java.time.Instant = java.time.Instant.now()) {
        mutex.withLock {
            local.update { it.copy(listening = false, processing = true, heardNow = null, message = null) }
            val lane = currentLane()
            val engine: SpeechEngine = if (lane.engine == RecognitionEngine.NEMOTRON_HINDI) {
                // Hindi lane: the experimental engine, opened on first use. If its files are not on the device, say so.
                openHindiEngine().getOrElse {
                    android.util.Log.w(TAG, "hindi lane unavailable: ${it.message}")
                    finishTurn(session.ui.value.fields, session.ui.value.journal.size, "I couldn't hear that: ${explain(it)}")
                    return@withLock
                }
            } else {
                speech ?: return@withLock
            }
            val before = session.ui.value.fields
            val journalBefore = session.ui.value.journal.size
            val heard = try {
                accelerator.run { engine.transcribe(pcm, lane.language) }
            } catch (e: Exception) {
                // Say why, in words and in the log: a real engine that fails must never be silent.
                android.util.Log.e(TAG, "speech engine failed", e)
                finishTurn(before, journalBefore, "I couldn't hear that: ${e.message?.take(160) ?: "the speech engine failed"}")
                return@withLock
            }
            // Hindi: hour words become digits ("दस बजे" to "10 बजे") so the time logic can read them. English is untouched.
            val said = if (lane.engine == RecognitionEngine.NEMOTRON_HINDI) heard.copy(text = HindiTranscript.normalize(heard.text)) else heard
            android.util.Log.i(TAG, "heard [${said.compute}] in ${said.elapsedMs} ms: ${said.text}")
            runCatching { factory.checkResult(heard.compute) }.onFailure { blocked = explain(it) }
            local.update { it.copy(heardNow = said.text.ifBlank { null }) }
            val tags = if (said.text.isBlank()) null else readVoiceTags(pcm)
            tags?.let { session.recordStage(StageRecord("Tags", "SenseVoice (sherpa-onnx, QNN)", it.compute, it.elapsedMs)) }
            session.onUtterance(said.text, StageRecord("Hear", engine.name, said.compute, said.elapsedMs), tags?.signals.orEmpty(), capturedAt)
            finishTurn(before, journalBefore, null)
            probeMissingAfterTurn()
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
            accelerator.run { engine.read(pcm) }.also { r ->
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
    fun debugEval(gold: String, promptFile: String?, runName: String, only: Set<String>?, probe: Boolean = false) {
        if (!Engines.isDebuggable(getApplication())) return
        val engine = llm ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val prompts = promptFile?.let { com.megathon.incident.session.PromptBuilder(java.io.File(it).readText().trim()) }
                ?: com.megathon.incident.session.PromptBuilder.default()
            val warm: suspend () -> Long = { (engine as? GenieXLlmEngine)?.warmUp() ?: 0L }
            android.util.Log.i(TAG, "eval $runName starting")
            val out = runCatching {
                com.megathon.incident.eval.EvalRunner(getApplication<Application>().filesDir, engine, warm).run(java.io.File(gold), prompts, runName, only, probe = probe)
            }
            out.onSuccess { android.util.Log.i(TAG, "eval $runName done: ${it.path}") }
                .onFailure { android.util.Log.e(TAG, "eval $runName failed", it) }
        }
    }

    /**
     * Debug builds only: replay a 16 kHz mono PCM16 WAV [loops] times and log `latency mode=live-replay|batch-replay stop_to_ready_ms=N`
     * for each. With [useLive], the audio is fed to the live coordinator at real-time pace (as spoken) and Stop is applied at its end; without,
     * the whole clip goes straight to the batch path at "Stop". The report is reset between loops. Needs the resident speech worker for live.
     */
    fun debugReplayFile(path: String, useLive: Boolean, loops: Int) {
        if (!Engines.isDebuggable(getApplication())) return
        viewModelScope.launch {
            val pcm = runCatching { readWav(java.io.File(path)) }.getOrElse {
                android.util.Log.e(TAG, "cannot read $path", it); say("Can't read that file: ${it.message}"); return@launch
            }
            replaying = true
            try {
                repeat(loops) { n ->
                    if (n > 0) newReport()
                    val l = if (useLive) beginLive() else null
                    if (useLive && l == null) {
                        android.util.Log.e(TAG, "replay: live inference is not available (it needs the resident speech worker)")
                        return@launch
                    }
                    this@AppViewModel.live = null // processLive owns the recording from here
                    local.update { it.copy(listening = true, elapsedMs = 0, levels = emptyList(), message = null, liveText = null) }
                    if (l != null) PacedReplay.feed(pcm) { buffer, count -> l.coordinator.offer(buffer, count) }
                    turnStartNs = System.nanoTime()
                    turnMode = if (l != null) "live-replay" else "batch-replay"
                    val capturedAt = java.time.Instant.now()
                    if (l != null) processLive(l, capturedAt) else process(pcm, capturedAt)
                }
            } finally {
                replaying = false
            }
        }
    }

    /** Debug builds only: run a 16 kHz mono PCM16 WAV through the same path as the microphone. */
    fun debugTranscribeFile(path: String, thenSend: Boolean = false) {
        if (!Engines.isDebuggable(getApplication())) return
        viewModelScope.launch {
            val pcm = runCatching { readWav(java.io.File(path)) }.getOrElse {
                android.util.Log.e(TAG, "cannot read $path", it); say("Can't read that file: ${it.message}"); return@launch
            }
            process(pcm)
            if (thenSend) send() // `--es submit 1`: the saved-report summary is spoken right after the clip, for hearing it
        }
    }

    /**
     * Debug builds only: Whisper (child process) and Qwen3-4B (GenieX, in process) taking turns [loops] times on one WAV,
     * to check they coexist without crashes or memory growth. Output: `<filesDir>/coexist/<timestamp>/`.
     */
    fun debugCoexist(path: String, loops: Int) {
        if (!Engines.isDebuggable(getApplication())) return
        // The lane decides which recogniser takes turns with the model: Whisper for English, the experimental Nemotron
        // child for Hindi (`--es lang hi`). The language handed to it is the lane's, so a Hindi run never feeds Whisper.
        val lane = currentLane()
        val stt: SpeechEngine? = if (lane.engine == RecognitionEngine.NEMOTRON_HINDI) {
            openHindiEngine().onFailure { android.util.Log.e(TAG, "coexist: hindi lane unavailable: ${it.message}") }.getOrNull()
        } else {
            speech
        }
        val engine = llm
        if (stt == null || engine == null) { android.util.Log.e(TAG, "coexist: no engines (${blocked ?: "unknown"})"); return }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val out = runCatching {
                val pcm = readWav(java.io.File(path))
                com.megathon.incident.eval.CoexistRunner(getApplication<Application>().filesDir, stt, engine, lane.language).run(pcm, loops)
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
        if (turnStartNs != 0L) {
            android.util.Log.i(TAG, "latency mode=$turnMode stop_to_ready_ms=${(System.nanoTime() - turnStartNs) / 1_000_000} ok=${error == null}")
            turnStartNs = 0L
        }
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
        viewModelScope.launch { local.update { it.copy(speaking = true) }; speakAloud(text); local.update { it.copy(speaking = false) } }
    }

    fun stopSpeaking() { speaker.stop(); local.update { it.copy(speaking = false) } }

    fun newReport() {
        speaker.stop()
        session.reset()
        demoSpeech.restart()
        local.update { Local(network = meter.read(), ttsOffline = it.ttsOffline, ttsNote = it.ttsNote, hindi = it.hindi, readiness = it.readiness) }
    }

    fun openProof() = local.update { it.copy(showProof = true, network = meter.read()) }
    fun closeProof() = local.update { it.copy(showProof = false) }
    fun dismissMessage() = local.update { it.copy(message = null) }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * The language the VOICE speaks, which is English in every lane: follow-up questions and spoken replies stay English
     * even when the reporter's speech was recognised as Hindi (the Piper voice is chosen from this, so it must not follow
     * the recognition lane or an English sentence would be read by the Hindi voice). What the RECOGNISER is asked for is
     * [currentLane]'s language.
     */
    private fun speechLanguage(): String = "en"

    /** The lane this turn's recognition runs in. English is exactly the lane the app had before the Hindi lane existed. */
    private fun currentLane(): SpeechLane =
        if (local.value.hindi) SpeechLanes.select("hi") else SpeechLane.ENGLISH

    private fun openHindiEngine(): Result<SpeechEngine> = runCatching {
        hindiEngine ?: NemotronHindiSpeechEngine(getApplication<Application>()).also { hindiEngine = it }
    }

    /**
     * The Home switch for the experimental Hindi lane. Turning it on checks that the Hindi files are on the device first
     * (a cheap size check, no model load) and says plainly if they are not, leaving the lane off. Not while listening.
     */
    fun setHindiLane(on: Boolean) {
        if (local.value.listening || local.value.processing) return
        if (!on) {
            local.update { it.copy(hindi = false, message = null) }
            android.util.Log.i(TAG, "speech lane: english (Hindi switched off)")
            return
        }
        if (selection.speech == SpeechKind.FAKE) {
            say("The Hindi lane needs the real speech engine. This run uses the scripted demo.")
            return
        }
        openHindiEngine().onFailure {
            android.util.Log.w(TAG, "hindi lane unavailable: ${it.message}")
            say(explain(it))
            return
        }
        local.update { it.copy(hindi = true, message = null) }
        android.util.Log.i(TAG, "speech lane: hindi (experimental), engine=${RecognitionEngine.NEMOTRON_HINDI}")
    }

    /** Debug builds only: speak [text] with the selected engine and log which engine spoke, on what, and how long it took. */
    fun debugSay(text: String) {
        if (!Engines.isDebuggable(getApplication())) return
        viewModelScope.launch {
            val t0 = System.nanoTime()
            speakAloud(text)
            val used = speaker.last
            android.util.Log.i(
                TAG,
                "tts say engine=${used?.engine} compute=${used?.compute} offline=${used?.offline} " +
                    "ms=${(System.nanoTime() - t0) / 1_000_000} fallback=${used?.fallbackReason ?: "none"}",
            )
        }
    }

    /**
     * Says [text] aloud. In the Hindi lane with the Supertonic voice ready, the app's English sentences are spoken in Hindi
     * ([HindiPhrases]: the reporter's own words and Latin-script place names are kept as they are, an unknown sentence stays
     * English). Anything else, including a Supertonic voice that is not available, speaks the text exactly as before.
     * The screen text is never changed: labels and questions stay English on screen.
     */
    private suspend fun speakAloud(text: String) {
        if (local.value.hindi) {
            val voice = hindiVoice
            val reason = if (voice == null) "the Hindi voice could not be created" else voice.unavailableReason("hi")
            if (voice != null && reason == null) {
                hindiVoiceNote = null
                speaker.usePrimary(voice)
                // The saved-report summary becomes one connected Hindi chain (what was said, the steps, the 112 call with its
                // reason); every other line is translated sentence by sentence.
                val ui = session.ui.value
                val segments = if (ui.spokenSummary != null && text == ui.spokenSummary) {
                    HindiSummary.compose(ui.report, ui.priority, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))
                } else {
                    HindiPhrases.segments(text)
                }
                speaker.speakAll(segments)
                return
            }
            hindiVoiceNote = "The Hindi voice is not available ($reason), so replies are spoken in English."
            android.util.Log.w(TAG, "tts: Hindi speech not used: $reason")
        } else {
            hindiVoiceNote = null
        }
        speaker.usePrimary(configuredVoice)
        speaker.speak(text)
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
        live?.let(::abandon)
        (speech as? ResidentSherpaSpeechEngine)?.release()
        recorder.cancel()
        speaker.shutdown()
        if (hindiVoice !== configuredVoice) hindiVoice?.shutdown()
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

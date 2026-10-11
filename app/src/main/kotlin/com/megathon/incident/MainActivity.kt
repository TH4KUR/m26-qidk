package com.megathon.incident

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import com.megathon.incident.speech.TtsVoiceCheck
import kotlinx.coroutines.launch
import java.io.File
import com.megathon.incident.engine.Engines
import com.megathon.incident.ui.AppRoot
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.screens.RecordScreen
import com.megathon.incident.ui.theme.IncidentTheme

class MainActivity : ComponentActivity() {
    // Debug switch for the kit, no rebuild: adb shell am start -n com.megathon.incident/.MainActivity --es speech FAKE --es llm FAKE
    private val vm: AppViewModel by viewModels { AppViewModel.Factory(application, Engines.selection(intent, application)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds: `--es speech_worker cli` uses the per-utterance CLI instead of the resident worker (the latency baseline).
        if (Engines.isDebuggable(this) && intent.getStringExtra("speech_worker") == "cli") com.megathon.incident.speech.ResidentSherpaSpeechEngine.preferCliBaseline = true
        // Debug builds: `--es model qualcomm/Qwen3-8B` loads that GenieX bundle from the app's model store instead of the default 4B.
        if (Engines.isDebuggable(this)) intent.getStringExtra("model")?.let { com.megathon.incident.speech.GenieXLlmEngine.debugModel = it }
        // Debug builds: `--es record <clipName>` opens a tap-to-record screen that writes filesDir/recordings/<clipName>.wav
        // (16 kHz mono PCM16) for the speech data collection. The normal report flow is not started.
        val recordClip = if (Engines.isDebuggable(this)) intent.getStringExtra("record") else null
        if (recordClip != null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            setContent { IncidentTheme { RecordScreen(recordClip) } }
            return
        }
        // Debug builds: `--es wav <path in the app's files dir>` runs that WAV through the real speech path.
        // Debug builds: `--es live off` transcribes and extracts only after Stop (the batch path), for a reproducible comparison.
        if (intent.getStringExtra("live") == "off") vm.debugSetLiveInference(false)
        // With `--es submit 1` the report is sent right after the clip, to hear the saved-report summary.
        intent.getStringExtra("wav")?.let { vm.debugTranscribeFile(it, thenSend = intent.getStringExtra("submit") == "1") }
        // Debug builds: `--es replay <wav> [--es live off] [--ei loops N]` plays the clip at real-time pace into the live path (or straight into
        // the batch path with `--es live off`) N times and logs `latency mode=... stop_to_ready_ms=...` per turn.
        intent.getStringExtra("replay")?.let { wav ->
            val n = intent.getStringExtra("loops")?.toIntOrNull() ?: intent.getIntExtra("loops", 1)
            vm.debugReplayFile(wav, useLive = intent.getStringExtra("live") != "off", loops = n.coerceAtLeast(1))
        }
        // Debug builds: `--es eval <gold file> [--es prompt <file>] --es run <name> [--es only g01,g02]` (dev split only).
        // Debug builds: the held-out items run ONLY through `--es holdout CONFIRM --es gold <file> --es run <name> [--es prompt <file>]`,
        // never in the same launch as `--es eval` (the launch is refused and nothing runs); a second run of the same prompt on the same
        // build also needs `--es holdout_again CONFIRM_AGAIN`. See AppViewModel.debugHoldout and HoldoutGuard.
        val holdoutExtra = intent.getStringExtra("holdout")
        if (holdoutExtra != null) {
            vm.debugHoldout(
                gold = intent.getStringExtra("gold"), promptFile = intent.getStringExtra("prompt"), runName = intent.getStringExtra("run") ?: "holdout",
                holdout = holdoutExtra, again = intent.getStringExtra("holdout_again"), devRequested = intent.hasExtra("eval"),
            )
        } else {
            intent.getStringExtra("eval")?.let { gold ->
                vm.debugEval(gold, intent.getStringExtra("prompt"), intent.getStringExtra("run") ?: "run", intent.getStringExtra("only")?.split(',')?.toSet(), probe = intent.getStringExtra("probe") == "on")
            }
        }
        // Debug builds: `--es tts PIPER` speaks with Piper TTS (sherpa-onnx, CPU) instead of the Android voice, falling back to the
        // Android voice with a logged reason if the Piper files or library are missing. It is read by Engines.selection above.
        // `--es say "text"` speaks that text with the selected voice and logs which engine spoke, its compute and the time (tag Incident).
        if (Engines.isDebuggable(this)) intent.getStringExtra("say")?.let { vm.debugSay(it) }
        // Debug builds: `--es tts check` logs the system TTS voices for en-IN/hi-IN/te-IN (tag Incident, prefix ttscheck) and writes files/ttscheck.txt. Silent, no downloads.
        if (intent.getStringExtra("tts") == "check" && Engines.isDebuggable(this)) {
            lifecycleScope.launch {
                val lines = TtsVoiceCheck(this@MainActivity).run()
                lines.forEach { Log.i("Incident", it) }
                runCatching { File(filesDir, "ttscheck.txt").writeText(lines.joinToString("\n") + "\n") }
            }
        }
        // Debug builds: `--es coexist <wav path> [--ei loops N]` loops Whisper then the 4B model N times (default 20) and logs PSS and timings.
        intent.getStringExtra("coexist")?.let { wav ->
            val n = intent.getStringExtra("loops")?.toIntOrNull() ?: intent.getIntExtra("loops", 20)
            vm.debugCoexist(wav, n.coerceAtLeast(1))
        }
        // Normal launches load the language model in the background now, so the first turn does not wait for it. The debug
        // eval, coexist and WAV paths time the load themselves and must not have it done for them.
        val timedByDebugPath = Engines.isDebuggable(this) && listOf("eval", "holdout", "coexist", "wav").any { intent.hasExtra(it) }
        if (!timedByDebugPath) vm.preloadModels()
        setContent {
            // The theme follows the Hindi switch live: burgundy palette when it is on, the English palette when it is off.
            val hindi = vm.ui.collectAsStateWithLifecycle().value.hindiLane
            IncidentTheme(hindi = hindi) { AppRoot(vm) }
        }
    }
}

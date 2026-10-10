package com.megathon.incident

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
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
    private val vm: AppViewModel by viewModels { AppViewModel.Factory(application, Engines.selection(intent)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds: `--es record <clipName>` opens a tap-to-record screen that writes filesDir/recordings/<clipName>.wav
        // (16 kHz mono PCM16) for the speech data collection. The normal report flow is not started.
        val recordClip = if (Engines.isDebuggable(this)) intent.getStringExtra("record") else null
        if (recordClip != null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            setContent { IncidentTheme { RecordScreen(recordClip) } }
            return
        }
        // Debug builds: `--es wav <path in the app's files dir>` runs that WAV through the real speech path.
        intent.getStringExtra("wav")?.let { vm.debugTranscribeFile(it) }
        // Debug builds: `--es eval <gold file> [--es prompt <file>] --es run <name> [--es only g01,g02]` (dev split only).
        intent.getStringExtra("eval")?.let { gold ->
            vm.debugEval(gold, intent.getStringExtra("prompt"), intent.getStringExtra("run") ?: "run", intent.getStringExtra("only")?.split(',')?.toSet())
        }
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
        setContent { IncidentTheme { AppRoot(vm) } }
    }
}

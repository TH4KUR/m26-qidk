package com.megathon.incident

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.megathon.incident.engine.Engines
import com.megathon.incident.ui.AppRoot
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.theme.IncidentTheme

class MainActivity : ComponentActivity() {
    // Debug switch for the kit, no rebuild: adb shell am start -n com.megathon.incident/.MainActivity --es speech FAKE --es llm FAKE
    private val vm: AppViewModel by viewModels { AppViewModel.Factory(application, Engines.selection(intent)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds: `--es wav <path in the app's files dir>` runs that WAV through the real speech path.
        intent.getStringExtra("wav")?.let { vm.debugTranscribeFile(it) }
        // Debug builds: `--es eval <gold file> [--es prompt <file>] --es run <name> [--es only g01,g02]` (dev split only).
        intent.getStringExtra("eval")?.let { gold ->
            vm.debugEval(gold, intent.getStringExtra("prompt"), intent.getStringExtra("run") ?: "run", intent.getStringExtra("only")?.split(',')?.toSet())
        }
        // Debug builds: `--es coexist <wav path> [--ei loops N]` loops Whisper then the 4B model N times (default 20) and logs PSS and timings.
        intent.getStringExtra("coexist")?.let { wav ->
            val n = intent.getStringExtra("loops")?.toIntOrNull() ?: intent.getIntExtra("loops", 20)
            vm.debugCoexist(wav, n.coerceAtLeast(1))
        }
        setContent { IncidentTheme { AppRoot(vm) } }
    }
}

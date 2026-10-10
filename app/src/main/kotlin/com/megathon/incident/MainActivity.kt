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
        setContent { IncidentTheme { AppRoot(vm) } }
    }
}

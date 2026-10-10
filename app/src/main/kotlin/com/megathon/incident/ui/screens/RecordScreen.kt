package com.megathon.incident.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.megathon.incident.audio.ClipName
import com.megathon.incident.audio.ClipStats
import com.megathon.incident.audio.MicRecorder
import com.megathon.incident.audio.WavWriter
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.LevelBars
import com.megathon.incident.ui.components.MicButton
import com.megathon.incident.ui.components.formatClock
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Saved(val path: String, val seconds: Double, val stats: ClipStats?)

private const val MAX_CLIP_SECONDS = 120

/**
 * Debug-only data collection: one big Record/Stop button, live level, timer. Recording starts only on the user's tap.
 * On stop the clip is written to filesDir/recordings/<clipName>.wav (16 kHz mono PCM16); a retake overwrites it.
 */
@Composable
fun RecordScreen(rawName: String) {
    val c = Theme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = remember(rawName) { ClipName.sanitize(rawName) }
    val recorder = remember { MicRecorder(context, maxSamples = MicRecorder.SAMPLE_RATE * MAX_CLIP_SECONDS) }
    val levels = remember { mutableStateListOf<Float>() }
    var recording by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Saved?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    fun stopAndSave() {
        if (!recording) return
        recording = false
        saving = true
        scope.launch {
            val samples = recorder.stop()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "recordings").apply { mkdirs() }
                    val file = File(dir, "$clip.wav")
                    file.writeBytes(WavWriter.encode(samples, MicRecorder.SAMPLE_RATE))
                    Saved(file.absolutePath, samples.size.toDouble() / MicRecorder.SAMPLE_RATE, ClipStats.of(samples))
                }
            }
            result.onSuccess { saved = it; error = null }.onFailure { error = "Could not save: ${it.message}" }
            saving = false
        }
    }

    fun begin() {
        levels.clear(); saved = null; error = null
        val ok = recorder.start(scope, onLevel = { lv -> levels.add(lv); if (levels.size > 48) levels.removeAt(0) }, onLimit = { scope.launch { stopAndSave() } })
        if (ok) { startedAt = System.currentTimeMillis(); elapsedMs = 0; recording = true }
        else error = "The microphone could not be opened (permission missing, or another app is using it)."
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else error = "Microphone permission was denied."
    }

    LaunchedEffect(recording) { while (recording) { elapsedMs = System.currentTimeMillis() - startedAt; delay(100) } }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    Scaffold(containerColor = c.canvas, topBar = { AppTopBar("Record clip", onProof = null) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.s4))
            Text("$clip.wav", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Text(
                "16 kHz mono PCM16. Tap to start, tap again to stop. Nothing is recorded until you tap.",
                style = MaterialTheme.typography.bodyMedium, color = c.ink2, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.s2),
            )
            Spacer(Modifier.height(Space.s8))
            Text(formatClock(elapsedMs), style = MaterialTheme.typography.displayMedium, color = if (recording) c.danger else c.ink)
            Spacer(Modifier.height(Space.s4))
            LevelBars(levels.toList(), count = 36, barHeight = 56.dp)
            Spacer(Modifier.height(Space.s6))
            MicButton(
                listening = recording, busy = saving, level = levels.lastOrNull() ?: 0f, diameter = 144.dp,
                onClick = {
                    if (recording) stopAndSave()
                    else if (recorder.hasPermission()) begin()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
            Text(
                if (recording) "Recording... tap to stop" else if (saving) "Saving" else if (saved != null) "Tap to record again (overwrites)" else "Tap to record",
                style = MaterialTheme.typography.labelLarge, color = c.ink2,
            )
            Spacer(Modifier.height(Space.s6))
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onDanger, modifier = Modifier.fillMaxWidth()) }
            saved?.let { s ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    Text("Saved", style = MaterialTheme.typography.titleMedium, color = c.accent)
                    Text(s.path, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    Text("Duration %.1f s".format(s.seconds), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    if (s.stats != null) {
                        Text("Peak %.1f dBFS · Mean %.1f dBFS".format(s.stats.peakDbfs, s.stats.meanDbfs), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        Text(levelHint(s.stats), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    }
                }
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

/** The kit mic is quiet (about -39 dBFS mean for normal speech); say so plainly instead of leaving the number to interpretation. */
private fun levelHint(s: ClipStats): String = when {
    s.peakDbfs >= -1.0 -> "Clipping: move back or speak softer."
    s.meanDbfs < -50.0 -> "Very quiet: get closer to the mic and speak up."
    s.meanDbfs < -42.0 -> "A bit quiet: speak up or move closer (about -39 dBFS mean is typical on this kit)."
    else -> "Level looks fine."
}

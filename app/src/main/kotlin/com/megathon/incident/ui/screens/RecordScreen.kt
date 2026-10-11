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
import com.megathon.incident.session.HindiSavedAt
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Saved(val path: String, val seconds: Double, val stats: ClipStats?)

/** What went wrong, kept as a kind and not as text, so it is written in whichever language is on when it is drawn. */
private enum class Problem { SAVE_FAILED, MIC_UNAVAILABLE, PERMISSION_DENIED }
private data class RecordError(val kind: Problem, val detail: String? = null)

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
    var error by remember { mutableStateOf<RecordError?>(null) }
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
            result.onSuccess { saved = it; error = null }.onFailure { error = RecordError(Problem.SAVE_FAILED, it.message) }
            saving = false
        }
    }

    fun begin() {
        levels.clear(); saved = null; error = null
        val ok = recorder.start(scope, onLevel = { lv -> levels.add(lv); if (levels.size > 48) levels.removeAt(0) }, onLimit = { scope.launch { stopAndSave() } })
        if (ok) { startedAt = System.currentTimeMillis(); elapsedMs = 0; recording = true }
        else error = RecordError(Problem.MIC_UNAVAILABLE)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else error = RecordError(Problem.PERMISSION_DENIED)
    }

    LaunchedEffect(recording) { while (recording) { elapsedMs = System.currentTimeMillis() - startedAt; delay(100) } }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    val hindi = isHindi()
    fun num(text: String) = if (hindi) HindiSavedAt.digits(text) else text
    Scaffold(containerColor = c.canvas, topBar = { AppTopBar(tr("Record clip", "क्लिप रिकॉर्ड करें"), onProof = null) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.s4))
            Text("$clip.wav", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Text(
                tr(
                    "16 kHz mono PCM16. Tap to start, tap again to stop. Nothing is recorded until you tap.",
                    "१६ किलोहर्ट्ज़, मोनो, PCM16। शुरू करने के लिए टैप करें, रोकने के लिए फिर टैप करें। आपके टैप करने तक कुछ भी रिकॉर्ड नहीं होता।",
                ),
                style = MaterialTheme.typography.bodyMedium, color = c.ink2, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.s2),
            )
            Spacer(Modifier.height(Space.s8))
            Text(num(formatClock(elapsedMs)), style = MaterialTheme.typography.displayMedium, color = if (recording) c.danger else c.ink)
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
                if (recording) tr("Recording... tap to stop", "रिकॉर्डिंग हो रही है... रोकने के लिए टैप करें")
                else if (saving) tr("Saving", "सहेजा जा रहा है")
                else if (saved != null) tr("Tap to record again (overwrites)", "दोबारा रिकॉर्ड करने के लिए टैप करें (पुराना मिट जाएगा)")
                else tr("Tap to record", "रिकॉर्ड करने के लिए टैप करें"),
                style = MaterialTheme.typography.labelLarge, color = c.ink2,
            )
            Spacer(Modifier.height(Space.s6))
            error?.let { e ->
                val message = when (e.kind) {
                    Problem.SAVE_FAILED -> tr("Could not save: ${e.detail}", "सहेजा नहीं जा सका: ${e.detail}")
                    Problem.MIC_UNAVAILABLE -> tr(
                        "The microphone could not be opened (permission missing, or another app is using it).",
                        "माइक खुल नहीं सका (अनुमति नहीं है, या कोई और ऐप उसे इस्तेमाल कर रहा है)।",
                    )
                    Problem.PERMISSION_DENIED -> tr("Microphone permission was denied.", "माइक की अनुमति नहीं दी गई।")
                }
                Text(message, style = MaterialTheme.typography.bodyMedium, color = c.onDanger, modifier = Modifier.fillMaxWidth())
            }
            saved?.let { s ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    Text(tr("Saved", "सहेजा गया"), style = MaterialTheme.typography.titleMedium, color = c.accent)
                    Text(s.path, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    Text(tr("Duration %.1f s".format(s.seconds), num("अवधि %.1f सेकंड".format(s.seconds))), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    if (s.stats != null) {
                        Text(
                            tr(
                                "Peak %.1f dBFS · Mean %.1f dBFS".format(s.stats.peakDbfs, s.stats.meanDbfs),
                                num("शिखर %.1f डीबीएफ़एस · औसत %.1f डीबीएफ़एस".format(s.stats.peakDbfs, s.stats.meanDbfs)),
                            ),
                            style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        Text(levelHint(s.stats, hindi), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    }
                }
            }
            Spacer(Modifier.height(Space.s6))
        }
    }
}

/** The kit mic is quiet (about -39 dBFS mean for normal speech); say so plainly instead of leaving the number to interpretation. */
private fun levelHint(s: ClipStats, hindi: Boolean): String = when {
    s.peakDbfs >= -1.0 -> if (hindi) "आवाज़ कट रही है: थोड़ा पीछे हटें या धीमे बोलें।" else "Clipping: move back or speak softer."
    s.meanDbfs < -50.0 -> if (hindi) "बहुत धीमी: माइक के पास आएँ और ज़ोर से बोलें।" else "Very quiet: get closer to the mic and speak up."
    s.meanDbfs < -42.0 ->
        if (hindi) "थोड़ी धीमी: ज़ोर से बोलें या पास आएँ (इस किट पर औसत लगभग -३९ डीबीएफ़एस सामान्य है)।"
        else "A bit quiet: speak up or move closer (about -39 dBFS mean is typical on this kit)."
    else -> if (hindi) "स्तर ठीक है।" else "Level looks fine."
}

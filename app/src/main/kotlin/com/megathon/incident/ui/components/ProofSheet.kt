package com.megathon.incident.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.megathon.incident.session.JournalEntry
import com.megathon.incident.session.Labels
import com.megathon.incident.session.StageRecord
import com.megathon.incident.session.ToolLogEntry
import com.megathon.incident.testing.Compute
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/**
 * "How this ran": per stage where it really ran, the tool-call trail, what changed, and what the network did.
 * Everything here is read from real results; a stage that has not run says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofSheet(ui: AppUi, onClose: () -> Unit) {
    val c = Theme.colors
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        contentColor = c.ink,
    ) {
        LazyColumn(
            Modifier.navigationBarsPadding().padding(horizontal = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s6),
        ) {
            item { Text("How this ran", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }

            if (ui.simulated || ui.session.stages.any { it.compute == Compute.SIMULATED }) {
                item {
                    Column(Modifier.fillMaxWidth().background(c.simulatedContainer, RoundedCornerShape(16.dp)).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        ComputePill(Compute.SIMULATED)
                        Text(
                            "Part of this run is simulated: ${ui.simulatedNote ?: "see the steps below"}. Simulated results are labelled wherever they appear.",
                            style = MaterialTheme.typography.bodyLarge, color = c.onSimulated,
                        )
                    }
                }
            }

            item { Section("Where each step ran") { Stages(ui.session.stages, ui.ttsOffline, ui.ttsNote) } }

            item { Section("Voice tags (suggestions only)") { VoiceTags(ui) } }

            item {
                Section("Network") {
                    Fact("Airplane mode", if (ui.network.airplaneMode) "On" else "Off")
                    Fact("Network available", if (ui.network.networkAvailable) "Yes" else "No")
                    Fact("Data sent by this app", formatBytes(ui.network.bytesSent))
                    Text("Reports are saved only on this device.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
            }

            item { Section("What the assistant did") { if (ui.session.toolLog.isEmpty()) Muted("Nothing yet.") } }
            items(ui.session.toolLog) { ToolRow(it) }

            item { Section("Changes you and the assistant made") { if (ui.session.journal.isEmpty()) Muted("No values have been changed.") } }
            items(ui.session.journal) { JournalRow(it) }

            item { Muted("The original wording is kept for every change.", Modifier.padding(bottom = Space.s8)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Theme.colors.ink2, modifier = modifier)

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Theme.colors.ink2)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private val STAGES = listOf("Hear" to "Heard with", "Read" to "Understood with", "Speak" to "Spoken with")

@Composable
private fun Stages(stages: List<StageRecord>, ttsOffline: Boolean?, ttsNote: String?) {
    val c = Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        STAGES.forEach { (key, label) ->
            val s = stages.firstOrNull { it.stage == key }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2)
                if (s == null) {
                    Text("Not run yet", style = MaterialTheme.typography.bodyLarge, color = c.ink3)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Text(s.engine, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                        ComputePill(s.compute)
                        s.ms?.let { Text("$it ms", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = c.ink2) }
                    }
                    if (key == "Speak") {
                        val piper = s.engine.startsWith("Piper")
                        Text(
                            when {
                                piper -> "Uses the Piper voice (an open model run by sherpa-onnx), offline, on the CPU."
                                ttsOffline == true -> "Uses an offline system voice, on the CPU."
                                else -> "Uses the system voice, on the CPU. No offline voice was confirmed."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = c.ink2,
                        )
                        if (ttsNote != null) Text(ttsNote, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceTags(ui: AppUi) {
    val c = Theme.colors
    val tags = ui.voiceTags
    if (tags == null || tags.signals.isEmpty()) {
        Muted(ui.voiceTagsNote ?: "None heard")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        tags.signals.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(
                    "${if (s.kind == "emotion") "Voice" else "Sound"}: ${s.label}",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false),
                )
                Text("from SenseVoice", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                ComputePill(tags.compute)
            }
        }
    }
}

@Composable
private fun ToolRow(e: ToolLogEntry) {
    val c = Theme.colors
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).clickable(onClickLabel = if (open) "Hide details" else "Show details") { open = !open }.padding(Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Text(e.tool, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodyLarge.fontSize), color = c.accent)
        Text(e.json, style = MaterialTheme.typography.bodyMedium, color = c.ink2, maxLines = if (open) Int.MAX_VALUE else 2)
    }
}

@Composable
private fun JournalRow(j: JournalEntry) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text(Labels.fieldLabel(j.field), style = MaterialTheme.typography.labelMedium, color = c.ink2)
        Text("${j.before ?: "(empty)"}  →  ${j.after ?: "(empty)"}", style = MaterialTheme.typography.bodyLarge)
        Text(j.reason, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
    }
}

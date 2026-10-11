package com.megathon.incident.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megathon.incident.session.FieldView
import com.megathon.incident.session.SessionState
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.components.StageTrack
import com.megathon.incident.ui.components.Reveal
import com.megathon.incident.ui.components.RecorderAura
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.Card
import com.megathon.incident.ui.components.Hairline
import com.megathon.incident.ui.components.Pill
import com.megathon.incident.ui.components.PrimaryButton
import com.megathon.incident.ui.components.PrivacyLine
import com.megathon.incident.ui.components.SecondaryButton
import com.megathon.incident.ui.components.StatusChip
import com.megathon.incident.ui.components.TextAction
import com.megathon.incident.ui.screens.isWide
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/** Read-only review before saving; assistant-written fields stay explicitly labelled. */
@Composable
fun CheckScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val missing = ui.session.fields.filter { it.mandatory && it.status == UiStatus.NEEDED }
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar("Review your report", onProof = vm::openProof, onBack = vm::backToReport, simulatedNote = ui.simulatedNote) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(c.canvas).navigationBarsPadding()) {
                Hairline()
                if (isWide()) {
                    Row(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        SecondaryButton("Keep editing", vm::backToReport, Modifier.weight(1f))
                        PrimaryButton(if (missing.isEmpty()) "Save report" else "Save with missing details", vm::send, Modifier.weight(1f))
                    }
                } else Column(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    PrimaryButton(if (missing.isEmpty()) "Save report" else "Save with missing details", vm::send, Modifier.fillMaxWidth())
                    SecondaryButton("Keep editing", vm::backToReport, Modifier.fillMaxWidth())
                }
            }
        },
    ) { pad ->
        if (isWide()) {
            Row(Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                Column(Modifier.weight(.4f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    StageTrack(2)
                    Text("One last look.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text("Your report stays on this device. Check the details before saving.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    Text("Assistant-written details are labelled.", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                }
                LazyColumn(Modifier.weight(.6f), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    if (missing.isNotEmpty()) item {
                        Text("Still missing: " + missing.joinToString(", ") { it.label.lowercase() }, style = MaterialTheme.typography.bodyMedium, color = c.onAttention)
                    }
                    item {
                        ui.session.fields.forEachIndexed { index, field ->
                            if (index > 0) Hairline()
                            SummaryRow(field)
                        }
                    }
                }
            }
        } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    StageTrack(2)
                    Text("One last look.", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
                    Text("Your report stays on this device. You decide when it’s ready.", style = MaterialTheme.typography.bodyLarge, color = c.ink2)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(Space.s4),
                    horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = c.onInfo, modifier = Modifier.size(20.dp))
                    Text(
                        "Built from your words. Check the details before saving.",
                        style = MaterialTheme.typography.bodyLarge, color = c.ink2,
                    )
                }
            }
            if (missing.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().background(c.attentionContainer, RoundedCornerShape(16.dp)).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        Text("Still missing", style = MaterialTheme.typography.titleMedium, color = c.onAttention)
                        Text(
                            missing.joinToString(", ") { it.label.lowercase() } + ". You can save without it, or go back and add it.",
                            style = MaterialTheme.typography.bodyLarge, color = c.onAttention,
                        )
                    }
                }
            }
            item {
                Card {
                    ui.session.fields.forEachIndexed { i, v -> if (i > 0) Hairline(); SummaryRow(v) }
                }
            }
            item { Spacer(Modifier.height(Space.s4)) }
        }
        }
    }
}

@Composable
private fun SummaryRow(v: FieldView) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.label, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
            if (v.writtenByAssistant) Text("Assistant-written", style = MaterialTheme.typography.labelSmall, color = c.onAttention) else StatusChip(v.status)
        }
        Text(
            v.value ?: if (v.status == UiStatus.NEEDED) "Needed" else "Not mentioned",
            style = MaterialTheme.typography.bodyLarge,
            color = when { v.value != null -> c.ink; v.status == UiStatus.NEEDED -> c.onAttention; else -> c.ink3 },
        )
    }
}

/** Confirmation that it is saved, and that it went nowhere else. */
@Composable
fun SentScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val sent = ui.session.state as? SessionState.Submitted
    Scaffold(containerColor = c.canvas, topBar = { AppTopBar("Complete", onProof = vm::openProof, simulatedNote = ui.simulatedNote) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.s6))
                Reveal {
                    Box(Modifier.size(112.dp).background(c.accentContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(80.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(48.dp))
                        }
                    }
                }
                Spacer(Modifier.height(Space.s6))
                Reveal(delayMillis = 120) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                        Text("All set.\nReport saved.", style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 40.sp), textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                        Text("Saved on this device. Nothing was sent anywhere.", style = MaterialTheme.typography.bodyLarge, color = c.ink2, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(Space.s6))
                Card {
                    ui.session.fields.filter { it.mandatory }.forEachIndexed { i, v -> if (i > 0) Hairline(); SummaryRow(v) }
                }
                Spacer(Modifier.height(Space.s4))
                if (ui.speaking) TextAction("Stop speaking", vm::stopSpeaking) else TextAction("Hear it again", vm::hearAgain)
                sent?.let { Text("Reference ${it.reportId.takeLast(8)}", style = MaterialTheme.typography.labelSmall, color = c.ink3) }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                PrimaryButton("New report", vm::newReport, Modifier.fillMaxWidth())
                PrivacyLine(ui.network)
            }
        }
    }
}

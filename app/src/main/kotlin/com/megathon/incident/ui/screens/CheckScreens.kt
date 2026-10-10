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
import com.megathon.incident.session.FieldView
import com.megathon.incident.session.SessionState
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
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

/** The human-in-the-loop gate: a read-only summary where the assistant's own writing is labelled before anything is sent. */
@Composable
fun CheckScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val missing = ui.session.fields.filter { it.mandatory && it.status == UiStatus.NEEDED }
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar("Check report", onProof = vm::openProof, onBack = vm::backToReport, simulatedNote = ui.simulatedNote) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(c.canvas).navigationBarsPadding()) {
                Hairline()
                if (isWide()) {
                    Row(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        SecondaryButton("Back to edit", vm::backToReport, Modifier.weight(1f))
                        PrimaryButton(if (missing.isEmpty()) "Send report" else "Send anyway", vm::send, Modifier.weight(1f))
                    }
                } else Column(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    PrimaryButton(if (missing.isEmpty()) "Send report" else "Send anyway", vm::send, Modifier.fillMaxWidth())
                    SecondaryButton("Back to edit", vm::backToReport, Modifier.fillMaxWidth())
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(c.infoContainer, RoundedCornerShape(16.dp)).padding(Space.s4),
                    horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = c.onInfo, modifier = Modifier.size(20.dp))
                    Text(
                        "The assistant filled some of these from your voice. Check them before sending.",
                        style = MaterialTheme.typography.bodyLarge, color = c.onInfo,
                    )
                }
            }
            if (missing.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().background(c.attentionContainer, RoundedCornerShape(16.dp)).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        Text("Still missing", style = MaterialTheme.typography.titleMedium, color = c.onAttention)
                        Text(
                            missing.joinToString(", ") { it.label.lowercase() } + ". You can send without it, or go back and add it.",
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

@Composable
private fun SummaryRow(v: FieldView) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.label, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
            if (v.writtenByAssistant) Pill("Written by the assistant", c.attentionContainer, c.onAttention) else StatusChip(v.status)
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
    Scaffold(containerColor = c.canvas, topBar = { AppTopBar("Sent", onProof = vm::openProof, simulatedNote = ui.simulatedNote) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.s8))
                Box(Modifier.size(96.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(Space.s6))
                Text("Report saved", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.s2))
                Text("Saved on this device. Nothing was sent anywhere.", style = MaterialTheme.typography.bodyLarge, color = c.ink2, textAlign = TextAlign.Center)
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

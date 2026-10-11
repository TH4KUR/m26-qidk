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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.session.FieldView
import com.megathon.incident.session.HindiLabels
import com.megathon.incident.session.HindiUiText
import com.megathon.incident.session.SessionState
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.components.Panel
import com.megathon.incident.ui.components.SummaryPlayerCard
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
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.screens.isWide
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme
import java.time.LocalDate
import java.time.ZoneId

/** Read-only review before saving; assistant-written fields stay explicitly labelled. */
@Composable
fun CheckScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val missing = ui.session.fields.filter { it.mandatory && it.status == UiStatus.NEEDED }
    val report = ui.session.report
    val saveLabel = if (missing.isEmpty()) tr("Save report", "रिपोर्ट सेव करें") else tr("Save with missing details", "बाकी जानकारी के बिना सेव करें")
    val keepEditing = tr("Keep editing", "और बदलाव करें")
    val hindi = isHindi()
    val missingNames = missing.joinToString(", ") { shownLabel(it, hindi).lowercase() }
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar(tr("Review your report", "रिपोर्ट देख लें"), onProof = vm::openProof, onBack = vm::backToReport, simulatedNote = ui.simulatedNote) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(c.canvas).navigationBarsPadding()) {
                Hairline()
                if (isWide()) {
                    Row(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        SecondaryButton(keepEditing, vm::backToReport, Modifier.weight(1f))
                        PrimaryButton(saveLabel, vm::send, Modifier.weight(1f))
                    }
                } else Column(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    PrimaryButton(saveLabel, vm::send, Modifier.fillMaxWidth())
                    SecondaryButton(keepEditing, vm::backToReport, Modifier.fillMaxWidth())
                }
            }
        },
    ) { pad ->
        if (isWide()) {
            Row(Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter, vertical = Space.s2), horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                Column(Modifier.weight(.4f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Text(tr("One last look.", "एक बार देख लें।"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(tr("Your report stays on this device. Check the details before saving.", "आपकी रिपोर्ट इसी डिवाइस पर रहती है। सेव करने से पहले जानकारी देख लें।"), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    Text(tr("Assistant-written details are labelled.", "सहायक की लिखी जानकारी पर लेबल है।"), style = MaterialTheme.typography.labelSmall, color = c.ink3)
                }
                LazyColumn(Modifier.weight(.6f), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    if (missing.isNotEmpty()) item {
                        Text(tr("Still missing: ", "अभी बाकी: ") + missingNames, style = MaterialTheme.typography.bodyMedium, color = c.onAttention)
                    }
                    item {
                        ui.session.fields.forEachIndexed { index, field ->
                            if (index > 0) Hairline()
                            SummaryRow(field, report)
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
                    Text(tr("One last look.", "एक बार देख लें।"), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
                    Text(tr("Built from your words and kept on this device. Check the details, then save.", "आपकी बातों से बनी है और इसी डिवाइस पर रहती है। जानकारी देख लें, फिर सेव करें।"), style = MaterialTheme.typography.bodyLarge, color = c.ink2)
                }
            }
            if (missing.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().background(c.attentionContainer, RoundedCornerShape(16.dp)).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        Text(tr("Still missing", "अभी बाकी है"), style = MaterialTheme.typography.titleMedium, color = c.onAttention)
                        Text(
                            missingNames + tr(". You can save without it, or go back and add it.", "। इसके बिना भी सेव कर सकते हैं, या वापस जाकर जोड़ सकते हैं।"),
                            style = MaterialTheme.typography.bodyLarge, color = c.onAttention,
                        )
                    }
                }
            }
            item {
                // Fields with an answer (and the essentials, even when empty) get a row; the rest are one quiet line.
                val shown = ui.session.fields.filter { it.value != null || it.mandatory }
                val empty = ui.session.fields.filter { it.value == null && !it.mandatory }
                Panel(tr("Your report", "आपकी रिपोर्ट")) {
                    shown.forEachIndexed { i, v -> if (i > 0) Hairline(); SummaryRow(v, report) }
                    if (empty.isNotEmpty()) {
                        Hairline()
                        Text(
                            tr("Not mentioned: ", "नहीं बताया: ") + empty.joinToString(", ") { shownLabel(it, hindi).lowercase() },
                            style = MaterialTheme.typography.bodyMedium, color = c.ink3,
                            modifier = Modifier.padding(horizontal = Space.s1, vertical = Space.s3),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Space.s4)) }
        }
        }
    }
}

@Composable
private fun SummaryRow(v: FieldView, report: IncidentReport) {
    val c = Theme.colors
    val today = remember { LocalDate.now(ZoneId.of("Asia/Kolkata")) }
    val needed = if (isHindi()) HindiLabels.status(UiStatus.NEEDED) ?: "Needed" else "Needed"
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s1, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(shownLabel(v, isHindi()), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
            if (v.writtenByAssistant) Pill(tr("Assistant-written", "सहायक ने लिखा"), c.infoContainer, c.onInfo, icon = androidx.compose.material.icons.Icons.Rounded.Info) else StatusChip(v.status)
        }
        Text(
            shownValue(v, report, today) ?: if (v.status == UiStatus.NEEDED) needed else tr("Not mentioned", "नहीं बताया"),
            style = MaterialTheme.typography.bodyLarge,
            color = when { v.value != null -> c.ink; v.status == UiStatus.NEEDED -> c.onAttention; else -> c.ink3 },
        )
    }
}

/** The field label in the language of the screen (the stored label is English). */
private fun shownLabel(v: FieldView, hindi: Boolean): String = if (hindi) HindiLabels.field(v.field) else v.label

/**
 * The value as drawn. English is exactly the session's own text. Hindi re-derives it from the stored value (type and severity by
 * name, the date through HindiDate); "Don't know" and "None" are words of the session, not stored values; the assistant's own
 * sentences go through the core's Hindi table and come back unchanged when it has none.
 */
@Composable
private fun shownValue(v: FieldView, report: IncidentReport, today: LocalDate): String? {
    val value = v.value ?: return null
    if (!isHindi()) return value
    return when {
        v.status == UiStatus.UNKNOWN -> "पता नहीं"
        v.status == UiStatus.NONE -> "कोई नहीं"
        v.writtenByAssistant -> HindiUiText.of(value)
        else -> HindiLabels.display(v.field, report.value(v.field).text, today) ?: value
    }
}

/** Confirmation that it is saved, and that it went nowhere else. */
@Composable
fun SentScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val sent = ui.session.state as? SessionState.Submitted
    Scaffold(containerColor = c.canvas, topBar = { AppTopBar(tr("Complete", "पूरा हुआ"), onProof = vm::openProof, simulatedNote = ui.simulatedNote) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.s4))
                // A compact confirmation: the player and the report are what the reporter came to this screen for.
                Reveal {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                        Box(Modifier.size(56.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(32.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                            Text(tr("Report saved.", "रिपोर्ट सेव हो गई।"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                            Text(tr("Saved on this device. Nothing was sent anywhere.", "इस डिवाइस पर सेव हुई। कहीं भेजी नहीं गई।"), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                        }
                    }
                }
                Spacer(Modifier.height(Space.s6))
                val audio = ui.summaryAudio
                if (audio != null) SummaryPlayerCard(audio, vm::toggleSummary, vm::seekSummary)
                else if (ui.speaking) TextAction(tr("Stop speaking", "बोलना बंद करें"), vm::stopSpeaking) else TextAction(tr("Hear it again", "फिर से सुनें"), vm::hearAgain)
                Spacer(Modifier.height(Space.s6))
                Panel(tr("Your report", "आपकी रिपोर्ट")) {
                    ui.session.fields.filter { it.mandatory }.forEachIndexed { i, v -> if (i > 0) Hairline(); SummaryRow(v, ui.session.report) }
                }
                Spacer(Modifier.height(Space.s3))
                sent?.let { Text(tr("Reference ", "संदर्भ ") + it.reportId.takeLast(8), style = MaterialTheme.typography.labelSmall, color = c.ink3) }
                Spacer(Modifier.height(Space.s4))
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                PrimaryButton(tr("New report", "नई रिपोर्ट"), vm::newReport, Modifier.fillMaxWidth())
                PrivacyLine(ui.network)
            }
        }
    }
}

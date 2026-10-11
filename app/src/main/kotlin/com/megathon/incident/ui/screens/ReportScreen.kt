package com.megathon.incident.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.session.FieldView
import com.megathon.incident.session.Labels
import com.megathon.incident.session.QuestionPlanner
import com.megathon.incident.session.QuickReply
import com.megathon.incident.session.SessionState
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.Card
import com.megathon.incident.ui.components.FieldRow
import com.megathon.incident.ui.components.Hairline
import com.megathon.incident.ui.components.LevelBars
import com.megathon.incident.ui.components.MessageCard
import com.megathon.incident.ui.components.MicButton
import com.megathon.incident.ui.components.MicGlyph
import com.megathon.incident.ui.components.ProvisionalCard
import com.megathon.incident.ui.components.Pill
import com.megathon.incident.ui.components.PriorityBanner
import com.megathon.incident.ui.components.PrimaryButton
import com.megathon.incident.ui.components.QuestionDock
import com.megathon.incident.ui.components.SecondaryButton
import com.megathon.incident.ui.components.TextAction
import com.megathon.incident.ui.components.TranscriptCard
import com.megathon.incident.ui.components.VoiceButton
import com.megathon.incident.ui.components.formatClock
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

private const val WIDE_DP = 700

/** The conversation and the live form. Wide screens (a projector, a tablet) show the conversation and the report side by side. */
@Composable
fun ReportScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val wide = isWide()
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar("Your report", onProof = vm::openProof, simulatedNote = ui.simulatedNote) },
        bottomBar = { ReportBottomBar(ui, vm) },
    ) { pad ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                LazyColumn(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(Space.s4), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4)) {
                    conversation(ui, vm, inlineQuestion = true)
                }
                LazyColumn(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(Space.s4), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4)) {
                    fields(ui, vm)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4),
            ) {
                conversation(ui, vm)
                fields(ui, vm)
            }
        }
    }
}

/** Wide when there is room side by side: a tablet, a projector, or any landscape screen. */
@Composable
fun isWide(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp >= WIDE_DP || cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
}

private fun androidx.compose.foundation.lazy.LazyListScope.conversation(ui: AppUi, vm: AppViewModel, inlineQuestion: Boolean = false) {
    ui.message?.let { m -> item(key = "message") { MessageCard(m, vm::dismissMessage) } }
    ui.session.priority?.let { p ->
        item(key = "priority") {
            PriorityBanner(
                p,
                onOverride = { vm.overridePriority(it, "set by the reporter") },
                deescalation = ui.session.deescalation,
                onLower = { vm.overridePriority(Priority.LOW, "cancelled alarm") },
            )
        }
    }
    item(key = "transcript") {
        val highlight = ui.session.fields.firstOrNull { it.field == ui.selected }?.evidence
        TranscriptCard(ui.session.transcripts, highlight, ui.heardNow ?: ui.liveText, reading = ui.processing)
    }
    if (ui.listening) item(key = "provisional") { ProvisionalCard(ui.session.provisional) }
    val q = ui.session.state as? SessionState.Asking
    if (inlineQuestion && q != null && !ui.processing && !ui.listening) {
        item(key = "question") { QuestionDock(q.question, ui.speaking, vm::reply, vm::onMicTapped, vm::stopSpeaking, vm::goCheck, inline = true, onKeyAnswer = vm::answerKeyQuestion) }
    }
}

private val TYPE_CHOICES: List<QuickReply> = IncidentType.entries.map { QuickReply(Labels.type(it.wire), Field.INCIDENT_TYPE, it.wire) }
private val SEVERITY_CHOICES: List<QuickReply> = QuestionPlanner.SEVERITY_CHOICES.map { (label, s) -> QuickReply(label, Field.SEVERITY, s.wire) }

private fun choicesFor(f: Field): List<QuickReply> = when (f) {
    Field.INCIDENT_TYPE -> TYPE_CHOICES
    Field.SEVERITY -> SEVERITY_CHOICES
    else -> emptyList()
}

private fun androidx.compose.foundation.lazy.LazyListScope.fields(ui: AppUi, vm: AppViewModel) {
    val all = ui.session.fields
    val needed = all.count { it.mandatory && it.status == UiStatus.NEEDED }
    val toConfirm = all.count { it.mandatory && it.status == UiStatus.SUGGESTED }
    item(key = "h-needed") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("To send", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            when {
                needed > 0 -> Pill("$needed needed", Theme.colors.attentionContainer, Theme.colors.onAttention)
                toConfirm > 0 -> Pill("$toConfirm to confirm", Theme.colors.attentionContainer, Theme.colors.onAttention)
                else -> Pill("Ready to check", Theme.colors.accentContainer, Theme.colors.onAccentContainer)
            }
        }
    }
    item(key = "mandatory") { FieldGroup(all.filter { it.mandatory }, ui, vm) }
    item(key = "h-details") { Text("Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
    item(key = "details") { FieldGroup(all.filter { !it.mandatory }, ui, vm) }
    item(key = "spacer") { Spacer(Modifier.height(Space.s6)) }
}

@Composable
private fun FieldGroup(group: List<FieldView>, ui: AppUi, vm: AppViewModel) {
    Card {
        group.forEachIndexed { i, v ->
            if (i > 0) Hairline()
            FieldRow(
                view = v,
                selected = ui.selected == v.field,
                editing = ui.editing == v.field,
                justFilled = v.field in ui.justFilled,
                choices = choicesFor(v.field),
                onSelect = { vm.select(v.field) },
                onEdit = { vm.startEdit(v.field) },
                onSave = { vm.saveEdit(v.field, it) },
                onChoose = vm::reply,
                onCancel = vm::cancelEdit,
                onAccept = { vm.acceptSuggestion(v.field) },
                chips = ui.session.suggestions.filter { it.field == v.field },
                onAcceptChip = vm::acceptChip,
                onDismissChip = vm::dismissChip,
            )
        }
    }
}

@Composable
private fun ReportBottomBar(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val state = ui.session.state
    val asking = state as? SessionState.Asking
    val docked = asking != null && !ui.listening && !ui.processing && !isWide()
    Column(Modifier.fillMaxWidth().background(if (docked) c.accentContainer else c.canvas).navigationBarsPadding()) {
        if (!docked) Hairline()
        ui.undoLabel?.let { UndoBar(it, vm::undo) }
        if (ui.session.canSubmitNow && !ui.listening && !ui.processing) {
            // Urgent lane: sending is never made to wait for the other fields; what is missing is listed in the report.
            PrimaryButton("Submit now", vm::send, Modifier.fillMaxWidth().padding(horizontal = Size.gutter, vertical = Space.s2))
        }
        if (asking != null && !ui.listening && !ui.processing && !isWide()) {
            // The question sits in the thumb zone and the report stays visible above it.
            QuestionDock(asking.question, ui.speaking, vm::reply, vm::onMicTapped, vm::stopSpeaking, vm::goCheck, onKeyAnswer = vm::answerKeyQuestion)
        } else Column(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            when {
                ui.listening -> ListeningBar(ui, vm)
                ui.processing -> Text("Reading it…", style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.padding(vertical = Space.s3))
                else -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.CenterVertically) {
                    VoiceButton(vm::onMicTapped)
                    PrimaryButton("Check and send", vm::goCheck, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ListeningBar(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Pill("Recording on this device", c.dangerContainer, c.onDanger)
            Text(formatClock(ui.elapsedMs), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
            LevelBars(ui.levels, barHeight = 24.dp, count = 18)
        }
        MicButton(listening = true, busy = false, level = ui.levels.lastOrNull() ?: 0f, onClick = vm::onMicTapped, diameter = 64.dp)
    }
}

@Composable
private fun UndoBar(label: String, onUndo: () -> Unit) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Size.gutter, vertical = Space.s2).background(c.ink, RoundedCornerShape(14.dp)).padding(start = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.canvas, modifier = Modifier.weight(1f))
        TextAction("Undo", onUndo, color = c.canvas)
    }
}

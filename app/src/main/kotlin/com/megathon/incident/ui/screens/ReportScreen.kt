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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import com.megathon.incident.ui.components.StageTrack
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
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
        topBar = { AppTopBar("Shape your report", onProof = vm::openProof, simulatedNote = ui.simulatedNote) },
        bottomBar = { ReportBottomBar(ui, vm) },
    ) { pad ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                LazyColumn(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(Space.s4), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4)) {
                    alerts(ui, vm)
                    conversation(ui, vm, inlineQuestion = true)
                }
                LazyColumn(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(Space.s4), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4)) {
                    if (!ui.listening && !ui.processing) fields(ui, vm)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4),
            ) {
                alerts(ui, vm)
                if (ui.listening || ui.processing) {
                    conversation(ui, vm)
                } else {
                    fields(ui, vm)
                    conversation(ui, vm)
                }
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

private fun androidx.compose.foundation.lazy.LazyListScope.alerts(ui: AppUi, vm: AppViewModel) {
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
 }

private fun androidx.compose.foundation.lazy.LazyListScope.conversation(ui: AppUi, vm: AppViewModel, inlineQuestion: Boolean = false) {
    val q = ui.session.state as? SessionState.Asking
    if (inlineQuestion && q != null && !ui.processing && !ui.listening) {
        item(key = "question") { QuestionDock(q.question, ui.speaking, vm::reply, vm::onMicTapped, vm::stopSpeaking, vm::goCheck, inline = true, onKeyAnswer = vm::answerKeyQuestion) }
    }
    item(key = "transcript") {
        var expanded by remember { mutableStateOf(false) }
        val reduce = LocalReduceMotion.current
        Column {
        if (!ui.listening && !ui.processing) TextAction(if (expanded) "Hide original words" else "Read your original words", { expanded = !expanded })
        AnimatedVisibility(expanded || ui.listening || ui.processing, enter = fadeIn(tween(if (reduce) 0 else Motion.standard)) + expandVertically(tween(if (reduce) 0 else Motion.standard)), exit = fadeOut(tween(if (reduce) 0 else Motion.quick)) + shrinkVertically(tween(if (reduce) 0 else Motion.standard))) {
        val highlight = ui.session.fields.firstOrNull { it.field == ui.selected }?.evidence
        TranscriptCard(
            ui.session.transcripts, highlight, ui.heardNow ?: ui.liveText, reading = ui.processing,
            readingLabel = if (ui.heardNow == null) "Transcribing your recording…" else "Organizing the report…",
        )
        }
        }
    }
    if (ui.listening) item(key = "provisional") { ProvisionalCard(ui.session.provisional) }

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
    val mandatory = all.filter { it.mandatory }
    val needed = mandatory.count { it.status == UiStatus.NEEDED }
    val toConfirm = mandatory.count { it.status == UiStatus.SUGGESTED }
    val resolved = mandatory.count { it.status == UiStatus.HEARD || it.status == UiStatus.CONFIRMED }
    val progress = if (mandatory.isEmpty()) 1f else resolved.toFloat() / mandatory.size
    item(key = "h-needed") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            StageTrack(1)
            if (!isWide()) Spacer(Modifier.height(Space.s3))
            if (!isWide()) Text("Your story, organized.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    if (!isWide()) Text("KEY DETAILS", style = MaterialTheme.typography.labelSmall, color = Theme.colors.accent)
                    Text("$resolved of ${mandatory.size} essentials ready", style = MaterialTheme.typography.bodyMedium, color = Theme.colors.ink2)
                }
                when {
                    needed > 0 -> Text("$needed needed", style = MaterialTheme.typography.labelSmall, color = Theme.colors.onAttention)
                    toConfirm > 0 -> Text("$toConfirm to confirm", style = MaterialTheme.typography.labelSmall, color = Theme.colors.onAttention)
                    else -> Text("Ready to review", style = MaterialTheme.typography.labelSmall, color = Theme.colors.accent)
                }
            }
            val reduce = LocalReduceMotion.current
            val animatedProgress = animateFloatAsState(progress, tween(if (reduce) 0 else Motion.slow), label = "essentials ready")
            LinearProgressIndicator(
                progress = { animatedProgress.value }, modifier = Modifier.fillMaxWidth().height(5.dp),
                color = Theme.colors.accent, trackColor = Theme.colors.line,
            )
        }
    }
    item(key = "mandatory") { FieldGroup(all.filter { it.mandatory }, ui, vm) }
    item(key = "details") {
        var expanded by remember { mutableStateOf(false) }
        val reduce = LocalReduceMotion.current
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            TextAction(if (expanded) "Hide additional details" else "Additional details  +", { expanded = !expanded }, color = Theme.colors.ink2)
            AnimatedVisibility(expanded, enter = fadeIn(tween(if (reduce) 0 else Motion.standard)) + expandVertically(tween(if (reduce) 0 else Motion.standard)), exit = fadeOut(tween(if (reduce) 0 else Motion.quick)) + shrinkVertically(tween(if (reduce) 0 else Motion.standard))) {
                FieldGroup(all.filter { !it.mandatory }, ui, vm)
            }
        }
    }
    item(key = "spacer") { Spacer(Modifier.height(Space.s6)) }
}

@Composable
private fun FieldGroup(group: List<FieldView>, ui: AppUi, vm: AppViewModel) {
    var openField by remember { mutableStateOf<Field?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        group.forEachIndexed { index, v ->
            if (index > 0) Hairline()
            FieldRow(
                view = v,
                selected = openField == v.field,
                editing = ui.editing == v.field,
                justFilled = v.field in ui.justFilled,
                choices = choicesFor(v.field),
                onSelect = { openField = if (openField == v.field) null else v.field; vm.select(v.field) },
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
    val reduce = LocalReduceMotion.current
    val wide = isWide()
    val mode = when {
        ui.listening -> 0
        ui.processing -> 1
        ui.session.state is SessionState.Asking && !wide -> 2
        else -> 3
    }
    Column(Modifier.fillMaxWidth().background(c.canvas).navigationBarsPadding()) {
        ui.undoLabel?.let { UndoBar(it, vm::undo) }
        if (ui.session.canSubmitNow && !ui.listening && !ui.processing) {
            PrimaryButton("Save now", vm::send, Modifier.fillMaxWidth().padding(horizontal = Size.gutter, vertical = Space.s2))
        }
        AnimatedContent(mode, transitionSpec = {
            if (reduce || targetState == 0) ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
            else (fadeIn(tween(Motion.standard, delayMillis = 80)) + slideInVertically(tween(Motion.standard)) { it / 8 }) togetherWith fadeOut(tween(Motion.quick))
        }, label = "report action") { activeMode ->
            var previousUi by remember(activeMode) { mutableStateOf(ui) }
            if (activeMode == mode) SideEffect { previousUi = ui }
            val dockUi = if (activeMode == mode) ui else previousUi
            if (activeMode == 2) {
                // The live state wins over an outgoing animation snapshot: never retain a prompt during capture.
                if (!ui.listening && !ui.processing) (dockUi.session.state as? SessionState.Asking)?.let { asking ->
                    QuestionDock(asking.question, dockUi.speaking, vm::reply, vm::onMicTapped, vm::stopSpeaking, vm::goCheck, onKeyAnswer = vm::answerKeyQuestion)
                }
            } else Column(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                when (activeMode) {
                    0 -> ListeningBar(dockUi, vm)
                    1 -> Text("Shaping the details…", style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.padding(vertical = Space.s3))
                    else -> if (!ui.listening && !ui.processing) Row(Modifier.fillMaxWidth(if (wide) .58f else 1f).align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.CenterVertically) {
                        VoiceButton(vm::onMicTapped)
                        PrimaryButton("Review report", vm::goCheck, Modifier.weight(1f))
                    }
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
        Modifier.fillMaxWidth().padding(horizontal = Size.gutter, vertical = Space.s2).background(c.accentContainer, RoundedCornerShape(4.dp)).padding(start = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.onAccentContainer, modifier = Modifier.weight(1f))
        TextAction("Undo", onUndo, color = c.accent)
    }
}

package com.megathon.incident.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
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
import com.megathon.incident.ui.components.Panel
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
import com.megathon.incident.ui.components.QuestionButton
import com.megathon.incident.ui.components.QuestionSheet
import com.megathon.incident.ui.components.SecondaryButton
import com.megathon.incident.ui.components.WorkingBar
import com.megathon.incident.ui.components.TextAction
import com.megathon.incident.ui.components.TranscriptCard
import com.megathon.incident.ui.components.VoiceButton
import com.megathon.incident.ui.components.coreText
import com.megathon.incident.ui.components.formatClock
import com.megathon.incident.ui.components.num
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

private const val WIDE_DP = 700

/** The conversation and the live form. Wide screens (a projector, a tablet) show the conversation and the report side by side. */
@Composable
fun ReportScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val wide = isWide()
    // The open question lives on its own sheet, not as one more row of the form. A new question opens the sheet once;
    // "Not now" closes it and the "!" button in the bottom bar brings it back.
    val asking = (ui.session.state as? SessionState.Asking)?.question
    val busy = ui.listening || ui.processing
    val questionKey = asking?.let { "${it.key}|${it.spoken}|${it.asks.firstOrNull()?.prompt}" }
    var sheetOpen by remember { mutableStateOf(false) }
    var shownFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(questionKey, busy) {
        if (questionKey == null || busy) sheetOpen = false
        else if (shownFor != questionKey) { shownFor = questionKey; sheetOpen = true }
    }
    if (sheetOpen && asking != null && !busy) {
        QuestionSheet(
            question = asking, speaking = ui.speaking,
            onReply = vm::reply, onKeyAnswer = vm::answerKeyQuestion,
            onVoice = { sheetOpen = false; vm.onMicTapped() },
            onStopSpeaking = vm::stopSpeaking,
            onDismiss = { sheetOpen = false },
        )
    }
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar(tr("Shape your report", "अपनी रिपोर्ट तैयार करें"), onProof = vm::openProof, simulatedNote = ui.simulatedNote) },
        bottomBar = { ReportBottomBar(ui, vm, onQuestion = if (asking != null) ({ sheetOpen = true }) else null) },
    ) { pad ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                LazyColumn(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(Space.s4), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4)) {
                    alerts(ui, vm)
                    conversation(ui, vm)
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

private fun androidx.compose.foundation.lazy.LazyListScope.conversation(ui: AppUi, vm: AppViewModel) {
    item(key = "transcript") {
        var expanded by remember { mutableStateOf(false) }
        val reduce = LocalReduceMotion.current
        Column {
        if (!ui.listening && !ui.processing) TextAction(if (expanded) tr("Hide original words", "आपके मूल शब्द छिपाएँ") else tr("Read your original words", "आपके मूल शब्द पढ़ें"), { expanded = !expanded })
        AnimatedVisibility(expanded || ui.listening || ui.processing, enter = fadeIn(tween(if (reduce) 0 else Motion.standard)) + expandVertically(tween(if (reduce) 0 else Motion.standard)), exit = fadeOut(tween(if (reduce) 0 else Motion.quick)) + shrinkVertically(tween(if (reduce) 0 else Motion.standard))) {
        val highlight = ui.session.fields.firstOrNull { it.field == ui.selected }?.evidence
        TranscriptCard(
            ui.session.transcripts, highlight, ui.heardNow ?: ui.liveText, reading = ui.processing,
            readingLabel = if (ui.heardNow == null) tr("Transcribing your recording…", "आपकी रिकॉर्डिंग को लिखा जा रहा है…") else tr("Organizing the report…", "रिपोर्ट को व्यवस्थित किया जा रहा है…"),
            scriptNote = ui.scriptNote,
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
            // No step timeline: the title bar already names the screen, and the bar below is the one progress there is.
            if (!isWide()) Text(tr("Your story, organized.", "आपकी बात, व्यवस्थित।"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("$resolved of ${mandatory.size} essentials ready", "${num(mandatory.size)} में से ${num(resolved)} ज़रूरी बातें तैयार"), style = MaterialTheme.typography.bodyMedium, color = Theme.colors.ink2, modifier = Modifier.weight(1f))
                val c = Theme.colors
                when {
                    needed > 0 -> Pill(tr("$needed needed", "${num(needed)} बाकी"), c.dangerContainer, c.onDanger, icon = Icons.Rounded.Warning)
                    toConfirm > 0 -> Pill(tr("$toConfirm to confirm", "${num(toConfirm)} की पुष्टि करें"), c.attentionContainer, c.onAttention, icon = Icons.Rounded.Info)
                    else -> Pill(tr("Ready to review", "जाँच के लिए तैयार"), c.accentContainer, c.onAccentContainer, icon = Icons.Rounded.Check)
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
    // Two panels on their own surface. The extra details are always shown: they are part of the report, not a hidden extra.
    item(key = "mandatory") { Panel(tr("Essentials", "ज़रूरी बातें")) { FieldGroup(all.filter { it.mandatory }, ui, vm) } }
    item(key = "details") { Panel(tr("More details", "और जानकारी")) { FieldGroup(all.filter { !it.mandatory }, ui, vm) } }
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
                storedText = ui.session.report.value(v.field).text,
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
private fun ReportBottomBar(ui: AppUi, vm: AppViewModel, onQuestion: (() -> Unit)?) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val wide = isWide()
    val mode = when {
        ui.listening -> 0
        ui.processing -> 1
        else -> 3
    }
    Column(Modifier.fillMaxWidth().background(c.canvas).navigationBarsPadding()) {
        ui.undoLabel?.let { UndoBar(it, vm::undo) }
        if (ui.session.canSubmitNow && !ui.listening && !ui.processing) {
            PrimaryButton(tr("Save now", "अभी सेव करें"), vm::send, Modifier.fillMaxWidth().padding(horizontal = Size.gutter, vertical = Space.s2))
        }
        AnimatedContent(mode, transitionSpec = {
            if (reduce || targetState == 0) ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
            else (fadeIn(tween(Motion.standard, delayMillis = 80)) + slideInVertically(tween(Motion.standard)) { it / 8 }) togetherWith fadeOut(tween(Motion.quick))
        }, label = "report action") { activeMode ->
            var previousUi by remember(activeMode) { mutableStateOf(ui) }
            if (activeMode == mode) SideEffect { previousUi = ui }
            val dockUi = if (activeMode == mode) ui else previousUi
            Column(Modifier.padding(horizontal = Size.gutter, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                when (activeMode) {
                    0 -> ListeningBar(dockUi, vm)
                    1 -> Row(Modifier.padding(vertical = Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        WorkingBar(width = 40.dp)
                        Text(tr("Shaping the details…", "जानकारी तैयार हो रही है…"), style = MaterialTheme.typography.bodyLarge, color = c.ink2)
                    }
                    else -> if (!ui.listening && !ui.processing) Row(Modifier.fillMaxWidth(if (wide) .58f else 1f).align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.CenterVertically) {
                        if (onQuestion != null) QuestionButton(onQuestion)
                        VoiceButton(vm::onMicTapped)
                        // One filled button per screen: "Save now" leads whenever it is offered, and reviewing steps back.
                        if (ui.session.canSubmitNow) SecondaryButton(tr("Review report", "रिपोर्ट जाँचें"), vm::goCheck, Modifier.weight(1f))
                        else PrimaryButton(tr("Review report", "रिपोर्ट जाँचें"), vm::goCheck, Modifier.weight(1f))
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
            Pill(tr("Recording on this device", "इस डिवाइस पर रिकॉर्डिंग चालू है"), c.dangerContainer, c.onDanger)
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
        Text(coreText(label), style = MaterialTheme.typography.bodyMedium, color = c.onAccentContainer, modifier = Modifier.weight(1f))
        TextAction(tr("Undo", "वापस करें"), onUndo, color = c.accent)
    }
}

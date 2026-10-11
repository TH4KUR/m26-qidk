package com.megathon.incident.ui.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.megathon.incident.speech.HindiLaneNotice
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import com.megathon.incident.ui.components.*
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megathon.incident.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.Readiness
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.AudioSignature
import com.megathon.incident.ui.components.MessageCard
import com.megathon.incident.ui.components.MicButton
import com.megathon.incident.ui.components.PrivacyLine
import com.megathon.incident.ui.components.ProvisionalCard
import com.megathon.incident.ui.components.TranscriptCard
import com.megathon.incident.ui.components.formatClock
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.CapturePalette
import com.megathon.incident.ui.theme.Theme

/** One clear invitation, one microphone, and a live preview that never hides that the draft is provisional. */
@Composable
fun HomeScreen(ui: AppUi, vm: AppViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val c = Theme.colors
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar(stringResource(R.string.app_tagline), onProof = vm::openProof, brand = true, simulatedNote = ui.simulatedNote) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (landscape) {
                Row(
                    Modifier.weight(1f).padding(horizontal = Size.gutter, vertical = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.s4, Alignment.CenterVertically),
                    ) {
                        Notices(ui, vm)
                        StageTrack(0)
                        HomeCopy(ui)
                        if (!ui.listening && !ui.processing) ExampleNote()
                        LiveDetails(ui)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        StudioPanel(ui, vm, compact = true)
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Spacer(Modifier.height(Space.s4))
                    Notices(ui, vm)
                    StageTrack(0)
                    Spacer(Modifier.height(Space.s8))
                    HomeCopy(ui)
                    Spacer(Modifier.height(Space.s6))
                    StudioPanel(ui, vm)
                    Spacer(Modifier.height(Space.s4))
                    if (ui.listening || ui.processing) LiveDetails(ui) else ExampleNote()
                    Spacer(Modifier.height(Space.s6))
                }
            }
            Footer(ui, vm)
        }
    }
}

@Composable
private fun Notices(ui: AppUi, vm: AppViewModel) {
    ui.blocked?.let { Blocked(it); Spacer(Modifier.height(Space.s3)) }
    ui.message?.let { MessageCard(it, vm::dismissMessage); Spacer(Modifier.height(Space.s3)) }
}

@Composable
private fun HomeCopy(ui: AppUi) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val mode = if (ui.listening) 1 else if (ui.processing) 2 else 0
    AnimatedContent(mode, transitionSpec = {
        (fadeIn(tween(if (reduce) 0 else Motion.standard)) + slideInVertically(tween(if (reduce) 0 else Motion.standard)) { it / 8 }) togetherWith fadeOut(tween(if (reduce) 0 else Motion.quick))
    }, label = "capture copy") { state ->
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) Space.s2 else Space.s3)) {
            if (!compact) Text("LESS FRICTION. MORE CLARITY.", style = MaterialTheme.typography.labelSmall, color = c.accent)
            Text(buildAnnotatedString {
                append(when (state) { 1 -> "Your voice.\n"; 2 -> "A little clarity.\n"; else -> "A moment.\n" })
                withStyle(SpanStyle(color = c.accent)) { append(when (state) { 1 -> "Taking shape."; 2 -> "Coming together."; else -> "A clear report." }) }
            }, style = if (compact) MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 36.sp) else MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
            Text(when (state) {
                1 -> "Tell it as it happened. Tap to finish when you’re ready."
                2 -> "Organizing your words, right here on this device."
                else -> "Tell it as it happened.\nWe’ll take care of the structure."
            }, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = c.ink2)
        }
    }
}

@Composable
private fun StudioPanel(ui: AppUi, vm: AppViewModel, compact: Boolean = false) {
    val c = Theme.colors
    val status = when {
        ui.listening -> "LISTENING  ·  ${formatClock(ui.elapsedMs)}"
        ui.processing -> "SHAPING YOUR REPORT"
        ui.readiness == Readiness.LOADING -> "MODEL WARMING UP"
        else -> "READY WHEN YOU ARE"
    }
    Reveal(delayMillis = 100) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RecorderAura(ui.listening, ui.levels.lastOrNull() ?: 0f, Modifier.then(if (compact) Modifier.size(140.dp) else Modifier)) {
                MicButton(ui.listening, ui.processing, ui.levels.lastOrNull() ?: 0f, vm::onMicTapped, diameter = if (compact) 68.dp else 112.dp)
            }
            Text(status, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = if (ui.listening) c.danger else c.accent)
            Spacer(Modifier.height(if (compact) Space.s1 else Space.s2))
            Text(when {
                ui.listening -> "Tap to finish your story"
                ui.processing -> "A few details. One clear report."
                ui.readiness == Readiness.LOADING -> "You can start while it warms up"
                else -> "Tap to tell your story"
            }, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, color = c.ink)
            if (ui.listening) AudioSignature(ui.levels, true, Modifier.fillMaxWidth(.65f).height(32.dp).padding(top = Space.s2))
        }
    }
}

@Composable
private fun LiveDetails(ui: AppUi) {
    if (ui.listening) ui.liveText?.let { text ->
        Spacer(Modifier.height(Space.s3))
        TranscriptCard(transcripts = emptyList(), highlight = null, heardNow = text, reading = false)
    }
    if (ui.listening) {
        Spacer(Modifier.height(Space.s2))
        ProvisionalCard(ui.session.provisional)
    }
    if (ui.processing) {
        Spacer(Modifier.height(Space.s3))
        TranscriptCard(
            transcripts = emptyList(), highlight = null, heardNow = ui.heardNow ?: ui.liveText, reading = true,
            readingLabel = if (ui.heardNow == null) "Transcribing your recording…" else "Organizing the report…",
            scriptNote = ui.scriptNote,
        )
    }
}

@Composable
private fun ExampleNote() {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    var open by remember { mutableStateOf(false) }
    Card {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { open = !open }.padding(Space.s4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                Text("Not sure where to start?", style = MaterialTheme.typography.labelLarge, color = c.ink)
                Text(if (open) "No special format. Just say what happened." else "See a quick example", style = MaterialTheme.typography.bodyMedium, color = c.ink3)
            }
            Icon(Icons.Rounded.KeyboardArrowDown, if (open) "Hide example" else "Show example", tint = c.accent)
        }
        AnimatedVisibility(open, enter = fadeIn(tween(if (reduce) 0 else Motion.standard)) + expandVertically(tween(if (reduce) 0 else Motion.standard)), exit = fadeOut(tween(if (reduce) 0 else Motion.quick)) + shrinkVertically(tween(if (reduce) 0 else Motion.standard))) {
            Text("“${FakeLlmEngine.DECK_TRANSCRIPT}”", Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s4), style = MaterialTheme.typography.bodyLarge, color = c.ink2)
        }
    }
}

@Composable
private fun Footer(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Size.gutter, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        if (!ui.listening && !ui.processing) HindiLaneSwitch(ui.hindiLane, vm::setHindiLane)
        PrivacyLine(ui.network)
    }
}

/**
 * The experimental Hindi lane, off by default. Small and plainly labelled: it changes only which recogniser hears the
 * next utterance. The whole row is the switch, so a screen reader announces it once with its state.
 */
@Composable
private fun HindiLaneSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(vertical = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Text(HindiLaneNotice.SWITCH_LABEL, style = MaterialTheme.typography.labelLarge, color = c.ink2, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun Blocked(message: String) {
    val c = Theme.colors
    Column(
        Modifier.fillMaxWidth().background(c.dangerContainer, RoundedCornerShape(16.dp)).padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            androidx.compose.material3.Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.onDanger)
            Text("This build can’t start", style = MaterialTheme.typography.titleMedium, color = c.onDanger, fontWeight = FontWeight.SemiBold)
        }
        Text(message, style = MaterialTheme.typography.bodyLarge, color = c.onDanger)
    }
}

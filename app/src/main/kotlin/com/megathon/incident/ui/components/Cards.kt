package com.megathon.incident.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.priority.Deescalation
import com.megathon.incident.priority.KeyAnswer
import com.megathon.incident.session.Question
import com.megathon.incident.session.QuickReply
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

private val CardShape = RoundedCornerShape(20.dp)

/** A plain content card: tonal surface plus a hairline, no shadow (one elevation language). */
@Composable
fun Card(modifier: Modifier = Modifier, container: androidx.compose.ui.graphics.Color = Theme.colors.surface, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(container, CardShape).border(BorderStroke(1.dp, Theme.colors.line), CardShape),
    ) { content() }
}

/**
 * What was heard, with the words that support the selected field highlighted: the judges' "evidence highlight",
 * and the worker's answer to "why did it write that?". While the model is still reading, says so.
 */
@Composable
fun TranscriptCard(transcripts: List<String>, highlight: String?, heardNow: String?, reading: Boolean, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Card(modifier) {
        Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text("What I heard", style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.semantics { heading() })
            val all = buildList { addAll(transcripts); if (heardNow != null && heardNow !in transcripts.takeLast(1)) add(heardNow) }
            all.forEachIndexed { i, t ->
                val latest = i == all.lastIndex
                Text(
                    highlighted(t, if (latest || highlight != null) highlight else null, c.accentContainer, c.onAccentContainer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (latest) c.ink else c.ink2,
                )
            }
            if (all.isEmpty() && !reading) Text("Nothing yet.", style = MaterialTheme.typography.bodyLarge, color = c.ink3)
            if (reading) {
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    LinearProgressIndicator(modifier = Modifier.size(width = 40.dp, height = 3.dp), color = c.accent, trackColor = c.line)
                    Text(if (heardNow == null) "Listening back to what you said…" else "Reading it…", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
            }
        }
    }
}

private fun highlighted(text: String, phrase: String?, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color): AnnotatedString {
    if (phrase.isNullOrBlank()) return AnnotatedString(text)
    val at = text.indexOf(phrase, ignoreCase = true)
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, at))
        withStyle(SpanStyle(background = bg, color = fg, fontWeight = FontWeight.Medium)) { append(text.substring(at, at + phrase.length)) }
        append(text.substring(at + phrase.length))
    }
}

/**
 * The one open question, docked at the bottom where a thumb reaches (one-handed, hands busy). Spoken aloud and written
 * here, with ready answers to tap and a mic to answer by voice. The report stays visible above it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionDock(
    question: Question,
    speaking: Boolean,
    onReply: (QuickReply) -> Unit,
    onVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
    onKeyAnswer: (KeyAnswer) -> Unit = {},
) {
    val c = Theme.colors
    val shape = if (inline) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(
        modifier.fillMaxWidth().background(c.accentContainer, shape).heightIn(max = maxHeight).verticalScroll(rememberScrollState())
            .padding(start = Space.s4, end = Space.s4, top = Space.s3, bottom = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("One quick question", style = MaterialTheme.typography.labelMedium, color = c.onAccentContainer, modifier = Modifier.weight(1f).semantics { heading() })
            if (speaking) TextAction("Stop speaking", onStopSpeaking, color = c.onAccentContainer)
        }
        if (question.key) {
            // The advisor's one dispatch-style question: no field behind it, answered Yes / No / Not sure.
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(question.spoken, style = MaterialTheme.typography.headlineSmall, color = c.onAccentContainer)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    OutlinedChoiceChip("Yes", onClick = { onKeyAnswer(KeyAnswer.YES) })
                    OutlinedChoiceChip("No", onClick = { onKeyAnswer(KeyAnswer.NO) })
                    OutlinedChoiceChip("Not sure", onClick = { onKeyAnswer(KeyAnswer.UNSURE) })
                }
            }
        }
        question.asks.forEach { ask ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(ask.prompt, style = MaterialTheme.typography.headlineSmall, color = c.onAccentContainer)
                if (ask.replies.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        ask.replies.forEach { r ->
                            OutlinedChoiceChip(r.label, onClick = { onReply(r) }, note = if (r.suggested) "suggested" else null, emphasis = r.suggested)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Answer by voice", onVoice, leading = { MicGlyph(c.onAccent, Modifier.padding(end = Space.s2)) })
            TextAction("Skip", onSkip, color = c.onAccentContainer)
        }
    }
}

/** Only shown when priority rises above routine. Urgent leads with the emergency line. Can be lowered by the reporter. */
@Composable
fun PriorityBanner(
    advice: PriorityAdvice,
    onOverride: (Priority) -> Unit,
    modifier: Modifier = Modifier,
    deescalation: Deescalation? = null,
    onLower: () -> Unit = {},
) {
    val c = Theme.colors
    if (advice.level < Priority.MEDIUM && !advice.overriddenByUser) return
    val urgent = advice.level >= Priority.HIGH
    val container = if (urgent) c.dangerContainer else c.attentionContainer
    val onC = if (urgent) c.onDanger else c.onAttention
    var open by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val title = when (advice.level) {
        Priority.URGENT -> "Urgent"
        Priority.HIGH -> "High priority"
        Priority.MEDIUM -> "Medium priority"
        Priority.LOW -> "Routine"
    } + if (advice.overriddenByUser) " (set by you)" else ""
    Card(modifier, container = container) {
        Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = onC, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = onC, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (advice.level == Priority.URGENT) {
                Text("If anyone is in danger, call 112.", style = MaterialTheme.typography.headlineSmall, color = onC)
            }
            if (deescalation != null) {
                // Offered, never applied by itself: one tap lowers the level, anything else leaves it as it is.
                Text(deescalation.reason.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = onC)
                OutlinedChoiceChip("Lower to LOW?", onClick = onLower)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(if (open) "Hide why" else "Why?", { open = !open }, color = onC)
                androidx.compose.foundation.layout.Box {
                    TextAction("Change", { menu = true }, color = onC)
                    DropdownMenu(menu, { menu = false }) {
                        listOf(Priority.LOW to "Routine", Priority.MEDIUM to "Medium", Priority.HIGH to "High", Priority.URGENT to "Urgent").forEach { (p, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onOverride(p) })
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(Motion.standard, easing = Motion.enter)) + expandVertically(tween(Motion.standard, easing = Motion.enter)),
                exit = shrinkVertically(tween(Motion.quick, easing = Motion.exit)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    advice.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = onC) }
                    if (advice.reasons.isEmpty()) Text("No specific signals.", style = MaterialTheme.typography.bodyMedium, color = onC)
                }
            }
        }
    }
}

/** A short, plain message with a way out. Announced politely to screen readers. */
@Composable
fun MessageCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val c = Theme.colors
    val container = if (danger) c.dangerContainer else c.attentionContainer
    val onC = if (danger) c.onDanger else c.onAttention
    Row(
        modifier.fillMaxWidth().background(container, RoundedCornerShape(16.dp)).padding(start = Space.s4, top = Space.s2, bottom = Space.s2, end = Space.s1)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = onC, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = onC, modifier = Modifier.weight(1f))
        IconButton(onDismiss, Modifier.size(Size.touch)) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss message", tint = onC) }
    }
}

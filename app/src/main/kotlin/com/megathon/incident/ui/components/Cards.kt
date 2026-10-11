package com.megathon.incident.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
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
import com.megathon.incident.session.HindiLabels
import com.megathon.incident.session.QuickReply
import com.megathon.incident.session.UiStatus
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Severity
import com.megathon.incident.session.Labels
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

private val CardShape = RoundedCornerShape(4.dp)

/** Editorial surface: standard content is unboxed; warnings retain a restrained tonal background. */
@Composable
fun Card(modifier: Modifier = Modifier, container: androidx.compose.ui.graphics.Color = Theme.colors.surface, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().then(if (container != Theme.colors.surface) Modifier.background(container, CardShape) else Modifier),
    ) { content() }
}

/**
 * What was heard, with the words that support the selected field highlighted: the judges' "evidence highlight",
 * and the worker's answer to "why did it write that?". While the model is still reading, says so.
 */
@Composable
fun TranscriptCard(
    transcripts: List<String>,
    highlight: String?,
    heardNow: String?,
    reading: Boolean,
    modifier: Modifier = Modifier,
    readingLabel: String? = null,
    /** One short line shown when the transcript is in a different script from the labels and replies; null shows nothing. */
    scriptNote: String? = null,
) {
    val c = Theme.colors
    val readingText = readingLabel?.let { coreText(it) } ?: tr("Working on your report…", "आपकी रिपोर्ट पर काम हो रहा है…")
    val note = scriptNote?.let { coreText(it) }
    Card(modifier) {
        Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(tr("What I heard", "मैंने जो सुना"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.semantics { heading() })
            val all = buildList { addAll(transcripts); if (heardNow != null && heardNow !in transcripts.takeLast(1)) add(heardNow) }
            all.forEachIndexed { i, t ->
                val latest = i == all.lastIndex
                Text(
                    highlighted(t, if (latest || highlight != null) highlight else null, c.accentContainer, c.onAccentContainer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (latest) c.ink else c.ink2,
                )
            }
            if (all.isEmpty() && !reading) Text(tr("Nothing yet.", "अभी कुछ नहीं।"), style = MaterialTheme.typography.bodyLarge, color = c.ink3)
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            if (reading) {
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    WorkingBar(width = 40.dp)
                    Text(readingText, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
            }
        }
    }
}

/**
 * What the words so far would fill in, while the reporter is still speaking. Labelled Provisional and kept apart from the
 * report: nothing here is saved, asked about or spoken until the reporter taps done.
 */
@Composable
fun ProvisionalCard(preview: com.megathon.incident.session.ProvisionalPreview?, modifier: Modifier = Modifier) {
    val shown = preview?.fields.orEmpty().filter { it.value != null }
    if (shown.isEmpty()) return
    val c = Theme.colors
    Card(modifier) {
        Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(tr("Filling in as you speak", "बोलते-बोलते भरा जा रहा है"), style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.semantics { heading() })
                Pill(tr("Provisional", "अस्थायी"), c.accentContainer, c.onAccentContainer)
            }
            shown.forEach { f ->
                Column {
                    Text(if (isHindi()) HindiLabels.field(f.field) else f.label, style = MaterialTheme.typography.labelMedium, color = c.ink2)
                    Text(provisionalValue(f), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                }
            }
            Text(tr("Not saved yet. I'll check everything when you tap done.", "अभी सेव नहीं हुआ है। रिकॉर्डिंग रोकते ही सब जाँचा जाएगा।"), style = MaterialTheme.typography.bodyMedium, color = c.ink3)
        }
    }
}

/**
 * A provisional value in the app language. The card only has the English display of the value (not the stored text), so a
 * type or a severity is found again by name; "Don't know" and "None" are the app's own words. Anything else, a date included,
 * stays as it is shown: a whole element in English rather than a guess.
 */
@Composable
private fun provisionalValue(f: com.megathon.incident.session.ProvisionalField): String {
    val shown = f.value.orEmpty()
    if (!isHindi()) return shown
    return when {
        f.status == UiStatus.UNKNOWN -> "पता नहीं"
        f.status == UiStatus.NONE -> HindiLabels.status(UiStatus.NONE) ?: shown
        f.field == Field.INCIDENT_TYPE -> IncidentType.entries.firstOrNull { Labels.type(it.wire) == shown }?.let { HindiLabels.type(it.wire) } ?: shown
        f.field == Field.SEVERITY -> Severity.entries.firstOrNull { Labels.severity(it.wire) == shown }?.let { HindiLabels.severity(it.wire) } ?: shown
        else -> shown
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
    val shape = RoundedCornerShape(28.dp)
    val reduce = LocalReduceMotion.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(
        modifier.fillMaxWidth().background(c.canvas).heightIn(max = maxHeight).verticalScroll(rememberScrollState())
            .padding(start = if (inline) 0.dp else Size.gutter, end = if (inline) 0.dp else Size.gutter, top = Space.s3, bottom = Space.s3),
        verticalArrangement = Arrangement.spacedBy(if (inline) Space.s2 else Space.s3),
    ) {
        Hairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("LET’S FILL ONE DETAIL", "एक जानकारी भरें"), style = MaterialTheme.typography.labelSmall, color = c.accent, modifier = Modifier.weight(1f).semantics { heading() })
            if (speaking) TextAction(tr("Stop speaking", "बोलना रोकें"), onStopSpeaking, color = c.onAccentContainer)
        }
        if (question.key) {
            // The advisor's one dispatch-style question: no field behind it, answered Yes / No / Not sure.
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(coreText(question.spoken), style = if (inline) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, color = c.onAccentContainer)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    OutlinedChoiceChip(tr("Yes", "हाँ"), onClick = { onKeyAnswer(KeyAnswer.YES) })
                    OutlinedChoiceChip(tr("No", "नहीं"), onClick = { onKeyAnswer(KeyAnswer.NO) })
                    OutlinedChoiceChip(statusLabel(UiStatus.UNKNOWN).orEmpty(), onClick = { onKeyAnswer(KeyAnswer.UNSURE) })
                }
            }
        }
        AnimatedContent(question.asks.firstOrNull(), transitionSpec = {
            (fadeIn(tween(if (reduce) 0 else Motion.standard)) + slideInVertically(tween(if (reduce) 0 else Motion.standard)) { it / 8 }) togetherWith fadeOut(tween(if (reduce) 0 else Motion.quick))
        }, label = "next question") { ask ->
            if (ask != null)
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(coreText(ask.prompt), style = if (inline) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, color = c.onAccentContainer)
                if (ask.replies.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        ask.replies.forEach { r ->
                            OutlinedChoiceChip(replyLabel(r), onClick = { onReply(r) }, note = if (r.suggested) statusLabel(UiStatus.SUGGESTED)?.let { if (isHindi()) it else it.lowercase() } else null, emphasis = r.suggested)
                        }
                    }
                }
            }
        }
        if (!inline) Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                MicGlyph(c.accent, size = 20.dp)
                TextAction(tr("Answer by voice", "बोलकर जवाब दें"), onVoice, flush = true)
            }
            TextAction(tr("Review", "जाँचें"), onSkip, color = c.ink2)
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
        Priority.URGENT -> tr("Urgent", "अत्यावश्यक")
        Priority.HIGH -> tr("High priority", "उच्च प्राथमिकता")
        Priority.MEDIUM -> tr("Medium priority", "मध्यम प्राथमिकता")
        Priority.LOW -> tr("Routine", "सामान्य")
    } + if (advice.overriddenByUser) tr(" (set by you)", " (आपने तय किया)") else ""
    Card(modifier, container = container) {
        Column(Modifier.padding(start = Space.s4, end = Space.s2, top = Space.s3, bottom = Space.s1), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = onC, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = onC, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (advice.level == Priority.URGENT) {
                Text(tr("If anyone is in danger, call 112.", "अगर कोई खतरे में है, तो अभी 112 पर कॉल करें।"), style = MaterialTheme.typography.headlineSmall, color = onC)
            }
            if (deescalation != null) {
                // Offered, never applied by itself: one tap lowers the level, anything else leaves it as it is.
                val reason = coreText(deescalation.reason)
                Text(if (reason == deescalation.reason) reason.replaceFirstChar { it.uppercase() } else reason, style = MaterialTheme.typography.bodyMedium, color = onC)
                OutlinedChoiceChip(tr("Lower to LOW?", "प्राथमिकता सामान्य करें?"), onClick = onLower)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(if (open) tr("Hide why", "कारण छिपाएँ") else tr("Why?", "क्यों?"), { open = !open }, color = onC)
                androidx.compose.foundation.layout.Box {
                    TextAction(tr("Change", "बदलें"), { menu = true }, color = onC)
                    DropdownMenu(menu, { menu = false }) {
                        listOf(
                            Priority.LOW to tr("Routine", "सामान्य"), Priority.MEDIUM to tr("Medium", "मध्यम"),
                            Priority.HIGH to tr("High", "उच्च"), Priority.URGENT to tr("Urgent", "अत्यावश्यक"),
                        ).forEach { (p, label) ->
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
                    advice.reasons.forEach { Text("• ${coreText(it)}", style = MaterialTheme.typography.bodyMedium, color = onC) }
                    if (advice.reasons.isEmpty()) Text(tr("No specific signals.", "कोई खास संकेत नहीं।"), style = MaterialTheme.typography.bodyMedium, color = onC)
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
    val dismissLabel = tr("Dismiss message", "संदेश हटाएँ")
    Row(
        modifier.fillMaxWidth().background(container, RoundedCornerShape(16.dp)).padding(start = Space.s4, top = Space.s2, bottom = Space.s2, end = Space.s1)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = onC, modifier = Modifier.size(20.dp))
        Text(coreText(text), style = MaterialTheme.typography.bodyLarge, color = onC, modifier = Modifier.weight(1f))
        IconButton(onDismiss, Modifier.size(Size.touch)) { Icon(Icons.Rounded.Close, contentDescription = dismissLabel, tint = onC) }
    }
}

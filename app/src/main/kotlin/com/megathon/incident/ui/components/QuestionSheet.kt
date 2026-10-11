package com.megathon.incident.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megathon.incident.priority.KeyAnswer
import com.megathon.incident.session.Question
import com.megathon.incident.session.QuickReply
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/**
 * The open question, on its own surface above the report instead of one more row inside it. It is spoken aloud and
 * written here with ready answers to tap and a mic to answer by voice. "Not now" closes it; the report's "!" button
 * brings it back, so nothing the reporter dismissed is lost.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuestionSheet(
    question: Question,
    speaking: Boolean,
    onReply: (QuickReply) -> Unit,
    onKeyAnswer: (KeyAnswer) -> Unit,
    onVoice: () -> Unit,
    onStopSpeaking: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Theme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = c.canvas.copy(alpha = .72f),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Size.gutter, end = Size.gutter, bottom = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                Box(Modifier.size(28.dp).background(c.attentionContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Text("!", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = c.onAttention)
                }
                Text(
                    tr("One quick question", "एक छोटा सवाल"),
                    style = MaterialTheme.typography.labelLarge, color = c.ink2,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (speaking) TextAction(tr("Stop speaking", "बोलना रोकें"), onStopSpeaking, color = c.ink2)
            }
            if (question.key) {
                // The advisor's one dispatch-style question: no field behind it, answered Yes / No / Not sure.
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(coreText(question.spoken), style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    FlowRow(maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        AnswerButton(tr("Yes", "हाँ"), { onKeyAnswer(KeyAnswer.YES) }, Modifier.weight(1f))
                        AnswerButton(tr("No", "नहीं"), { onKeyAnswer(KeyAnswer.NO) }, Modifier.weight(1f))
                        AnswerButton(statusLabel(UiStatus.UNKNOWN).orEmpty(), { onKeyAnswer(KeyAnswer.UNSURE) }, Modifier.weight(1f))
                    }
                }
            }
            question.asks.firstOrNull()?.let { ask ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(coreText(ask.prompt), style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    if (ask.replies.isNotEmpty()) {
                        // Two columns, in the order the question lists them; the odd one out takes the full row.
                        FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            ask.replies.forEach { r ->
                                AnswerButton(replyLabel(r), { onReply(r) }, Modifier.weight(1f), note = if (r.suggested) statusLabel(UiStatus.SUGGESTED) else null, emphasized = r.suggested)
                            }
                        }
                    }
                }
            }
            SecondaryButton(
                tr("Answer by voice", "बोलकर जवाब दें"), onVoice, Modifier.fillMaxWidth(),
                leading = { MicGlyph(c.ink, size = 20.dp); Spacer(Modifier.width(Space.s2)) },
            )
            TextAction(tr("Not now", "अभी नहीं"), onDismiss, Modifier.align(Alignment.CenterHorizontally), color = c.ink2)
        }
    }
}

/**
 * The "!" that shows while a question is waiting. The glyph and the spoken name carry the meaning, not the colour.
 */
@Composable
fun QuestionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val description = tr("A question is waiting for your answer", "एक सवाल आपके जवाब का इंतज़ार कर रहा है")
    val openLabel = tr("Open the question", "सवाल खोलें")
    Box(
        modifier
            .size(Size.primaryButton)
            .pressMotion(interaction)
            .clip(CircleShape)
            .background(c.attentionContainer)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = openLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = c.onAttention)
    }
}

/**
 * One answer to the question: a real button at least 56 dp tall with a visible border (it does not rely on colour), a pressed
 * state, and a label that wraps instead of being cut off at large text sizes or in Hindi. The suggested answer is the
 * emphasised one and says so in a word.
 */
@Composable
private fun AnswerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null, emphasized: Boolean = false) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val container = when {
        pressed -> c.accentContainer
        emphasized -> c.accentContainer
        else -> c.canvas
    }
    Box(
        modifier
            .heightIn(min = 56.dp)
            .pressMotion(interaction)
            .clip(shape)
            .background(container, shape)
            .border(BorderStroke(if (emphasized) 2.dp else 1.5.dp, if (emphasized) c.accent else c.ink3), shape)
            .semantics { contentDescription = if (note != null) "$label, $note" else label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.ink, textAlign = TextAlign.Center)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = c.onAccentContainer)
        }
    }
}

package com.megathon.incident.ui.components

import androidx.compose.foundation.background
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
                    "One quick question",
                    style = MaterialTheme.typography.labelLarge, color = c.ink2,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (speaking) TextAction("Stop speaking", onStopSpeaking, color = c.ink2)
            }
            if (question.key) {
                // The advisor's one dispatch-style question: no field behind it, answered Yes / No / Not sure.
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(question.spoken, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        OutlinedChoiceChip("Yes", onClick = { onKeyAnswer(KeyAnswer.YES) })
                        OutlinedChoiceChip("No", onClick = { onKeyAnswer(KeyAnswer.NO) })
                        OutlinedChoiceChip("Not sure", onClick = { onKeyAnswer(KeyAnswer.UNSURE) })
                    }
                }
            }
            question.asks.firstOrNull()?.let { ask ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(ask.prompt, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    if (ask.replies.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            ask.replies.forEach { r ->
                                OutlinedChoiceChip(r.label, onClick = { onReply(r) }, note = if (r.suggested) "suggested" else null, emphasis = r.suggested)
                            }
                        }
                    }
                }
            }
            Hairline()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    MicGlyph(c.accent, size = 20.dp)
                    TextAction("Answer by voice", onVoice, flush = true)
                }
                TextAction("Not now", onDismiss, color = c.ink2)
            }
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
    Box(
        modifier
            .size(Size.primaryButton)
            .pressMotion(interaction)
            .clip(CircleShape)
            .background(c.attentionContainer)
            .semantics { contentDescription = "A question is waiting for your answer" }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Open the question", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = c.onAttention)
    }
}

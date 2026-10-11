package com.megathon.incident.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.session.FieldView
import com.megathon.incident.session.QuickReply
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/** Answer options are text with a fine underline, rather than another set of cards. */
@Composable
fun ChoiceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null, selected: Boolean = false) {
    ReplyOption(label, onClick, modifier, note, selected)
}

@Composable
fun OutlinedChoiceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null, emphasis: Boolean = false) {
    ReplyOption(label, onClick, modifier, note, emphasis)
}

@Composable
private fun ReplyOption(label: String, onClick: () -> Unit, modifier: Modifier, note: String?, emphasis: Boolean) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.pressMotion(interaction).defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(4.dp))
            .drawBehind {
                drawLine(if (emphasis) c.accent else c.line, androidx.compose.ui.geometry.Offset(12.dp.toPx(), size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), size.height - 1.dp.toPx()), 1.dp.toPx())
            }
            .semantics { contentDescription = if (note != null) "$label, $note" else label }
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Choose $label", onClick = onClick)
            .padding(horizontal = Space.s3, vertical = Space.s2),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (emphasis) c.accent else c.ink)
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = c.ink3)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FieldRow(
    view: FieldView,
    selected: Boolean,
    editing: Boolean,
    justFilled: Boolean,
    choices: List<QuickReply>,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onSave: (String) -> Unit,
    onChoose: (QuickReply) -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    /** Chips from the suggestion engine for this field: proposals the reporter accepts or dismisses. */
    chips: List<Suggestion> = emptyList(),
    onAcceptChip: (Suggestion) -> Unit = {},
    onDismissChip: (Suggestion) -> Unit = {},
) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val bar by animateFloatAsState(
        targetValue = if (justFilled) 1f else 0f,
        animationSpec = tween(if (reduce) 0 else Motion.slow, easing = if (justFilled) Motion.enter else Motion.exit),
        label = "filled",
    )
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected, editing) {
        if (selected || editing) { delay(Motion.standard.toLong() + 40); requester.bringIntoView() }
    }
    val valueText = view.value
    val summary = buildString {
        append(view.label).append(": ")
        append(valueText ?: if (view.status == UiStatus.NEEDED) "needed" else "not mentioned")
        statusWord(view.status)?.let { append(", ").append(it) }
    }

    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c.accent.copy(alpha = bar)))
        Column(Modifier.weight(1f)) {
            val turn = animateFloatAsState(if (selected || editing) 180f else 0f, tween(if (reduce) 0 else Motion.standard), label = "field expansion")
            // The tappable header is one accessibility node; the action buttons below stay separate.
            Column(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = summary }
                    .clickable(onClickLabel = if (selected) "Hide details for ${view.label}" else "Show details for ${view.label}", onClick = onSelect)
                    .padding(horizontal = Space.s1, vertical = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(view.label, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
                    StatusChip(view.status)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.ink3, modifier = Modifier.padding(start = Space.s2).graphicsLayer { rotationZ = turn.value })
                }
                when {
                    valueText != null -> Text(
                        valueText, style = MaterialTheme.typography.titleMedium,
                        color = if (view.status == UiStatus.NONE || view.status == UiStatus.UNKNOWN) c.ink2 else c.ink,
                    )
                    view.status == UiStatus.NEEDED -> Text("Needed", style = MaterialTheme.typography.bodyLarge, color = c.onAttention)
                    else -> Text("Not mentioned", style = MaterialTheme.typography.bodyMedium, color = c.ink3)
                }
                if (view.status == UiStatus.NEEDED) view.hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink2) }
            }

            // A guessed value is never left looking like an answer: it sits in its own box with Accept and Decline.
            if (view.status == UiStatus.SUGGESTED && !view.writtenByAssistant && valueText != null && !editing) {
                SuggestedValueBox(
                    label = view.label, value = valueText, onAccept = onAccept, onDecline = onEdit,
                    modifier = Modifier.padding(start = Space.s1, end = Space.s1),
                )
            }

            if (chips.isNotEmpty() && !editing) {
                Column(Modifier.padding(start = Space.s1, end = Space.s1, bottom = Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    chips.forEach { chip -> SuggestionChipRow(chip, onAccept = { onAcceptChip(chip) }, onDismiss = { onDismissChip(chip) }) }
                }
            }

            AnimatedVisibility(
                visible = selected || editing,
                enter = fadeIn(tween(if (reduce) 0 else Motion.standard, easing = Motion.enter)) + expandVertically(tween(if (reduce) 0 else Motion.standard, easing = Motion.enter)),
                exit = fadeOut(tween(if (reduce) 0 else Motion.quick, easing = Motion.exit)) + shrinkVertically(tween(if (reduce) 0 else Motion.quick, easing = Motion.exit)),
            ) {
                Column(Modifier.bringIntoViewRequester(requester).padding(start = Space.s4, end = Space.s4, bottom = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    if (view.writtenByAssistant) {
                        Text("Written by the assistant from what you said. Edit it if it is not right.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    } else if (view.evidence != null) {
                        Column(Modifier.fillMaxWidth().drawBehind { drawLine(c.accent, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(0f, size.height), 2.dp.toPx()) }.padding(start = Space.s3, top = Space.s1, bottom = Space.s1)) {
                            Text("You said", style = MaterialTheme.typography.labelSmall, color = c.onAccentContainer)
                            Text("“${view.evidence}”", style = MaterialTheme.typography.bodyLarge, color = c.onAccentContainer)
                        }
                    }
                    if (editing) {
                        if (choices.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                                choices.forEach { OutlinedChoiceChip(it.label, onClick = { onChoose(it) }) }
                            }
                            TextAction("Cancel", onCancel, color = c.ink2, flush = true)
                        } else {
                            EditBox(view, onSave, onCancel)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                            TextAction(if (valueText == null) "Add" else "Edit", onEdit, flush = true)
                        }
                    }
                }
            }
            if (!selected && !editing) Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun EditBox(view: FieldView, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val c = Theme.colors
    var draft by remember(view.field) { mutableStateOf(view.value.orEmpty()) }
    val multiline = view.field == Field.DESCRIPTION || view.field == Field.RECOMMENDED_FOLLOW_UP
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(view.label) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave(draft) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.ink3,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.ink2,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.accent,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton("Save", { onSave(draft) }, Modifier.defaultMinSize(minHeight = 48.dp))
        TextAction("Cancel", onCancel, color = c.ink2)
    }
}

/** The status in a word, for screen readers (colour and icon are never the only signal). */
fun statusWord(s: UiStatus): String? = when (s) {
    UiStatus.HEARD -> "heard from you"
    UiStatus.SUGGESTED -> "suggested, not confirmed"
    UiStatus.CONFIRMED -> "confirmed"
    UiStatus.NEEDED -> "needed"
    UiStatus.NONE -> "none"
    UiStatus.UNKNOWN -> "not sure"
    UiStatus.EMPTY -> null
}

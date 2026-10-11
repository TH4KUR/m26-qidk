package com.megathon.incident.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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

/** The option buttons are squarer than a pill: a 10 dp corner (the user asked for less rounded). */
private val ChipShape = RoundedCornerShape(10.dp)

/** A tappable answer chip. 48 dp tall, 3:1 border. [note] is a second small line (for example "suggested"). */
@Composable
fun ChoiceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null, selected: Boolean = false) {
    val c = Theme.colors
    val shape = ChipShape
    Column(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(if (selected) c.accentContainer else c.surface, shape)
            .then(Modifier.semantics { contentDescription = if (note != null) "$label, $note" else label })
            .clickable(onClickLabel = "Choose $label", onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onAccentContainer else c.ink)
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = if (selected) c.onAccentContainer else c.ink2)
    }
}

/** Outlined version used where the chip sits on a tinted card. 1.5 dp border at 3:1 so it is visible without colour. */
@Composable
fun OutlinedChoiceChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null, emphasis: Boolean = false) {
    val c = Theme.colors
    val shape = ChipShape
    Column(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(c.surface, shape)
            .border(BorderStroke(if (emphasis) 2.dp else 1.5.dp, if (emphasis) c.accent else c.ink3), shape)
            .semantics { contentDescription = if (note != null) "$label, $note" else label }
            .clickable(onClickLabel = "Choose $label", onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = c.onAccentContainer)
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
            // The tappable header is one accessibility node; the action buttons below stay separate.
            Column(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = summary }
                    .clickable(onClickLabel = if (selected) "Hide details for ${view.label}" else "Show details for ${view.label}", onClick = onSelect)
                    .padding(horizontal = Space.s4, vertical = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(view.label, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
                    StatusChip(view.status)
                }
                when {
                    valueText != null -> Text(
                        valueText, style = MaterialTheme.typography.bodyLarge,
                        color = if (view.status == UiStatus.NONE || view.status == UiStatus.UNKNOWN) c.ink2 else c.ink,
                    )
                    view.status == UiStatus.NEEDED -> Text("Needed", style = MaterialTheme.typography.bodyLarge, color = c.onAttention)
                    else -> Text("Not mentioned", style = MaterialTheme.typography.bodyLarge, color = c.ink3)
                }
                view.hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink2) }
            }

            if (chips.isNotEmpty() && !editing) {
                Column(Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    chips.forEach { chip -> SuggestionChipRow(chip, onAccept = { onAcceptChip(chip) }, onDismiss = { onDismissChip(chip) }) }
                }
            }

            AnimatedVisibility(
                visible = selected || editing,
                enter = fadeIn(tween(Motion.standard, easing = Motion.enter)) + expandVertically(tween(Motion.standard, easing = Motion.enter)),
                exit = fadeOut(tween(Motion.quick, easing = Motion.exit)) + shrinkVertically(tween(Motion.quick, easing = Motion.exit)),
            ) {
                Column(Modifier.bringIntoViewRequester(requester).padding(start = Space.s4, end = Space.s4, bottom = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    if (view.writtenByAssistant) {
                        Text("Written by the assistant from what you said. Edit it if it is not right.", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    } else if (view.evidence != null) {
                        Column(Modifier.fillMaxWidth().background(c.accentContainer, RoundedCornerShape(12.dp)).padding(Space.s3)) {
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
                            if (view.status == UiStatus.SUGGESTED && !view.writtenByAssistant) {
                                SecondaryButton("Use this", onAccept, Modifier.defaultMinSize(minHeight = 48.dp))
                            }
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

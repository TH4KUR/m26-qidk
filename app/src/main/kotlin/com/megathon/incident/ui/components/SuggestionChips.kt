package com.megathon.incident.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.session.HindiUiText
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/** What the chip asks, in words: a site name is "Did you mean", a hedged value is "Confirm". */
fun suggestionQuestion(s: Suggestion): String = when {
    s.proposed == s.original -> "Confirm “${s.proposed}”?"
    s.source == Source.GAZETTEER -> "Did you mean “${s.proposed}”?"
    else -> "Change to “${s.proposed}”?"
}

/**
 * [suggestionQuestion] in the app language. In Hindi the core's table ([HindiUiText]) is asked first; a question it has no
 * Hindi for yet is written here from the same three shapes, so the box is never half English.
 */
@Composable
fun suggestionQuestionText(s: Suggestion): String {
    val english = suggestionQuestion(s)
    if (!isHindi()) return english
    val known = HindiUiText.of(english)
    if (known != english) return known
    return when {
        s.proposed == s.original -> "“${s.proposed}” की पुष्टि करें?"
        s.source == Source.GAZETTEER -> "क्या आपका मतलब “${s.proposed}” है?"
        else -> "क्या “${s.proposed}” कर दें?"
    }
}

/**
 * A suggestion under the field it is about, in its own box so it cannot be mistaken for an answer. It is labelled
 * "Suggested" with an icon and a word, never colour alone, and it changes nothing by itself: Accept and Decline are both
 * 48 dp targets. The hint of why comes from the rule that made it.
 */
@Composable
fun SuggestionChipRow(suggestion: Suggestion, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val question = suggestionQuestionText(suggestion)
    val reason = if (isHindi()) HindiUiText.of(suggestion.reason) else suggestion.reason
    val suggested = statusLabel(UiStatus.SUGGESTED).orEmpty()
    val description = tr("Suggested. $question $reason", "$suggested। $question $reason")
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.s1)
            .background(c.attentionContainer, RoundedCornerShape(12.dp))
            .padding(Space.s3)
            .semantics(mergeDescendants = false) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Pill(suggested, c.canvas, c.onAttention, icon = Icons.Rounded.Info)
        Text(question, style = MaterialTheme.typography.bodyLarge, color = c.onAttention)
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = c.onAttention)
        AcceptDeclineRow(onAccept = onAccept, onDecline = onDismiss)
    }
}

/**
 * A value the app only guessed (for example the severity), shown as a box with the guess in it and two clear choices.
 * Accept keeps it; Decline opens the field so the reporter can give the right answer. Nothing is kept silently.
 */
@Composable
fun SuggestedValueBox(label: String, value: String, onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val suggested = statusLabel(UiStatus.SUGGESTED).orEmpty()
    val description = tr("Suggested $label: $value. Accept or decline.", "$suggested, $label: $value। स्वीकार करें या नहीं।")
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Space.s1, bottom = Space.s2)
            .background(c.attentionContainer, RoundedCornerShape(12.dp))
            .padding(Space.s3)
            .semantics(mergeDescendants = false) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Pill(suggested, c.canvas, c.onAttention, icon = Icons.Rounded.Info)
        Text(
            tr("I guessed “$value” from your story. Is that right?", "आपकी बात से मैंने “$value” समझा। क्या यह सही है?"),
            style = MaterialTheme.typography.bodyLarge, color = c.onAttention,
        )
        AcceptDeclineRow(onAccept = onAccept, onDecline = onDecline)
    }
}

@Composable
private fun AcceptDeclineRow(onAccept: () -> Unit, onDecline: () -> Unit) {
    val c = Theme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton(
            tr("Accept", "ठीक है"), onAccept, Modifier.defaultMinSize(minHeight = 48.dp),
            leading = { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.padding(end = Space.s1)) },
        )
        TextButton(
            onDecline, Modifier.defaultMinSize(minHeight = Size.touch),
            colors = ButtonDefaults.textButtonColors(contentColor = c.onAttention),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.padding(end = Space.s1))
            Text(tr("Decline", "नहीं"), style = MaterialTheme.typography.labelLarge)
        }
    }
}

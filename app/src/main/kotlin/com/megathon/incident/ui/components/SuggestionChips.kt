package com.megathon.incident.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
 * A suggestion under the field it is about. It is labelled "Suggested" with an icon and a word, never colour alone, and it
 * changes nothing by itself: Accept and Dismiss are both 48 dp targets. Hint of why comes from the rule that made it.
 */
@Composable
fun SuggestionChipRow(suggestion: Suggestion, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val question = suggestionQuestion(suggestion)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.attentionContainer, RoundedCornerShape(12.dp))
            .padding(Space.s3)
            .semantics(mergeDescendants = false) { contentDescription = "Suggested. $question ${suggestion.reason}" },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Pill("Suggested", c.canvas, c.onAttention, icon = Icons.Rounded.Info)
        Text(question, style = MaterialTheme.typography.bodyLarge, color = c.onAttention)
        Text(suggestion.reason, style = MaterialTheme.typography.bodyMedium, color = c.onAttention)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(
                "Accept", onAccept, Modifier.defaultMinSize(minHeight = 48.dp),
                leading = { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.padding(end = Space.s1)) },
            )
            TextButton(
                onDismiss, Modifier.defaultMinSize(minHeight = Size.touch),
                colors = ButtonDefaults.textButtonColors(contentColor = c.onAttention),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.padding(end = Space.s1))
                Text("Dismiss", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

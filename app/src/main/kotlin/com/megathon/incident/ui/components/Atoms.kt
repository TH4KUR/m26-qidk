package com.megathon.incident.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.Field
import com.megathon.incident.session.HindiLabels
import com.megathon.incident.session.HindiUiText
import com.megathon.incident.session.QuickReply
import com.megathon.incident.session.UiStatus
import com.megathon.incident.testing.Compute
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

private val ButtonShape = RoundedCornerShape(20.dp)

/** The one filled button on a screen. 56 dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    Button(
        interactionSource = interaction,
        onClick = onClick,
        modifier = modifier.pressMotion(interaction).defaultMinSize(minHeight = Size.primaryButton),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent, contentColor = c.onAccent,
            disabledContainerColor = c.line, disabledContentColor = c.ink3,
        ),
        contentPadding = PaddingValues(horizontal = Space.s6, vertical = Space.s3),
    ) {
        leading?.invoke(this)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Outlined, with a 3:1 border so the control is visible without colour. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        interactionSource = interaction,
        onClick = onClick,
        modifier = modifier.pressMotion(interaction).defaultMinSize(minHeight = Size.primaryButton),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, if (enabled) c.ink3 else c.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink, disabledContentColor = c.ink3),
        contentPadding = PaddingValues(horizontal = Space.s6, vertical = Space.s3),
    ) {
        leading?.invoke(this)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Theme.colors.accent, flush: Boolean = false) {
    TextButton(
        onClick, modifier.defaultMinSize(minHeight = Size.touch), colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = if (flush) PaddingValues(horizontal = 0.dp) else ButtonDefaults.TextButtonContentPadding,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A small label with an icon and a word: status is never colour alone. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, glyph: (@Composable () -> Unit)? = null) {
    Row(
        modifier.background(container, RoundedCornerShape(10.dp)).padding(horizontal = Space.s2, vertical = Space.s1),
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        glyph?.invoke()
        Text(text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

/**
 * Where a value came from, as a colour-coded pill that always has an icon and a word as well (never colour alone):
 * green = heard from the reporter, solid green = confirmed, amber = suggested and waiting for a yes,
 * red = still needed, blue = "not sure", grey = explicitly none.
 */
@Composable
fun StatusChip(status: UiStatus, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val word = statusLabel(status) ?: return
    when (status) {
        UiStatus.HEARD -> Pill(word, c.accentContainer, c.onAccentContainer, modifier, glyph = { MicGlyph(c.onAccentContainer, size = 14.dp) })
        UiStatus.CONFIRMED -> Pill(word, c.accent, c.onAccent, modifier, icon = Icons.Rounded.Check)
        UiStatus.SUGGESTED -> Pill(word, c.attentionContainer, c.onAttention, modifier, icon = Icons.Rounded.Info)
        UiStatus.NEEDED -> Pill(word, c.dangerContainer, c.onDanger, modifier, icon = Icons.Rounded.Warning)
        UiStatus.UNKNOWN -> Pill(word, c.infoContainer, c.onInfo, modifier, icon = Icons.Rounded.Info)
        UiStatus.NONE -> Pill(word, c.line.copy(alpha = 0.55f), c.ink, modifier)
        UiStatus.EMPTY -> Unit
    }
}

/** The status word in the app language: the English pill word, or [HindiLabels.status] when the app is in Hindi. Null for EMPTY. */
@Composable
fun statusLabel(s: UiStatus): String? = if (isHindi()) HindiLabels.status(s) else when (s) {
    UiStatus.HEARD -> "Heard"
    UiStatus.CONFIRMED -> "Confirmed"
    UiStatus.SUGGESTED -> "Suggested"
    UiStatus.NEEDED -> "Needed"
    UiStatus.UNKNOWN -> "Not sure"
    UiStatus.NONE -> "None"
    UiStatus.EMPTY -> null
}

/**
 * A sentence the core wrote (a question, a reason, a hint, a message), in the app language: the English as given, or its Hindi
 * from [HindiUiText] (the same table the spoken line uses, so what is shown equals what is heard). With no Hindi yet it stays
 * English, as a whole element.
 */
@Composable
fun coreText(english: String): String = if (isHindi()) HindiUiText.of(english) else english

/** A count as the current language writes it: Devanagari digits in Hindi (as the dates are), plain digits in English. */
@Composable
fun num(n: Int): String =
    if (isHindi()) n.toString().map { if (it in '0'..'9') '\u0966' + (it - '0') else it }.joinToString("") else n.toString()

/**
 * What a ready answer says in the app language. An incident type or a severity goes through [HindiLabels] by its stored value;
 * any other answer (Today, Yesterday, None, Skip, a name) through [com.megathon.incident.session.HindiUiText], so it reads
 * the same as the spoken question. English is the label as given.
 */
@Composable
fun replyLabel(r: QuickReply): String {
    if (!isHindi()) return r.label
    if (!r.value.startsWith("@")) {
        when (r.field) {
            Field.INCIDENT_TYPE -> return HindiLabels.type(r.value)
            Field.SEVERITY -> return HindiLabels.severity(r.value)
            else -> Unit
        }
    }
    return HindiUiText.of(r.label)
}

/** Where a stage really ran. SIMULATED is loud on purpose. */
@Composable
fun ComputePill(compute: Compute, modifier: Modifier = Modifier) {
    val c = Theme.colors
    when (compute) {
        Compute.NPU -> Pill("NPU", c.accentContainer, c.onAccentContainer, modifier)
        Compute.CPU -> Pill("CPU", c.line.copy(alpha = 0.5f), c.ink, modifier)
        Compute.HYBRID -> Pill("NPU + CPU", c.infoContainer, c.onInfo, modifier)
        Compute.SIMULATED -> Pill("SIMULATED", c.simulatedContainer, c.onSimulated, modifier, icon = Icons.Rounded.Warning)
    }
}

/** Decorative divider (1.5:1): structure only, never the sole boundary of a control. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Theme.colors.line))
}

/** Add or correct by voice: a 56 dp outlined circle with the mic glyph. Icon-only, so it has an explicit name for screen readers. */
@Composable
fun VoiceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val description = tr("Add or correct by voice", "बोलकर जोड़ें या सुधारें")
    androidx.compose.material3.OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.size(Size.primaryButton).semantics { contentDescription = description },
        shape = androidx.compose.foundation.shape.CircleShape,
        border = BorderStroke(1.5.dp, c.ink3),
    ) { MicGlyph(c.ink, size = 26.dp) }
}

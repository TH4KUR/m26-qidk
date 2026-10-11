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
import com.megathon.incident.session.UiStatus
import com.megathon.incident.testing.Compute
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

@Composable
fun StatusChip(status: UiStatus, modifier: Modifier = Modifier) {
    val c = Theme.colors
    val label = when (status) {
        UiStatus.HEARD -> "Heard"
        UiStatus.CONFIRMED -> "Confirmed"
        UiStatus.SUGGESTED -> "Suggested"
        UiStatus.NEEDED -> "Needed"
        UiStatus.NONE -> "None"
        UiStatus.UNKNOWN -> "Not sure"
        UiStatus.EMPTY -> return
    }
    val color = when (status) {
        UiStatus.CONFIRMED -> c.accent
        UiStatus.SUGGESTED, UiStatus.NEEDED -> c.onAttention
        else -> c.ink3
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
        when (status) {
            UiStatus.CONFIRMED -> Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(14.dp))
            UiStatus.SUGGESTED -> Icon(Icons.Rounded.Info, null, tint = color, modifier = Modifier.size(14.dp))
            UiStatus.NEEDED -> Icon(Icons.Rounded.Warning, null, tint = color, modifier = Modifier.size(14.dp))
            UiStatus.HEARD -> MicGlyph(color, size = 14.dp)
            else -> Unit
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
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
    androidx.compose.material3.OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.size(Size.primaryButton).semantics { contentDescription = "Add or correct by voice" },
        shape = androidx.compose.foundation.shape.CircleShape,
        border = BorderStroke(1.5.dp, c.ink3),
    ) { MicGlyph(c.ink, size = 26.dp) }
}

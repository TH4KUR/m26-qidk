package com.megathon.incident.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Theme

/**
 * The microphone. Three honest states: ready (accent), listening (red, with a ring that follows how loud it is),
 * working (a quiet static report mark). The listening ring follows real audio levels; the working state never loops.
 */
@Composable
fun MicButton(
    listening: Boolean,
    busy: Boolean,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = Size.mic,
) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val ringScale = animateFloatAsState(
        targetValue = if (listening && !reduce) 1f + 0.4f * level else 1f,
        animationSpec = tween(if (reduce) 0 else 90),
        label = "ring",
    )
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(when {
        listening -> c.danger
        busy -> c.surface
        else -> c.accent
    }, tween(if (reduce) 0 else Motion.standard), label = "mic color")
    val onFill = if (busy) c.ink2 else if (c.isDark && listening) c.canvas else c.onAccent
    val description = when {
        listening -> "Stop recording"
        busy -> "Working on it"
        else -> "Start recording"
    }
    Box(
        modifier
            .pressMotion(interaction)
            .size(diameter + 56.dp)
            .semantics { role = Role.Button; contentDescription = description; stateDescription = if (listening) "Recording" else if (busy) "Working" else "Ready" }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = !busy, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Box(
                Modifier.size(diameter)
                    .graphicsLayer { scaleX = ringScale.value; scaleY = ringScale.value }
                    .background(c.danger.copy(alpha = 0.22f), CircleShape),
            )
        }
        Box(
            Modifier.size(diameter).background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(if (listening) 1 else if (busy) 2 else 0, transitionSpec = {
                (fadeIn(tween(if (reduce) 0 else Motion.quick)) + scaleIn(tween(if (reduce) 0 else Motion.standard), initialScale = .8f)) togetherWith fadeOut(tween(if (reduce) 0 else Motion.quick))
            }, label = "recorder symbol") { state ->
            when (state) {
                2 -> {
                    Canvas(Modifier.size(diameter * 0.42f)) {
                        val stroke = 3.dp.toPx()
                        val left = size.width * 0.18f
                        val right = size.width * 0.82f
                        val top = size.height * 0.25f
                        val gap = size.height * 0.26f
                        repeat(3) { row ->
                            val y = top + gap * row
                            drawLine(c.accent, Offset(left, y), Offset(right - if (row == 1) size.width * 0.2f else 0f, y), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                }
                1 -> StopGlyph(onFill, size = diameter * 0.42f)
                else -> MicGlyph(onFill, size = diameter * 0.46f)
            }
            }
        }
        if (busy) Canvas(Modifier.size(diameter)) { drawCircle(c.line, radius = size.minDimension / 2f - 1f, style = Stroke(2f)) }
    }
}

/** A short history of loudness: proof that the device hears. Bars are drawn from the real levels, newest on the right. */
@Composable
fun LevelBars(levels: List<Float>, modifier: Modifier = Modifier, count: Int = 24, barHeight: Dp = 40.dp) {
    val c = Theme.colors
    val padded = List((count - levels.size).coerceAtLeast(0)) { 0f } + levels.takeLast(count)
    Row(
        modifier.height(barHeight).semantics { contentDescription = "Sound level" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        padded.forEach { lv ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(maxOf(4.dp, barHeight * lv))
                    .background(if (lv > 0.02f) c.accent else c.line, RoundedCornerShape(2.dp)),
            )
        }
    }
}

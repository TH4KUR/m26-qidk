package com.megathon.incident.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.State
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
 * The microphone. Three honest states: ready (accent), listening (red, a ring that follows how loud it is and breathes
 * gently so a quiet room still reads as alive), working (a progress ring that turns while the device works).
 * Nothing moves at rest, and nothing moves when the system animator scale is zero.
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
        targetValue = if (listening && !reduce) 1f + 0.4f * visualLevel(level) else 1f,
        animationSpec = tween(if (reduce) 0 else 90),
        label = "ring",
    )
    // Only while listening: a slow breath on top of the level, so the ring is alive even when the room is quiet.
    val breath: State<Float>? = if (listening && !reduce) {
        rememberInfiniteTransition(label = "listening breath").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = Motion.enter), RepeatMode.Reverse),
            label = "breath",
        )
    } else null
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
                    .graphicsLayer {
                        val s = ringScale.value + .08f * (breath?.value ?: 0f)
                        scaleX = s; scaleY = s
                    }
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
                    // The ring that turns while the device works; a still two-thirds ring when motion is off.
                    if (reduce) CircularProgressIndicator(progress = { 0.66f }, color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(diameter * 0.5f))
                    else CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(diameter * 0.5f))
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
        padded.forEach { raw ->
            val lv = visualLevel(raw)
            Box(
                Modifier
                    .width(4.dp)
                    .height(maxOf(4.dp, barHeight * lv))
                    .background(if (lv > 0.02f) c.accent else c.line, RoundedCornerShape(2.dp)),
            )
        }
    }
}

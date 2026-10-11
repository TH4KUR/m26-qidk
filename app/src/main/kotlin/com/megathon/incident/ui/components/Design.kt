package com.megathon.incident.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Read animation state in the drawing layer; a press never relays out the screen. */
@Composable
fun Modifier.pressMotion(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale = animateFloatAsState(if (pressed && !reduce) .97f else 1f, tween(if (reduce) 0 else Motion.quick), label = "press")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** A finite entrance, with no coroutine or redraw loop after it finishes. */
@Composable
fun Reveal(modifier: Modifier = Modifier, delayMillis: Int = 0, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val amount = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) amount.snapTo(1f) else { delay(delayMillis.toLong()); amount.animateTo(1f, tween(Motion.slow, easing = Motion.enter)) }
    }
    Box(modifier.graphicsLayer { alpha = amount.value; translationY = (1f - amount.value) * 16.dp.toPx() }) { content() }
}

@Composable
fun StageTrack(stage: Int, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Row(modifier.fillMaxWidth().semantics { contentDescription = "Step ${stage + 1} of 3: ${listOf("Capture", "Shape", "Review")[stage]}" }, verticalAlignment = Alignment.CenterVertically) {
        listOf("Capture", "Shape", "Review").forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(22.dp).background(if (index <= stage) c.accentContainer else c.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (index < stage) "✓" else "${index + 1}", style = MaterialTheme.typography.labelSmall, color = if (index <= stage) c.accent else c.ink3)
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (index == stage) c.ink else c.ink3)
            }
            if (index < 2) Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(c.line))
        }
    }
}

/**
 * One section of a screen on its own tonal surface, under a small heading, so sections read as separate groups instead of
 * one long list. The gap between panels is larger than the gap between rows inside one (screens use Space.s6 / Space.s1).
 */
@Composable
fun Panel(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Theme.colors
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.ink3,
                modifier = Modifier.padding(start = Space.s1, bottom = Space.s2).semantics { heading() },
            )
        }
        Column(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).padding(horizontal = Space.s3, vertical = Space.s1),
            content = content,
        )
    }
}

/**
 * What the microphone hears, on a gentle curve: the kit's built-in microphone sits near -39 dBFS for normal speech
 * (about 0.3 on the recorder's 0..1 scale), which would barely move a ring drawn on a straight scale. Display only;
 * the recorder's own level is untouched everywhere else.
 */
fun visualLevel(level: Float): Float = sqrt(level.coerceIn(0f, 1f))

/**
 * Capture geometry. Still at rest; while listening or working it moves, because something is actually happening:
 * the arcs turn (slowly while listening, faster while the device works) and the rings follow the measured level.
 * With the system animator scale at zero it stays still.
 */
@Composable
fun RecorderAura(
    listening: Boolean,
    level: Float,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    diameter: Dp = 208.dp,
    content: @Composable () -> Unit,
) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val moving = (listening || busy) && !reduce
    val scale = animateFloatAsState(if (listening && !reduce) 1f + .16f * visualLevel(level) else 1f, tween(if (reduce) 0 else 100), label = "audio aura")
    val color = if (listening) c.danger else c.accent
    // Created only while something is happening: an idle screen draws no frames.
    val turn: State<Float>? = if (moving) {
        rememberInfiniteTransition(label = "aura").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(if (listening) 16_000 else 7_000, easing = LinearEasing)),
            label = "aura turn",
        )
    } else null
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            val radius = size.minDimension / 2
            val a = turn?.value ?: 0f // read while drawing: the turn never recomposes the screen
            val outerStart = -52f + a
            val innerStart = 110f - a * 1.5f
            drawCircle(color.copy(alpha = .06f), radius)
            drawCircle(color.copy(alpha = .09f), radius * .94f, style = Stroke(1.dp.toPx()))
            drawCircle(color.copy(alpha = .19f), radius * .75f, style = Stroke(1.dp.toPx()))
            drawArc(color.copy(alpha = .55f), outerStart, 56f, false, Offset(radius * .06f, radius * .06f), androidx.compose.ui.geometry.Size(radius * 1.88f, radius * 1.88f), style = Stroke(2.dp.toPx()))
            drawArc(color.copy(alpha = .35f), innerStart, 38f, false, Offset(radius * .25f, radius * .25f), androidx.compose.ui.geometry.Size(radius * 1.5f, radius * 1.5f), style = Stroke(2.dp.toPx()))
            // The two dots ride the leading edge of their arcs.
            val outer = Math.toRadians(outerStart.toDouble())
            val inner = Math.toRadians(innerStart.toDouble())
            drawCircle(color, 3.dp.toPx(), Offset(center.x + radius * .94f * cos(outer).toFloat(), center.y + radius * .94f * sin(outer).toFloat()))
            drawCircle(color.copy(alpha = .45f), 2.dp.toPx(), Offset(center.x + radius * .75f * cos(inner).toFloat(), center.y + radius * .75f * sin(inner).toFloat()))
        }
        content()
    }
}

/**
 * Progress for work with no known length (model warm-up, transcribing). It runs while the work runs and is a still
 * half-filled bar when motion is off. The words next to it carry the meaning; this only shows that it is alive.
 */
@Composable
fun WorkingBar(modifier: Modifier = Modifier, width: Dp = 96.dp) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val bar = modifier.width(width).height(3.dp)
    if (reduce) LinearProgressIndicator(progress = { .5f }, modifier = bar, color = c.accent, trackColor = c.line)
    else LinearProgressIndicator(modifier = bar, color = c.accent, trackColor = c.line)
}

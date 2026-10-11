package com.megathon.incident.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.*
import kotlinx.coroutines.delay

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

/** Custom capture geometry: static at rest; rings respond only to measured audio. */
@Composable
fun RecorderAura(listening: Boolean, level: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val scale = animateFloatAsState(if (listening && !reduce) 1f + .07f * level.coerceIn(0f, 1f) else 1f, tween(if (reduce) 0 else 100), label = "audio aura")
    val color = if (listening) c.danger else c.accent
    Box(modifier.size(252.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            val radius = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .10f), color.copy(alpha = 0f)), center, radius), radius)
            drawCircle(color.copy(alpha = .09f), radius * .94f, style = Stroke(1.dp.toPx()))
            drawCircle(color.copy(alpha = .19f), radius * .75f, style = Stroke(1.dp.toPx()))
            drawArc(color.copy(alpha = .55f), -52f, 56f, false, Offset(radius * .06f, radius * .06f), androidx.compose.ui.geometry.Size(radius * 1.88f, radius * 1.88f), style = Stroke(2.dp.toPx()))
            drawArc(color.copy(alpha = .35f), 110f, 38f, false, Offset(radius * .25f, radius * .25f), androidx.compose.ui.geometry.Size(radius * 1.5f, radius * 1.5f), style = Stroke(2.dp.toPx()))
            drawCircle(color, 3.dp.toPx(), Offset(size.width * .84f, size.height * .18f))
            drawCircle(color.copy(alpha = .45f), 2.dp.toPx(), Offset(size.width * .11f, size.height * .70f))
        }
        content()
    }
}

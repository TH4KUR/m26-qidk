package com.megathon.incident.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.CapturePalette
import com.megathon.incident.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.sin

/** One static sound mark at rest; during capture, the same bars show the recorder's real level history. */
@Composable
fun AudioSignature(levels: List<Float>, listening: Boolean, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Canvas(modifier) {
        val step = 7.dp.toPx()
        val stroke = 3.dp.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val live = levels.takeLast(count)
        val missing = (count - live.size).coerceAtLeast(0)
        val center = size.height / 2f
        val maxHalf = size.height * 0.46f

        repeat(count) { index ->
            val historyIndex = index - missing
            val hasLevel = listening && historyIndex >= 0
            val value = when {
                hasLevel -> visualLevel(live[historyIndex]) // the same gentle curve as the rings: a quiet mic still moves
                listening -> 0f
                else -> {
                    val envelope = 0.18f + 0.82f * abs(sin(index * 0.17f + 0.6f))
                    val carrier = 0.12f + 0.88f * abs(sin(index * 0.061f))
                    envelope * carrier
                }
            }
            val half = (size.height * (0.07f + 0.91f * value) / 2f).coerceAtMost(maxHalf)
            val x = step * index + step / 2f
            drawLine(c.line, Offset(x, center - 1.dp.toPx()), Offset(x, center + 1.dp.toPx()), stroke, StrokeCap.Round)
            drawLine(if (listening) c.danger else c.accent, Offset(x, center - half), Offset(x, center + half), stroke, StrokeCap.Round)
        }
    }
}

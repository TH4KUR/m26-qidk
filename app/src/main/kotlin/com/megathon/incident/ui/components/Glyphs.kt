package com.megathon.incident.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Drawn, not imported: the Material core icon set has no microphone or stop glyph. Decorative; the owner labels it. */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f
        drawRoundRect(color, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.5f), CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.22f, h * 0.28f), Size(w * 0.56f, h * 0.44f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.92f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.92f), Offset(w * 0.64f, h * 0.92f), stroke, StrokeCap.Round)
    }
}

@Composable
fun StopGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(color, Offset(w * 0.22f, w * 0.22f), Size(w * 0.56f, w * 0.56f), CornerRadius(w * 0.1f))
    }
}

/** Three lines with a dot each: the list of saved reports. Decorative; the tab carries the label. */
@Composable
fun ListGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.085f
        listOf(0.26f, 0.5f, 0.74f).forEach { y ->
            drawCircle(color, w * 0.055f, Offset(w * 0.2f, w * y))
            drawLine(color, Offset(w * 0.34f, w * y), Offset(w * 0.82f, w * y), stroke, StrokeCap.Round)
        }
    }
}

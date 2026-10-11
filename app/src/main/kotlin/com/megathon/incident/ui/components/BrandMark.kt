package com.megathon.incident.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.Theme

/**
 * The app mark: a speech bubble holding report lines (what was said becomes the report), with an amber band behind the second
 * line for the evidence highlight. The same shapes as the launcher icon (res/drawable/ic_launcher_foreground.xml), drawn on a
 * 52 x 50 grid. Decorative: whoever places it names the app next to it.
 */
@Composable
fun BrandTile(tile: Color, bubble: Color, lines: Color, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(tile, RoundedCornerShape(size * 0.35f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.62f)) {
            // Grid units: the bubble is 52 wide (x 0..52) and 40 tall (y 0..40); the tail reaches y 50.
            val u = this.size.width / 52f
            fun p(x: Float, y: Float) = Offset(x * u, y * u)
            val r = 12f * u
            drawRoundRect(bubble, p(0f, 0f), Size(52f * u, 40f * u), CornerRadius(r))
            val tail = Path().apply {
                moveTo(20f * u, 38f * u); lineTo(6f * u, 50f * u); lineTo(8f * u, 38f * u); close()
            }
            drawPath(tail, bubble)
            drawRoundRect(HIGHLIGHT, p(5f, 17f), Size(34f * u, 9f * u), CornerRadius(2f * u))
            val w = 4f * u
            drawLine(lines, p(9f, 12f), p(43f, 12f), w, StrokeCap.Round)
            drawLine(lines, p(9f, 21.5f), p(35f, 21.5f), w, StrokeCap.Round)
            drawLine(lines, p(9f, 31f), p(25f, 31f), w, StrokeCap.Round)
        }
    }
}

/** The mark in the app's own colours: accent tile, light bubble, accent lines. */
@Composable
fun BrandMark(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val c = Theme.colors
    BrandTile(tile = c.accent, bubble = c.onAccent, lines = c.accent, size = size, modifier = modifier)
}

private val HIGHLIGHT = Color(0xFFF2BE72)

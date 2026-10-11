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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.Theme

/**
 * The app mark: a speech bubble that says "bol" (Hindi: speak), with an amber band behind the word for the evidence highlight
 * the Report screen draws behind the words that support a field. The same shapes as the launcher icon
 * (res/drawable/ic_launcher_foreground.xml, 108 dp grid; the bubble is x 24..84, y 34..82). The letters are strokes, not a font.
 * Decorative: whoever places it names the app next to it.
 */
@Composable
fun BrandTile(tile: Color, bubble: Color, letters: Color, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(tile, RoundedCornerShape(size * 0.35f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.66f)) {
            val u = this.size.width / 60f
            val oy = (this.size.height - 48f * u) / 2f
            fun p(x: Float, y: Float) = Offset((x - 24f) * u, oy + (y - 34f) * u)
            drawRoundRect(bubble, p(24f, 34f), Size(60f * u, 40f * u), CornerRadius(12f * u))
            val tail = Path().apply {
                val a = p(52f, 72f)
                val b = p(38f, 82f)
                val c = p(40f, 72f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
            }
            drawPath(tail, bubble)
            drawRoundRect(HIGHLIGHT, p(30f, 42.5f), Size(48f * u, 23f * u), CornerRadius(4f * u))
            val w = 3.6f * u
            drawLine(letters, p(34.4f, 46f), p(34.4f, 62f), w, StrokeCap.Round)
            drawCircle(letters, 6f * u, p(40.4f, 56f), style = Stroke(w))
            drawCircle(letters, 6f * u, p(60f, 56f), style = Stroke(w))
            drawLine(letters, p(73.6f, 46f), p(73.6f, 62f), w, StrokeCap.Round)
        }
    }
}

/** The mark in the app's own colours: accent tile, light bubble, dark letters. */
@Composable
fun BrandMark(size: Dp = 34.dp, modifier: Modifier = Modifier) {
    val c = Theme.colors
    BrandTile(tile = c.accent, bubble = c.onAccent, letters = LETTERS, size = size, modifier = modifier)
}

/**
 * The wordmark "bayaan", drawn as strokes (b a y a a n) with the evidence highlight behind the double "a". The same shapes as
 * res/drawable/splash_branding.xml, on a 164 x 60 grid. Decorative: the caller gives it the accessible name.
 */
@Composable
fun BrandWordmark(color: Color, height: Dp = 22.dp, modifier: Modifier = Modifier, onBand: Color = LETTERS) {
    Canvas(modifier.size(height * (164f / 60f), height)) {
        val u = this.size.width / 164f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val w = 4f * u
        val box = Size(20f * u, 20f * u)
        fun ring(c: Color, x: Float) = drawCircle(c, 10f * u, p(x + 10f, 30f), style = Stroke(w))
        fun stem(c: Color, x: Float, top: Float, bottom: Float) = drawLine(c, p(x, top), p(x, bottom), w, StrokeCap.Round)
        drawRoundRect(HIGHLIGHT, p(82f, 14f), Size(56f * u, 32f * u), CornerRadius(8f * u))
        // b
        stem(color, 2f, 8f, 40f); ring(color, 2f)
        // a
        ring(color, 30f); stem(color, 50f, 20f, 40f)
        // y: a bowl, the right stem, and the hook of the tail
        drawArc(color, 0f, 180f, false, p(58f, 20f), box, style = Stroke(w, cap = StrokeCap.Round))
        stem(color, 58f, 20f, 30f); stem(color, 78f, 20f, 44f)
        drawArc(color, 0f, 90f, false, p(58f, 34f), box, style = Stroke(w, cap = StrokeCap.Round))
        // aa, on the band, in the dark letter colour
        ring(onBand, 86f); stem(onBand, 106f, 20f, 40f)
        ring(onBand, 114f); stem(onBand, 134f, 20f, 40f)
        // n: a stem and an arch
        stem(color, 142f, 20f, 40f)
        drawArc(color, 180f, 180f, false, p(142f, 20f), box, style = Stroke(w, cap = StrokeCap.Round))
        stem(color, 162f, 30f, 40f)
    }
}

private val HIGHLIGHT = Color(0xFFF2BE72)
private val LETTERS = Color(0xFF0A4F49)

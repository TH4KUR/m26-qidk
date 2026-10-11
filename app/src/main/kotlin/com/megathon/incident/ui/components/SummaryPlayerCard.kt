package com.megathon.incident.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.SummaryAudio
import com.megathon.incident.ui.SummaryPhase
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/**
 * The saved report's spoken summary: the words, and a player with play/pause and a seek bar. The words are the same ones the
 * voice speaks, so a reporter who cannot or does not want to listen loses nothing.
 */
@Composable
fun SummaryPlayerCard(audio: SummaryAudio, onToggle: () -> Unit, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Panel("Spoken summary", modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
            Text(audio.text, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            when (audio.phase) {
                SummaryPhase.PREPARING -> Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    WorkingBar(width = 40.dp)
                    Text("Preparing the audio…", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
                SummaryPhase.UNAVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio is not available for this voice.", style = MaterialTheme.typography.bodyMedium, color = c.ink3, modifier = Modifier.weight(1f))
                    TextAction("Try again", onToggle)
                }
                SummaryPhase.PLAYING, SummaryPhase.PAUSED -> {
                    val playing = audio.phase == SummaryPhase.PLAYING
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val shown = dragging ?: audio.positionMs.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        PlayPauseButton(playing, onToggle)
                        Column(Modifier.weight(1f)) {
                            Slider(
                                value = shown.coerceIn(0f, audio.durationMs.coerceAtLeast(1).toFloat()),
                                onValueChange = { dragging = it },
                                onValueChangeFinished = { dragging?.let { onSeek(it.toInt()) }; dragging = null },
                                valueRange = 0f..audio.durationMs.coerceAtLeast(1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line,
                                ),
                                modifier = Modifier.semantics {
                                    contentDescription = "Summary position"
                                    stateDescription = "${clock(shown.toInt())} of ${clock(audio.durationMs)}"
                                },
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Text(clock(shown.toInt()), style = TIME, color = c.ink2, modifier = Modifier.weight(1f))
                                Text(clock(audio.durationMs), style = TIME, color = c.ink3)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TIME: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

private fun clock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** A 48 dp round button; the glyph is drawn so it never depends on an extra icon set. */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val c = Theme.colors
    Canvas(
        Modifier
            .size(Size.touch)
            .clip(CircleShape)
            .background(c.accent)
            .semantics { role = Role.Button; contentDescription = if (playing) "Pause summary" else "Play summary" }
            .clickable(onClick = onClick),
    ) {
        glyph(playing, c.onAccent)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.glyph(playing: Boolean, color: Color) {
    val s = size.minDimension * 0.34f
    val cx = size.width / 2f
    val cy = size.height / 2f
    if (playing) {
        val bar = s * 0.62f
        val gap = s * 0.50f
        val corner = CornerRadius(bar * 0.25f)
        drawRoundRect(color, Offset(cx - gap / 2f - bar, cy - s), androidx.compose.ui.geometry.Size(bar, s * 2f), corner)
        drawRoundRect(color, Offset(cx + gap / 2f, cy - s), androidx.compose.ui.geometry.Size(bar, s * 2f), corner)
    } else {
        val path = Path().apply {
            moveTo(cx - s * 0.7f, cy - s)
            lineTo(cx + s * 1.05f, cy)
            lineTo(cx - s * 0.7f, cy + s)
            close()
        }
        drawPath(path, color)
    }
}

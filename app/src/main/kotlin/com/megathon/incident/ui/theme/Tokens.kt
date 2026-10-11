package com.megathon.incident.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Spacing on a 4 dp scale. The gap inside a group is always smaller than the gap between groups. */
object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp
}

object Size {
    /** Minimum touch target. */
    val touch = 48.dp
    val primaryButton = 56.dp
    val mic = 96.dp
    val gutter = 20.dp
}

/** Fixed colours for the capture panel, whose dark surface is part of the app's visual identity in both themes. */
object CapturePalette {
    val panel = Color(0xFF142720)
    val content = Color(0xFFF3F1E8)
    val muted = Color(0xFFB7C9BD)
    val signal = Color(0xFF91E2C8)
    val trace = Color(0xFF49645A)
    val outline = Color(0xFF39564B)
}

/** Brief, finite motion. Audio reactions use their own shorter duration. */
object Motion {
    const val quick = 160
    const val standard = 280
    const val slow = 420
    val enter = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/**
 * Shared semantic palette. [line] is decorative; interactive borders use [ink3].
 */
@Immutable
data class IncidentColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val attentionContainer: Color,
    val onAttention: Color,
    val dangerContainer: Color,
    val onDanger: Color,
    val danger: Color,
    val simulatedContainer: Color,
    val onSimulated: Color,
    val simulated: Color,
    val infoContainer: Color,
    val onInfo: Color,
    val isDark: Boolean,
)

val LightColors = IncidentColors(
    canvas = Color(0xFFF4F3EF), surface = Color(0xFFFBFAF7),
    ink = Color(0xFF17191C), ink2 = Color(0xFF4A4F57), ink3 = Color(0xFF626872), line = Color(0xFFC9C6BD),
    accent = Color(0xFF0B6E66), onAccent = Color(0xFFF4FBFA),
    accentContainer = Color(0xFFD5EBE7), onAccentContainer = Color(0xFF0A4F49),
    attentionContainer = Color(0xFFF8E7C8), onAttention = Color(0xFF7A4300),
    dangerContainer = Color(0xFFF9DEDA), onDanger = Color(0xFF9F1D15), danger = Color(0xFFB3261E),
    simulatedContainer = Color(0xFFF8DCE7), onSimulated = Color(0xFF9A0F45), simulated = Color(0xFFB5124F),
    infoContainer = Color(0xFFE3EEF7), onInfo = Color(0xFF12476F),
    isDark = false,
)

val DarkColors = IncidentColors(
    canvas = Color(0xFF0E1719), surface = Color(0xFF172426),
    ink = Color(0xFFF2F5EE), ink2 = Color(0xFFB9C8C5), ink3 = Color(0xFF91A7A2), line = Color(0xFF344849),
    accent = Color(0xFFC3EFCB), onAccent = Color(0xFF14271C),
    accentContainer = Color(0xFF243D32), onAccentContainer = Color(0xFFD0F3D6),
    attentionContainer = Color(0xFF352D20), onAttention = Color(0xFFECCB8D),
    dangerContainer = Color(0xFF3B2423), onDanger = Color(0xFFFFC4B8), danger = Color(0xFFFFA18C),
    simulatedContainer = Color(0xFF272B23), onSimulated = Color(0xFFD6C8A0), simulated = Color(0xFFECCB8D),
    infoContainer = Color(0xFF203238), onInfo = Color(0xFFB6DCE2),
    isDark = true,
)

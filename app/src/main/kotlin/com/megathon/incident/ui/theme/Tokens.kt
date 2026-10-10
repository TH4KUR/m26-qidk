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

/** Motion: 120 to 250 ms, ease-out entering, ease-in leaving, transform and opacity only. */
object Motion {
    const val quick = 120
    const val standard = 200
    const val slow = 250
    val enter = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/**
 * Palette (docs/ui/ui-design.md section 4). Every text pair is 4.5:1 or better; interactive outlines use [ink3] (3:1+);
 * [line] is decorative only. No pure black or white, no gradients.
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
    canvas = Color(0xFF101214), surface = Color(0xFF181B1E),
    ink = Color(0xFFECEAE6), ink2 = Color(0xFFB8BCC2), ink3 = Color(0xFF969CA5), line = Color(0xFF3A4047),
    accent = Color(0xFF58C4B7), onAccent = Color(0xFF06201D),
    accentContainer = Color(0xFF163B37), onAccentContainer = Color(0xFFA9E6DE),
    attentionContainer = Color(0xFF3A2A12), onAttention = Color(0xFFF2BE72),
    dangerContainer = Color(0xFF3F1512), onDanger = Color(0xFFFFB4AB), danger = Color(0xFFFF8A80),
    simulatedContainer = Color(0xFF3F1226), onSimulated = Color(0xFFFFB1CC), simulated = Color(0xFFFF6FA3),
    infoContainer = Color(0xFF14324A), onInfo = Color(0xFFA9D2F2),
    isDark = true,
)

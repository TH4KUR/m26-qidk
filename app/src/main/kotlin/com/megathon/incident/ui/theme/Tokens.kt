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
    canvas = Color(0xFF0E1719), surface = Color(0xFF1D2E30),
    ink = Color(0xFFF2F5EE), ink2 = Color(0xFFB9C8C5), ink3 = Color(0xFF91A7A2), line = Color(0xFF344849),
    accent = Color(0xFFC3EFCB), onAccent = Color(0xFF14271C),
    accentContainer = Color(0xFF243D32), onAccentContainer = Color(0xFFD0F3D6),
    attentionContainer = Color(0xFF352D20), onAttention = Color(0xFFECCB8D),
    dangerContainer = Color(0xFF3B2423), onDanger = Color(0xFFFFC4B8), danger = Color(0xFFFFA18C),
    simulatedContainer = Color(0xFF272B23), onSimulated = Color(0xFFD6C8A0), simulated = Color(0xFFECCB8D),
    infoContainer = Color(0xFF203238), onInfo = Color(0xFFB6DCE2),
    isDark = true,
)

/*
 * The Hindi palette: burgundy in place of the English teal. Every IncidentColors role is defined again, light and dark,
 * and tools/theme_contrast_check.py parses the hex values in this file and fails if any text or control pair drops below WCAG 2.1 AA.
 * Burgundy is the brand of this palette (accent, primary button, confirmed pill). Danger is vermilion, a different hue and a much
 * lighter tone than burgundy, and simulated is violet, so neither can be read as "the accent" (they also always carry an icon and a word).
 * The amber evidence band (BrandMark, colours.xml) is not a palette role and is untouched.
 * Details and the measured table: docs/ui/theme-burgundy.md.
 */
val HindiLightColors = IncidentColors(
    canvas = Color(0xFFF8F2F1), surface = Color(0xFFFDFAF9),
    ink = Color(0xFF1F1519), ink2 = Color(0xFF54464C), ink3 = Color(0xFF6A5A61), line = Color(0xFFDAC9CD),
    accent = Color(0xFF7A1F3D), onAccent = Color(0xFFFFF7F9),
    accentContainer = Color(0xFFF4DDE5), onAccentContainer = Color(0xFF5A1229),
    attentionContainer = Color(0xFFF8E7C8), onAttention = Color(0xFF7A4300),
    dangerContainer = Color(0xFFFBE1D6), onDanger = Color(0xFF953000), danger = Color(0xFFB03A00),
    simulatedContainer = Color(0xFFE9E0F7), onSimulated = Color(0xFF4A1D8F), simulated = Color(0xFF5B2AA8),
    infoContainer = Color(0xFFE3EEF7), onInfo = Color(0xFF12476F),
    isDark = false,
)

val HindiDarkColors = IncidentColors(
    canvas = Color(0xFF1A0F13), surface = Color(0xFF2A1A20),
    ink = Color(0xFFF7EFF1), ink2 = Color(0xFFD4C2C8), ink3 = Color(0xFFAE9AA2), line = Color(0xFF4B343B),
    accent = Color(0xFFF2B8C8), onAccent = Color(0xFF3B0A1D),
    accentContainer = Color(0xFF4D1B2D), onAccentContainer = Color(0xFFF8D5E0),
    attentionContainer = Color(0xFF352D20), onAttention = Color(0xFFECCB8D),
    dangerContainer = Color(0xFF3E1D12), onDanger = Color(0xFFFFC9B3), danger = Color(0xFFFF8040),
    simulatedContainer = Color(0xFF2E2346), onSimulated = Color(0xFFD9CCFF), simulated = Color(0xFFC3A8FF),
    infoContainer = Color(0xFF1F2D3D), onInfo = Color(0xFFB9D7F2),
    isDark = true,
)

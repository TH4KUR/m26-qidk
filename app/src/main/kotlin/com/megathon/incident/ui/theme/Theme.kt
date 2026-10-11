package com.megathon.incident.ui.theme

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LocalColors = staticCompositionLocalOf { LightColors }

/** System animator scale zero disables all app motion. Read when the activity is composed. */
val LocalReduceMotion = compositionLocalOf { false }

object Theme {
    val colors: IncidentColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
}

/** System typography: generous headings and compact, readable report details. */
private val AppTypography = Typography(
    displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun IncidentTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
            background = c.canvas, onBackground = c.ink, surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.ink2,
            surfaceVariant = c.surface, outline = c.ink3, outlineVariant = c.line, error = c.danger, onError = c.canvas,
            errorContainer = c.dangerContainer, onErrorContainer = c.onDanger,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
            background = c.canvas, onBackground = c.ink, surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.ink2,
            surfaceVariant = c.surface, outline = c.ink3, outlineVariant = c.line, error = c.danger, onError = c.surface,
            errorContainer = c.dangerContainer, onErrorContainer = c.onDanger,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        )
    }
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val context = LocalContext.current
    val reduce = remember { animationsOff(context) }
    CompositionLocalProvider(LocalColors provides c, LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

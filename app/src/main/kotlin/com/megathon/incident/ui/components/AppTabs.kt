package com.megathon.incident.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.megathon.incident.ui.theme.Theme

/**
 * The two places in the app: tell a new report, or read the reports already saved on this device. Shown only when nothing is
 * in progress (no recording, no question turn), so it can never cut one off.
 */
@Composable
fun AppTabs(selected: Int, onNewReport: () -> Unit, onReports: () -> Unit) {
    val c = Theme.colors
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = c.onAccentContainer, selectedTextColor = c.ink,
        indicatorColor = c.accentContainer, unselectedIconColor = c.ink3, unselectedTextColor = c.ink3,
    )
    NavigationBar(containerColor = c.canvas, contentColor = c.ink, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selected == 0, onClick = onNewReport, colors = colors,
            icon = { MicGlyph(if (selected == 0) c.onAccentContainer else c.ink3, size = 24.dp) },
            label = { Text("New report") },
        )
        NavigationBarItem(
            selected = selected == 1, onClick = onReports, colors = colors,
            icon = { ListGlyph(if (selected == 1) c.onAccentContainer else c.ink3, size = 24.dp) },
            label = { Text("Reports") },
        )
    }
}

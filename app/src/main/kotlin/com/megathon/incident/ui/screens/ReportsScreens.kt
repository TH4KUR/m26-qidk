package com.megathon.incident.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.Field
import com.megathon.incident.session.Labels
import com.megathon.incident.session.ReportListRow
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.components.AppTabs
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.Hairline
import com.megathon.incident.ui.components.Panel
import com.megathon.incident.ui.components.Pill
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAVED_AT = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"))

/** Every report saved on this device, newest first. Offline by construction: it only reads the app's own files. */
@Composable
fun ReportsScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar("Reports", onProof = vm::openProof, simulatedNote = ui.simulatedNote) },
        bottomBar = { AppTabs(selected = 1, onNewReport = vm::closeReports, onReports = {}) },
    ) { pad ->
        if (ui.savedReports.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No reports yet", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(
                    "Reports you save appear here. They stay on this device.",
                    style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.padding(top = Space.s2),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
                contentPadding = PaddingValues(vertical = Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                items(ui.savedReports, key = { it.id }) { row -> ReportRow(row) { vm.openSavedReport(row.id) } }
            }
        }
    }
}

@Composable
private fun ReportRow(row: ReportListRow, onOpen: () -> Unit) {
    val c = Theme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClickLabel = "Open this report", onClick = onOpen)
            .heightIn(min = Size.touch)
            .padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            Pill(row.status, c.accentContainer, c.onAccentContainer)
        }
        val where = listOfNotNull(row.place, row.whenText).joinToString(" · ")
        if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodyLarge, color = c.ink2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            row.severity?.let { Pill("Severity: $it", c.attentionContainer, c.onAttention) }
            Text("Saved " + SAVED_AT.format(Instant.ofEpochMilli(row.savedAtEpochMs)), style = MaterialTheme.typography.labelSmall, color = c.ink3)
        }
    }
}

/** One saved report, read-only. Nothing here can change it; it is the file as it was saved. */
@Composable
fun SavedReportScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val report = ui.openedReport
    val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar("Saved report", onProof = vm::openProof, onBack = vm::closeSavedReport, simulatedNote = ui.simulatedNote) },
    ) { pad ->
        val rows = report?.let { r ->
            Field.entries.mapNotNull { f -> Labels.display(f, r.value(f).text, today)?.let { Labels.fieldLabel(f) to it } }
        }.orEmpty()
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
            contentPadding = PaddingValues(vertical = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            item {
                Panel("Saved on this device") {
                    if (rows.isEmpty()) Text("This report has no details.", style = MaterialTheme.typography.bodyLarge, color = c.ink3, modifier = Modifier.padding(vertical = Space.s3))
                    rows.forEachIndexed { i, (label, value) ->
                        if (i > 0) Hairline()
                        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s1, vertical = Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2)
                            Text(value, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                        }
                    }
                }
            }
        }
    }
}

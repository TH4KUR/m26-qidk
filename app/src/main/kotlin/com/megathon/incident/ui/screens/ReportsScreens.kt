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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.domain.Field
import com.megathon.incident.session.HindiDate
import com.megathon.incident.session.HindiLabels
import com.megathon.incident.session.HindiSavedAt
import com.megathon.incident.session.Labels
import com.megathon.incident.session.ReportListRow
import com.megathon.incident.session.UiStatus
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.components.AppTabs
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.Hairline
import com.megathon.incident.ui.components.Panel
import com.megathon.incident.ui.components.Pill
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
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
        topBar = { AppTopBar(tr("Reports", "रिपोर्ट"), onProof = vm::openProof, simulatedNote = ui.simulatedNote) },
        bottomBar = { AppTabs(selected = 1, onNewReport = vm::closeReports, onReports = {}) },
    ) { pad ->
        if (ui.savedReports.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tr("No reports yet", "अभी कोई रिपोर्ट नहीं"), style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(
                    tr("Reports you save appear here. They stay on this device.", "आपकी सहेजी रिपोर्ट यहाँ दिखेंगी। वे इसी डिवाइस पर रहती हैं।"),
                    style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.padding(top = Space.s2),
                )
            }
        } else {
            val today = remember { LocalDate.now(ZoneId.of("Asia/Kolkata")) }
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
                contentPadding = PaddingValues(vertical = Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                items(ui.savedReports, key = { it.id }) { row -> ReportRow(row, today) { vm.openSavedReport(row.id) } }
            }
        }
    }
}

@Composable
private fun ReportRow(row: ReportListRow, today: LocalDate, onOpen: () -> Unit) {
    val c = Theme.colors
    val hindi = isHindi()
    // In Hindi the type, severity and date are said from the stored values; what the reporter said (the place) is shown as it was.
    val title = if (hindi) row.typeWire?.let { HindiLabels.type(it) } ?: "घटना की रिपोर्ट" else row.title
    val whenText = if (hindi) row.whenRaw?.let { HindiDate.friendly(it, today) } else row.whenText
    val severity = if (hindi) {
        row.severityWire?.let { w ->
            HindiLabels.severity(w) + (if (row.severityIsSuggested) " (${HindiLabels.status(UiStatus.SUGGESTED).orEmpty()})" else "")
        }
    } else row.severity
    val status = if (hindi && row.status == "Saved") "सहेजी गई" else row.status
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClickLabel = tr("Open this report", "यह रिपोर्ट खोलें"), onClick = onOpen)
            .heightIn(min = Size.touch)
            .padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            Pill(status, c.accentContainer, c.onAccentContainer)
        }
        val where = listOfNotNull(row.place, whenText).joinToString(" · ")
        if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodyLarge, color = c.ink2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            severity?.let { Pill(tr("Severity: $it", "गंभीरता: $it"), c.attentionContainer, c.onAttention) }
            val savedAt = if (hindi) HindiSavedAt.format(row.savedAtEpochMs) + " सहेजी गई" else "Saved " + SAVED_AT.format(Instant.ofEpochMilli(row.savedAtEpochMs))
            Text(savedAt, style = MaterialTheme.typography.labelSmall, color = c.ink3)
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
        topBar = { AppTopBar(tr("Saved report", "सहेजी हुई रिपोर्ट"), onProof = vm::openProof, onBack = vm::closeSavedReport, simulatedNote = ui.simulatedNote) },
    ) { pad ->
        val hindi = isHindi()
        val rows = report?.let { r ->
            Field.entries.mapNotNull { f ->
                if (hindi) HindiLabels.display(f, r.value(f).text, today)?.let { HindiLabels.field(f) to it }
                else Labels.display(f, r.value(f).text, today)?.let { Labels.fieldLabel(f) to it }
            }
        }.orEmpty()
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = Size.gutter),
            contentPadding = PaddingValues(vertical = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            item {
                Panel(tr("Saved on this device", "इस डिवाइस पर सहेजी गई रिपोर्ट")) {
                    if (rows.isEmpty()) Text(tr("This report has no details.", "इस रिपोर्ट में कोई ब्यौरा नहीं है।"), style = MaterialTheme.typography.bodyLarge, color = c.ink3, modifier = Modifier.padding(vertical = Space.s3))
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

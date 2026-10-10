package com.megathon.incident.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.megathon.incident.session.UiStatus
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.Card
import com.megathon.incident.ui.components.ComputePill
import com.megathon.incident.ui.components.LevelBars
import com.megathon.incident.ui.components.MessageCard
import com.megathon.incident.ui.components.MicButton
import com.megathon.incident.ui.components.Pill
import com.megathon.incident.ui.components.PrivacyLine
import com.megathon.incident.ui.components.TranscriptCard
import com.megathon.incident.ui.components.formatClock
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/** The way in: one invitation, one button. Becomes the listening and working view without leaving the screen. */
@Composable
fun HomeScreen(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(containerColor = c.canvas, topBar = { AppTopBar("Incident report", onProof = vm::openProof, brand = true, simulatedNote = ui.simulatedNote) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (landscape) {
                // A projector or a landscape screen is short: text on the left, the mic always on screen at the right.
                Row(Modifier.weight(1f).padding(horizontal = Size.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.s4, Alignment.CenterVertically),
                    ) {
                        Notices(ui, vm)
                        HomeText(ui, centered = false)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        HomeMic(ui, vm)
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(Space.s4))
                    Notices(ui, vm)
                    HomeText(ui, centered = true)
                    Spacer(Modifier.height(Space.s6))
                    HomeMic(ui, vm)
                    if (!ui.listening && !ui.processing) { Spacer(Modifier.height(Space.s6)); ExampleCard() }
                    Spacer(Modifier.height(Space.s6))
                }
            }
            Footer(ui)
        }
    }
}

@Composable
private fun Notices(ui: AppUi, vm: AppViewModel) {
    ui.blocked?.let { Blocked(it); Spacer(Modifier.height(Space.s4)) }
    ui.message?.let { MessageCard(it, vm::dismissMessage); Spacer(Modifier.height(Space.s4)) }
}

/** The words: an invitation, or what is happening now. */
@Composable
private fun HomeText(ui: AppUi, centered: Boolean) {
    val c = Theme.colors
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val h = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(horizontalAlignment = h, verticalArrangement = Arrangement.spacedBy(Space.s3), modifier = if (centered) Modifier.fillMaxWidth() else Modifier) {
        when {
            ui.listening -> {
                Pill("Recording on this device", c.dangerContainer, c.onDanger, glyph = {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(c.onDanger, CircleShape))
                })
                Text("Listening…", style = MaterialTheme.typography.displayMedium, textAlign = align, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
                Text(formatClock(ui.elapsedMs), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = c.ink2)
            }
            ui.processing -> {
                Text(
                    if (ui.heardNow == null) "Got it." else "Heard you.", style = MaterialTheme.typography.displayMedium, textAlign = align,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
                TranscriptCard(transcripts = emptyList(), highlight = null, heardNow = ui.heardNow, reading = true)
            }
            else -> {
                Text("Tell me what happened.", style = MaterialTheme.typography.displayMedium, textAlign = align, modifier = Modifier.semantics { heading() })
                Text("Just speak. I'll fill in the report and ask only what's missing.", style = MaterialTheme.typography.bodyLarge, color = c.ink2, textAlign = align)
                if (!centered) ExampleCard()
            }
        }
    }
}

/** The one control: start, stop, or working. Always in view. */
@Composable
private fun HomeMic(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (ui.listening) {
            LevelBars(ui.levels)
            Spacer(Modifier.height(Space.s4))
        }
        MicButton(
            listening = ui.listening, busy = ui.processing, level = ui.levels.lastOrNull() ?: 0f,
            onClick = vm::onMicTapped,
        )
        Text(
            when { ui.listening -> "Tap when you're done"; ui.processing -> "Reading it…"; else -> "Tap to start" },
            style = MaterialTheme.typography.labelLarge, color = c.ink2,
        )
    }
}

@Composable
private fun ExampleCard() {
    val c = Theme.colors
    Card {
        Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text("For example", style = MaterialTheme.typography.labelMedium, color = c.ink2)
            Text("“${FakeLlmEngine.DECK_TRANSCRIPT}”", style = MaterialTheme.typography.bodyLarge, color = c.ink)
        }
    }
}

@Composable
private fun Footer(ui: AppUi) {
    val c = Theme.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Size.gutter, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        PrivacyLine(ui.network)
    }
}

@Composable
private fun Blocked(message: String) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().background(c.dangerContainer, RoundedCornerShape(16.dp)).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text("This build can't start", style = MaterialTheme.typography.titleMedium, color = c.onDanger, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = c.onDanger)
    }
}

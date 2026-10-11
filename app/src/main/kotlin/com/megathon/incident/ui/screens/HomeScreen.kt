package com.megathon.incident.ui.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.megathon.incident.speech.HindiLaneNotice
import com.megathon.incident.session.HindiSavedAt
import com.megathon.incident.session.HindiUiText
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import com.megathon.incident.ui.components.*
import com.megathon.incident.ui.theme.LocalReduceMotion
import com.megathon.incident.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megathon.incident.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.AppViewModel
import com.megathon.incident.ui.Readiness
import com.megathon.incident.ui.components.AppTopBar
import com.megathon.incident.ui.components.AudioSignature
import com.megathon.incident.ui.components.MessageCard
import com.megathon.incident.ui.components.MicButton
import com.megathon.incident.ui.components.PrivacyLine
import com.megathon.incident.ui.components.ProvisionalCard
import com.megathon.incident.ui.components.TranscriptCard
import com.megathon.incident.ui.components.WorkingBar
import com.megathon.incident.ui.components.formatClock
import androidx.compose.foundation.layout.heightIn
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.CapturePalette
import com.megathon.incident.ui.theme.Theme

/** One clear invitation, one microphone, and a live preview that never hides that the draft is provisional. */
@Composable
fun HomeScreen(ui: AppUi, vm: AppViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val c = Theme.colors
    Scaffold(
        containerColor = c.canvas,
        topBar = { AppTopBar(tr(stringResource(R.string.app_tagline), "बोल दो, बयान हो गया।"), onProof = vm::openProof, brand = true, simulatedNote = ui.simulatedNote) },
        bottomBar = { if (!ui.listening && !ui.processing) AppTabs(selected = 0, onNewReport = {}, onReports = vm::openReports) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (landscape) {
                Row(
                    Modifier.weight(1f).padding(horizontal = Size.gutter, vertical = Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Space.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.s4, Alignment.CenterVertically),
                    ) {
                        Notices(ui, vm)
                        HomeCopy(ui)
                        if (!ui.listening && !ui.processing) ExampleNote()
                        LiveDetails(ui)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        StudioPanel(ui, vm, compact = true)
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Size.gutter),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Spacer(Modifier.height(Space.s2))
                    Notices(ui, vm)
                    HomeCopy(ui)
                    Spacer(Modifier.height(Space.s4))
                    StudioPanel(ui, vm)
                    // The example row is a separate, lesser action: a clear gap keeps it from reading as part of the mic's label.
                    Spacer(Modifier.height(Space.s8))
                    if (ui.listening || ui.processing) LiveDetails(ui) else ExampleNote()
                    Spacer(Modifier.height(Space.s4))
                }
            }
            Footer(ui, vm)
        }
    }
}

@Composable
private fun Notices(ui: AppUi, vm: AppViewModel) {
    ui.blocked?.let { Blocked(it); Spacer(Modifier.height(Space.s3)) }
    ui.message?.let { MessageCard(if (isHindi()) HindiUiText.of(it) else it, vm::dismissMessage); Spacer(Modifier.height(Space.s3)) }
}

@Composable
private fun HomeCopy(ui: AppUi) {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val mode = if (ui.listening) 1 else if (ui.processing) 2 else 0
    AnimatedContent(mode, transitionSpec = {
        (fadeIn(tween(if (reduce) 0 else Motion.standard)) + slideInVertically(tween(if (reduce) 0 else Motion.standard)) { it / 8 }) togetherWith fadeOut(tween(if (reduce) 0 else Motion.quick))
    }, label = "capture copy") { state ->
        // One headline, one supporting line: the status under the microphone says the rest.
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            val first = when (state) {
                1 -> tr("Your voice.\n", "आपकी आवाज़।\n")
                2 -> tr("A little clarity.\n", "थोड़ी स्पष्टता।\n")
                else -> tr("A moment.\n", "एक पल।\n")
            }
            val second = when (state) {
                1 -> tr("Taking shape.", "आकार ले रही है।")
                2 -> tr("Coming together.", "बात बन रही है।")
                else -> tr("A clear report.", "एक साफ़ रिपोर्ट।")
            }
            Text(buildAnnotatedString {
                append(first)
                withStyle(SpanStyle(color = c.accent)) { append(second) }
            }, style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
            Text(when (state) {
                1 -> tr("Tell it as it happened. Tap to finish when you’re ready.", "जैसा हुआ, वैसा बताइए। पूरा होने पर टैप करें।")
                2 -> tr("Organizing your words, right here on this device.", "आपकी बात इसी डिवाइस पर व्यवस्थित हो रही है।")
                else -> tr("Tell it as it happened. We’ll take care of the structure.", "जैसा हुआ, वैसा बताइए। ढाँचा हम संभाल लेंगे।")
            }, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = c.ink2)
        }
    }
}

@Composable
private fun StudioPanel(ui: AppUi, vm: AppViewModel, compact: Boolean = false) {
    val c = Theme.colors
    // A status line only while it carries news (a timer, work in progress). At rest the one instruction is enough.
    val status = when {
        ui.listening -> tr("LISTENING  ·  ${formatClock(ui.elapsedMs)}", "सुन रहे हैं  ·  ${HindiSavedAt.digits(formatClock(ui.elapsedMs))}")
        ui.processing -> tr("SHAPING YOUR REPORT", "रिपोर्ट बन रही है")
        ui.readiness == Readiness.LOADING -> tr("MODEL WARMING UP", "मॉडल तैयार हो रहा है")
        else -> null
    }
    val warming = ui.readiness == Readiness.LOADING && !ui.listening && !ui.processing
    Reveal(delayMillis = 100) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RecorderAura(
                ui.listening, ui.levels.lastOrNull() ?: 0f,
                busy = ui.processing || warming, diameter = if (compact) 140.dp else 208.dp,
            ) {
                MicButton(ui.listening, ui.processing, ui.levels.lastOrNull() ?: 0f, vm::onMicTapped, diameter = if (compact) 68.dp else 104.dp)
            }
            // Air between the microphone and the words under it, so the instruction does not sit on the aura's outer ring.
            Spacer(Modifier.height(if (compact) Space.s2 else Space.s6))
            if (status != null) {
                Text(status, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = if (ui.listening) c.danger else c.accent)
                Spacer(Modifier.height(Space.s1))
            }
            Text(when {
                ui.listening -> tr("Tap to finish your story", "अपनी बात पूरी करने के लिए टैप करें")
                ui.processing -> tr("A few details. One clear report.", "कुछ ब्यौरे। एक साफ़ रिपोर्ट।")
                warming -> tr("You can start while it warms up", "तैयार होते समय भी आप शुरू कर सकते हैं")
                else -> tr("Tap to tell your story", "अपनी बात बताने के लिए टैप करें")
            }, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, color = c.ink)
            if (warming) {
                Spacer(Modifier.height(Space.s2))
                WorkingBar()
            }
            if (ui.listening) AudioSignature(ui.levels, true, Modifier.fillMaxWidth(.65f).height(32.dp).padding(top = Space.s2))
        }
    }
}

@Composable
private fun LiveDetails(ui: AppUi) {
    if (ui.listening) ui.liveText?.let { text ->
        Spacer(Modifier.height(Space.s3))
        TranscriptCard(transcripts = emptyList(), highlight = null, heardNow = text, reading = false)
    }
    if (ui.listening) {
        Spacer(Modifier.height(Space.s2))
        ProvisionalCard(ui.session.provisional)
    }
    if (ui.processing) {
        Spacer(Modifier.height(Space.s3))
        TranscriptCard(
            transcripts = emptyList(), highlight = null, heardNow = ui.heardNow ?: ui.liveText, reading = true,
            readingLabel = if (ui.heardNow == null) tr("Transcribing your recording…", "आपकी रिकॉर्डिंग लिखी जा रही है…") else tr("Organizing the report…", "रिपोर्ट व्यवस्थित हो रही है…"),
            // The note says labels and questions stay English, which is not true while the whole app is in Hindi.
            scriptNote = if (isHindi()) null else ui.scriptNote,
        )
    }
}

@Composable
private fun ExampleNote() {
    val c = Theme.colors
    val reduce = LocalReduceMotion.current
    var open by remember { mutableStateOf(false) }
    Card {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Size.touch).clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(horizontal = Space.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (open) tr("No special format. Just say what happened.", "कोई खास तरीका नहीं। बस बताइए क्या हुआ।")
                else tr("Not sure where to start? See a quick example", "शुरू कैसे करें, पता नहीं? एक छोटा उदाहरण देखें"),
                style = MaterialTheme.typography.labelLarge, color = c.ink2, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.KeyboardArrowDown, if (open) tr("Hide example", "उदाहरण छिपाएँ") else tr("Show example", "उदाहरण दिखाएँ"), tint = c.accent)
        }
        AnimatedVisibility(open, enter = fadeIn(tween(if (reduce) 0 else Motion.standard)) + expandVertically(tween(if (reduce) 0 else Motion.standard)), exit = fadeOut(tween(if (reduce) 0 else Motion.quick)) + shrinkVertically(tween(if (reduce) 0 else Motion.standard))) {
            val example = tr(FakeLlmEngine.DECK_TRANSCRIPT, "कॉन्फ़्रेंस रूम २ के पास सुबह लगभग १० बजे पानी का रिसाव हो गया। मेंटेनेंस ने वह जगह पहले ही बंद कर दी है।")
            Text("“$example”", Modifier.padding(start = Space.s1, end = Space.s1, bottom = Space.s3), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
        }
    }
}

@Composable
private fun Footer(ui: AppUi, vm: AppViewModel) {
    val c = Theme.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Size.gutter, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        if (!ui.listening && !ui.processing) HindiLaneSwitch(ui.hindiLane, vm::setHindiLane)
        PrivacyLine(ui.network)
    }
}

/**
 * The experimental Hindi lane, off by default. Small and plainly labelled: it changes only which recogniser hears the
 * next utterance. The whole row is the switch, so a screen reader announces it once with its state.
 */
@Composable
private fun HindiLaneSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Theme.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(vertical = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Text(tr(HindiLaneNotice.SWITCH_LABEL, "हिन्दी (प्रायोगिक)"), style = MaterialTheme.typography.labelLarge, color = c.ink2, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun Blocked(message: String) {
    val c = Theme.colors
    // The reason is a sentence the app built. When it has no Hindi, the whole card stays English rather than mixing the two.
    val inHindi = isHindi() && HindiUiText.of(message) != message
    val reason = if (inHindi) HindiUiText.of(message) else message
    Column(
        Modifier.fillMaxWidth().background(c.dangerContainer, RoundedCornerShape(16.dp)).padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            androidx.compose.material3.Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.onDanger)
            Text(if (inHindi) "यह बिल्ड शुरू नहीं हो सकता" else "This build can’t start", style = MaterialTheme.typography.titleMedium, color = c.onDanger, fontWeight = FontWeight.SemiBold)
        }
        Text(reason, style = MaterialTheme.typography.bodyLarge, color = c.onDanger)
    }
}

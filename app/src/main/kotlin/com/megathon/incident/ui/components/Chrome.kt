package com.megathon.incident.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.megathon.incident.R
import com.megathon.incident.device.NetworkProof
import com.megathon.incident.session.HindiSavedAt
import com.megathon.incident.session.HindiUiText
import com.megathon.incident.ui.i18n.isHindi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.testing.Compute
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/**
 * A top bar that grows with the text size (a fixed-height bar clips the title at large font scales). When any part of the
 * run is simulated, a slim full-width strip sits directly under it, on every screen, and can never be squeezed out.
 */
@Composable
fun AppTopBar(title: String, onProof: (() -> Unit)?, onBack: (() -> Unit)? = null, brand: Boolean = false, simulatedNote: String? = null) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().background(c.canvas).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = if (onBack != null) Space.s1 else Size.gutter, end = Space.s2), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack, Modifier.size(Size.touch)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस"), tint = c.ink) }
            BrandMark(34.dp)
            Column(
                Modifier.weight(1f).padding(start = Space.s3, top = Space.s2, bottom = Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                val appName = stringResource(R.string.app_name)
                // The wordmark is sized in sp, so it grows with the text size and the line under it can never sit on it.
                val wordmarkHeight = with(LocalDensity.current) { 20.sp.toDp() }
                BrandWordmark(c.ink, height = wordmarkHeight, modifier = Modifier.semantics { contentDescription = appName; heading() })
                Text(title, style = MaterialTheme.typography.labelSmall, color = c.ink3)
            }
            if (onProof != null) IconButton(onProof, Modifier.size(Size.touch).background(c.surface, CircleShape)) { Icon(Icons.Rounded.Info, tr("How this ran", "यह कैसे चला"), tint = c.ink2, modifier = Modifier.size(20.dp)) }
        }
        if (simulatedNote != null) {
            // The note is a sentence the app built. When it has no Hindi, the whole strip stays English rather than mixing the two.
            val hindiNote = if (isHindi()) simulatedNoteInHindi(simulatedNote) else null
            val shown = hindiNote ?: simulatedNote
            val said = if (hindiNote != null) "नकली। $shown।" else "Simulated. $shown."
            Row(Modifier.fillMaxWidth().padding(horizontal = Size.gutter).background(c.simulatedContainer, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(horizontal = Space.s3, vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = said },
                horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = c.onSimulated, modifier = Modifier.size(14.dp))
                Text(if (hindiNote != null) "नकली · $shown" else "SIMULATED · $shown", style = MaterialTheme.typography.labelSmall, color = c.onSimulated)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = Space.s2))
        }
    }
}

/** The one-line privacy promise, backed by a measured network state. */
@Composable
fun PrivacyLine(network: NetworkProof, modifier: Modifier = Modifier) {
    val c = Theme.colors
    // Centred under the content: it is a statement about the whole screen, not a label for the row above it.
    Row(
        modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.ink2, modifier = Modifier.size(16.dp))
        val hindi = isHindi()
        val net = when {
            network.airplaneMode -> tr("Airplane mode on", "एयरप्लेन मोड चालू")
            network.bytesSent != null -> tr("${formatBytes(network.bytesSent)} sent", "${formatBytes(network.bytesSent, hindi)} भेजा गया")
            else -> tr("Network on", "नेटवर्क चालू")
        }
        Text(tr("On-device · $net", "इसी डिवाइस पर · $net"), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
    }
}

/** The size as text. With [hindi] the units are Hindi words and the digits Devanagari; the default is the English text, unchanged. */
fun formatBytes(b: Long?, hindi: Boolean = false): String {
    if (b == null) return if (hindi) "उपलब्ध नहीं" else "not available"
    if (hindi) {
        return HindiSavedAt.digits(
            when {
                b < 1024 -> "$b\u00A0बाइट"
                b < 1024 * 1024 -> "%.1f\u00A0केबी".format(b / 1024.0)
                else -> "%.1f\u00A0एमबी".format(b / 1024.0 / 1024.0)
            },
        )
    }
    return when {
        b < 1024 -> "$b\u00A0B"
        b < 1024 * 1024 -> "%.1f\u00A0KB".format(b / 1024.0)
        else -> "%.1f\u00A0MB".format(b / 1024.0 / 1024.0)
    }
}

/** The Hindi of a "simulated" note, or null when there is none: the table of the core first, then the three notes the app writes itself. */
private fun simulatedNoteInHindi(note: String): String? {
    HindiUiText.of(note).takeIf { it != note }?.let { return it }
    return when (note) {
        "scripted demo, not live recognition" -> "स्क्रिप्ट वाला डेमो, असली पहचान नहीं"
        "speech is scripted, not live recognition" -> "आवाज़ स्क्रिप्ट से आ रही है, असली पहचान नहीं"
        "speech is live, but the report is filled by canned replies, not a live model" ->
            "आवाज़ असली है, पर रिपोर्ट पहले से तय जवाबों से भरी गई है, असली मॉडल से नहीं"
        else -> null
    }
}

fun formatClock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

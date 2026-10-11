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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megathon.incident.R
import com.megathon.incident.device.NetworkProof
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
            if (onBack != null) IconButton(onBack, Modifier.size(Size.touch)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.ink) }
            BrandMark(34.dp)
            Column(Modifier.weight(1f).padding(start = Space.s3, top = Space.s2, bottom = Space.s2)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(title, style = MaterialTheme.typography.labelSmall, color = c.ink3)
            }
            if (onProof != null) IconButton(onProof, Modifier.size(Size.touch).background(c.surface, CircleShape)) { Icon(Icons.Rounded.Info, "How this ran", tint = c.ink2, modifier = Modifier.size(20.dp)) }
        }
        if (simulatedNote != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Size.gutter).background(c.simulatedContainer, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(horizontal = Space.s3, vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "Simulated. $simulatedNote." },
                horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = c.onSimulated, modifier = Modifier.size(14.dp))
                Text("SIMULATED · $simulatedNote", style = MaterialTheme.typography.labelSmall, color = c.onSimulated)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = Space.s2))
        }
    }
}

/** The one-line privacy promise, backed by a measured network state. */
@Composable
fun PrivacyLine(network: NetworkProof, modifier: Modifier = Modifier) {
    val c = Theme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.ink2, modifier = Modifier.size(16.dp))
        val net = when {
            network.airplaneMode -> "Airplane mode on"
            network.bytesSent != null -> "${formatBytes(network.bytesSent)} sent"
            else -> "Network on"
        }
        Text("On-device · $net", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
    }
}

fun formatBytes(b: Long?): String = when {
    b == null -> "not available"
    b < 1024 -> "$b\u00A0B"
    b < 1024 * 1024 -> "%.1f\u00A0KB".format(b / 1024.0)
    else -> "%.1f\u00A0MB".format(b / 1024.0 / 1024.0)
}

fun formatClock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

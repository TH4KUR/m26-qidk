package com.megathon.incident.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme

/** The team line shown under About. Decided by the user: the team number and the team name, both. */
const val TEAM_LABEL = "Team 087 - ReadyOrNot"

/**
 * About: who made it (the team number and name, as the team asked), the product, and the components that do the work on this
 * device. Everything listed here is what the app actually uses. Note for the submission mirror: the team name is on
 * tools/blindcheck.words, so `tools/mirror_to_submission.sh` refuses this file until that is resolved.
 */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    val c = Theme.colors
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Text(
            tr("About", "परिचय"), style = MaterialTheme.typography.titleMedium, color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Panel(null) {
            AboutRow(tr("Team", "टीम"), TEAM_LABEL)
            Hairline()
            AboutRow(tr("App", "ऐप"), "Bayaan" + (version?.let { " · $it" } ?: ""))
            Hairline()
            AboutRow(tr("English speech", "अंग्रेज़ी वाणी"), tr("Whisper-Small, on the NPU", "व्हिस्पर-स्मॉल, NPU पर"))
            Hairline()
            AboutRow(tr("Hindi speech (experimental)", "हिन्दी वाणी (प्रायोगिक)"), tr("Nemotron, on the NPU", "नेमोट्रॉन, NPU पर"))
            Hairline()
            AboutRow(tr("Voice tags", "आवाज़ के संकेत"), "SenseVoice")
            Hairline()
            AboutRow(tr("Language model", "भाषा मॉडल"), tr("Qwen3-4B-Instruct, on the NPU", "Qwen3-4B-Instruct, NPU पर"))
            Hairline()
            AboutRow(tr("Spoken replies", "बोले गए उत्तर"), tr("Supertonic or the Android voice, on the CPU", "Supertonic या एंड्रॉइड की आवाज़, CPU पर"))
        }
        Text(
            tr("Everything runs on this device. Nothing is sent anywhere.", "सब कुछ इसी डिवाइस पर चलता है। कुछ भी कहीं नहीं भेजा जाता।"),
            style = MaterialTheme.typography.bodyMedium, color = c.ink2,
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s3), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1.4f))
    }
}

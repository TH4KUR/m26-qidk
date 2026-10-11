package com.megathon.incident.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.megathon.incident.scoring.AudioScore
import com.megathon.incident.scoring.CaptureThresholds
import com.megathon.incident.scoring.GlobalSummary
import com.megathon.incident.scoring.RecentRow
import com.megathon.incident.scoring.ScoreTerm
import com.megathon.incident.scoring.VoiceTerm
import com.megathon.incident.scoring.VoiceTermKind
import com.megathon.incident.session.JournalEntry
import com.megathon.incident.session.Labels
import com.megathon.incident.session.StageRecord
import com.megathon.incident.session.ToolLogEntry
import com.megathon.incident.speech.HindiLaneNotice
import com.megathon.incident.testing.Compute
import com.megathon.incident.ui.AppUi
import com.megathon.incident.ui.i18n.tr
import com.megathon.incident.ui.theme.Size
import com.megathon.incident.ui.theme.Space
import com.megathon.incident.ui.theme.Theme
import java.util.Locale

/**
 * "How this ran", in two views switched by a visible control. "This session" is the report being worked on: where each
 * step ran, how its audio was scored (every term with its value, its rule and its points), the assistant's tool calls and
 * the network. "All sessions" is read from the log of every saved report on this device (numbers only, no text).
 * Everything here is read from real results; a measurement that is not available says "not measured".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofSheet(ui: AppUi, onClose: () -> Unit) {
    val c = Theme.colors
    // Opened with nothing happening in this session (from the Reports tab, say): start on the view that has something to show.
    var showAll by rememberSaveable { mutableStateOf(ui.session.stages.isEmpty() && ui.session.transcripts.isEmpty()) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
        contentColor = c.ink,
    ) {
        LazyColumn(
            Modifier.navigationBarsPadding().padding(horizontal = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s6),
        ) {
            item {
                Text(
                    tr("How this ran", "यह कैसे चला"),
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() },
                )
            }
            item { ViewSwitch(showAll) { showAll = it } }

            // The simulated note stays above both views: it is about how much of what follows can be trusted.
            if (ui.simulated || ui.session.stages.any { it.compute == Compute.SIMULATED }) {
                item {
                    Column(Modifier.fillMaxWidth().background(c.simulatedContainer, RoundedCornerShape(16.dp)).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        ComputePill(Compute.SIMULATED)
                        Text(
                            tr(
                                "Part of this run is simulated: ${ui.simulatedNote ?: "see the steps below"}. Simulated results are labelled wherever they appear.",
                                "इस रन का एक हिस्सा नकली (सिम्युलेटेड) है: ${ui.simulatedNote ?: "नीचे के चरण देखें"}। नकली नतीजों पर जहाँ भी वे दिखते हैं, लेबल लगा है।",
                            ),
                            style = MaterialTheme.typography.bodyLarge, color = c.onSimulated,
                        )
                    }
                }
            }

            if (showAll) allSessionsView(ui) else thisSessionView(ui)
        }
    }
}

// ---- the switch ---------------------------------------------------------------------------------------------------------------

/** Two big, labelled cells in one outlined bar. The chosen one is filled and carries a check, so it is never colour alone. */
@Composable
private fun ViewSwitch(showAll: Boolean, onChange: (Boolean) -> Unit) {
    val c = Theme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().border(1.5.dp, c.ink3, shape).clip(shape).selectableGroup()) {
        listOf(false to tr("This session", "यह सत्र"), true to tr("All sessions", "सभी सत्र")).forEach { (all, label) ->
            val on = showAll == all
            Box(
                Modifier.weight(1f).defaultMinSize(minHeight = Size.touch)
                    .background(if (on) c.accent else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onChange(all) })
                    .padding(horizontal = Space.s3, vertical = Space.s3),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onAccent else c.ink)
                }
            }
        }
    }
}

// ---- This session -------------------------------------------------------------------------------------------------------------

private fun LazyListScope.thisSessionView(ui: AppUi) {
    item { Section(tr("Where each step ran", "हर चरण कहाँ चला")) { Stages(ui.session.stages, ui.ttsOffline, ui.ttsNote) } }

    item { Section(tr("Where the work ran", "काम कहाँ हुआ")) { ComputeCounts(ui.session.stages.map { it.compute.name }) } }

    item { AudioScoreCard(ui.audioScore, ui.audioTurns) }

    // Only when the experimental Hindi lane is on or was used: says plainly that nothing about its accuracy is measured.
    if (ui.hindiLane || ui.session.stages.any { it.stage == "Hear" && it.engine.startsWith("Nemotron") }) {
        item { Section(tr("Hindi (experimental)", "हिन्दी (प्रयोगात्मक)")) { Muted(HindiLaneNotice.TEXT) } }
    }

    item { Section(tr("Voice tags (suggestions only)", "आवाज़ के टैग (केवल सुझाव)")) { VoiceTags(ui) } }

    item {
        Section(tr("Network", "नेटवर्क")) {
            Fact(tr("Airplane mode", "एयरप्लेन मोड"), if (ui.network.airplaneMode) tr("On", "चालू") else tr("Off", "बंद"))
            Fact(tr("Network available", "नेटवर्क उपलब्ध"), if (ui.network.networkAvailable) tr("Yes", "हाँ") else tr("No", "नहीं"))
            Fact(tr("Data sent by this app", "इस ऐप द्वारा भेजा गया डेटा"), formatBytes(ui.network.bytesSent))
            Text(
                tr("Reports are saved only on this device.", "रिपोर्ट केवल इसी डिवाइस पर सहेजी जाती हैं।"),
                style = MaterialTheme.typography.bodyMedium, color = Theme.colors.ink2,
            )
        }
    }

    item {
        Section(tr("What the assistant did", "सहायक ने क्या किया")) {
            if (ui.session.toolLog.isEmpty()) Muted(tr("Nothing yet.", "अभी कुछ नहीं।"))
        }
    }
    items(ui.session.toolLog) { ToolRow(it) }

    item {
        Section(tr("Changes you and the assistant made", "आपने और सहायक ने क्या बदला")) {
            if (ui.session.journal.isEmpty()) Muted(tr("No values have been changed.", "कोई मान नहीं बदला गया।"))
        }
    }
    items(ui.session.journal) { JournalRow(it) }

    item {
        Muted(
            tr("The original wording is kept for every change.", "हर बदलाव के साथ मूल शब्द सुरक्षित रखे जाते हैं।"),
            Modifier.padding(bottom = Space.s8),
        )
    }
}

// ---- All sessions -------------------------------------------------------------------------------------------------------------

private fun LazyListScope.allSessionsView(ui: AppUi) {
    item {
        Muted(
            tr(
                "Read from the log kept on this device: one line for every saved report, numbers only (no words, names or places). It is never sent anywhere.",
                "इस डिवाइस पर रखे लॉग से पढ़ा गया: हर सहेजी गई रिपोर्ट की एक पंक्ति, केवल संख्याएँ (कोई शब्द, नाम या स्थान नहीं)। यह कहीं भेजा नहीं जाता।",
            ),
        )
    }
    val g: GlobalSummary? = ui.globalLog
    if (g == null) {
        item { Muted(tr("Reading the log…", "लॉग पढ़ा जा रहा है…")) }
        return
    }
    item {
        Section(tr("Summary", "सारांश")) {
            Fact(tr("Reports saved", "सहेजी गई रिपोर्टें"), "${g.reports}")
            Fact(
                tr("Average audio score", "औसत ऑडियो स्कोर"),
                g.averageAudioScore?.let { "${String.format(Locale.ROOT, "%.0f", it)} / 100  (${g.scoredReports})" } ?: notMeasured(),
            )
            Fact(tr("Data sent by this app", "इस ऐप द्वारा भेजा गया डेटा"), formatBytes(ui.network.bytesSent))
            if (g.reports == 0) {
                Muted(
                    tr(
                        "No report has been saved yet. A line is added here each time you save one.",
                        "अभी कोई रिपोर्ट सहेजी नहीं गई है। हर बार रिपोर्ट सहेजने पर यहाँ एक पंक्ति जुड़ती है।",
                    ),
                )
            }
        }
    }
    if (g.reports == 0) return

    item {
        Section(tr("Typical time per step (median)", "हर चरण का सामान्य समय (मध्यिका)")) {
            g.stageMedians.forEach { m ->
                Fact(
                    stageName(m.stage),
                    if (m.medianMs == null) notMeasured() else "${formatMs(m.medianMs)}  (${m.count})",
                )
            }
            Muted(
                tr(
                    "The middle value over every timed run of the step; the number in brackets is how many runs.",
                    "उस चरण के हर समयबद्ध रन का बीच का मान; कोष्ठक की संख्या रन की गिनती है।",
                ),
            )
        }
    }

    item {
        val total = g.computeMix.sumOf { it.count }
        Section(tr("Where the work ran", "काम कहाँ हुआ")) {
            if (g.computeMix.isEmpty()) Muted(notMeasured())
            g.computeMix.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    ComputeLabel(m.compute)
                    Text(
                        "${m.count} ${tr("steps", "चरण")}  (${if (total > 0) m.count * 100 / total else 0}%)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }

    item {
        Section(tr("Latest reports", "हाल की रिपोर्टें")) {
            RecentHeader()
            g.recent.forEach { RecentLine(it) }
        }
    }
    item { Muted("", Modifier.padding(bottom = Space.s8)) }
}

@Composable
private fun RecentHeader() {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(tr("When", "कब"), Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, color = c.ink2)
        Text(tr("Audio", "ऑडियो"), Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium, color = c.ink2)
        Text(tr("Speech · Model", "वाणी · मॉडल"), Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium, color = c.ink2)
        Text(tr("Ran on", "कहाँ चला"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.ink2)
    }
}

/** One saved report on one line: when, its audio score, the speech and model times, and where it ran. */
@Composable
private fun RecentLine(r: RecentRow) {
    val c = Theme.colors
    val whenText = remember(r.atEpochMs) {
        java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm")
            .format(java.time.Instant.ofEpochMilli(r.atEpochMs).atZone(java.time.ZoneId.systemDefault()))
    }
    val num = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Row(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(horizontal = Space.s3, vertical = Space.s2),
        horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(whenText, Modifier.weight(1.3f), style = num)
        Text(r.audioScore?.toString() ?: "–", Modifier.weight(0.7f), style = num)
        Text("${formatMs(r.hearMs)} · ${formatMs(r.readMs)}", Modifier.weight(1.5f), style = num)
        Text(r.compute.ifEmpty { "–" }, Modifier.weight(1f), style = num)
    }
}

// ---- the audio score card -----------------------------------------------------------------------------------------------------

@Composable
private fun AudioScoreCard(score: AudioScore?, turns: Int) {
    val c = Theme.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(
                tr("Audio score", "ऑडियो स्कोर"), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            Pill(tr("Heuristic", "अनुमान-आधारित"), c.attentionContainer, c.onAttention)
        }
        if (score == null) {
            Muted(
                tr(
                    "No audio has been scored in this report yet. Speak, and the score appears here, term by term.",
                    "इस रिपोर्ट में अभी किसी ऑडियो का स्कोर नहीं बना है। बोलिए, तो स्कोर यहाँ एक-एक मद के साथ दिखेगा।",
                ),
            )
            return@Column
        }
        Muted(
            tr(
                "The latest of $turns spoken turn(s) in this report.",
                "इस रिपोर्ट के $turns बोले गए चरणों में से नवीनतम।",
            ),
        )

        // 1. capture quality, every term with its value, its rule and its points
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Capture quality", "रिकॉर्डिंग की गुणवत्ता"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    score.capture.total?.let { "$it / 100" } ?: notMeasured(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
            Muted(
                tr(
                    "How well the device heard the voice: five measured terms, each worth a fixed number of points.",
                    "डिवाइस ने आवाज़ को कितना अच्छा सुना: पाँच मापी गई मदें, हर एक के निश्चित अंक।",
                ),
            )
            score.capture.terms.forEach { TermRow(it) }
        }

        // 2. recognition facts
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(tr("Recognition", "पहचान"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            val r = score.recognition
            Fact(tr("Engine", "इंजन"), r.engine ?: notMeasured())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Ran on", "कहाँ चला"), style = MaterialTheme.typography.bodyLarge, color = c.ink2)
                if (r.compute == null) Text(notMeasured(), style = MaterialTheme.typography.bodyLarge) else ComputeLabel(r.compute!!)
            }
            Fact(tr("Time to recognise", "पहचानने में लगा समय"), if (r.processingMs == null) notMeasured() else formatMs(r.processingMs))
            Fact(tr("Audio length", "ऑडियो की लंबाई"), formatMs(r.audioMs))
            Fact(
                tr("Speed against real time", "वास्तविक समय की तुलना में गति"),
                r.realTimeFactor?.let { String.format(Locale.ROOT, "%.1f× ", it) + tr("real time", "वास्तविक समय") } ?: notMeasured(),
            )
            Muted(
                tr(
                    "Audio length divided by processing time; higher is faster. Where an engine did not report its time, it says not measured.",
                    "ऑडियो की लंबाई को प्रोसेसिंग समय से भाग देने पर; जितना अधिक, उतना तेज़। जिस इंजन ने समय नहीं बताया, वहाँ \"मापा नहीं गया\" लिखा है।",
                ),
            )
            Fact(tr("Words recognised", "पहचाने गए शब्द"), "${r.words}")
            Fact(tr("Recognition confidence", "पहचान का भरोसा"), notMeasured())
            Muted(
                tr(
                    "None of the speech engines in this app reports a confidence number, so none is shown.",
                    "इस ऐप का कोई भी स्पीच इंजन भरोसे की संख्या नहीं देता, इसलिए कोई संख्या नहीं दिखाई गई।",
                ),
            )
        }

        // 3. how the voice fed the priority
        val inf = score.influence
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(
                tr("How the voice fed the priority", "आवाज़ ने प्राथमिकता को कैसे प्रभावित किया"),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() },
            )
            if (inf == null) {
                Muted(notMeasured())
            } else {
                Muted(
                    tr(
                        "Priority score (1 to 20) = impact (1 to 5) × immediacy (1 to 4). The words set impact and immediacy. " +
                            "Voice tags can only raise them: a sound on the rule list sets a minimum impact and immediacy, and an angry, " +
                            "sad or fearful voice counts as distress, which adds 1 to immediacy (at most 4). Nothing lowers the score.",
                        "प्राथमिकता स्कोर (1 से 20) = प्रभाव (1 से 5) × तात्कालिकता (1 से 4)। शब्द प्रभाव और तात्कालिकता तय करते हैं। " +
                            "आवाज़ के टैग इन्हें केवल बढ़ा सकते हैं: नियम-सूची की कोई ध्वनि न्यूनतम प्रभाव और तात्कालिकता तय करती है, " +
                            "और ग़ुस्से, उदास या डरी आवाज़ को संकट माना जाता है, जिससे तात्कालिकता में 1 जुड़ता है (अधिकतम 4)। कुछ भी स्कोर घटाता नहीं।",
                    ),
                )
                Muted(
                    tr(
                        "Levels: 1 to 4 LOW, 5 to 9 MEDIUM, 10 to 14 HIGH, 15 to 20 URGENT.",
                        "स्तर: 1 से 4 कम, 5 से 9 मध्यम, 10 से 14 उच्च, 15 से 20 अत्यावश्यक।",
                    ),
                )
                if (inf.terms.isEmpty()) {
                    Muted(tr("No cue was found in the words and no voice tag was heard.", "शब्दों में कोई संकेत नहीं मिला और कोई आवाज़ का टैग नहीं सुना गया।"))
                }
                inf.terms.forEach { VoiceTermRow(it) }
                Fact(tr("Words alone", "केवल शब्द"), "${inf.wordsOnlyScore} / 20")
                Fact(tr("Words and voice tags", "शब्द और आवाज़ के टैग"), "${inf.withVoiceScore} / 20")
                Fact(tr("Voice tags added", "आवाज़ के टैग से जुड़ा"), "+${inf.voicePoints}")
                Fact(tr("Level from these rules", "इन नियमों से स्तर"), levelName(inf.level))
                Muted(
                    tr(
                        "A replay of the app's own rules on this report's words and voice tags. The live priority can be higher: " +
                            "the language model's hint and the reporter's answers also count, and it never goes down during a report.",
                        "इस रिपोर्ट के शब्दों और आवाज़ के टैग पर ऐप के अपने नियमों का दोहराव। असली प्राथमिकता इससे अधिक हो सकती है: " +
                            "भाषा मॉडल का संकेत और रिपोर्टर के उत्तर भी गिने जाते हैं, और रिपोर्ट के दौरान यह कभी घटती नहीं।",
                    ),
                )
            }
        }

        Muted(
            tr(
                "A heuristic written out in the open: it rates how well the device captured the voice and shows how the voice tags " +
                    "fed the priority rules. It is not a measure of speech accuracy or of how serious the report is.",
                "खुले रूप में लिखा गया एक अनुमान-आधारित (हीयूरिस्टिक) नियम: यह बताता है कि डिवाइस ने आवाज़ कितनी अच्छी तरह पकड़ी और आवाज़ के " +
                    "टैग ने प्राथमिकता के नियमों को कैसे प्रभावित किया। यह बोली की सटीकता या रिपोर्ट की गंभीरता का माप नहीं है।",
            ),
        )
    }
}

/** One measured term: its label and points on one line, then what was measured, then the rule it is judged by. */
@Composable
private fun TermRow(t: ScoreTerm) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = Space.s1), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(termLabel(t), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (t.measured == null) notMeasured() else "${t.points} / ${t.weight}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(tr("Measured", "मापा गया"), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            Text(
                if (t.measured == null) notMeasured() else t.valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        Muted(termRule(t))
    }
}

@Composable
private fun termLabel(t: ScoreTerm): String = when (t.id) {
    "peak" -> tr("Peak level", "चरम स्तर")
    "mean" -> tr("Average level (RMS)", "औसत स्तर (RMS)")
    "clipping" -> tr("Clipped samples", "कटे हुए नमूने")
    "duration" -> tr("Length", "अवधि")
    "silence" -> tr("Silence", "खामोशी")
    else -> t.label
}

/** The rule in words, built from the same constants the arithmetic uses, so what is shown is what was done. */
@Composable
private fun termRule(t: ScoreTerm): String {
    val k = CaptureThresholds
    return when (t.id) {
        "peak" -> tr(
            "${k.PEAK_WEIGHT} points at or above ${k.PEAK_FULL_DBFS.toInt()} dBFS, falling in a straight line to 0 at ${k.PEAK_ZERO_DBFS.toInt()} dBFS.",
            "${k.PEAK_WEIGHT} अंक ${k.PEAK_FULL_DBFS.toInt()} dBFS या उससे ऊपर, फिर सीधी रेखा में घटकर ${k.PEAK_ZERO_DBFS.toInt()} dBFS पर 0।",
        )
        "mean" -> tr(
            "${k.MEAN_WEIGHT} points from ${k.MEAN_FULL_LOW_DBFS.toInt()} to ${k.MEAN_FULL_HIGH_DBFS.toInt()} dBFS; fewer below " +
                "${k.MEAN_FULL_LOW_DBFS.toInt()} (0 at ${k.MEAN_ZERO_LOW_DBFS.toInt()}) and fewer above ${k.MEAN_FULL_HIGH_DBFS.toInt()} (0 at ${k.MEAN_ZERO_HIGH_DBFS.toInt()}).",
            "${k.MEAN_WEIGHT} अंक ${k.MEAN_FULL_LOW_DBFS.toInt()} से ${k.MEAN_FULL_HIGH_DBFS.toInt()} dBFS के बीच; " +
                "${k.MEAN_FULL_LOW_DBFS.toInt()} से नीचे कम (${k.MEAN_ZERO_LOW_DBFS.toInt()} पर 0) और ${k.MEAN_FULL_HIGH_DBFS.toInt()} से ऊपर कम (${k.MEAN_ZERO_HIGH_DBFS.toInt()} पर 0)।",
        )
        "clipping" -> tr(
            "${k.CLIP_WEIGHT} points at 0 %, falling to 0 at ${(k.CLIP_ZERO_FRACTION * 100).toInt()} % of samples at full scale.",
            "${k.CLIP_WEIGHT} अंक 0 % पर, घटकर पूर्ण स्तर पर पहुँचे नमूनों के ${(k.CLIP_ZERO_FRACTION * 100).toInt()} % पर 0।",
        )
        "duration" -> tr(
            "${k.DURATION_WEIGHT} points at ${k.DURATION_FULL_S.toInt()} s or longer, falling to 0 at ${k.DURATION_ZERO_S} s.",
            "${k.DURATION_WEIGHT} अंक ${k.DURATION_FULL_S.toInt()} सेकंड या उससे लंबे पर, घटकर ${k.DURATION_ZERO_S} सेकंड पर 0।",
        )
        "silence" -> tr(
            "${k.SILENCE_WEIGHT} points at ${(k.SILENCE_FULL_FRACTION * 100).toInt()} % or less of ${k.SILENCE_FRAME_MS} ms frames below " +
                "${k.SILENCE_BELOW_DBFS.toInt()} dBFS, falling to 0 at ${(k.SILENCE_ZERO_FRACTION * 100).toInt()} %.",
            "${k.SILENCE_WEIGHT} अंक, जब ${k.SILENCE_BELOW_DBFS.toInt()} dBFS से नीचे के ${k.SILENCE_FRAME_MS} ms फ्रेम ${(k.SILENCE_FULL_FRACTION * 100).toInt()} % या कम हों; " +
                "${(k.SILENCE_ZERO_FRACTION * 100).toInt()} % पर 0।",
        )
        else -> t.rule
    }
}

/** One audio-derived input to the priority: what it is, how the rules treat it, and the points it added. */
@Composable
private fun VoiceTermRow(t: VoiceTerm) {
    val c = Theme.colors
    val kind = when (t.kind) {
        VoiceTermKind.EMOTION -> tr("Voice", "आवाज़")
        VoiceTermKind.SOUND -> tr("Sound", "ध्वनि")
        VoiceTermKind.WORDS -> tr("Words", "शब्द")
    }
    val rule = when (t.kind) {
        VoiceTermKind.SOUND ->
            if (t.counted) {
                tr(
                    "On the rule list: impact at least ${t.minImpact}, immediacy at least ${t.minImmediacy}.",
                    "नियम-सूची में है: प्रभाव कम से कम ${t.minImpact}, तात्कालिकता कम से कम ${t.minImmediacy}।",
                )
            } else {
                tr("No priority rule uses this sound, so it changes nothing.", "कोई प्राथमिकता-नियम इस ध्वनि का उपयोग नहीं करता, इसलिए इससे कुछ नहीं बदलता।")
            }
        VoiceTermKind.EMOTION ->
            if (t.counted) {
                tr("Counts as distress: immediacy goes up by 1, at most 4.", "संकट माना जाता है: तात्कालिकता 1 बढ़ती है, अधिकतम 4।")
            } else {
                tr("No priority rule uses this voice, so it changes nothing.", "कोई प्राथमिकता-नियम इस आवाज़ का उपयोग नहीं करता, इसलिए इससे कुछ नहीं बदलता।")
            }
        VoiceTermKind.WORDS ->
            tr(
                "Found in the recognised words; with the other cues it makes the words-only score.",
                "पहचाने गए शब्दों में मिला; बाकी संकेतों के साथ मिलकर केवल-शब्द स्कोर बनता है।",
            )
    }
    val points = when {
        t.points == null -> "–"
        t.points!! > 0 -> "+${t.points} ${tr("points", "अंक")}"
        else -> tr("no change", "कोई बदलाव नहीं")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(top = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Text("$kind: ${t.label}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(points, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold))
        }
        Muted(rule)
        Text("${tr("from", "स्रोत")} ${t.source}", style = MaterialTheme.typography.labelSmall, color = c.ink3)
    }
}

// ---- shared pieces ------------------------------------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Theme.colors.ink2, modifier = modifier)

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Theme.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun notMeasured(): String = tr("not measured", "मापा नहीं गया")

@Composable
private fun levelName(level: String): String = when (level) {
    "LOW" -> tr("LOW", "कम")
    "MEDIUM" -> tr("MEDIUM", "मध्यम")
    "HIGH" -> tr("HIGH", "उच्च")
    "URGENT" -> tr("URGENT", "अत्यावश्यक")
    else -> level
}

@Composable
private fun stageName(stage: String): String = when (stage) {
    "Hear" -> tr("Speech to text", "बोली से लिखित पाठ")
    "Read" -> tr("Understanding the report (model)", "रिपोर्ट समझना (मॉडल)")
    "Tags" -> tr("Voice tags (emotion, sounds)", "आवाज़ के टैग (भाव, ध्वनियाँ)")
    "Speak" -> tr("Spoken replies", "बोले गए उत्तर")
    else -> stage
}

/** The compute kind as the same pill the steps use, from the name stored in the log. */
@Composable
private fun ComputeLabel(name: String) {
    val compute = Compute.values().firstOrNull { it.name == name }
    if (compute != null) ComputePill(compute) else Text(name, style = MaterialTheme.typography.bodyLarge)
}

/** A pill per kind of compute with how many steps ran there ("NPU x 2"). */
@Composable
private fun ComputeCounts(names: List<String>) {
    if (names.isEmpty()) {
        Text(tr("Not run yet", "अभी नहीं चला"), style = MaterialTheme.typography.bodyLarge, color = Theme.colors.ink3)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s3), verticalAlignment = Alignment.CenterVertically) {
        names.groupingBy { it }.eachCount().entries.sortedBy { Compute.values().indexOfFirst { c -> c.name == it.key } }.forEach { (name, n) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s1), verticalAlignment = Alignment.CenterVertically) {
                ComputeLabel(name)
                Text("× $n", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Theme.colors.ink2)
            }
        }
    }
}

private fun formatMs(ms: Long?): String = when {
    ms == null -> "–"
    ms < 1000 -> "$ms ms"
    else -> String.format(Locale.ROOT, "%.1f s", ms / 1000.0)
}

@Composable
private fun Stages(stages: List<StageRecord>, ttsOffline: Boolean?, ttsNote: String?) {
    val c = Theme.colors
    val steps = listOf(
        "Hear" to tr("Heard with", "सुनने में"),
        "Read" to tr("Understood with", "समझने में"),
        "Speak" to tr("Spoken with", "बोलने में"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        steps.forEach { (key, label) ->
            val s = stages.firstOrNull { it.stage == key }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2)
                if (s == null) {
                    Text(tr("Not run yet", "अभी नहीं चला"), style = MaterialTheme.typography.bodyLarge, color = c.ink3)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Text(s.engine, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                        ComputePill(s.compute)
                        s.ms?.let { Text("$it ms", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = c.ink2) }
                    }
                    if (key == "Speak") {
                        val piper = s.engine.startsWith("Piper")
                        Text(
                            when {
                                piper -> tr(
                                    "Uses the Piper voice (an open model run by sherpa-onnx), offline, on the CPU.",
                                    "Piper आवाज़ (sherpa-onnx पर चलने वाला खुला मॉडल) का उपयोग, ऑफ़लाइन, CPU पर।",
                                )
                                ttsOffline == true -> tr("Uses an offline system voice, on the CPU.", "ऑफ़लाइन सिस्टम आवाज़ का उपयोग, CPU पर।")
                                else -> tr(
                                    "Uses the system voice, on the CPU. No offline voice was confirmed.",
                                    "सिस्टम आवाज़ का उपयोग, CPU पर। ऑफ़लाइन आवाज़ की पुष्टि नहीं हुई।",
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium, color = c.ink2,
                        )
                        if (ttsNote != null) Text(ttsNote, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceTags(ui: AppUi) {
    val c = Theme.colors
    val tags = ui.voiceTags
    if (tags == null || tags.signals.isEmpty()) {
        Muted(ui.voiceTagsNote ?: tr("None heard", "कोई नहीं सुना"))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        tags.signals.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(
                    "${if (s.kind == "emotion") tr("Voice", "आवाज़") else tr("Sound", "ध्वनि")}: ${s.label}",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false),
                )
                Text(tr("from SenseVoice", "SenseVoice से"), style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                ComputePill(tags.compute)
            }
        }
    }
}

@Composable
private fun ToolRow(e: ToolLogEntry) {
    val c = Theme.colors
    var open by remember { mutableStateOf(false) }
    val hide = tr("Hide details", "विवरण छिपाएँ")
    val show = tr("Show details", "विवरण दिखाएँ")
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).clickable(onClickLabel = if (open) hide else show) { open = !open }.padding(Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Text(e.tool, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodyLarge.fontSize), color = c.accent)
        Text(e.json, style = MaterialTheme.typography.bodyMedium, color = c.ink2, maxLines = if (open) Int.MAX_VALUE else 2)
    }
}

@Composable
private fun JournalRow(j: JournalEntry) {
    val c = Theme.colors
    val empty = tr("(empty)", "(खाली)")
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text(Labels.fieldLabel(j.field), style = MaterialTheme.typography.labelMedium, color = c.ink2)
        Text("${j.before ?: empty}  →  ${j.after ?: empty}", style = MaterialTheme.typography.bodyLarge)
        Text(j.reason, style = MaterialTheme.typography.bodyMedium, color = c.ink2)
    }
}

package com.megathon.incident.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.megathon.incident.testing.Compute

/** Colour per compute unit. SIMULATED is deliberately loud (magenta): it must never pass as live. */
fun computeColor(compute: Compute): Color = when (compute) {
    Compute.NPU -> Color(0xFF2E7D32)
    Compute.CPU -> Color(0xFFF9A825)
    Compute.HYBRID -> Color(0xFF1565C0)
    Compute.SIMULATED -> Color(0xFFD81B60)
}

@Composable
fun ComputeBadge(compute: Compute, modifier: Modifier = Modifier) {
    Text(
        text = compute.name,
        color = Color.White,
        modifier = modifier
            .background(computeColor(compute), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** One row per pipeline stage: what ran it and where. Real timings and the NPU self-test come later (A9). */
data class StageProof(val stage: String, val engine: String, val compute: Compute, val ms: Long? = null)

@Composable
fun ProofStrip(stages: List<StageProof>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stages.forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${it.stage}: ${it.engine}")
                ComputeBadge(it.compute)
                it.ms?.let { ms -> Text("$ms ms") }
            }
        }
    }
}

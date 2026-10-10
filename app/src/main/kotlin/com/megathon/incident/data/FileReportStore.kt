package com.megathon.incident.data

import android.content.Context
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.session.ReportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** One JSON file per report under `filesDir/reports/` (spec D12). Stays on the device. */
class FileReportStore(context: Context) : ReportStore {
    private val dir = File(context.filesDir, "reports").apply { mkdirs() }
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    override suspend fun save(report: IncidentReport) {
        withContext(Dispatchers.IO) {
            File(dir, "${report.id}.json").writeText(json.encodeToString(IncidentReport.serializer(), report))
        }
    }
}

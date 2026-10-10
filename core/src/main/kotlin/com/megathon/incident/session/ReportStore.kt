package com.megathon.incident.session

import com.megathon.incident.domain.IncidentReport

/** Where submitted reports go. Interim port: the storage owner's repository replaces the implementations. */
interface ReportStore {
    suspend fun save(report: IncidentReport)
}

class InMemoryReportStore : ReportStore {
    private val items = mutableListOf<IncidentReport>()
    val saved: List<IncidentReport> get() = items.toList()

    override suspend fun save(report: IncidentReport) {
        items += report
    }
}

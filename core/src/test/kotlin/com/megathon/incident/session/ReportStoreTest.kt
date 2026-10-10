package com.megathon.incident.session

import com.megathon.incident.domain.IncidentReport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportStoreTest {
    @Test fun theInMemoryStoreKeepsSavedReportsInOrder() = runBlocking {
        val store = InMemoryReportStore()
        store.save(IncidentReport.empty("a", 1))
        store.save(IncidentReport.empty("b", 2))
        assertEquals(listOf("a", "b"), store.saved.map { it.id })
    }
}

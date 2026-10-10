package com.megathon.incident.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    private val json = Json

    @Test
    fun emptyReportHasEveryFieldMissing() {
        val r = IncidentReport.empty("r1", 0)
        assertTrue(Field.entries.all { r.value(it).status == ValueStatus.MISSING })
        assertTrue(Field.entries.none { r.isFilled(it) })
    }

    @Test
    fun missingUnknownAndExplicitNoneAreDistinct() {
        val r = IncidentReport.empty("r1", 0)
            .with(Field.WITNESSES, FieldValue.explicitNone(evidence = "nobody saw it"))
            .with(Field.EQUIPMENT_ASSET, FieldValue.unknown(evidence = "I don't know which machine"))
        assertEquals(ValueStatus.EXPLICIT_NONE, r.value(Field.WITNESSES).status)
        assertEquals(ValueStatus.UNKNOWN, r.value(Field.EQUIPMENT_ASSET).status)
        assertEquals(ValueStatus.MISSING, r.value(Field.LOCATION).status)
        assertNull(r.value(Field.WITNESSES).text)
        // Explicit "none" answers the question; "unknown" and "missing" do not.
        assertTrue(r.isAnswered(Field.WITNESSES))
        assertTrue(r.isAnswered(Field.EQUIPMENT_ASSET))
        assertFalse(r.isAnswered(Field.LOCATION))
    }

    @Test
    fun suggestedValueDoesNotCountAsFilled() {
        val r = IncidentReport.empty("r1", 0).with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED))
        assertFalse(r.isFilled(Field.SEVERITY))
    }

    @Test
    fun reportedConfirmedAndEditedCountAsFilled() {
        val statuses = listOf(ValueStatus.REPORTED, ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED)
        statuses.forEach { s ->
            val r = IncidentReport.empty("r1", 0).with(Field.LOCATION, FieldValue("Lab 3", s))
            assertTrue(r.isFilled(Field.LOCATION), "status $s should count as filled")
        }
    }

    @Test
    fun reportRoundTripsThroughJsonIncludingHindi() {
        val r = IncidentReport.empty("r1", 1_700_000_000_000)
            .with(Field.LOCATION, FieldValue("कॉन्फ़्रेंस रूम 2", ValueStatus.REPORTED, evidence = "कॉन्फ़्रेंस रूम 2 के पास"))
            .with(Field.INCIDENT_TYPE, FieldValue(IncidentType.WATER_LEAK_FLOOD.wire, ValueStatus.REPORTED, evidence = "paani leak"))
        val text = json.encodeToString(IncidentReport.serializer(), r)
        assertEquals(r, json.decodeFromString(IncidentReport.serializer(), text))
        assertTrue(text.contains("कॉन्फ़्रेंस"))
    }

    @Test
    fun enumWireValuesMatchTheLlmContract() {
        assertEquals(
            listOf("water_leak_flood", "fire_smoke", "electrical", "spill_chemical", "injury", "illness",
                "near_miss", "property_damage", "equipment_failure", "security", "environmental", "other"),
            IncidentType.entries.map { it.wire },
        )
        assertEquals(listOf("none", "minor", "moderate", "severe", "critical"), Severity.entries.map { it.wire })
        assertEquals(IncidentType.FIRE_SMOKE, IncidentType.fromWire("fire_smoke"))
        assertNull(IncidentType.fromWire("banana"))
    }

    @Test
    fun priorityAdviceAndSignalsCarryTheirSource() {
        val s = Signal(kind = "event", label = "fire alarm", source = Source.YAMNET, segmentId = 4)
        val advice = PriorityAdvice(Priority.URGENT, listOf("fire alarm heard (YAMNET)"))
        assertEquals(Source.YAMNET, s.source)
        assertFalse(advice.overriddenByUser)
        assertTrue(Priority.URGENT > Priority.HIGH && Priority.HIGH > Priority.MEDIUM && Priority.MEDIUM > Priority.LOW)
    }
}

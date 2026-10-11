package com.megathon.incident.speech

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.session.Labels
import com.megathon.incident.suggest.FIELD_LABELS
import com.megathon.incident.suggest.Homophones
import com.megathon.incident.suggest.RuleSuggestionEngine
import com.megathon.incident.suggest.SiteDirectory
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Everything a person reads or hears says "tool" or "item", never "equipment": nobody calls a bicycle or a pan
 * "equipment". Only the words change; the wire key `equipment_asset` and the extraction prompt are untouched.
 */
class PlainWordingTest {
    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private val withItem = IncidentReport.empty("r1", 0L)
        .with(Field.INCIDENT_TYPE, heard("fire_smoke"))
        .with(Field.LOCATION, heard("floor 3"))
        .with(Field.EQUIPMENT_ASSET, heard("printer"))

    @Test
    fun `the field label on screen is tools or items`() {
        assertEquals("Tools / items", Labels.fieldLabel(Field.EQUIPMENT_ASSET))
    }

    @Test
    fun `the read back names the field in plain words`() {
        val text = SummaryText.readBack(withItem)
        assertTrue("Tools or items: printer." in text, text)
        assertFalse("quipment" in text, text)
    }

    @Test
    fun `what is still needed is phrased as a tool or item`() {
        val text = SummaryText.readBack(IncidentReport.empty("r2", 0L), mandatory = listOf(Field.EQUIPMENT_ASSET))
        assertTrue("which tool or item" in text, text)
    }

    @Test
    fun `the spoken acknowledgement says tool or item`() {
        val text = AckTemplates.forChanges(withItem, listOf(Field.EQUIPMENT_ASSET))!!
        assertTrue("tool or item: printer" in text, text)
        assertFalse("quipment" in text, text)
    }

    @Test
    fun `a voice change command accepts tool, tools, item and items as well as equipment`() {
        listOf("tool", "the tool", "tools", "item", "the item", "items", "equipment", "thing").forEach { name ->
            assertEquals(
                VoiceCommand.Change(Field.EQUIPMENT_ASSET, "a drill"),
                VoiceCommandParser.parse("change $name to a drill"),
                name,
            )
        }
    }

    @Test
    fun `a chip that questions the field uses the plain word`() {
        assertEquals("tool or item", FIELD_LABELS.toMap().getValue(Field.EQUIPMENT_ASSET))
    }

    @Test
    fun `the chip for a tool the model did not emit asks in plain words`() {
        val engine = RuleSuggestionEngine(
            SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
            Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
        )
        val state = ReportState.empty("r3", 0)
        val chip = engine.suggest(state, "Smoke coming out of the microwave in the canteen").first { it.field == Field.EQUIPMENT_ASSET }
        assertTrue("tool or item" in chip.reason, chip.reason)
        assertFalse("quipment" in chip.reason, chip.reason)
    }
}

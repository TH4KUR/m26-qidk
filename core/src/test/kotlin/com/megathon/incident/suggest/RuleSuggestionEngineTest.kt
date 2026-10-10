package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.Compute
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleSuggestionEngineTest : SuggestionEngineContract() {

    override fun engine(): SuggestionEngine =
        RuleSuggestionEngine(SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()), clock)

    private fun fixture(name: String): List<Suggestion> =
        Json.decodeFromString(javaClass.getResourceAsStream("/fixtures/suggestions/$name")!!.bufferedReader().use { it.readText() })

    private fun heard(field: Field, text: String): ReportState {
        val empty = ReportState.empty("r1", 0)
        return empty.copy(report = empty.report.with(field, FieldValue(text, ValueStatus.REPORTED, text)))
    }

    @Test
    fun realEngineIsLabelledCpu() = assertEquals(Compute.CPU, engine().compute)

    /** A's UI was built on these fixtures; the real engine must produce exactly the same chips. */
    @Test
    fun reproducesEveryGazetteerFixtureChip() {
        for (chip in fixture("deck_example.json") + fixture("campus_snaps.json")) {
            assertEquals(listOf(chip), engine().suggest(heard(chip.field, chip.original), ""), "for \"${chip.original}\"")
        }
    }

    @Test
    fun reproducesTheWitnessAndHedgeFixtureChips() {
        val witness = heard(Field.PERSONS_INVOLVED, "Ravi, Priya")
        assertEquals(fixture("witness_role.json"), engine().suggest(witness, "Ravi slipped near the pantry, Priya saw it."))
        val empty = ReportState.empty("r1", 0)
        val hedged = empty.copy(report = empty.report.with(Field.LOCATION, FieldValue("Room 2", ValueStatus.SUGGESTED, "Room 2")))
        assertEquals(fixture("hedge.json"), engine().suggest(hedged, "I think it was Room 2."))
    }

    @Test
    fun valuesTheUserAlreadyDecidedGetNoChip() {
        for (status in listOf(ValueStatus.USER_CONFIRMED, ValueStatus.USER_EDITED)) {
            val empty = ReportState.empty("r1", 0)
            val state = empty.copy(report = empty.report.with(Field.LOCATION, FieldValue("conference room to", status)))
            assertTrue(engine().suggest(state, DECK).isEmpty(), "status $status")
        }
    }
}

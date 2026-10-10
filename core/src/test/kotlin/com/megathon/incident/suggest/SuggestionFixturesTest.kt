package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The chip fixtures A renders before the real engine exists; they must stay valid `Suggestion` JSON. */
class SuggestionFixturesTest {

    private fun load(name: String): List<Suggestion> {
        val path = "/fixtures/suggestions/$name"
        val text = javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: error("missing fixture $path")
        return Json.decodeFromString(text)
    }

    /** The deck's sentence with a real campus place: "water leak near seminar hall to". */
    @Test
    fun deckExampleChipSnapsHallToToSeminarHall2() {
        assertEquals(
            listOf(Suggestion(Field.LOCATION, "seminar hall to", "Seminar Hall 2", "Heard \"hall to\"; the site has Seminar Hall 2.", Source.GAZETTEER)),
            load("deck_example.json"),
        )
    }

    @Test
    fun deckExampleChipProposesARealSiteName() {
        val chip = load("deck_example.json").single()
        assertEquals("sh-2", SiteDirectory.loadDefault().lookup(chip.proposed)?.id)
    }

    @Test
    fun allThreeSeminarHallsAreOnTheSite() {
        val site = SiteDirectory.loadDefault()
        assertEquals(listOf("sh-1", "sh-2", "sh-3"), listOf("SH1", "SH 2", "seminar hall three").map { site.lookup(it)?.id })
    }

    @Test
    fun witnessChipMovesAPersonWhoOnlySawItToWitnesses() {
        val chip = load("witness_role.json").single()
        assertEquals(Field.WITNESSES, chip.field)
        assertEquals("Priya", chip.proposed)
        assertEquals(Source.RULE, chip.source)
    }

    @Test
    fun hedgeChipComesFromATextCue() {
        val chip = load("hedge.json").single()
        assertEquals(Source.TEXT_CUE, chip.source)
        assertTrue("I think" in chip.reason, chip.reason)
    }

    @Test
    fun campusChipsCoverAPlaceAndAnAsset() {
        val chips = load("campus_snaps.json")
        assertEquals(setOf(Field.LOCATION, Field.EQUIPMENT_ASSET), chips.map { it.field }.toSet())
        assertTrue(chips.all { it.source == Source.GAZETTEER })
    }

    @Test
    fun everyCampusChipProposesARealSiteName() {
        val site = SiteDirectory.loadDefault()
        for (chip in load("campus_snaps.json")) {
            assertEquals(chip.proposed, site.lookup(chip.proposed)?.name, "not a site name: ${chip.proposed}")
        }
    }
}

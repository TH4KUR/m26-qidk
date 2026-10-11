package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissedFieldRulesTest {

    private val engine = RuleSuggestionEngine(
        SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
        Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
    )

    private fun state(vararg fields: Pair<Field, FieldValue>): ReportState {
        var s = ReportState.empty("r1", 0)
        for ((f, v) in fields) s = s.copy(report = s.report.with(f, v))
        return s
    }

    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private fun chips(field: Field, transcript: String, s: ReportState = state()) =
        engine.suggest(s, transcript).filter { it.field == field }

    private fun proposed(field: Field, transcript: String, s: ReportState = state()) = chips(field, transcript, s).map { it.proposed }

    // ---- equipment -------------------------------------------------------------------------------

    @Test
    fun `an equipment noun the model did not emit becomes a chip with the words as said`() {
        val c = chips(Field.EQUIPMENT_ASSET, "I tripped over a ladder in the corridor.")
        assertEquals(1, c.size)
        assertEquals("", c[0].original)
        assertEquals("ladder", c[0].proposed)
        assertEquals(Source.RULE, c[0].source)
    }

    @Test
    fun `a two-word equipment name is proposed whole`() {
        assertEquals(listOf("vending machine"), proposed(Field.EQUIPMENT_ASSET, "The vending machine fell on my foot."))
    }

    @Test
    fun `the words keep the speaker's spelling and case`() {
        assertEquals(listOf("Forklift"), proposed(Field.EQUIPMENT_ASSET, "Forklift hit the wall."))
    }

    @Test
    fun `several equipment nouns go into one chip in the order said`() {
        assertEquals(listOf("ladder, bucket"), proposed(Field.EQUIPMENT_ASSET, "I knocked the ladder into a bucket of paint."))
    }

    @Test
    fun `a negated equipment noun gives no chip`() {
        assertEquals(emptyList(), proposed(Field.EQUIPMENT_ASSET, "There was no ladder involved."))
    }

    @Test
    fun `a hedged equipment noun gives no chip`() {
        assertEquals(emptyList(), proposed(Field.EQUIPMENT_ASSET, "I think a trolley hit him."))
    }

    @Test
    fun `a planned equipment noun gives no chip`() {
        assertEquals(emptyList(), proposed(Field.EQUIPMENT_ASSET, "We will bring a ladder tomorrow."))
    }

    @Test
    fun `an equipment word inside the location value is a place, not equipment`() {
        val s = state(Field.LOCATION to heard("near the vending machine"))
        assertEquals(emptyList(), proposed(Field.EQUIPMENT_ASSET, "Water on the floor near the vending machine.", s))
    }

    @Test
    fun `no equipment chip when the model already filled the field`() {
        val s = state(Field.EQUIPMENT_ASSET to heard("ladder"))
        assertTrue(chips(Field.EQUIPMENT_ASSET, "I fell off the ladder.", s).none { it.original == "" })
    }

    @Test
    fun `no equipment chip when the speaker said there was none`() {
        val s = state(Field.EQUIPMENT_ASSET to FieldValue("None (stated)", ValueStatus.EXPLICIT_NONE))
        assertEquals(emptyList(), proposed(Field.EQUIPMENT_ASSET, "I fell off the ladder.", s))
    }

    @Test
    fun `a tool the speaker used to respond is an action, not the equipment involved`() {
        val transcript = "The bin caught fire. I put it out with the fire extinguisher."
        assertEquals(listOf("bin"), proposed(Field.EQUIPMENT_ASSET, transcript))
        assertEquals(listOf("put it out with the fire extinguisher"), proposed(Field.IMMEDIATE_ACTIONS, transcript))
    }

    @Test
    fun `no equipment chip without an equipment noun`() {
        assertEquals(emptyList(), proposed(Field.EQUIPMENT_ASSET, "Ravi slipped near the pantry, Priya saw it."))
    }

    // ---- immediate actions -----------------------------------------------------------------------

    @Test
    fun `a first-person perfect clause becomes an action chip trimmed to the action`() {
        val c = chips(Field.IMMEDIATE_ACTIONS, "The guard was not around. I've told the security guard.")
        assertEquals(1, c.size)
        assertEquals("", c[0].original)
        assertEquals("told the security guard", c[0].proposed)
        assertEquals(Source.RULE, c[0].source)
    }

    @Test
    fun `a first-person ongoing clause becomes an action chip`() {
        assertEquals(listOf("calling the ambulance now"), proposed(Field.IMMEDIATE_ACTIONS, "He is bleeding. I'm calling the ambulance now."))
    }

    @Test
    fun `a first-person simple past clause becomes an action chip`() {
        assertEquals(listOf("switched off the main power"), proposed(Field.IMMEDIATE_ACTIONS, "Sparks came out. We switched off the main power."))
    }

    @Test
    fun `a pronoun object is kept`() {
        assertEquals(listOf("unplugged it"), proposed(Field.IMMEDIATE_ACTIONS, "The kettle was smoking, I've unplugged it."))
    }

    @Test
    fun `two action clauses go into one chip`() {
        assertEquals(
            listOf("switched off the power; called the electrician"),
            proposed(Field.IMMEDIATE_ACTIONS, "I switched off the power. Then I called the electrician."),
        )
    }

    @Test
    fun `someone else's action is not the speaker's action`() {
        assertEquals(emptyList(), proposed(Field.IMMEDIATE_ACTIONS, "Maintenance has already isolated the area."))
    }

    @Test
    fun `a planned action gives no chip`() {
        assertEquals(emptyList(), proposed(Field.IMMEDIATE_ACTIONS, "I will call security."))
    }

    @Test
    fun `a negated action gives no chip`() {
        assertEquals(emptyList(), proposed(Field.IMMEDIATE_ACTIONS, "I didn't call anyone yet."))
    }

    @Test
    fun `a hedged action gives no chip`() {
        assertEquals(emptyList(), proposed(Field.IMMEDIATE_ACTIONS, "I think I called security."))
    }

    @Test
    fun `a first-person clause without an action verb gives no chip`() {
        assertEquals(emptyList(), proposed(Field.IMMEDIATE_ACTIONS, "I was walking to the canteen and I slipped."))
    }

    @Test
    fun `no action chip when the model already filled the field`() {
        val s = state(Field.IMMEDIATE_ACTIONS to heard("called security"))
        assertTrue(chips(Field.IMMEDIATE_ACTIONS, "I called security.", s).none { it.original == "" })
    }

    // ---- decisions and unchanged behaviour -------------------------------------------------------

    @Test
    fun `accepting a missed-field chip fills the field as confirmed`() {
        val s = state()
        val chip = chips(Field.IMMEDIATE_ACTIONS, "I've told the security guard.", s).single()
        val after = engine.accept(s, chip)
        assertEquals(FieldValue("told the security guard", ValueStatus.USER_CONFIRMED), after.report.value(Field.IMMEDIATE_ACTIONS))
    }

    @Test
    fun `a rejected missed-field chip is not offered again`() {
        val s = state()
        val transcript = "I tripped over a ladder."
        val chip = chips(Field.EQUIPMENT_ASSET, transcript, s).single()
        val after = engine.reject(s, chip)
        assertEquals(emptyList(), chips(Field.EQUIPMENT_ASSET, transcript, after))
    }

    @Test
    fun `the deck sentence gets no missed-field chip`() {
        val deck = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."
        val s = state(Field.LOCATION to heard("Conference Room 2"), Field.IMMEDIATE_ACTIONS to heard("isolated the area"))
        assertEquals(emptyList(), engine.suggest(s, deck).filter { it.original == "" })
    }
}

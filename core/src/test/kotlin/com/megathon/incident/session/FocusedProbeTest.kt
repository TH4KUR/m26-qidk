package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.testing.Compute
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Answers the focused prompts by which one it was asked; records every system prompt it was given. */
internal class ProbeLlm(
    var item: String = "{}",
    var action: String = "{}",
    private val fail: Boolean = false,
    private val delayMs: Long = 0,
) : LlmEngine {
    override val name = "PROBE-FAKE"
    val systems = mutableListOf<String>()
    override suspend fun complete(request: LlmRequest): LlmResult {
        systems += request.system
        if (fail) error("engine down")
        if (delayMs > 0) delay(delayMs)
        return LlmResult(if ("ALREADY DID" in request.system) action else item, 5, Compute.SIMULATED)
    }
}

/**
 * The focused second look (the user's option A): one short call per field the first pass left empty, in plain words
 * ("tool, machine or item"). It only ever proposes; the value must be grounded in the worker's words.
 */
class FocusedProbeTest {
    private val said = "The drill sparked in the workshop around 10 AM and I pressed the stop button."
    private val empty = IncidentReport.empty("r", 0)

    private fun propose(llm: ProbeLlm, report: IncidentReport = empty, transcript: String = said, skip: Set<Field> = emptySet()) =
        runBlocking { FocusedProbe(llm, timeoutMs = 300).propose(report, transcript, skip) }

    @Test fun `a grounded item becomes a chip from the model that quotes the worker`() {
        val chips = propose(ProbeLlm(item = """{"item":"drill","quote":"The drill sparked in the workshop"}"""))
        val chip = chips.single { it.field == Field.EQUIPMENT_ASSET }
        assertEquals("drill", chip.proposed)
        assertEquals("", chip.original)
        assertEquals(Source.LLM, chip.source)
        assertTrue("tool or item" in chip.reason && "The drill sparked in the workshop" in chip.reason, chip.reason)
        assertFalse("quipment" in chip.reason)
    }

    @Test fun `an item that is not in the workers words is dropped`() {
        assertTrue(propose(ProbeLlm(item = """{"item":"generator","quote":"The generator sparked"}""")).isEmpty())
    }

    @Test fun `the incident itself, a place and too many words are not items`() {
        val r = empty.with(Field.LOCATION, FieldValue("workshop", ValueStatus.REPORTED, "in the workshop"))
        assertTrue(propose(ProbeLlm(item = """{"item":"workshop","quote":"in the workshop"}"""), report = r).none { it.field == Field.EQUIPMENT_ASSET })
        assertTrue(propose(ProbeLlm(item = """{"item":"smoke","quote":"Smoke in the workshop"}"""), transcript = "Smoke in the workshop", report = r).none { it.field == Field.EQUIPMENT_ASSET })
        assertTrue(propose(ProbeLlm(item = """{"item":"the old drill press near the back wall","quote":"the old drill press near the back wall"}"""), transcript = "the old drill press near the back wall sparked").none { it.field == Field.EQUIPMENT_ASSET })
    }

    @Test fun `a place or a known campus site is never proposed as a tool or item`() {
        // Seen on the kit: "Actually it was Conference Room 3" came back as an item (a correction of the location).
        val corrected = "There was a water leak near Conference Room 2 around 10 AM. Actually it was Conference Room 3."
        listOf("Conference Room 3", "Seminar Hall 2", "main gate", "North Block", "second floor", "the canteen").forEach { place ->
            val chips = propose(ProbeLlm(item = """{"item":"$place","quote":"$place"}"""), transcript = "Water everywhere near $place, I think it was $place")
            assertTrue(chips.none { it.field == Field.EQUIPMENT_ASSET }, "$place -> $chips")
        }
        assertTrue(propose(ProbeLlm(item = """{"item":"Conference Room 3","quote":"it was Conference Room 3"}"""), transcript = corrected).none { it.field == Field.EQUIPMENT_ASSET })
        // Real items that merely sit near a place word stay.
        listOf("server rack", "fume hood", "projector", "meeting room projector").forEach { item ->
            val chips = propose(ProbeLlm(item = """{"item":"$item","quote":"the $item caught fire"}"""), transcript = "Smoke, the $item caught fire in the building")
            assertTrue(chips.any { it.field == Field.EQUIPMENT_ASSET && it.proposed == item }, "$item -> $chips")
        }
    }

    @Test fun `a person, a role or a body part is never proposed as a tool or item`() {
        val withPeople = empty
            .with(Field.PERSONS_INVOLVED, FieldValue("Priya", ValueStatus.REPORTED, "Priya"))
            .with(Field.WITNESSES, FieldValue("Rahul, Meera", ValueStatus.REPORTED, "Rahul and Meera"))
        listOf("Priya", "rahul", "Meera").forEach { name ->
            val chips = propose(ProbeLlm(item = """{"item":"$name","quote":"$name was near the lab"}"""), report = withPeople, transcript = "$name was near the lab and the lights went out")
            assertTrue(chips.none { it.field == Field.EQUIPMENT_ASSET }, "$name -> $chips")
        }
        listOf("ankle", "head", "security guard", "nurse", "colleague").forEach { word ->
            val chips = propose(ProbeLlm(item = """{"item":"$word","quote":"the $word was hurt"}"""), transcript = "Someone said the $word was hurt near the lab")
            assertTrue(chips.none { it.field == Field.EQUIPMENT_ASSET }, "$word -> $chips")
        }
        // A real item next to the same people is still proposed.
        assertTrue(propose(ProbeLlm(item = """{"item":"ladder","quote":"Priya fell off the ladder"}"""), report = withPeople, transcript = "Priya fell off the ladder").any { it.proposed == "ladder" })
    }

    @Test fun `nothing, junk, a failing engine and a slow engine all give no chips and never throw`() {
        assertTrue(propose(ProbeLlm()).isEmpty())
        assertTrue(propose(ProbeLlm(item = "Sure! The item is a drill.", action = "no idea")).isEmpty())
        assertTrue(propose(ProbeLlm(fail = true)).isEmpty())
        assertTrue(propose(ProbeLlm(item = """{"item":"drill","quote":"The drill sparked"}""", delayMs = 5_000)).isEmpty())
    }

    @Test fun `only fields that are still empty are asked about, and skipped ones are not`() {
        val llm = ProbeLlm()
        propose(llm, report = empty.with(Field.EQUIPMENT_ASSET, FieldValue("drill", ValueStatus.REPORTED, "drill")))
        // the item and action probes are one call per empty field; the people probe is one more call for both people fields
        val itemAndAction = llm.systems.filter { "NAMED people" !in it }
        assertEquals(1, itemAndAction.size)
        assertTrue("ALREADY DID" in itemAndAction.single())

        val skipped = ProbeLlm()
        propose(skipped, skip = setOf(Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS, Field.WITNESSES, Field.PERSONS_INVOLVED))
        assertTrue(skipped.systems.isEmpty())
    }

    @Test fun `a done action becomes a chip, a plan or advice does not`() {
        val done = propose(ProbeLlm(action = """{"action":"pressed the stop button","quote":"I pressed the stop button"}"""))
        val chip = done.single { it.field == Field.IMMEDIATE_ACTIONS }
        assertEquals("pressed the stop button", chip.proposed)
        assertTrue("action taken" in chip.reason, chip.reason)

        val planned = propose(
            ProbeLlm(action = """{"action":"call the electrician","quote":"we should call the electrician"}"""),
            transcript = "Sparks from the switch, we should call the electrician.",
        )
        assertTrue(planned.none { it.field == Field.IMMEDIATE_ACTIONS }, planned.toString())
    }

    @Test fun `the prompts speak plainly and are the ones that ship`() {
        val item = FocusedProbe::class.java.getResourceAsStream("/prompts/probe_item_v1.txt")!!.readBytes().decodeToString()
        assertTrue("tool, machine" in item && "quipment" !in item)
        assertTrue("ALREADY DID" in FocusedProbe::class.java.getResourceAsStream("/prompts/probe_action_v1.txt")!!.readBytes().decodeToString())
    }
}

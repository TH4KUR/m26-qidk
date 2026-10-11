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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The people probe: named people the first pass left out, as chips (a witness unless hurt), never a fill. */
class FocusedProbePeopleTest {
    private fun engine(people: String) = object : LlmEngine {
        override val name = "PEOPLE-FAKE"
        override suspend fun complete(request: LlmRequest): LlmResult =
            LlmResult(if ("NAMED people" in request.system) people else "{}", 5, Compute.SIMULATED)
    }

    private val empty = IncidentReport.empty("r", 0)

    private fun propose(reply: String, said: String, report: IncidentReport = empty, skip: Set<Field> = emptySet()) =
        runBlocking { FocusedProbe(engine(reply), timeoutMs = 300).propose(report, said, skip) }

    private val said = "Water is leaking near Conference Room 2. My friend Rahul was with me when it happened."

    @Test fun aPersonWhoWasPresentBecomesAWitnessChip() {
        val chip = propose("""{"witnesses":["Rahul"]}""", said).single { it.field == Field.WITNESSES }
        assertEquals("Rahul", chip.proposed)
        assertEquals("", chip.original)
        assertEquals(Source.LLM, chip.source)
        assertTrue("Rahul" in chip.reason && "witness" in chip.reason, chip.reason)
    }

    @Test fun anInvolvedPersonIsAChipForPersonsInvolved() {
        val chip = propose("""{"persons_involved":["Priya"]}""", "Priya slipped on the wet floor near the pantry.")
            .single { it.field == Field.PERSONS_INVOLVED }
        assertEquals("Priya", chip.proposed)
    }

    @Test fun aWitnessWhoWasAlsoHurtIsInvolvedNotAWitness() {
        val chips = propose("""{"witnesses":["Rahul"]}""", "Rahul was with me when he slipped and hurt his arm.")
        assertNull(chips.firstOrNull { it.field == Field.WITNESSES })
        assertEquals("Rahul", chips.single { it.field == Field.PERSONS_INVOLVED }.proposed)
    }

    @Test fun aNameThatWasNotSaidIsDropped() {
        assertTrue(propose("""{"witnesses":["Meera"]}""", said).none { it.field == Field.WITNESSES })
    }

    @Test fun theSpeakerRolesPlacesAndLowercaseWordsAreNotNames() {
        for (name in listOf("I", "Me", "Colleague", "Guard", "friend", "Conference Room")) {
            assertTrue(propose("""{"witnesses":["$name"]}""", "The guard and my friend were there, a colleague saw it, I was at Conference Room 2.")
                .none { it.field == Field.WITNESSES }, name)
        }
    }

    @Test fun aPlaceInTheLocationIsNotAPerson() {
        val report = empty.with(Field.LOCATION, FieldValue("Maple Hostel", ValueStatus.REPORTED, "Maple Hostel"))
        assertTrue(propose("""{"witnesses":["Maple"]}""", "Maple Hostel has a leak and Maple was there.", report).none { it.field == Field.WITNESSES })
    }

    @Test fun aPersonAlreadyRecordedIsNotProposedAgain() {
        val report = empty.with(Field.PERSONS_INVOLVED, FieldValue("Rahul", ValueStatus.REPORTED, "Rahul"))
        assertTrue(propose("""{"witnesses":["Rahul"]}""", said, report).none { it.field == Field.WITNESSES })
    }

    @Test fun answeredOrSkippedFieldsAreNotProbed() {
        val answered = empty.with(Field.WITNESSES, FieldValue.explicitNone("nobody saw it"))
        assertTrue(propose("""{"witnesses":["Rahul"]}""", said, answered).none { it.field == Field.WITNESSES })
        assertTrue(propose("""{"witnesses":["Rahul"]}""", said, skip = setOf(Field.WITNESSES)).none { it.field == Field.WITNESSES })
    }

    @Test fun anUnreadableReplyCostsOnlyTheChip() {
        assertTrue(propose("not json at all", said).none { it.field == Field.WITNESSES || it.field == Field.PERSONS_INVOLVED })
    }
}

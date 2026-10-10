package com.megathon.incident.priority

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriorityAdvisorTest {
    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))
    private val strategy = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == UrgencyStrategies.DEFAULT_ID }

    private fun advisor() = PriorityAdvisor(extractor, strategy)

    // ---- live scoring per chunk ----------------------------------------------------------------

    @Test fun firstChunkProducesAnAdvice() {
        val a = advisor()
        val advice = a.onChunk("there is a water leak near conference room 2").advice
        assertEquals(Priority.LOW, advice.level)
    }

    @Test fun laterChunksRaiseTheLevel() {
        val a = advisor()
        a.onChunk("there is a water leak near conference room 2")
        val after = a.onChunk("it is spreading towards the server room and sparks are coming out")
        assertTrue(after.advice.level >= Priority.HIGH, "was ${after.advice.level}")
    }

    @Test fun aCalmFollowUpNeverLowersTheLevel() {
        val a = advisor()
        val urgent = a.onChunk("there is a fire in the kitchen").advice.level
        val calm = a.onChunk("everything is fine now, nobody is hurt, it is not urgent").advice.level
        assertTrue(calm >= urgent, "dropped from $urgent to $calm")
    }

    @Test fun resolutionOfAnEarlierHazardDoesNotLowerTheSessionLevel() {
        val a = advisor()
        val first = a.onChunk("there is a fire in the kitchen").advice.level
        val later = a.onChunk("maintenance has put it out and the area is isolated").advice.level
        assertTrue(later >= first)
    }

    @Test fun scoreAndLevelAreMonotonicOverAnyChunkSequence() {
        val pool = listOf(
            "there is a fire in the kitchen", "nobody is hurt", "the leak is fixed", "it is not urgent",
            "someone is unconscious", "maintenance has isolated the area", "it was nothing", "help!!",
            "ghar mein aag lag gayi hai", "sab theek hai", "a pothole near the gate", "",
        )
        repeat(200) { seed ->
            val rnd = Random(seed)
            val a = advisor()
            var previous = 0
            repeat(8) {
                val chunk = pool[rnd.nextInt(pool.size)]
                val score = a.onChunk(chunk).advice.let { _ -> a.currentScore }
                assertTrue(score >= previous, "seed $seed: score fell $previous→$score on '$chunk'")
                previous = score
            }
        }
    }

    // ---- signals -------------------------------------------------------------------------------

    @Test fun aSoundSignalRaisesTheAdvice() {
        val a = advisor()
        val quiet = a.onChunk("there is some noise outside").advice.level
        val loud = a.onChunk("", listOf(Signal("event", "fire alarm", Source.YAMNET, 1))).advice.level
        assertTrue(loud > quiet)
    }

    @Test fun reasonsNameTheirSource() {
        val a = advisor()
        val advice = a.onChunk("", listOf(Signal("event", "screaming", Source.YAMNET, 2))).advice
        assertTrue(advice.reasons.any { it.contains("YAMNET") }, advice.reasons.toString())
    }

    // ---- "call 112" ----------------------------------------------------------------------------

    @Test fun urgentSpeaksTheEmergencyLineOnce() {
        val a = advisor()
        val first = a.onChunk("my house is on fire")
        assertEquals("If anyone is in danger, call 112 now.", first.spoken)
        val second = a.onChunk("the flames are spreading")
        assertNull(second.spoken, "the line must not repeat on every chunk")
    }

    @Test fun lowPriorityNeverSpeaksTheEmergencyLine() {
        val a = advisor()
        assertNull(a.onChunk("there is a pothole near the gate").spoken)
    }

    @Test fun aFireAlarmTriggersTheEmergencyLineEvenBelowUrgent() {
        val a = advisor()
        val out = a.onChunk("", listOf(Signal("event", "fire alarm", Source.YAMNET, 1)))
        assertTrue(out.advice.level < Priority.URGENT, "this test needs a sub-urgent level, was ${out.advice.level}")
        assertEquals("If anyone is in danger, call 112 now.", out.spoken)
    }

    @Test fun aDangerSoundAloneTriggersTheEmergencyLine() {
        val a = advisor()
        val out = a.onChunk("", listOf(Signal("event", "gunshot", Source.YAMNET, 1)))
        assertEquals("If anyone is in danger, call 112 now.", out.spoken)
    }

    // ---- user override -------------------------------------------------------------------------

    @Test fun userCanLowerTheLevelAndItIsJournaled() {
        val a = advisor()
        a.onChunk("there is a fire in the kitchen")
        val overridden = a.override(Priority.LOW, reason = "it was a cooking flame, extinguished")
        assertEquals(Priority.LOW, overridden.level)
        assertTrue(overridden.overriddenByUser)
        assertEquals(1, a.journal.size)
        assertEquals(Priority.URGENT, a.journal.single().from)
        assertEquals(Priority.LOW, a.journal.single().to)
    }

    @Test fun overrideStaysInForceButTheAutomaticLevelKeepsRising() {
        val a = advisor()
        a.onChunk("there is a pothole near the gate")
        a.override(Priority.MEDIUM, reason = "near the school")
        val after = a.onChunk("someone is unconscious now")
        assertTrue(after.advice.level == Priority.URGENT, "automatic level must still surface: ${after.advice.level}")
        assertFalse(after.advice.overriddenByUser, "a new hazard clears the stale override")
    }

    // ---- triage lanes --------------------------------------------------------------------------

    @Test fun urgentLaneAsksOnlyForTheLocationAndOffersSubmitNow() {
        val lane = TriageLanes.forLevel(Priority.URGENT)
        assertEquals(listOf(Field.LOCATION), lane.mandatoryFields)
        assertEquals(1, lane.maxQuestionsPerTurn)
        assertTrue(lane.offerSubmitNow)
        assertTrue(lane.speak112)
        assertFalse(lane.requireConfirmation)
    }

    @Test fun lowLaneIsThoroughAndRequiresConfirmation() {
        val lane = TriageLanes.forLevel(Priority.LOW)
        assertTrue(Field.SEVERITY in lane.mandatoryFields)
        assertTrue(Field.EQUIPMENT_ASSET in lane.mandatoryFields)
        assertTrue(lane.requireConfirmation)
        assertFalse(lane.speak112)
        assertFalse(lane.offerSubmitNow)
    }

    @Test fun laneStrictnessNeverIncreasesWithUrgency() {
        val levels = Priority.entries
        for (i in 0 until levels.size - 1) {
            val calmer = TriageLanes.forLevel(levels[i])
            val hotter = TriageLanes.forLevel(levels[i + 1])
            assertTrue(hotter.mandatoryFields.size <= calmer.mandatoryFields.size, "${levels[i + 1]} asks more than ${levels[i]}")
            assertTrue(hotter.maxQuestionsPerTurn <= calmer.maxQuestionsPerTurn)
        }
    }

    @Test fun everyLaneMatchesItsLevel() = Priority.entries.forEach { assertEquals(it, TriageLanes.forLevel(it).level) }

    @Test fun adviceCanBeQueriedForItsLane() {
        val a = advisor()
        a.onChunk("someone is unconscious in the corridor")
        assertNotNull(a.lane())
        assertEquals(Priority.URGENT, a.lane().level)
    }
}

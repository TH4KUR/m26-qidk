package com.megathon.incident.signals

import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamNetSignalMapperTest {
    private fun map(vararg scores: Pair<String, Float>, segmentId: Long? = null) =
        YamNetSignalMapper.map(scores.map { ClassScore(it.first, it.second) }, segmentId)

    @Test
    fun aFireAlarmBecomesAnEventSignalWithTheLabelThePriorityRulesExpect() {
        val signals = map("Fire alarm" to 0.82f, "Speech" to 0.40f, segmentId = 7)
        assertEquals(listOf(Signal("event", "fire alarm", Source.YAMNET, 7)), signals)
    }

    @Test
    fun theAudioSetDisplayNamesOfOneHazardCollapseToOneSignalKeepingTheBestScore() {
        val signals = map(
            "Smoke detector, smoke alarm" to 0.55f,
            "Fire alarm" to 0.35f,
            "Alarm" to 0.90f,
        )
        assertEquals(listOf("fire alarm"), signals.map { it.label })
    }

    @Test
    fun hazardsAreReportedStrongestFirst() {
        val signals = map("Siren" to 0.45f, "Screaming" to 0.80f, "Explosion" to 0.60f)
        assertEquals(listOf("screaming", "explosion", "siren"), signals.map { it.label })
    }

    @Test
    fun theOtherAudioSetNamesForSirensGunshotsAndBreakingGlassAreMapped() {
        assertEquals("siren", map("Civil defense siren" to 0.6f).single().label)
        assertEquals("siren", map("Emergency vehicle" to 0.6f).single().label)
        assertEquals("gunshot", map("Gunshot, gunfire" to 0.7f).single().label)
        assertEquals("glass breaking", map("Shatter" to 0.6f).single().label)
        assertEquals("crying", map("Crying, sobbing" to 0.6f).single().label)
        assertEquals("running water", map("Pour" to 0.8f).single().label)
        assertEquals("hissing", map("Hiss" to 0.7f).single().label)
    }

    @Test
    fun ordinarySoundsAndWeakScoresProduceNothing() {
        assertTrue(map("Speech" to 0.95f, "Music" to 0.9f, "Silence" to 0.5f, "Door" to 0.7f).isEmpty())
        assertTrue(map("Fire alarm" to 0.10f, "Screaming" to 0.05f).isEmpty())
    }

    @Test
    fun aGenericLabelNeedsMoreEvidenceThanAnAlarm() {
        // Running water is everywhere (taps, rain): it takes a clearly stronger score than a fire alarm does.
        assertTrue(map("Water" to 0.40f).isEmpty())
        assertEquals(listOf("running water"), map("Water" to 0.70f).map { it.label })
        assertEquals(listOf("fire alarm"), map("Fire alarm" to 0.40f).map { it.label })
    }

    @Test
    fun theLabelMatchIgnoresCaseAndSurroundingSpace() {
        assertEquals("fire alarm", map("  FIRE ALARM " to 0.8f).single().label)
    }
}

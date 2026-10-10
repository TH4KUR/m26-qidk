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
        )
        assertEquals(listOf("fire alarm"), signals.map { it.label })
    }

    @Test
    fun aBeepingAlarmTheModelOnlyCallsAlarmStillGivesAGenericSignalNeverFireAlarm() {
        // On a clock-alarm clip the model says "Alarm clock" and "Alarm" and "Fire alarm" only about 0.12; a generic
        // alarm signal keeps it visible without claiming a fire alarm.
        assertEquals(listOf("alarm"), map("Alarm" to 0.60f, "Fire alarm" to 0.12f).map { it.label })
        assertTrue(map("Alarm" to 0.40f).isEmpty(), "a weak generic alarm is not a signal")
        assertTrue(map("Alarm clock" to 0.95f).isEmpty(), "an alarm clock is not an alarm")
        // A real smoke alarm scores both: two labels, the stronger first.
        assertEquals(listOf("alarm", "fire alarm"), map("Fire alarm" to 0.90f, "Alarm" to 0.95f).map { it.label })
    }

    @Test
    fun rainIsNotRunningWater() {
        // "Water" scores 0.96 on rain as well as on a leak; the rain classes tell them apart.
        assertTrue(map("Water" to 0.96f, "Rain" to 0.96f).isEmpty())
        assertTrue(map("Water" to 0.90f, "Rain on surface" to 0.31f).isEmpty())
        assertTrue(map("Water" to 0.90f, "Raindrop" to 0.30f).isEmpty())
        assertEquals(listOf("running water"), map("Water" to 0.60f, "Pour" to 0.80f, "Rain" to 0.10f).map { it.label })
    }

    @Test
    fun breakingGlassAlsoCountsFromBreakingAndFromAWeakerShatter() {
        assertEquals("glass breaking", map("Breaking" to 0.60f).single().label)
        assertTrue(map("Breaking" to 0.40f).isEmpty(), "Breaking alone is generic, so it needs a clear score")
        assertEquals("glass breaking", map("Shatter" to 0.20f).single().label)
        assertTrue(map("Shatter" to 0.10f).isEmpty())
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

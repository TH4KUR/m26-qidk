package com.megathon.incident.tts

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TtsPcmTest {
    @Test
    fun floatsScaleToSixteenBit() {
        assertContentEquals(shortArrayOf(0, 16384, -16384), TtsPcm.toPcm16(floatArrayOf(0f, 0.5f, -0.5f)))
    }

    @Test
    fun outOfRangeSamplesAreClippedNotWrapped() {
        assertContentEquals(shortArrayOf(32767, -32767, 32767), TtsPcm.toPcm16(floatArrayOf(1f, -1f, 3.5f)))
    }

    @Test
    fun notANumberBecomesSilence() {
        assertContentEquals(shortArrayOf(0), TtsPcm.toPcm16(floatArrayOf(Float.NaN)))
    }

    @Test
    fun durationInMilliseconds() {
        assertEquals(1000L, TtsPcm.durationMs(22_050, 22_050))
        assertEquals(0L, TtsPcm.durationMs(0, 22_050))
        assertEquals(0L, TtsPcm.durationMs(100, 0))
    }
}

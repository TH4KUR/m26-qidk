package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhisperKitFeedTest {
    @Test
    fun pcm16BecomesLittleEndianBytes() {
        val bytes = WhisperKitFeed.toPcmBytes(shortArrayOf(1, -1, 0x1234, -32768))
        assertContentEquals(
            byteArrayOf(0x01, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0x34, 0x12, 0x00, 0x80.toByte()),
            bytes,
        )
    }

    @Test
    fun audioIsCutIntoHundredMillisecondChunksWithAShorterLastOne() {
        val bytes = ByteArray(3200 * 2 + 700) { it.toByte() }
        val chunks = WhisperKitFeed.chunks(bytes).toList()
        assertEquals(listOf(3200, 3200, 700), chunks.map { it.size })
        assertContentEquals(bytes, chunks.reduce { a, b -> a + b })
    }

    @Test
    fun emptyAudioHasNoChunks() {
        assertTrue(WhisperKitFeed.chunks(ByteArray(0)).toList().isEmpty())
    }

    @Test
    fun paddingRoundsUpToTheThirtySecondWindow() {
        val window = WhisperKitFeed.WINDOW_BYTES
        assertEquals(960_000, window) // 30 s of 16 kHz PCM16 mono
        assertEquals(window - 32_000, WhisperKitFeed.paddingBytes(32_000)) // 1 s of speech
        assertEquals(0, WhisperKitFeed.paddingBytes(window)) // already a whole window
        assertEquals(0, WhisperKitFeed.paddingBytes(2 * window))
        assertEquals(window - 1, WhisperKitFeed.paddingBytes(window + 1))
        assertEquals(0, WhisperKitFeed.paddingBytes(0)) // nothing was said, nothing to flush
    }

    @Test
    fun anAccumulatorJoinsSegmentTextAndNormalisesWhitespace() {
        val acc = WhisperKitFeed.TranscriptAccumulator()
        acc.onResult("ignored when segments exist", listOf(" water leak ", "in  conference room 2 "))
        assertEquals("water leak in conference room 2", acc.text())
    }

    @Test
    fun aResultWithoutSegmentsFallsBackToItsOwnText() {
        val acc = WhisperKitFeed.TranscriptAccumulator()
        acc.onResult("  hello   there ", emptyList())
        assertEquals("hello there", acc.text())
    }

    @Test
    fun consecutiveRepeatsAndEmptyResultsAreDropped() {
        val acc = WhisperKitFeed.TranscriptAccumulator()
        acc.onResult("first", emptyList())
        acc.onResult("first", emptyList())
        acc.onResult("   ", emptyList())
        acc.onResult("second", emptyList())
        acc.onResult("first", emptyList()) // not consecutive any more, so it is kept
        assertEquals("first second first", acc.text())
    }

    @Test
    fun resetEmptiesTheAccumulatorForTheNextUtterance() {
        val acc = WhisperKitFeed.TranscriptAccumulator()
        acc.onResult("one", emptyList())
        acc.reset()
        assertEquals("", acc.text())
        acc.onResult("one", emptyList()) // the duplicate guard also restarts
        assertEquals("one", acc.text())
    }

    @Test
    fun computeLabelNeverOverclaimsTheNpu() {
        val cpu = 1
        val npu = 3
        assertEquals(Compute.NPU, WhisperKitFeed.computeLabel(npu, npu))
        assertEquals(Compute.CPU, WhisperKitFeed.computeLabel(cpu, cpu))
        assertEquals(Compute.HYBRID, WhisperKitFeed.computeLabel(npu, cpu))
        assertEquals(Compute.HYBRID, WhisperKitFeed.computeLabel(cpu, npu))
        assertEquals(Compute.CPU, WhisperKitFeed.computeLabel(2, 2)) // GPU is not the NPU
    }
}

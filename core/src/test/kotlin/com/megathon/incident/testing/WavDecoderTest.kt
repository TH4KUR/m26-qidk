package com.megathon.incident.testing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WavDecoderTest {
    private fun wav(
        samples: ShortArray = shortArrayOf(-1000, 0, 1000, 32767),
        channels: Int = 1,
        rate: Int = 16_000,
        bits: Int = 16,
        format: Int = 1,
        extraChunkBeforeData: ByteArray? = null,
        dataSizeOverride: Int? = null,
    ): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> samples.forEach(b::putShort) }.array()
        val extra = extraChunkBeforeData?.let { chunk("LIST", it) } ?: ByteArray(0)
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(format.toShort()); putShort(channels.toShort()); putInt(rate)
            putInt(rate * channels * bits / 8); putShort((channels * bits / 8).toShort()); putShort(bits.toShort())
        }.array()
        val body = "WAVE".toByteArray() + chunk("fmt ", fmt) + extra + chunk("data", data, dataSizeOverride)
        return "RIFF".toByteArray() + le(body.size) + body
    }

    private fun le(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun chunk(id: String, payload: ByteArray, sizeOverride: Int? = null): ByteArray {
        val pad = if (payload.size % 2 == 1) byteArrayOf(0) else ByteArray(0)
        return id.toByteArray() + le(sizeOverride ?: payload.size) + payload + pad
    }

    @Test
    fun decodesPcm16MonoAt16kHz() {
        assertContentEquals(shortArrayOf(-1000, 0, 1000, 32767), WavDecoder.decode(wav()))
    }

    @Test
    fun skipsChunksItDoesNotKnowIncludingOddSizedOnes() {
        // Recorders add LIST/INFO chunks; an odd-sized chunk is followed by a pad byte.
        val decoded = WavDecoder.decode(wav(extraChunkBeforeData = ByteArray(5) { 7 }))
        assertContentEquals(shortArrayOf(-1000, 0, 1000, 32767), decoded)
    }

    @Test
    fun anEmptyDataChunkIsEmptyAudio() {
        assertEquals(0, WavDecoder.decode(wav(samples = ShortArray(0))).size)
    }

    @Test
    fun rejectsStereo() {
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(wav(channels = 2)) }.also { assertContains(it, "mono") }
    }

    @Test
    fun rejectsOtherSampleRates() {
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(wav(rate = 44_100)) }.also { assertContains(it, "16 kHz") }
    }

    @Test
    fun rejectsEightBitAndFloatAudio() {
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(wav(bits = 8)) }.also { assertContains(it, "16-bit") }
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(wav(format = 3)) }.also { assertContains(it, "PCM") }
    }

    @Test
    fun rejectsATruncatedDataChunk() {
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(wav(dataSizeOverride = 1_000_000)) }
            .also { assertContains(it, "beyond") }
    }

    @Test
    fun rejectsFilesThatAreNotWav() {
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(ByteArray(4)) }.also { assertContains(it, "short") }
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode("RIFX".toByteArray() + ByteArray(20)) }.also { assertContains(it, "RIFF") }
    }

    @Test
    fun rejectsAFileWithNoDataChunk() {
        val noData = "WAVE".toByteArray() + chunk("fmt ", ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
        }.array())
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode("RIFF".toByteArray() + le(noData.size) + noData) }
            .also { assertContains(it, "data") }
    }

    @Test
    fun theErrorNamesTheSourceWhenOneIsGiven() {
        assertFailsWith<IllegalArgumentException> { WavDecoder.decode(wav(channels = 2), source = "clips/a01.wav") }
            .also { assertContains(it, "clips/a01.wav") }
    }

    private fun assertContains(error: Throwable, text: String) {
        kotlin.test.assertTrue(text in error.message.orEmpty(), "expected \"$text\" in: ${error.message}")
    }
}

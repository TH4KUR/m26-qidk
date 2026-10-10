package com.megathon.incident.testing

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertContentEquals
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class WavFileSourceTest {
    @Test
    fun readsPcm16MonoSamplesFromWav() {
        val samples = shortArrayOf(-1000, 0, 1000)
        val wav = Files.createTempFile("speech", ".wav")
        Files.write(wav, pcm16MonoWav(samples))

        try {
            val actual = SpeechEngineContractTestRunner.run {
                WavFileSource(wav).readPcm()
            }
            assertContentEquals(samples, actual)
        } finally {
            Files.deleteIfExists(wav)
        }
    }

    @Test
    fun theFileIsReadOffTheCallersThread() = runBlocking {
        val wav = Files.createTempFile("speech", ".wav")
        Files.write(wav, pcm16MonoWav(shortArrayOf(1, 2, 3)))
        try {
            val callerThread = Thread.currentThread().name
            var readerThread = ""
            val source = WavFileSource(wav, readBytes = { path -> readerThread = Thread.currentThread().name; Files.readAllBytes(path) })
            source.readPcm()
            assertNotEquals(callerThread, readerThread, "a blocking read must not run on the caller's dispatcher")
        } finally {
            Files.deleteIfExists(wav)
        }
    }

    private fun pcm16MonoWav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray())
        bytes.putInt(36 + dataSize)
        bytes.put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray())
        bytes.putInt(16)
        bytes.putShort(1)
        bytes.putShort(1)
        bytes.putInt(16_000)
        bytes.putInt(32_000)
        bytes.putShort(2)
        bytes.putShort(16)
        bytes.put("data".toByteArray())
        bytes.putInt(dataSize)
        samples.forEach(bytes::putShort)
        return bytes.array()
    }
}

package com.megathon.incident.testing

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the 16 kHz, mono, PCM16 WAV format used by the recording and Judge Bench paths.
 */
class WavFileSource(private val wavPath: Path) : AudioSource {
    override suspend fun readPcm(): ShortArray {
        val bytes = Files.readAllBytes(wavPath)
        require(bytes.size >= 12) { "WAV file is too short: $wavPath" }
        require(ascii(bytes, 0, 4) == "RIFF") { "WAV file is missing RIFF header: $wavPath" }
        require(ascii(bytes, 8, 4) == "WAVE") { "WAV file is missing WAVE header: $wavPath" }

        var offset = 12
        var pcmData: ByteArray? = null
        var validFormat = false

        while (offset + 8 <= bytes.size) {
            val chunkId = ascii(bytes, offset, 4)
            val chunkSize = littleEndianInt(bytes, offset + 4)
            require(chunkSize >= 0) { "WAV chunk is too large: $wavPath" }
            val payloadStart = offset + 8
            val payloadEnd = payloadStart + chunkSize
            require(payloadEnd >= payloadStart && payloadEnd <= bytes.size) {
                "WAV chunk extends beyond file: $wavPath"
            }

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16) { "WAV fmt chunk is too short: $wavPath" }
                    require(littleEndianShort(bytes, payloadStart) == 1) {
                        "Only PCM WAV files are supported: $wavPath"
                    }
                    require(littleEndianShort(bytes, payloadStart + 2) == 1) {
                        "Only mono WAV files are supported: $wavPath"
                    }
                    require(littleEndianInt(bytes, payloadStart + 4) == 16_000) {
                        "Only 16 kHz WAV files are supported: $wavPath"
                    }
                    require(littleEndianShort(bytes, payloadStart + 14) == 16) {
                        "Only 16-bit WAV files are supported: $wavPath"
                    }
                    validFormat = true
                }

                "data" -> pcmData = bytes.copyOfRange(payloadStart, payloadEnd)
            }

            offset = payloadEnd + (chunkSize and 1)
        }

        require(validFormat) { "WAV fmt chunk not found: $wavPath" }
        val data = requireNotNull(pcmData) { "WAV data chunk not found: $wavPath" }
        require(data.size % 2 == 0) { "WAV PCM16 data has an odd byte count: $wavPath" }

        return ShortArray(data.size / 2) { index ->
            littleEndianShort(data, index * 2).toShort()
        }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

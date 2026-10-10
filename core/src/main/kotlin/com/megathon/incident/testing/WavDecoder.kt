package com.megathon.incident.testing

/**
 * Decodes the one WAV format the app and the Judge Bench use: PCM, 16 kHz, mono, 16-bit. Works on bytes so it
 * serves files (`WavFileSource`) and Android assets alike. Anything else is rejected with a message naming
 * the source, because a wrong sample rate silently ruins recognition rather than failing.
 */
object WavDecoder {
    fun decode(bytes: ByteArray, source: String = "WAV data"): ShortArray {
        fun fail(message: String): Nothing = throw IllegalArgumentException("$message: $source")

        if (bytes.size < 12) fail("WAV file is too short")
        if (ascii(bytes, 0, 4) != "RIFF") fail("WAV file is missing RIFF header")
        if (ascii(bytes, 8, 4) != "WAVE") fail("WAV file is missing WAVE header")

        var offset = 12
        var pcmData: ByteArray? = null
        var validFormat = false

        while (offset + 8 <= bytes.size) {
            val chunkId = ascii(bytes, offset, 4)
            val chunkSize = littleEndianInt(bytes, offset + 4)
            if (chunkSize < 0) fail("WAV chunk is too large")
            val payloadStart = offset + 8
            val payloadEnd = payloadStart + chunkSize
            if (payloadEnd < payloadStart || payloadEnd > bytes.size) fail("WAV chunk extends beyond file")

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) fail("WAV fmt chunk is too short")
                    if (littleEndianShort(bytes, payloadStart) != 1) fail("Only PCM WAV files are supported")
                    if (littleEndianShort(bytes, payloadStart + 2) != 1) fail("Only mono WAV files are supported")
                    if (littleEndianInt(bytes, payloadStart + 4) != 16_000) fail("Only 16 kHz WAV files are supported")
                    if (littleEndianShort(bytes, payloadStart + 14) != 16) fail("Only 16-bit WAV files are supported")
                    validFormat = true
                }

                "data" -> pcmData = bytes.copyOfRange(payloadStart, payloadEnd)
            }

            offset = payloadEnd + (chunkSize and 1)
        }

        if (!validFormat) fail("WAV fmt chunk not found")
        val data = pcmData ?: fail("WAV data chunk not found")
        if (data.size % 2 != 0) fail("WAV PCM16 data has an odd byte count")

        return ShortArray(data.size / 2) { index -> littleEndianShort(data, index * 2).toShort() }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

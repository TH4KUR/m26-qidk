package com.megathon.incident.testing

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the 16 kHz, mono, PCM16 WAV format used by the recording and Judge Bench paths.
 */
class WavFileSource(private val wavPath: Path) : AudioSource {
    override suspend fun readPcm(): ShortArray = WavDecoder.decode(Files.readAllBytes(wavPath), wavPath.toString())
}

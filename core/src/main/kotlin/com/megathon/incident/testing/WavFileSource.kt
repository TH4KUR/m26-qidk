package com.megathon.incident.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the 16 kHz, mono, PCM16 WAV format used by the recording and Judge Bench paths. The read is blocking
 * file I/O, so it runs on the I/O dispatcher and never on the caller's thread (which may be the UI thread).
 */
class WavFileSource(
    private val wavPath: Path,
    private val readBytes: (Path) -> ByteArray = Files::readAllBytes,
) : AudioSource {
    override suspend fun readPcm(): ShortArray =
        withContext(Dispatchers.IO) { WavDecoder.decode(readBytes(wavPath), wavPath.toString()) }
}

package com.megathon.incident.tts

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Absolute paths of the files OfflineTts needs for one Piper voice. */
data class PiperModelFiles(val model: String, val tokens: String, val dataDir: String)

sealed interface PiperLocation {
    data class Found(val files: PiperModelFiles) : PiperLocation

    /** Names of what is not on the device (relative to the voice folder). */
    data class Missing(val missing: List<String>) : PiperLocation

    /** Everything is there but the model is not the file the app expects (wrong size or hash). */
    data class Corrupt(val reason: String) : PiperLocation
}

/**
 * Finds a voice's files under the app's TTS folder (`files/tts/<folder>/`) and, when asked, checks the model's size
 * and SHA-256 against the catalogue. Pure file access, so it is tested on the JVM with temporary folders.
 */
object PiperModelLocator {
    private val espeakCommon = listOf("phontab", "phondata", "phonindex", "intonations")

    fun locate(root: File, voice: PiperVoice, verifyHash: Boolean): PiperLocation {
        val dir = File(root, voice.folder)
        val model = File(dir, voice.modelFile)
        val tokens = File(dir, "tokens.txt")
        val espeak = File(dir, "espeak-ng-data")
        val missing = buildList {
            if (!model.isFile) add(voice.modelFile)
            if (!tokens.isFile) add("tokens.txt")
            if (!espeak.isDirectory) {
                add("espeak-ng-data")
            } else {
                val dict = voice.espeakVoice.substringBefore('-') + "_dict"
                (espeakCommon + dict).filter { !File(espeak, it).isFile }.forEach { add("espeak-ng-data/$it") }
            }
        }
        if (missing.isNotEmpty()) return PiperLocation.Missing(missing)

        if (model.length() != voice.modelBytes) {
            return PiperLocation.Corrupt("${voice.modelFile} has size ${model.length()}, expected ${voice.modelBytes}")
        }
        if (verifyHash) {
            val actual = sha256(model)
            if (!actual.equals(voice.modelSha256, ignoreCase = true)) {
                return PiperLocation.Corrupt("${voice.modelFile} sha256 is $actual, expected ${voice.modelSha256}")
            }
        }
        return PiperLocation.Found(PiperModelFiles(model.path, tokens.path, espeak.path))
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

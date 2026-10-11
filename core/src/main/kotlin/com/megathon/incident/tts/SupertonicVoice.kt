package com.megathon.incident.tts

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** One model file as released: its name, exact size and SHA-256. */
data class SupertonicFile(val name: String, val bytes: Long, val sha256: String)

/** Absolute paths of the seven files `OfflineTtsSupertonicModelConfig` needs. */
data class SupertonicModelFiles(
    val durationPredictor: String,
    val textEncoder: String,
    val vectorEstimator: String,
    val vocoder: String,
    val ttsJson: String,
    val unicodeIndexer: String,
    val voiceStyle: String,
)

/**
 * The Supertonic text-to-speech model as shipped in the sherpa-onnx `tts-models` release: one model for many languages,
 * the language chosen per call. [folder] is the extracted release folder (the same name is used on the kit under `files/tts/`).
 */
data class SupertonicVoice(
    val folder: String,
    val durationPredictor: SupertonicFile,
    val textEncoder: SupertonicFile,
    val vectorEstimator: SupertonicFile,
    val vocoder: SupertonicFile,
    val ttsJson: SupertonicFile,
    val unicodeIndexer: SupertonicFile,
    val voiceStyle: SupertonicFile,
    val label: String,
    val licence: String,
    val languages: Set<String>,
) {
    val all: List<SupertonicFile> =
        listOf(durationPredictor, textEncoder, vectorEstimator, vocoder, ttsJson, unicodeIndexer, voiceStyle)

    /** Blank or missing means English (the app's default); the region is ignored (`hi-IN`, `EN_us`). */
    fun supports(language: String?): Boolean {
        val base = language?.trim()?.lowercase()?.split('-', '_')?.firstOrNull().orEmpty()
        return base.isEmpty() || base in languages
    }
}

/** Telugu is not here on purpose: this model does not cover it (the app falls back to the Android voice and says so). */
object SupertonicVoices {
    val multilingual = SupertonicVoice(
        folder = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
        durationPredictor = SupertonicFile("duration_predictor.int8.onnx", 3_700_147L, "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
        textEncoder = SupertonicFile("text_encoder.int8.onnx", 36_416_150L, "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
        vectorEstimator = SupertonicFile("vector_estimator.int8.onnx", 78_400_833L, "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"),
        vocoder = SupertonicFile("vocoder.int8.onnx", 25_991_073L, "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"),
        ttsJson = SupertonicFile("tts.json", 8_253L, "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
        unicodeIndexer = SupertonicFile("unicode_indexer.bin", 262_144L, "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"),
        voiceStyle = SupertonicFile("voice.bin", 517_168L, "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"),
        label = "Supertonic 3 (sherpa-onnx int8, Hindi and English)",
        licence = "model weights OpenRAIL-M, sample code MIT (Supertone Inc.)",
        languages = setOf("en", "hi"),
    )
}

sealed interface SupertonicLocation {
    data class Found(val files: SupertonicModelFiles) : SupertonicLocation

    /** Names of the files that are not on the device (relative to the model folder). */
    data class Missing(val missing: List<String>) : SupertonicLocation

    /** Everything is there but a file is not the one the app expects (wrong size or hash). */
    data class Corrupt(val reason: String) : SupertonicLocation
}

/** Finds the model's files under `files/tts/<folder>/` and checks size (always) and SHA-256 (when asked). Pure file access. */
object SupertonicModelLocator {
    fun locate(root: File, voice: SupertonicVoice, verifyHash: Boolean): SupertonicLocation {
        val dir = File(root, voice.folder)
        val missing = voice.all.filter { !File(dir, it.name).isFile }.map { it.name }
        if (missing.isNotEmpty()) return SupertonicLocation.Missing(missing)

        for (file in voice.all) {
            val actual = File(dir, file.name).length()
            if (actual != file.bytes) return SupertonicLocation.Corrupt("${file.name} has size $actual, expected ${file.bytes}")
        }
        if (verifyHash) {
            for (file in voice.all) {
                val actual = sha256(File(dir, file.name))
                if (!actual.equals(file.sha256, ignoreCase = true)) {
                    return SupertonicLocation.Corrupt("${file.name} sha256 is $actual, expected ${file.sha256}")
                }
            }
        }
        fun path(f: SupertonicFile) = File(dir, f.name).path
        return SupertonicLocation.Found(
            SupertonicModelFiles(
                durationPredictor = path(voice.durationPredictor), textEncoder = path(voice.textEncoder),
                vectorEstimator = path(voice.vectorEstimator), vocoder = path(voice.vocoder), ttsJson = path(voice.ttsJson),
                unicodeIndexer = path(voice.unicodeIndexer), voiceStyle = path(voice.voiceStyle),
            ),
        )
    }

    private fun sha256(file: File): String {
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

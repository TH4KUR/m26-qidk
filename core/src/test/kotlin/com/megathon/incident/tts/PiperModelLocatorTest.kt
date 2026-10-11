package com.megathon.incident.tts

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PiperModelLocatorTest {
    private val root: File = Files.createTempDirectory("tts-root").toFile()

    @AfterTest
    fun cleanUp() { root.deleteRecursively() }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val modelBytes = ByteArray(2048) { (it % 251).toByte() }

    private fun voice() = PiperVoice(
        language = "en", folder = "vits-test-voice", modelFile = "m.onnx", espeakVoice = "en-us", label = "Piper test voice",
        licence = "test", modelBytes = modelBytes.size.toLong(), modelSha256 = sha(modelBytes),
    )

    private fun lay(
        model: ByteArray? = modelBytes,
        tokens: Boolean = true,
        espeak: List<String> = listOf("phontab", "phondata", "phonindex", "intonations", "en_dict"),
    ): File {
        val dir = File(root, "vits-test-voice").apply { mkdirs() }
        if (model != null) File(dir, "m.onnx").writeBytes(model)
        if (tokens) File(dir, "tokens.txt").writeText("a 1\n")
        if (espeak.isNotEmpty()) {
            val e = File(dir, "espeak-ng-data").apply { mkdirs() }
            espeak.forEach { File(e, it).writeText("x") }
        }
        return dir
    }

    @Test
    fun aCompleteFolderIsFoundWithAbsolutePaths() {
        val dir = lay()
        val found = assertIs<PiperLocation.Found>(PiperModelLocator.locate(root, voice(), verifyHash = false))
        assertEquals(File(dir, "m.onnx").path, found.files.model)
        assertEquals(File(dir, "tokens.txt").path, found.files.tokens)
        assertEquals(File(dir, "espeak-ng-data").path, found.files.dataDir)
    }

    @Test
    fun aMissingModelIsReportedByName() {
        lay(model = null)
        val r = assertIs<PiperLocation.Missing>(PiperModelLocator.locate(root, voice(), verifyHash = false))
        assertTrue(r.missing.contains("m.onnx"), r.missing.toString())
    }

    @Test
    fun missingTokensAndEspeakDataAreAllListed() {
        lay(tokens = false, espeak = emptyList())
        val r = assertIs<PiperLocation.Missing>(PiperModelLocator.locate(root, voice(), verifyHash = false))
        assertTrue(r.missing.containsAll(listOf("tokens.txt", "espeak-ng-data")), r.missing.toString())
    }

    @Test
    fun anIncompleteEspeakFolderIsMissing() {
        lay(espeak = listOf("phontab"))
        val r = assertIs<PiperLocation.Missing>(PiperModelLocator.locate(root, voice(), verifyHash = false))
        assertTrue(r.missing.any { it.startsWith("espeak-ng-data/") }, r.missing.toString())
    }

    @Test
    fun noFolderAtAllIsMissing() {
        val r = assertIs<PiperLocation.Missing>(PiperModelLocator.locate(root, voice(), verifyHash = false))
        assertTrue(r.missing.isNotEmpty())
    }

    @Test
    fun aWrongSizeFailsVerification() {
        lay(model = ByteArray(10))
        val r = assertIs<PiperLocation.Corrupt>(PiperModelLocator.locate(root, voice(), verifyHash = true))
        assertTrue(r.reason.contains("size"), r.reason)
    }

    @Test
    fun aWrongHashFailsVerification() {
        lay(model = ByteArray(modelBytes.size) { 7 })
        val r = assertIs<PiperLocation.Corrupt>(PiperModelLocator.locate(root, voice(), verifyHash = true))
        assertTrue(r.reason.contains("sha256"), r.reason)
    }

    @Test
    fun aMatchingHashPassesVerification() {
        lay()
        assertIs<PiperLocation.Found>(PiperModelLocator.locate(root, voice(), verifyHash = true))
    }

    @Test
    fun hashIsNotCheckedWhenNotAsked() {
        lay(model = ByteArray(modelBytes.size) { 7 })
        assertIs<PiperLocation.Found>(PiperModelLocator.locate(root, voice(), verifyHash = false))
    }
}

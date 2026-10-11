package com.megathon.incident.tts

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SupertonicModelLocatorTest {
    private val root: File = Files.createTempDirectory("tts-root").toFile()

    @AfterTest
    fun cleanUp() { root.deleteRecursively() }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val content = mapOf(
        "dp.onnx" to ByteArray(10) { 1 },
        "te.onnx" to ByteArray(20) { 2 },
        "ve.onnx" to ByteArray(30) { 3 },
        "vo.onnx" to ByteArray(40) { 4 },
        "tts.json" to ByteArray(5) { 5 },
        "idx.bin" to ByteArray(6) { 6 },
        "voice.bin" to ByteArray(7) { 7 },
    )

    private fun file(name: String) = SupertonicFile(name, content.getValue(name).size.toLong(), sha(content.getValue(name)))

    private fun voice() = SupertonicVoice(
        folder = "supertonic-test",
        durationPredictor = file("dp.onnx"), textEncoder = file("te.onnx"), vectorEstimator = file("ve.onnx"),
        vocoder = file("vo.onnx"), ttsJson = file("tts.json"), unicodeIndexer = file("idx.bin"), voiceStyle = file("voice.bin"),
        label = "Supertonic test voice", licence = "test", languages = setOf("en", "hi"),
    )

    private fun lay(skip: Set<String> = emptySet(), override: Map<String, ByteArray> = emptyMap()): File {
        val dir = File(root, "supertonic-test").apply { mkdirs() }
        for ((name, bytes) in content) if (name !in skip) File(dir, name).writeBytes(override[name] ?: bytes)
        return dir
    }

    @Test
    fun aCompleteFolderIsFoundWithAbsolutePathsForEveryFile() {
        val dir = lay()
        val found = assertIs<SupertonicLocation.Found>(SupertonicModelLocator.locate(root, voice(), verifyHash = false))
        assertEquals(File(dir, "dp.onnx").path, found.files.durationPredictor)
        assertEquals(File(dir, "te.onnx").path, found.files.textEncoder)
        assertEquals(File(dir, "ve.onnx").path, found.files.vectorEstimator)
        assertEquals(File(dir, "vo.onnx").path, found.files.vocoder)
        assertEquals(File(dir, "tts.json").path, found.files.ttsJson)
        assertEquals(File(dir, "idx.bin").path, found.files.unicodeIndexer)
        assertEquals(File(dir, "voice.bin").path, found.files.voiceStyle)
    }

    @Test
    fun missingFilesAreNamedSoThePushScriptCanBeRerun() {
        lay(skip = setOf("ve.onnx", "voice.bin"))
        val missing = assertIs<SupertonicLocation.Missing>(SupertonicModelLocator.locate(root, voice(), verifyHash = false))
        assertEquals(listOf("ve.onnx", "voice.bin"), missing.missing)
    }

    @Test
    fun aMissingFolderListsEveryFile() {
        val missing = assertIs<SupertonicLocation.Missing>(SupertonicModelLocator.locate(root, voice(), verifyHash = false))
        assertEquals(7, missing.missing.size)
    }

    @Test
    fun aTruncatedFileIsCorruptEvenWhenNoHashIsAsked() {
        lay(override = mapOf("te.onnx" to ByteArray(3)))
        val corrupt = assertIs<SupertonicLocation.Corrupt>(SupertonicModelLocator.locate(root, voice(), verifyHash = false))
        assertTrue("te.onnx" in corrupt.reason, corrupt.reason)
        assertTrue("20" in corrupt.reason, corrupt.reason)
    }

    @Test
    fun aSameSizedButDifferentFileIsOnlyCaughtWhenTheHashIsChecked() {
        lay(override = mapOf("vo.onnx" to ByteArray(40) { 9 }))
        assertIs<SupertonicLocation.Found>(SupertonicModelLocator.locate(root, voice(), verifyHash = false))
        val corrupt = assertIs<SupertonicLocation.Corrupt>(SupertonicModelLocator.locate(root, voice(), verifyHash = true))
        assertTrue("vo.onnx" in corrupt.reason, corrupt.reason)
    }

    @Test
    fun aCorrectFolderPassesTheHashCheck() {
        lay()
        assertIs<SupertonicLocation.Found>(SupertonicModelLocator.locate(root, voice(), verifyHash = true))
    }

    @Test
    fun theCatalogueCoversHindiAndEnglishAndNeverTelugu() {
        val v = SupertonicVoices.multilingual
        assertTrue(v.supports("hi"))
        assertTrue(v.supports("hi-IN"))
        assertTrue(v.supports("EN_us"))
        assertTrue(v.supports(null)) // blank means English, like Piper
        assertFalse(v.supports("te"))
        assertFalse(v.supports("fr"))
    }

    @Test
    fun theCatalogueNamesTheSevenFilesTheModelNeedsWithTheirRecordedSizes() {
        val v = SupertonicVoices.multilingual
        assertEquals(7, v.all.size)
        // 3,700,147 + 36,416,150 + 78,400,833 + 25,991,073 + 8,253 + 262,144 + 517,168, measured on the downloaded release.
        assertEquals(145_295_768L, v.all.sumOf { it.bytes })
        assertEquals(78_400_833L, v.vectorEstimator.bytes)
        assertEquals(517_168L, v.voiceStyle.bytes)
    }
}

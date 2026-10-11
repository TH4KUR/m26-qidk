package com.megathon.incident.speech

import com.megathon.incident.testing.Compute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NemotronHindiCommandTest {
    private val paths = NemotronHindiPaths(
        cli = "/data/app/x/lib/arm64/libsherpa_online.so",
        nativeLibDir = "/data/app/x/lib/arm64",
        qnnDir = "/data/data/p/files/sherpa/qnn",
        modelDir = "/data/data/p/files/sherpa/nemotron-hi",
    )
    private val wav = "/data/data/p/cache/utterance1.wav"
    private val command = NemotronHindiCommand.build(paths, "hi", wav)

    @Test
    fun runsTheOnlineCliAsTheFirstArgumentAndTheWavLast() {
        assertEquals(paths.cli, command.args.first())
        assertEquals(wav, command.args.last())
    }

    @Test
    fun usesTheNemoTransducerModelTypeWithoutWhichTheOnlineCliRefusesTheBundle() {
        assertTrue("--model-type=nemo_transducer" in command.args)
    }

    @Test
    fun asksForTheQnnProviderAndHindi() {
        assertTrue("--provider=qnn" in command.args)
        assertTrue("--language=hi" in command.args)
    }

    @Test
    fun givesTheThreeContextBinariesCommaSeparatedInTheOrderEncoderDecoderJoiner() {
        val flag = command.args.single { it.startsWith("--transducer.qnn-context-binary=") }
        assertEquals(
            "--transducer.qnn-context-binary=${paths.modelDir}/encoder.bin,${paths.modelDir}/decoder.bin,${paths.modelDir}/joiner.bin",
            flag,
        )
    }

    @Test
    fun pointsTheQnnBackendAndSystemLibrariesAtTheSherpaQnnFolderNeverAnythingElse() {
        assertTrue("--transducer.qnn-backend-lib=${paths.qnnDir}/libQnnHtp.so" in command.args)
        assertTrue("--transducer.qnn-system-lib=${paths.qnnDir}/libQnnSystem.so" in command.args)
        assertTrue("--tokens=${paths.modelDir}/tokens.txt" in command.args)
    }

    @Test
    fun theDspSearchPathIsSemicolonSeparatedAndStartsWithTheQnn240Folder() {
        val adsp = command.environment.getValue("ADSP_LIBRARY_PATH")
        assertEquals("${paths.qnnDir};/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp", adsp)
        assertTrue(';' in adsp && ':' !in adsp)
    }

    @Test
    fun theLibraryPathIsTheSameOneTheWhisperChildGetsSoOneLibraryFolderServesBoth() {
        // SherpaCliSpeechEngine: "<qnnDir>:<nativeDir>:/system/lib64:/vendor/lib64". libonnxruntime.so is the same file in both.
        assertEquals(
            "${paths.qnnDir}:${paths.nativeLibDir}:/system/lib64:/vendor/lib64",
            command.environment.getValue("LD_LIBRARY_PATH"),
        )
    }

    @Test
    fun runsFromTheModelFolderLikeTheVerifiedInvocation() {
        assertEquals(paths.modelDir, command.workingDir)
    }

    @Test
    fun theEnvironmentCarriesOnlyTheTwoSearchPathsSoNothingFromGeniexLeaksIn() {
        assertEquals(setOf("LD_LIBRARY_PATH", "ADSP_LIBRARY_PATH"), command.environment.keys)
        assertTrue(command.environment.values.none { "2.45" in it || "geniex" in it.lowercase() })
    }

    @Test
    fun noEnglishWhisperFlagsAppear() {
        assertTrue(command.args.none { it.startsWith("--whisper") })
        assertTrue(command.args.none { it == "--language=en" })
    }

    @Test
    fun refusesAnyLanguageButHindiInsteadOfSilentlyFeedingTheHindiModelOtherAudio() {
        for (language in listOf("en", "te", "fr", "")) {
            assertFailsWith<IllegalArgumentException>("language=$language") { NemotronHindiCommand.build(paths, language, wav) }
        }
    }

    @Test
    fun acceptsTheRegionFormOfHindi() {
        assertTrue("--language=hi" in NemotronHindiCommand.build(paths, "hi-IN", wav).args)
    }

    @Test
    fun computeIsNpuOnlyWhenTheRunUsedTheQnnProvider() {
        assertEquals(Compute.NPU, NemotronHindiCommand.computeFor(command.args))
        assertEquals(Compute.CPU, NemotronHindiCommand.computeFor(command.args.filter { it != "--provider=qnn" }))
        assertEquals(Compute.CPU, NemotronHindiCommand.computeFor(command.args.map { if (it == "--provider=qnn") "--provider=cpu" else it }))
    }
}

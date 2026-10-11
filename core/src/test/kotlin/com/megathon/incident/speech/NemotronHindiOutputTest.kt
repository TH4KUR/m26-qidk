package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NemotronHindiOutputTest {
    // A fictional sentence: "there is a fire on the first floor".
    private val hindi = "पहली मंज़िल पर आग लगी है"
    private val json = """{"lang": "", "text": "$hindi", "timestamps": [0.00, 0.48, 0.96], "tokens": ["▁पहली", "▁मंज़िल", "▁पर"], "words": []}"""

    private fun heard(output: String, exit: Int = 0): String =
        assertIs<NemotronHindiResult.Heard>(NemotronHindiOutput.parse(output, exit)).text

    private fun failed(output: String, exit: Int = 0): String =
        assertIs<NemotronHindiResult.Failed>(NemotronHindiOutput.parse(output, exit)).reason

    @Test
    fun readsTheTextFieldFromTheJsonLine() {
        assertEquals(hindi, heard(json))
    }

    @Test
    fun readsItWhenWarningsAndTheHumanLineSurroundTheJson() {
        val output = listOf(
            "<W> [QnnDsp] Initializing HtpProvider",
            "<W> [QnnDsp] could not find {optional} config",
            "OnlineRecognizerConfig(feat_config=FeatureExtractorConfig(sampling_rate=16000))",
            "/data/data/p/cache/utterance1.wav",
            json,
            "num threads: 1",
            "<W> [QnnDsp] DspTransport.openSession qnn_open failed, 0x80000406",
            "Elapsed seconds: 0.9 s",
        ).joinToString("\n")
        assertEquals(hindi, heard(output))
    }

    @Test
    fun readsEscapedUnicodeAndTrimsTheText() {
        val escaped = """{"text": "  आग लगी  ", "tokens": []}"""
        assertEquals("आग लगी", heard(escaped))
    }

    @Test
    fun handlesWindowsLineEndings() {
        assertEquals(hindi, heard("/x.wav\r\n$json\r\nDone\r\n"))
    }

    @Test
    fun aJsonLineThatIsSilenceIsAnEmptyHeardNotAFailure() {
        // The model ran and heard nothing: that is a real, blank transcript (the contract says silence gives blank text).
        val result = NemotronHindiOutput.parse("""{"text": "", "tokens": []}""", 0)
        assertEquals(NemotronHindiResult.Heard(""), result)
    }

    @Test
    fun emptyOutputIsAFailureNotAnEmptyTranscript() {
        for (output in listOf("", "   ", "\n\n")) {
            assertTrue("no output" in failed(output), "output=[$output]")
        }
    }

    @Test
    fun noJsonLineIsAFailure() {
        val reason = failed("<W> [QnnDsp] something\n/x.wav\nElapsed seconds: 1.0 s\n")
        assertTrue("result" in reason, reason)
    }

    @Test
    fun aNonZeroExitIsAFailureEvenWhenAJsonLineIsPresent() {
        val reason = failed(json, exit = 1)
        assertTrue("exit" in reason && "1" in reason, reason)
    }

    @Test
    fun aNonZeroExitCarriesTheTailOfTheLogSoTheOperatorCanSeeWhy() {
        val reason = failed("line one\nQNN error: context binary does not match the SoC", exit = 134)
        assertTrue("134" in reason)
        assertTrue("does not match the SoC" in reason, reason)
    }

    @Test
    fun theTailIsBoundedSoAHugeLogCannotFloodTheScreen() {
        val reason = failed("x".repeat(100_000), exit = 2)
        assertTrue(reason.length < 700, "length=${reason.length}")
    }

    @Test
    fun garbledJsonIsAFailure() {
        for (output in listOf("""{"text": "आग लगी""", """{"text": "a" "tokens": [}""", """{"text": }""", """{"text"""")) {
            val reason = failed(output)
            assertTrue("unreadable" in reason || "result" in reason, "output=[$output] reason=$reason")
        }
    }

    @Test
    fun jsonWithoutATextFieldIsAFailure() {
        val reason = failed("""{"lang": "", "tokens": ["a"]}""")
        assertTrue("result" in reason, reason)
    }

    @Test
    fun aTextFieldThatIsNotAStringIsAFailure() {
        failed("""{"text": 42}""")
        failed("""{"text": null}""")
        failed("""{"text": ["आग"]}""")
    }

    @Test
    fun garbledJsonFollowedByAGoodLineStillGivesTheGoodLine() {
        assertEquals(hindi, heard("""{"text": "trunc""" + "\n" + json))
    }

    @Test
    fun anEmptyTranscriptIsNeverReportedForAnythingButARealBlankResult() {
        // Every non-Heard outcome is a Failed with a non-blank reason.
        val outcomes = listOf(
            NemotronHindiOutput.parse("", 0),
            NemotronHindiOutput.parse("noise", 0),
            NemotronHindiOutput.parse("""{"text": """, 0),
            NemotronHindiOutput.parse(json, 3),
        )
        assertTrue(outcomes.all { it is NemotronHindiResult.Failed && it.reason.isNotBlank() })
    }
}

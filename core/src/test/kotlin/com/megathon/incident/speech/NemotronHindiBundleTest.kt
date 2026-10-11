package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NemotronHindiBundleTest {
    private val complete: Map<String, Long> = mapOf(
        "encoder.bin" to 610_848_768L,
        "decoder.bin" to 29_978_624L,
        "joiner.bin" to 9_732_096L,
        "tokens.txt" to 5_000L,
    )

    @Test
    fun aCompleteBundleHasNoProblems() {
        assertEquals(emptyList(), NemotronHindiBundle.problems { complete[it] })
    }

    @Test
    fun aMissingFileIsNamed() {
        val problems = NemotronHindiBundle.problems { if (it == "joiner.bin") null else complete[it] }
        assertEquals(1, problems.size)
        assertTrue("joiner.bin" in problems.single() && "missing" in problems.single(), problems.single())
    }

    @Test
    fun aTruncatedPushIsCaughtBySize() {
        val problems = NemotronHindiBundle.problems { if (it == "encoder.bin") 100_000_000L else complete[it] }
        assertEquals(1, problems.size)
        assertTrue("encoder.bin" in problems.single() && "610848768" in problems.single(), problems.single())
    }

    @Test
    fun anEmptyTokensFileIsAProblemButItsExactSizeIsNotChecked() {
        assertEquals(emptyList(), NemotronHindiBundle.problems { if (it == "tokens.txt") 1L else complete[it] })
        assertEquals(1, NemotronHindiBundle.problems { if (it == "tokens.txt") 0L else complete[it] }.size)
    }

    @Test
    fun theContextBinaryOrderIsEncoderDecoderJoiner() {
        assertEquals(listOf("encoder.bin", "decoder.bin", "joiner.bin"), NemotronHindiBundle.CONTEXT_BINARIES)
    }
}

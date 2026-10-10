package com.megathon.incident.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the reusable suite itself: it must pass a well-behaved engine and name the exact check that a
 * broken engine fails. Real engines run the same suite on the device (androidTest) and in `:core` tests.
 */
class SpeechEngineContractSuiteTest {
    private val speech = ShortArray(16_000) { (it % 200 - 100).toShort() }

    private fun verify(
        engine: SpeechEngine,
        allowed: Set<Compute> = setOf(Compute.SIMULATED),
    ) = SpeechEngineContractTestRunner.runLong { SpeechEngineContract.verify(engine, speech, allowed) }

    private fun failedNames(report: ContractReport) = report.failures.map { it.name }

    private class Scripted(
        override val name: String = "scripted",
        val onCall: suspend (ShortArray, String) -> Transcript,
    ) : SpeechEngine {
        override suspend fun transcribe(pcm: ShortArray, language: String) = onCall(pcm, language)
    }

    private fun ok(text: String, language: String, compute: Compute = Compute.SIMULATED, elapsedMs: Long = 1) =
        Transcript(text, language, elapsedMs, compute)

    @Test
    fun aWellBehavedEnginePassesEveryCheck() {
        val report = verify(FakeSpeechEngine(defaultText = "water leak in conference room two"))
        assertTrue(report.passed, report.toString())
        assertEquals(7, report.checks.size, report.toString())
    }

    @Test
    fun aSlowButNonBlockingEnginePasses() {
        val report = verify(FakeSpeechEngine(defaultText = "ok", delayMs = 300))
        assertTrue(report.passed, report.toString())
    }

    @Test
    fun anEngineThatTalksOverSilenceFails() {
        val ghost = Scripted { _, lang -> ok("thank you", lang) }
        assertEquals(listOf("silence gives blank text", "empty input gives blank text"), failedNames(verify(ghost)))
    }

    @Test
    fun anEngineThatReturnsNothingForSpeechFails() {
        val deaf = Scripted { _, lang -> ok("", lang) }
        assertEquals(listOf("speech sample gives non-empty text"), failedNames(verify(deaf)))
    }

    @Test
    fun anEngineThatIgnoresTheRequestedLanguageFails() {
        val englishOnly = Scripted { pcm, _ -> ok(if (pcm.all { it == 0.toShort() }) "" else "text", "en") }
        assertEquals(listOf("echoes the requested language"), failedNames(verify(englishOnly)))
    }

    @Test
    fun anEngineThatBlocksTheCallingThreadFails() {
        val blocker = Scripted { pcm, lang ->
            if (pcm.any { it != 0.toShort() }) Thread.sleep(400) // blocks the caller instead of suspending
            ok(if (pcm.all { it == 0.toShort() }) "" else "text", lang)
        }
        assertEquals(listOf("does not block the calling thread"), failedNames(verify(blocker)))
    }

    @Test
    fun anEngineThatThrowsOnEmptyInputFailsThatCheckOnly() {
        val fragile = Scripted { pcm, lang ->
            require(pcm.isNotEmpty()) { "empty input" }
            ok(if (pcm.all { it == 0.toShort() }) "" else "text", lang)
        }
        val report = verify(fragile)
        assertEquals(listOf("empty input gives blank text"), failedNames(report))
        assertTrue("empty input" in report.failures.single().detail, report.toString())
    }

    @Test
    fun aNegativeElapsedTimeFails() {
        val broken = Scripted { pcm, lang -> ok(if (pcm.all { it == 0.toShort() }) "" else "text", lang, elapsedMs = -5) }
        assertEquals(listOf("elapsedMs is plausible"), failedNames(verify(broken)))
    }

    @Test
    fun aSimulatedEngineCannotPassAsLive() {
        val live = setOf(Compute.NPU, Compute.CPU, Compute.HYBRID)
        val report = verify(FakeSpeechEngine(defaultText = "ok"), allowed = live)
        assertEquals(listOf("compute label is one of the allowed"), failedNames(report))
        assertTrue("SIMULATED" in report.failures.single().detail, report.toString())
    }

    @Test
    fun aRealLabelPassesTheLiveCheck() {
        val npu = Scripted { pcm, lang ->
            ok(if (pcm.all { it == 0.toShort() }) "" else "text", lang, compute = Compute.NPU)
        }
        assertTrue(verify(npu, allowed = setOf(Compute.NPU)).passed)
    }
}

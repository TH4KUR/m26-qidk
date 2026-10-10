package com.megathon.incident.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakeSpeechEngineTest {
    @Test
    fun scriptedFixtureCanPreserveTheRoomToMistake() {
        val engine = FakeSpeechEngine(
            scriptedTexts = mapOf("mistake" to fixture("deck_room_to_mistake.txt")),
            clipId = { "mistake" },
        )

        val transcript = runSuspend { engine.transcribe(shortArrayOf(1), "en") }

        assertEquals(fixture("deck_room_to_mistake.txt"), transcript.text)
    }

    @Test
    fun scriptedFixtureCanRepresentTheCorrectedRoomName() {
        val engine = FakeSpeechEngine(
            scriptedTexts = mapOf("corrected" to fixture("deck_corrected.txt")),
            clipId = { "corrected" },
        )

        val transcript = runSuspend { engine.transcribe(shortArrayOf(1), "en") }

        assertEquals(fixture("deck_corrected.txt"), transcript.text)
    }

    @Test
    fun configuredFailureIsPropagated() {
        val failure = IllegalStateException("fake failure")
        val engine = FakeSpeechEngine(defaultText = "unused", failure = failure)

        val thrown = assertFailsWith<IllegalStateException> {
            runSuspend { engine.transcribe(shortArrayOf(1), "en") }
        }

        assertEquals("fake failure", thrown.message)
    }

    private fun <T> runSuspend(block: suspend () -> T): T =
        SpeechEngineContractTestRunner.run(block)

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name"))
            .readText()
            .trim()
}

package com.megathon.incident.testing

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeSpeakerTest {
    @Test
    fun speakerLogsSpokenTextInOrder() {
        val speaker = FakeSpeaker()

        runSuspend {
            speaker.speak("What happened?")
            speaker.speak("Where did it happen?")
        }

        assertEquals(
            listOf("What happened?", "Where did it happen?"),
            speaker.spokenTexts,
        )
    }

    private fun <T> runSuspend(block: suspend () -> T): T =
        SpeechEngineContractTestRunner.run(block)
}

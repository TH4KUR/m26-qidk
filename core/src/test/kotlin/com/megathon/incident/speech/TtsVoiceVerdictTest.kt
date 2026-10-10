package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtsVoiceVerdictTest {
    private fun voice(name: String, network: Boolean, features: Set<String> = emptySet()) =
        TtsVoiceInfo(name, network, features, quality = 400, latency = 200)

    @Test fun availabilityNamesFollowAndroidCodes() {
        assertEquals(TtsAvailability.AVAILABLE, TtsAvailability.fromCode(0))
        assertEquals(TtsAvailability.COUNTRY, TtsAvailability.fromCode(1))
        assertEquals(TtsAvailability.VARIANT, TtsAvailability.fromCode(2))
        assertEquals(TtsAvailability.MISSING_DATA, TtsAvailability.fromCode(-1))
        assertEquals(TtsAvailability.NOT_SUPPORTED, TtsAvailability.fromCode(-2))
        assertEquals(TtsAvailability.UNKNOWN, TtsAvailability.fromCode(99))
    }

    @Test fun offlineOkOnlyWithMeasuredOfflineVoiceAndUsableAvailability() {
        val offline = listOf(voice("hi-in-x-local", network = false))
        assertEquals(TtsVerdict.OFFLINE_OK, ttsVerdict(TtsAvailability.COUNTRY, offline))
        assertEquals(TtsVerdict.OFFLINE_OK, ttsVerdict(TtsAvailability.AVAILABLE, offline))
        assertEquals(TtsVerdict.OFFLINE_OK, ttsVerdict(TtsAvailability.VARIANT, offline))
    }

    @Test fun missingDataOrNotSupportedIsNeverOfflineOk() {
        val offline = listOf(voice("te-in-x-local", network = false))
        assertEquals(TtsVerdict.UNAVAILABLE, ttsVerdict(TtsAvailability.MISSING_DATA, offline))
        assertEquals(TtsVerdict.UNAVAILABLE, ttsVerdict(TtsAvailability.NOT_SUPPORTED, offline))
        assertEquals(TtsVerdict.UNAVAILABLE, ttsVerdict(TtsAvailability.UNKNOWN, offline))
    }

    @Test fun networkOnlyVoicesAreNotOffline() {
        val online = listOf(voice("hi-in-x-network", network = true))
        assertEquals(TtsVerdict.NETWORK_ONLY, ttsVerdict(TtsAvailability.COUNTRY, online))
    }

    @Test fun notInstalledVoiceDoesNotCountAsOffline() {
        val ghost = listOf(voice("te-in-x-local", network = false, features = setOf("notInstalled")))
        assertEquals(TtsVerdict.NETWORK_ONLY, ttsVerdict(TtsAvailability.COUNTRY, ghost))
    }

    @Test fun noVoicesIsNoVoices() {
        assertEquals(TtsVerdict.NO_VOICES, ttsVerdict(TtsAvailability.COUNTRY, emptyList()))
    }

    @Test fun lineIsHonestAndSingleLine() {
        val line = ttsCheckLine("te-IN", TtsAvailability.MISSING_DATA, emptyList())
        assertTrue(line.startsWith("ttscheck te-IN avail=MISSING_DATA verdict=UNAVAILABLE voices=0"), line)
        assertTrue('\n' !in line)
        val ok = ttsCheckLine("en-IN", TtsAvailability.COUNTRY, listOf(voice("en-in-x-a", false, setOf("embeddedTts"))))
        assertTrue("verdict=OFFLINE_OK" in ok && "en-in-x-a net=false" in ok && "q=400" in ok && "lat=200" in ok, ok)
    }
}

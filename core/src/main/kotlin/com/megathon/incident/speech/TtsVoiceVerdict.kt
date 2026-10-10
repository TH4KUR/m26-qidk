package com.megathon.incident.speech

/** Names for TextToSpeech.isLanguageAvailable() codes (kept here so the logic stays testable without Android). */
enum class TtsAvailability {
    AVAILABLE, COUNTRY, VARIANT, MISSING_DATA, NOT_SUPPORTED, UNKNOWN;

    companion object {
        fun fromCode(code: Int): TtsAvailability = when (code) {
            0 -> AVAILABLE
            1 -> COUNTRY
            2 -> VARIANT
            -1 -> MISSING_DATA
            -2 -> NOT_SUPPORTED
            else -> UNKNOWN
        }
    }
}

data class TtsVoiceInfo(
    val name: String,
    val networkRequired: Boolean,
    val features: Set<String>,
    val quality: Int,
    val latency: Int,
) {
    /** A voice that is listed but whose data is not on the device reports the "notInstalled" feature. */
    val usableOffline: Boolean get() = !networkRequired && "notInstalled" !in features
}

enum class TtsVerdict { OFFLINE_OK, NETWORK_ONLY, NO_VOICES, UNAVAILABLE }

/** OFFLINE_OK only when the engine reports a usable language AND a voice that needs no network and is installed. */
fun ttsVerdict(availability: TtsAvailability, voices: List<TtsVoiceInfo>): TtsVerdict = when {
    availability == TtsAvailability.MISSING_DATA ||
        availability == TtsAvailability.NOT_SUPPORTED ||
        availability == TtsAvailability.UNKNOWN -> TtsVerdict.UNAVAILABLE
    voices.isEmpty() -> TtsVerdict.NO_VOICES
    voices.any { it.usableOffline } -> TtsVerdict.OFFLINE_OK
    else -> TtsVerdict.NETWORK_ONLY
}

/** One log line per locale. */
fun ttsCheckLine(tag: String, availability: TtsAvailability, voices: List<TtsVoiceInfo>): String {
    val detail = voices.joinToString("; ") {
        "${it.name} net=${it.networkRequired} q=${it.quality} lat=${it.latency} feat=${it.features.sorted().joinToString(",")}"
    }
    return "ttscheck $tag avail=$availability verdict=${ttsVerdict(availability, voices)} voices=${voices.size}" +
        if (detail.isEmpty()) "" else " [$detail]"
}

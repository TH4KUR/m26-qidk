package com.megathon.incident.tts

/** Which speech-output engine the app is asked to use. ANDROID (the system voice) is the default and the fallback. */
enum class TtsKind {
    ANDROID,
    PIPER,
    ;

    companion object {
        /**
         * Reads the debug switch (`--es tts PIPER`). Anything else, including `check` (the existing voice probe, which
         * shares the extra), means ANDROID.
         */
        fun parse(value: String?): TtsKind = when (value?.trim()?.uppercase()) {
            "PIPER" -> PIPER
            else -> ANDROID
        }
    }
}

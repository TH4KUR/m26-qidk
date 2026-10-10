package com.megathon.incident.testing

/** Records speech requests without producing audio. */
class FakeSpeaker : Speaker {
    private val recordedTexts = mutableListOf<String>()

    val spokenTexts: List<String>
        get() = synchronized(recordedTexts) { recordedTexts.toList() }

    override suspend fun speak(text: String) {
        synchronized(recordedTexts) {
            recordedTexts += text
        }
    }

    fun clear() {
        synchronized(recordedTexts) {
            recordedTexts.clear()
        }
    }
}

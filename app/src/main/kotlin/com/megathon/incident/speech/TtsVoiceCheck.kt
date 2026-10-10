package com.megathon.incident.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Debug-only probe of the system TTS engine: for each locale reports isLanguageAvailable and the engine's voices.
 * It only reads what the engine reports. It never speaks, downloads or installs anything.
 */
class TtsVoiceCheck(private val context: Context) {
    suspend fun run(): List<String> {
        val ready = CompletableDeferred<Int>()
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status) }
        try {
            val status = withTimeoutOrNull(10_000) { ready.await() }
                ?: return LOCALES.map { "ttscheck ${it.toLanguageTag()} engine init timeout verdict=UNAVAILABLE" }
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                return LOCALES.map { "ttscheck ${it.toLanguageTag()} engine init failed status=$status verdict=UNAVAILABLE" }
            }
            val all = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            return LOCALES.map { locale ->
                val availability = TtsAvailability.fromCode(engine.isLanguageAvailable(locale))
                val voices = all.filter { it.locale.language == locale.language && it.locale.country == locale.country }
                    .map { TtsVoiceInfo(it.name, it.isNetworkConnectionRequired, it.features.orEmpty().toSet(), it.quality, it.latency) }
                    .sortedBy { it.name }
                ttsCheckLine(locale.toLanguageTag(), availability, voices)
            }
        } finally {
            tts?.shutdown()
        }
    }

    companion object {
        val LOCALES: List<Locale> = listOf(Locale.forLanguageTag("en-IN"), Locale.forLanguageTag("hi-IN"), Locale.forLanguageTag("te-IN"))
    }
}

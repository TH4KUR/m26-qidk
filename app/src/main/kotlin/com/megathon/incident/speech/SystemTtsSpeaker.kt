package com.megathon.incident.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.megathon.incident.testing.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Reads text aloud with the system voice, using only an **offline** voice (spec D11). The system TTS runs on the CPU, so
 * the proof sheet labels it CPU. [offline] is false when no offline voice was found: the screen then says so.
 */
class SystemTtsSpeaker(context: Context) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private var tts: TextToSpeech? = null
    private val waiting = mutableMapOf<String, CompletableDeferred<Unit>>()

    @Volatile var offline: Boolean = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) { ready.complete(false); return@TextToSpeech }
            val voice = engine.voices.orEmpty()
                .filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }
                .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "IN" }.thenByDescending { it.quality })
                .firstOrNull()
            if (voice != null) { engine.voice = voice; offline = true } else { engine.language = Locale.US }
            engine.setSpeechRate(0.95f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { finish(utteranceId) }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { finish(utteranceId) }
            })
            ready.complete(true)
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank() || ready.await().not()) return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        synchronized(waiting) { waiting[id] = done }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        withTimeoutOrNull(20_000) { done.await() }
        synchronized(waiting) { waiting.remove(id) }
    }

    /**
     * Renders [text] with the system voice into a WAV file instead of speaking it, so it can be paused and sought.
     * Returns false when the engine is not ready or wrote nothing.
     */
    suspend fun renderToWav(text: String, out: java.io.File): Boolean {
        if (text.isBlank() || ready.await().not()) return false
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        synchronized(waiting) { waiting[id] = done }
        out.parentFile?.mkdirs()
        out.delete()
        val status = tts?.synthesizeToFile(text, null, out, id) ?: TextToSpeech.ERROR
        if (status != TextToSpeech.SUCCESS) {
            synchronized(waiting) { waiting.remove(id) }
            return false
        }
        withTimeoutOrNull(60_000) { done.await() }
        synchronized(waiting) { waiting.remove(id) }
        return out.length() > 44
    }

    fun stop() { tts?.stop() }

    fun shutdown() { tts?.shutdown(); tts = null }

    private fun finish(id: String?) {
        val d = synchronized(waiting) { waiting[id] }
        d?.complete(Unit)
    }
}

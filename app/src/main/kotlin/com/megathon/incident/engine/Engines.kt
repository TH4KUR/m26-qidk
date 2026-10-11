package com.megathon.incident.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.megathon.incident.speech.GenieXLlmEngine
import com.megathon.incident.speech.SpeechBundle
import com.megathon.incident.tts.TtsKind
import java.io.File

/**
 * Wiring of the engine factory for the app. Real providers (GenieX, WhisperKit, sherpa-onnx) are registered
 * here as their tasks land; until then only the FAKE engines exist.
 */
object Engines {
    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun factory(context: Context) = EngineFactory(isDebug = isDebuggable(context))

    /**
     * Which engines to use. With no extras, a REAL engine is chosen whenever its files are on the device (a plain launch
     * from the launcher must never show the scripted sentence on a kit that has the models); otherwise the labelled
     * SIMULATED engine. Debug switch for the kit, no rebuild needed:
     * `adb shell am start -n com.megathon.incident/.MainActivity --es speech FAKE --es llm FAKE`
     * Unknown values fall back to that default.
     *
     * Speech output: the Android system voice unless `--es tts PIPER` is given (Piper TTS on the CPU through sherpa-onnx).
     * `--es tts check` is the separate voice probe and leaves the choice at ANDROID. A release build keeps the Android voice
     * unless the Piper files are present and match their recorded size and SHA-256 (checked by the speaker on first use).
     */
    fun selection(intent: Intent?, context: Context): EngineSelection = EngineSelection(
        speech = intent?.getStringExtra("speech")?.let { runCatching { SpeechKind.valueOf(it.uppercase()) }.getOrNull() }
            ?: if (SpeechBundle(context).missing().isEmpty()) SpeechKind.SHERPA_QNN else SpeechKind.FAKE,
        llm = intent?.getStringExtra("llm")?.let { runCatching { LlmKind.valueOf(it.uppercase()) }.getOrNull() }
            ?: if (File(context.filesDir, "geniex/models/" + GenieXLlmEngine.DEFAULT_MODEL).exists()) LlmKind.GENIEX else LlmKind.FAKE,
        tts = TtsKind.parse(intent?.getStringExtra("tts")),
    )
}

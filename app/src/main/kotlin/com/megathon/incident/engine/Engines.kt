package com.megathon.incident.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.megathon.incident.speech.GenieXLlmEngine
import com.megathon.incident.speech.SpeechBundle
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
     */
    fun selection(intent: Intent?, context: Context): EngineSelection = EngineSelection(
        speech = intent?.getStringExtra("speech")?.let { runCatching { SpeechKind.valueOf(it.uppercase()) }.getOrNull() }
            ?: if (SpeechBundle(context).missing().isEmpty()) SpeechKind.SHERPA_QNN else SpeechKind.FAKE,
        llm = intent?.getStringExtra("llm")?.let { runCatching { LlmKind.valueOf(it.uppercase()) }.getOrNull() }
            ?: if (File(context.filesDir, "geniex/models/" + GenieXLlmEngine.DEFAULT_MODEL).exists()) LlmKind.GENIEX else LlmKind.FAKE,
    )
}

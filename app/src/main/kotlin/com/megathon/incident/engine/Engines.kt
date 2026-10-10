package com.megathon.incident.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Wiring of the engine factory for the app. Real providers (GenieX, WhisperKit, sherpa-onnx) are registered
 * here as their tasks land; until then only the FAKE engines exist.
 */
object Engines {
    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun factory(context: Context) = EngineFactory(isDebug = isDebuggable(context))

    /**
     * Debug switch for the kit, no rebuild needed:
     * `adb shell am start -n com.megathon.incident/.MainActivity --es speech FAKE --es llm FAKE`
     * Unknown values fall back to the default (FAKE).
     */
    fun selection(intent: Intent?): EngineSelection = EngineSelection(
        speech = intent?.getStringExtra("speech")?.let { runCatching { SpeechKind.valueOf(it.uppercase()) }.getOrNull() }
            ?: SpeechKind.FAKE,
        llm = intent?.getStringExtra("llm")?.let { runCatching { LlmKind.valueOf(it.uppercase()) }.getOrNull() }
            ?: LlmKind.FAKE,
    )
}

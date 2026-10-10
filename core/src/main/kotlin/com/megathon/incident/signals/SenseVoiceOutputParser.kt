package com.megathon.incident.signals

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the SenseVoice command line printed for one clip. [lang] is the model's own language tag; it is kept raw
 * only so a test can see it, and nothing in the app uses or shows it (it is not reliable for English).
 * [emotion] and [event] are raw tags such as `<|NEUTRAL|>`; [SenseVoiceSignalMapper] decides what they mean.
 */
data class SenseVoiceReading(val lang: String?, val emotion: String?, val event: String?, val text: String)

/** Reads the CLI's whole stdout (noise lines, then one JSON line per clip). Never throws. */
object SenseVoiceOutputParser {
    private val KEYS = listOf("emotion", "event", "text")

    /** The first JSON line that has an "emotion", "event" or "text" key, or null when there is none. */
    fun parse(stdout: String): SenseVoiceReading? {
        for (raw in stdout.lineSequence()) {
            val line = raw.trim()
            val start = line.indexOf("{\"")
            if (start < 0) continue
            val obj = parseObject(line.substring(start)) ?: continue
            if (KEYS.none { it in obj }) continue
            return SenseVoiceReading(lang = obj.string("lang"), emotion = obj.string("emotion"), event = obj.string("event"), text = obj.string("text")?.trim().orEmpty())
        }
        return null
    }

    private fun parseObject(json: String): JsonObject? =
        try {
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        }

    private fun JsonObject.string(key: String): String? {
        val e: JsonElement = this[key] ?: return null
        return if (e is JsonPrimitive && e !is JsonNull && e.isString) e.content else null
    }
}

package com.megathon.incident.llm

import com.megathon.incident.domain.ExtractionPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class FailureCode { NO_ENVELOPE, MULTIPLE_ENVELOPES, TRUNCATED, UNKNOWN_TOOL, INVALID_ARGUMENTS }

sealed interface ParseResult {
    data class Ok(val payload: ExtractionPayload, val envelope: Envelope) : ParseResult
    data class Failure(val code: FailureCode, val detail: String, val raw: String) : ParseResult
}

/** `JSON` = `{"tool":…,"arguments":…}` (GBNF mode); `NATIVE` = `{"name":…,"arguments":…}` inside `<tool_call>` (D5). */
enum class Envelope { JSON, NATIVE }

/**
 * Lenient about *syntax noise* (think blocks, fences, prose, trailing commas), strict about *content*:
 * exactly one complete `update_report` envelope, typed arguments, no salvage of truncated output.
 */
object ToolCallParser {
    const val TOOL_NAME = "update_report"

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun parse(raw: String): ParseResult {
        val cleaned = stripNoise(raw)
        val scan = JsonObjectScanner.scan(cleaned)

        val envelopes = scan.complete.mapNotNull { text ->
            val obj = runCatching { json.parseToJsonElement(removeTrailingCommas(text)) as? JsonObject }.getOrNull()
            obj?.let { toEnvelope(it) }
        }

        return when {
            envelopes.size > 1 -> ParseResult.Failure(FailureCode.MULTIPLE_ENVELOPES, "found ${envelopes.size} envelopes", raw)
            envelopes.isEmpty() && scan.truncatedTail -> ParseResult.Failure(FailureCode.TRUNCATED, "unterminated JSON object", raw)
            envelopes.isEmpty() -> ParseResult.Failure(FailureCode.NO_ENVELOPE, "no tool-call envelope", raw)
            else -> decode(envelopes.single(), raw)
        }
    }

    private data class Found(val name: String?, val arguments: JsonElement?, val kind: Envelope)

    private fun toEnvelope(obj: JsonObject): Found? = when {
        "tool" in obj -> Found(obj["tool"]?.stringOrNull(), obj["arguments"], Envelope.JSON)
        "name" in obj && "arguments" in obj -> Found(obj["name"]?.stringOrNull(), obj["arguments"], Envelope.NATIVE)
        else -> null
    }

    private fun decode(found: Found, raw: String): ParseResult {
        if (found.name != TOOL_NAME) {
            return ParseResult.Failure(FailureCode.UNKNOWN_TOOL, "tool '${found.name}'", raw)
        }
        val args = found.arguments as? JsonObject
            ?: return ParseResult.Failure(FailureCode.INVALID_ARGUMENTS, "arguments is not an object", raw)
        return runCatching { json.decodeFromJsonElement(ExtractionPayload.serializer(), nullifyLiterals(args)) }
            .fold(
                onSuccess = { ParseResult.Ok(it, found.kind) },
                onFailure = { ParseResult.Failure(FailureCode.INVALID_ARGUMENTS, it.message ?: "decode error", raw) },
            )
    }

    /** Small models write `"null"` or `""` for absent values; treat those as JSON null / drop them from lists. */
    private fun nullifyLiterals(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, v) -> nullifyLiterals(v) })
        is JsonArray -> JsonArray(element.filterNot { it.isNullLiteral() }.map { nullifyLiterals(it) })
        else -> if (element.isNullLiteral()) JsonNull else element
    }

    private fun JsonElement.isNullLiteral(): Boolean {
        val p = this as? JsonPrimitive ?: return false
        if (!p.isString) return false
        val s = p.content.trim()
        return s.isEmpty() || s.equals("null", ignoreCase = true)
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.contentOrNull

    private val THINK = Regex("(?s)<think>.*?(</think>|$)")
    private val FENCE = Regex("```[a-zA-Z]*")
    private val TOOL_CALL_TAGS = Regex("</?tool_call>")

    internal fun stripNoise(raw: String): String =
        raw.replace(THINK, " ").replace(FENCE, " ").replace(TOOL_CALL_TAGS, " ")

    /** Removes commas directly before `}` or `]` outside string literals. */
    internal fun removeTrailingCommas(text: String): String {
        val out = StringBuilder(text.length)
        var inString = false
        var escaped = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else if (c == '"') {
                inString = true
                out.append(c)
            } else if (c == ',') {
                var j = i + 1
                while (j < text.length && text[j].isWhitespace()) j++
                if (j < text.length && (text[j] == '}' || text[j] == ']')) {
                    // drop the comma
                } else {
                    out.append(c)
                }
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }
}

/** Finds top-level `{…}` spans, respecting string literals and escapes. */
internal object JsonObjectScanner {
    data class Scan(val complete: List<String>, val truncatedTail: Boolean)

    fun scan(text: String): Scan {
        val found = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for ((i, c) in text.withIndex()) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> if (depth > 0) inString = true
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0) found += text.substring(start, i + 1)
                }
            }
        }
        return Scan(found, truncatedTail = depth > 0)
    }
}

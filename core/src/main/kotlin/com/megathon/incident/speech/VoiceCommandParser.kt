package com.megathon.incident.speech

import com.megathon.incident.domain.Field

/** What the user can say instead of narrating (hands-free loop, plan task A10). */
sealed interface VoiceCommand {
    data object ReadBack : VoiceCommand
    data object Submit : VoiceCommand

    /** The value is exactly as the speech engine heard it; the report logic validates it like any other input. */
    data class Change(val field: Field, val value: String) : VoiceCommand
}

/**
 * Recognises "read it back", "submit" and "change X to Y". A command must be the whole utterance, so
 * narration such as "I will submit the report tomorrow" or "there was a change of plans" is never one.
 */
object VoiceCommandParser {
    private const val IC = "(?i)"
    private val trailer = Regex("""(?i)(?:\s*,?\s*please)?[.!?\s]*$""")
    private val submit = Regex(
        IC + """^(?:please\s+)?(?:submit|send)(?:\s+(?:the|this|my))?(?:\s+incident)?(?:\s+(?:report|it))?(?:\s+now)?$""",
    )
    private val readBackObjectFirst = Regex(
        IC + """^(?:please\s+)?read\s+(?:it|that|this|everything|the\s+(?:incident\s+)?report)\s+back(?:\s+to\s+me)?(?:\s+now)?$""",
    )
    private val readBackFirst = Regex(
        IC + """^(?:please\s+)?read\s+back(?:\s+(?:it|that|this|everything|the\s+(?:incident\s+)?report))?(?:\s+to\s+me)?(?:\s+now)?$""",
    )
    private val change = Regex(
        IC + """^(?:please\s+)?(?:change|set|update|make)\s+(?:the\s+|my\s+)?(.+?)\s+(?:to|as)\s+(.+)$""",
    )

    private val fieldNames: Map<String, Field> = mapOf(
        "incident type" to Field.INCIDENT_TYPE, "type of incident" to Field.INCIDENT_TYPE, "type" to Field.INCIDENT_TYPE,
        "severity" to Field.SEVERITY, "severity level" to Field.SEVERITY,
        "location" to Field.LOCATION, "place" to Field.LOCATION,
        "date and time" to Field.DATE_TIME, "date time" to Field.DATE_TIME, "date" to Field.DATE_TIME,
        "time" to Field.DATE_TIME, "when" to Field.DATE_TIME,
        "persons involved" to Field.PERSONS_INVOLVED, "people involved" to Field.PERSONS_INVOLVED,
        "persons" to Field.PERSONS_INVOLVED, "people" to Field.PERSONS_INVOLVED,
        "equipment" to Field.EQUIPMENT_ASSET, "asset" to Field.EQUIPMENT_ASSET, "equipment asset" to Field.EQUIPMENT_ASSET,
        "tool" to Field.EQUIPMENT_ASSET, "tools" to Field.EQUIPMENT_ASSET, "item" to Field.EQUIPMENT_ASSET, "items" to Field.EQUIPMENT_ASSET,
        "thing" to Field.EQUIPMENT_ASSET, "tool or item" to Field.EQUIPMENT_ASSET, "tools or items" to Field.EQUIPMENT_ASSET,
        "description" to Field.DESCRIPTION,
        "immediate actions" to Field.IMMEDIATE_ACTIONS, "actions taken" to Field.IMMEDIATE_ACTIONS,
        "actions" to Field.IMMEDIATE_ACTIONS,
        "witnesses" to Field.WITNESSES, "witness" to Field.WITNESSES,
        "follow up" to Field.RECOMMENDED_FOLLOW_UP, "recommended follow up" to Field.RECOMMENDED_FOLLOW_UP,
    )

    fun parse(text: String): VoiceCommand? {
        val utterance = text.replace(trailer, "").trim()
        if (utterance.isEmpty()) return null
        if (submit.matches(utterance)) return VoiceCommand.Submit
        if (readBackObjectFirst.matches(utterance) || readBackFirst.matches(utterance)) return VoiceCommand.ReadBack
        val match = change.matchEntire(utterance) ?: return null
        val name = match.groupValues[1].lowercase().replace('-', ' ').trim().removePrefix("the ").trim()
        val field = fieldNames[name] ?: return null
        return VoiceCommand.Change(field, match.groupValues[2].trim())
    }
}

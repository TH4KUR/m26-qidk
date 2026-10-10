package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.HedgeDetector

/**
 * "Confirm?" chips for values the speaker hedged ("I think it was Room 2"). The chip proposes the same
 * value; accepting it marks the value USER_CONFIRMED. A hedge counts only in the sentence that holds the
 * value's evidence. Uses C's [HedgeDetector]; rule-based, CPU.
 */
internal class HedgeChips(private val hedges: HedgeDetector) {

    fun chips(state: ReportState, transcript: String): List<Suggestion> {
        val spans = hedges.hedges(transcript)
        if (spans.isEmpty()) return emptyList()
        val sentences = SENTENCE.findAll(transcript).map { it.range }.toList()
        val chips = mutableListOf<Suggestion>()
        for ((field, label) in FIELD_LABELS) {
            val value = state.report.value(field)
            if (value.status !in MACHINE_HEARD) continue
            val text = value.text ?: continue
            val anchor = value.evidence ?: text
            val at = transcript.indexOf(anchor, ignoreCase = true)
            if (at < 0) continue
            val sentence = sentences.firstOrNull { at in it } ?: continue
            val hedge = spans.firstOrNull { it.first in sentence } ?: continue
            val words = transcript.substring(hedge.first, (hedge.last + 1).coerceAtMost(transcript.length))
            chips += Suggestion(field, text, text, "The speaker said \"$words\"; confirm the $label.", Source.TEXT_CUE)
        }
        return chips
    }

    private companion object {
        val SENTENCE = Regex("[^.!?]+[.!?]*")
        val MACHINE_HEARD = setOf(ValueStatus.REPORTED, ValueStatus.SUGGESTED)
    }
}

/** Report fields a chip can question, with the words the chip uses for them. */
internal val FIELD_LABELS = listOf(
    Field.INCIDENT_TYPE to "incident type",
    Field.SEVERITY to "severity",
    Field.LOCATION to "location",
    Field.DATE_TIME to "time",
    Field.PERSONS_INVOLVED to "people involved",
    Field.EQUIPMENT_ASSET to "equipment",
    Field.IMMEDIATE_ACTIONS to "actions taken",
    Field.WITNESSES to "witnesses",
)

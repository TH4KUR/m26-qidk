package com.megathon.incident.suggest

import com.megathon.incident.domain.GroundingValidator
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus

/**
 * Confirm chips for C's near-misses. C's validator stores "last weak" heard, "last week" meant as the
 * meant value, status SUGGESTED, with the heard words as evidence. A SUGGESTED value whose evidence and
 * value do not contain each other is such a near-miss; the chip shows what was heard, and accepting it
 * confirms the meant value. (A hedged value's evidence contains the value, so it is not one.)
 */
internal object NearMissChips {

    fun chips(state: ReportState): List<Suggestion> = FIELD_LABELS.mapNotNull { (field, label) ->
        val value = state.report.value(field)
        val text = value.text
        val heard = value.evidence?.trim()
        when {
            value.status != ValueStatus.SUGGESTED || text.isNullOrBlank() || heard.isNullOrEmpty() -> null
            GroundingValidator.containsPhrase(heard, text) || GroundingValidator.containsPhrase(text, heard) -> null
            else -> Suggestion(field, text, text, "Heard \"$heard\", close to \"$text\"; confirm the $label.", Source.RULE)
        }
    }
}

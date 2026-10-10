package com.megathon.incident.priority

import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ValueStatus

/**
 * A severity the app *suggests*; it never fills the field (spec D18). The reporter's own words
 * ("minor", "serious") are read separately by the enum normaliser and the payload validator.
 */
data class SeveritySuggestion(val severity: Severity, val reason: String, val source: Source = Source.RULE) {
    fun toFieldValue() = FieldValue(severity.wire, ValueStatus.SUGGESTED, evidence = reason)
}

/**
 * Suggests a severity from the worst recognised, non-negated hazard (design doc 2). Returns null
 * when no hazard was recognised: an unknown report gets no guess rather than a trivial one.
 */
class SeveritySuggester(private val extractor: UrgencyFeatureExtractor) {
    fun suggest(transcript: String): SeveritySuggestion? {
        val top = extractor.extract(UrgencyInput(transcript)).hazards.maxByOrNull { it.impact } ?: return null
        val severity = when (top.impact) {
            5 -> Severity.CRITICAL
            4 -> Severity.SEVERE
            3 -> Severity.MODERATE
            else -> Severity.MINOR
        }
        return SeveritySuggestion(
            severity,
            "${top.label.replace('_', ' ')} (\"${top.phrase}\") is a hazard of impact ${top.impact} of 5",
        )
    }
}

package com.megathon.incident.llm

import com.megathon.incident.domain.EnumNormalizer
import com.megathon.incident.domain.ExtractionPayload
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.GroundingValidator
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.PartOfDay
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.TemporalValue
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamClause
import com.megathon.incident.tam.TamDetector
import java.time.Instant

data class FieldProposal(val field: Field, val value: FieldValue, val note: String? = null)

data class Rejection(val field: Field, val reason: String, val raw: String? = null)

data class ValidationResult(val proposals: List<FieldProposal>, val rejections: List<Rejection>)

/**
 * Applies the rules of design doc 3-4 to what the LLM proposed, *before* it touches a report:
 * grounding in the transcript, enum normalisation, tense/aspect/modality effects, and temporal
 * precision. It never writes a report; it returns proposals and rejections for D's merge.
 *
 *  - not grounded            -> SUGGESTED (a chip), never a fill; a severity is rejected instead
 *  - planned action          -> rejected as an immediate action, offered as a follow-up suggestion
 *  - negated claim           -> rejected ("Ravi did not see it" never leaves Ravi a witness)
 *  - "nobody saw / hurt"     -> EXPLICIT_NONE for witnesses / persons involved
 *  - hedged                  -> SUGGESTED
 */
class PayloadValidator(
    private val tam: TamDetector,
    private val temporal: TemporalResolver,
) {
    private val proposals = mutableListOf<FieldProposal>()
    private val rejections = mutableListOf<Rejection>()

    fun validate(payload: ExtractionPayload, transcript: String, capturedAt: Instant): ValidationResult {
        proposals.clear()
        rejections.clear()
        val clauses = tam.analyze(transcript)
        val ev = payload.evidence

        enumField(Field.INCIDENT_TYPE, payload.incident_type, ev["incident_type"], transcript, clauses, mustBeStated = false)
        enumField(Field.SEVERITY, payload.severity, ev["severity"], transcript, clauses, mustBeStated = true)
        verbatim(Field.LOCATION, payload.location, ev["location"], transcript, clauses)
        verbatim(Field.EQUIPMENT_ASSET, payload.equipment_asset, ev["equipment_asset"], transcript, clauses)
        action(payload.immediate_actions, ev["immediate_actions"], transcript, clauses)
        people(Field.PERSONS_INVOLVED, payload.persons_involved, ev["persons_involved"], transcript, clauses)
        people(Field.WITNESSES, payload.witnesses, ev["witnesses"], transcript, clauses)
        dateTime(payload.date_time_text, ev["date_time_text"], transcript, clauses, capturedAt)

        universalNegations(clauses)
        return ValidationResult(proposals.toList(), rejections.toList())
    }

    // ---- field handlers ------------------------------------------------------------------------

    private fun enumField(
        field: Field, raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>, mustBeStated: Boolean,
    ) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        val wire = when (field) {
            Field.INCIDENT_TYPE -> EnumNormalizer.incidentType(text)?.wire
            else -> EnumNormalizer.severity(text)?.wire
        }
        if (wire == null) {
            rejections += Rejection(field, "'$text' is not a known ${field.name.lowercase().replace('_', ' ')}", text)
            return
        }
        val piece = pieceFor(clauses, evidence)
        if (piece?.tam?.modality == Modality.NEGATED) {
            rejections += Rejection(field, "negated in the transcript: \"${evidence}\"", text)
            return
        }
        val grounded = GroundingValidator.isGrounded(wire, evidence, transcript, enumValue = true)
        when {
            !grounded && mustBeStated ->
                rejections += Rejection(field, "not stated by the reporter (no matching words in the transcript)", text)
            !grounded -> proposals += FieldProposal(field, FieldValue(wire, ValueStatus.SUGGESTED, evidence), "not found in what was said")
            piece?.hedged == true -> proposals += FieldProposal(field, FieldValue(wire, ValueStatus.SUGGESTED, evidence), "hedged by the reporter")
            else -> proposals += FieldProposal(field, FieldValue(wire, ValueStatus.REPORTED, evidence))
        }
    }

    private fun verbatim(field: Field, raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        val piece = pieceFor(clauses, evidence ?: text)
        if (piece?.tam?.modality == Modality.NEGATED) {
            rejections += Rejection(field, "negated in the transcript: \"${evidence ?: text}\"", text)
            return
        }
        val grounded = GroundingValidator.isGrounded(text, evidence, transcript)
        when {
            !grounded -> proposals += FieldProposal(field, FieldValue(text, ValueStatus.SUGGESTED, evidence), "not found in what was said")
            piece?.hedged == true -> proposals += FieldProposal(field, FieldValue(text, ValueStatus.SUGGESTED, evidence), "hedged by the reporter")
            else -> proposals += FieldProposal(field, FieldValue(text, ValueStatus.REPORTED, evidence))
        }
    }

    private fun action(raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        val piece = pieceFor(clauses, evidence ?: text)
        when (piece?.tam?.modality) {
            Modality.NEGATED ->
                rejections += Rejection(Field.IMMEDIATE_ACTIONS, "negated: the action was not done (\"${evidence ?: text}\")", text)
            Modality.PLANNED -> {
                rejections += Rejection(Field.IMMEDIATE_ACTIONS, "planned, not done yet: \"${evidence ?: text}\"", text)
                proposals += FieldProposal(
                    Field.RECOMMENDED_FOLLOW_UP, FieldValue(text, ValueStatus.SUGGESTED, evidence), "planned action moved to follow-up",
                )
            }
            else -> verbatim(Field.IMMEDIATE_ACTIONS, text, evidence, transcript, clauses)
        }
    }

    private fun people(field: Field, items: List<String>, evidence: String?, transcript: String, clauses: List<TamClause>) {
        if (items.isEmpty()) return
        val piece = pieceFor(clauses, evidence)
        val kept = mutableListOf<String>()
        for (item in items.map { it.trim() }.filter { it.isNotEmpty() }) {
            val itemPiece = pieceFor(clauses, item) ?: piece
            when {
                itemPiece?.tam?.modality == Modality.NEGATED ->
                    rejections += Rejection(field, "negated in the transcript: \"${evidence ?: item}\"", item)
                !GroundingValidator.isGrounded(item, null, transcript) ->
                    rejections += Rejection(field, "'$item' was not said", item)
                else -> kept += item
            }
        }
        if (kept.isEmpty()) return
        val status = if (piece?.hedged == true) ValueStatus.SUGGESTED else ValueStatus.REPORTED
        proposals += FieldProposal(field, FieldValue(kept.joinToString(", "), status, evidence))
    }

    private fun dateTime(raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>, capturedAt: Instant) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        if (!GroundingValidator.isGrounded(text, evidence, transcript)) {
            rejections += Rejection(Field.DATE_TIME, "time was not said: \"$text\"", text)
            return
        }
        val resolved = temporal.resolve(text, capturedAt)
        if (resolved == null) {
            proposals += FieldProposal(
                Field.DATE_TIME, FieldValue(text, ValueStatus.REPORTED, evidence), "could not resolve this time; ask the reporter",
            )
            return
        }
        val note = when {
            resolved.ambiguous -> "ambiguous (for example 'kal'); ask which day"
            resolved.needsClarification -> "needs clarification: date or exact time missing"
            else -> null
        }
        proposals += FieldProposal(Field.DATE_TIME, FieldValue(describe(resolved), ValueStatus.REPORTED, evidence), note)
    }

    /** "nobody saw it" / "koi ghayal nahi hua": the question is answered with an explicit none. */
    private fun universalNegations(clauses: List<TamClause>) {
        val pieces = clauses.flatMap { c -> c.text.split(COMMA).map { it.trim() } }.filter { it.isNotEmpty() }
        for ((field, cues) in FIELD_CUES) {
            if (proposals.any { it.field == field }) continue
            val hit = pieces.firstOrNull { p ->
                val lower = p.lowercase()
                UNIVERSAL_NEGATIVE.any { it.containsMatchIn(lower) } &&
                    cues.any { cue -> Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(cue) + "(?![\\p{L}\\p{N}])").containsMatchIn(lower) || (cue.any { ch -> ch.code in 0x0900..0x097F } && lower.contains(cue)) }
            } ?: continue
            proposals += FieldProposal(field, FieldValue.explicitNone(evidence = hit), "stated: none")
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** The comma-delimited piece of the transcript containing [phrase], analysed on its own. */
    private fun pieceFor(clauses: List<TamClause>, phrase: String?): TamClause? {
        if (phrase.isNullOrBlank()) return null
        for (c in clauses) {
            val parts = c.text.split(COMMA)
            val part = parts.firstOrNull { GroundingValidator.containsPhrase(it, phrase) } ?: continue
            return if (parts.size <= 1) c else tam.analyze(part.trim()).firstOrNull() ?: c
        }
        return clauses.firstOrNull { GroundingValidator.containsPhrase(it.text, phrase) }
    }

    private fun describe(v: TemporalValue): String {
        val date = v.date?.toString() ?: "date missing"
        val clock = when {
            v.time != null -> (if (v.approximate) "≈" else "") + v.time.toString()
            v.partOfDay != null -> v.partOfDay.name.lowercase()
            else -> "time missing"
        }
        return "$date $clock (${v.sourceText})"
    }

    private companion object {
        val COMMA = Regex("[,،]")

        val UNIVERSAL_NEGATIVE = listOf(
            Regex("(?<![\\p{L}\\p{N}])(nobody|no one|none|no witnesses?)(?![\\p{L}\\p{N}])"),
            Regex("(?<![\\p{L}\\p{N}])koi(?![\\p{L}\\p{N}]).*(?<![\\p{L}\\p{N}])(nahi|nahin)(?![\\p{L}\\p{N}])"),
            Regex("कोई.*नहीं"),
        )

        val FIELD_CUES: Map<Field, List<String>> = mapOf(
            Field.WITNESSES to listOf("witness", "witnesses", "witnessed", "saw", "seen", "see", "noticed", "dekha", "देखा", "गवाह"),
            Field.PERSONS_INVOLVED to listOf("hurt", "injured", "injury", "involved", "affected", "ghayal", "घायल", "चोट"),
        )
    }
}

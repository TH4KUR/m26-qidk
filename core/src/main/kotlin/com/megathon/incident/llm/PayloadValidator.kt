package com.megathon.incident.llm

import com.megathon.incident.domain.EnumNormalizer
import com.megathon.incident.domain.ExtractionPayload
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.GroundingValidator
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.LlmHint
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.NearMiss
import com.megathon.incident.domain.PartOfDay
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.TemporalValue
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.suggest.Homophones
import com.megathon.incident.tam.TamClause
import com.megathon.incident.tam.TamDetector
import java.time.Instant

/**
 * One proposed field value.
 *
 * @param temporal for DATE_TIME: the structured reading (a week arrives as `date == null`, `from`/`to`),
 *                 so the follow-up planner never has to parse [FieldValue.text].
 * @param nearMiss set when the value is not in the transcript but a word in it sounds or looks like it:
 *                 the status is SUGGESTED and `value.evidence` holds the words that were actually heard.
 */
data class FieldProposal(
    val field: Field,
    val value: FieldValue,
    val note: String? = null,
    val temporal: TemporalValue? = null,
    val nearMiss: NearMiss? = null,
    /** Why this is not a plain "heard" (null when it is). Stable, for per-field scoring; [note] is the human text. */
    val reasonCode: ReasonCode? = null,
)

data class Rejection(val field: Field, val reason: String, val raw: String? = null, val reasonCode: ReasonCode? = null)

/** Stable reason for a suggestion or a rejection. Additive: new values may be added, none renamed. */
enum class ReasonCode {
    /** The reporter hedged ("I think", "maybe"). */
    HEDGED,
    /** A negation applies to the quoted words ("not in Room 2", "nobody was hurt"). */
    NEGATED,
    /** The action is planned, not done ("we will call"). */
    PLANNED,
    /** The value is not in the transcript and no close match was found. */
    UNGROUNDED,
    /** The value is not in the transcript but a word that sounds or looks like it is. */
    NEAR_MISS,
    /** A pronoun or a role where a name is expected. */
    NOT_A_NAME,
    /** A quote under evidence that is an observation, not something the reporter did. */
    NOT_AN_ACTION,
    /** The model proposed it but the reporter never said it (a name, a severity, a time). */
    NOT_STATED,
    /** The value is not one of the known values of its field. */
    UNKNOWN_VALUE,
}

data class ValidationResult(
    val proposals: List<FieldProposal>,
    val rejections: List<Rejection>,
    /** The model's optional urgency hint, for the priority advisor. Never becomes a field. */
    val hint: LlmHint? = null,
)

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
 *  - near-miss (sounds alike) -> SUGGESTED with the heard words; never REPORTED, thresholds unchanged
 */
class PayloadValidator(
    private val tam: TamDetector,
    private val temporal: TemporalResolver,
    /** Sound-alike test, by default D's shared table (`suggest/homophones.json`). */
    private val sameSound: (String, String) -> Boolean = Homophones::sameSound,
) {
    private val proposals = mutableListOf<FieldProposal>()
    private val rejections = mutableListOf<Rejection>()

    fun validate(payload: ExtractionPayload, transcript: String, capturedAt: Instant): ValidationResult {
        proposals.clear()
        rejections.clear()
        val clauses = tam.analyze(transcript)
        val ev = payload.evidence

        // A small model often writes the quote under evidence{} and forgets the field itself. The quote is the
        // reporter's own words (checked against the transcript below), so it can stand in for the missing value.
        fun promoted(value: String?, key: String): String? = value?.takeIf { it.isNotBlank() } ?: ev[key]?.takeIf { it.isNotBlank() }

        enumField(Field.INCIDENT_TYPE, payload.incident_type, ev["incident_type"], transcript, clauses, mustBeStated = false)
        enumField(Field.SEVERITY, payload.severity ?: ev["severity"]?.takeIf { EnumNormalizer.severity(it) != null }, ev["severity"], transcript, clauses, mustBeStated = true)
        verbatim(Field.LOCATION, promoted(payload.location, "location"), ev["location"], transcript, clauses)
        verbatim(Field.EQUIPMENT_ASSET, promoted(payload.equipment_asset, "equipment_asset"), ev["equipment_asset"], transcript, clauses)
        val actionText = promoted(payload.immediate_actions, "immediate_actions")
        if (actionText != null && payload.immediate_actions.isNullOrBlank() && !looksLikeAnAction(actionText, clauses)) {
            // Written only under evidence{}: an observation ("I saw it at 8:10 PM", "fell over") is not something the reporter did.
            rejections += Rejection(
                Field.IMMEDIATE_ACTIONS, "a quote under evidence that is not an action: \"$actionText\"", actionText, ReasonCode.NOT_AN_ACTION,
            )
        } else {
            action(actionText, ev["immediate_actions"], transcript, clauses)
        }
        people(Field.PERSONS_INVOLVED, payload.persons_involved, ev["persons_involved"], transcript, clauses)
        people(Field.WITNESSES, payload.witnesses, ev["witnesses"], transcript, clauses)
        val said = promoted(payload.date_time_text, "date_time_text")
            ?: temporal.findTimePhrase(transcript)?.takeIf { attempted(payload) }?.takeIf { p -> pieceFor(clauses, p)?.tam?.modality.let { it != Modality.PLANNED && it != Modality.NEGATED } }
        dateTime(said, ev["date_time_text"], transcript, clauses, capturedAt)

        universalNegations(clauses)
        return ValidationResult(proposals.toList(), rejections.toList(), LlmHint.from(payload))
    }

    /** The model proposed at least one field this turn (an empty reply means "nothing new", and stays that). */
    private fun attempted(p: ExtractionPayload) = listOf(p.incident_type, p.severity, p.location, p.equipment_asset, p.immediate_actions).any { !it.isNullOrBlank() } ||
        p.persons_involved.isNotEmpty() || p.witnesses.isNotEmpty() || p.evidence.isNotEmpty()

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
            rejections += Rejection(field, "'$text' is not a known ${field.name.lowercase().replace('_', ' ')}", text, ReasonCode.UNKNOWN_VALUE)
            return
        }
        var evidence = evidence
        var grounded = GroundingValidator.isGrounded(wire, evidence, transcript, enumValue = true)
        // No usable quote, but the reporter did say a cue word for this very value ("leak" for a water leak, "moderate"
        // for moderate). The words are real and found by rule, not written by the model: take them as the evidence.
        // "security" in "a security guard" is a job title, not a security incident (same for the model's own quote).
        if (field == Field.INCIDENT_TYPE && grounded && evidence != null && onlyAsModifier(transcript, evidence)) grounded = false
        if (!grounded) {
            val cues = if (field == Field.INCIDENT_TYPE) IncidentType.fromWire(wire)?.let { EnumNormalizer.statedTypeCues(it, transcript) }.orEmpty()
            else listOfNotNull(Severity.fromWire(wire)?.let { EnumNormalizer.statedSeverityCue(it, transcript) })
            val cue = cues.firstOrNull { !negatedNear(transcript, it) && !onlyAsModifier(transcript, it) }
            if (cue != null) { evidence = cue; grounded = true }
        }
        val miss = missFor(wire, evidence, transcript, enumValue = true)
        val piece = pieceFor(clauses, evidence) ?: miss?.let { pieceFor(clauses, it.heard) }
        // "no power since 9 PM" does not negate "electrical": only a negation right at the quoted words does.
        val negated = if (field == Field.INCIDENT_TYPE) evidence?.let { negatedNear(piece?.text ?: transcript, it) } == true else piece?.tam?.modality == Modality.NEGATED
        if (negated) {
            rejections += Rejection(field, "negated in the transcript: \"${evidence ?: miss?.heard}\"", text, ReasonCode.NEGATED)
            return
        }
        when {
            miss != null -> proposals += suggestedNearMiss(field, wire, miss)
            !grounded && mustBeStated ->
                rejections += Rejection(field, "not stated by the reporter (no matching words in the transcript)", text, ReasonCode.NOT_STATED)
            !grounded -> proposals += FieldProposal(
                field, FieldValue(wire, ValueStatus.SUGGESTED, evidence), "not found in what was said", reasonCode = ReasonCode.UNGROUNDED,
            )
            piece?.hedged == true -> proposals += FieldProposal(
                field, FieldValue(wire, ValueStatus.SUGGESTED, evidence), "hedged by the reporter", reasonCode = ReasonCode.HEDGED,
            )
            else -> proposals += FieldProposal(field, FieldValue(wire, ValueStatus.REPORTED, evidence))
        }
    }

    /** True when a negation word sits inside [phrase] or within three words before it, in the first place [phrase] occurs in [text]. */
    private fun negatedNear(text: String, phrase: String): Boolean {
        val lower = text.lowercase().replace('’', '\'')
        val at = lower.indexOf(phrase.lowercase().trim())
        if (at < 0) return false
        val before = lower.substring(0, at).trimEnd().split(Regex("\\s+")).takeLast(3).joinToString(" ")
        val window = "$before ${phrase.lowercase()}"
        return tam.lists.negated.any { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])").containsMatchIn(window) }
    }

    private fun verbatim(field: Field, raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        val grounded = GroundingValidator.isGrounded(text, evidence, transcript)
        val miss = missFor(text, evidence, transcript)
        val piece = pieceFor(clauses, evidence ?: text) ?: miss?.let { pieceFor(clauses, it.heard) }
        // A place or a thing is not negated by its clause ("no power on the third floor"), only by a negation right at
        // the quoted words ("not in Room 2"). An action or a person is negated by its whole clause.
        val placeOrThing = field == Field.LOCATION || field == Field.EQUIPMENT_ASSET
        val negated = if (placeOrThing) negatedNear(piece?.text ?: transcript, evidence ?: text)
        else piece?.tam?.modality == Modality.NEGATED
        if (negated) {
            rejections += Rejection(field, "negated in the transcript: \"${evidence ?: text}\"", text, ReasonCode.NEGATED)
            return
        }
        when {
            miss != null -> proposals += suggestedNearMiss(field, text, miss)
            !grounded -> proposals += FieldProposal(
                field, FieldValue(text, ValueStatus.SUGGESTED, evidence), "not found in what was said", reasonCode = ReasonCode.UNGROUNDED,
            )
            piece?.hedged == true -> proposals += FieldProposal(
                field, FieldValue(text, ValueStatus.SUGGESTED, evidence), "hedged by the reporter", reasonCode = ReasonCode.HEDGED,
            )
            else -> proposals += FieldProposal(field, FieldValue(text, ValueStatus.REPORTED, evidence))
        }
    }

    /** True when every word of [item] is a job, a relationship or a place, so there is no name in it. */
    private fun isRolePhrase(item: String): Boolean {
        val words = GroundingValidator.tokens(item)
        return words.isNotEmpty() && words.all { it in ROLE_WORDS }
    }

    /** "Rahul was there but he didn't see her fall": being present is not witnessing. Checked in the sentence that names the person. */
    private fun saidNotToHaveSeen(transcript: String, name: String): Boolean {
        val sentences = transcript.split(Regex("(?<=[.!?;])\\s+"))
        return sentences.any { s -> GroundingValidator.containsPhrase(s, name) && DID_NOT_SEE.containsMatchIn(s.lowercase().replace('\u2019', '\'')) }
    }

    /** [word] occurs in [text], but only ever as the first word of a job or place ("security guard", "medical team"). */
    private fun onlyAsModifier(text: String, word: String): Boolean {
        val w = GroundingValidator.tokens(word)
        if (w.size != 1) return false
        val t = GroundingValidator.tokens(text)
        val at = t.indices.filter { t[it] == w[0] }
        return at.isNotEmpty() && at.all { i -> t.getOrNull(i + 1) in MODIFIED_ROLE_NOUNS }
    }

    /** A quote promoted from evidence{} must say something the reporter did: an action verb, or a completed-action cue. */
    private fun looksLikeAnAction(quote: String, clauses: List<TamClause>): Boolean {
        if (quote.any { it.code in 0x0900..0x097F }) return true // Hindi: no verb list, keep the old behaviour
        val words = GroundingValidator.tokens(quote)
        if (words.any { it in ACTION_VERBS }) return true
        val piece = pieceFor(clauses, quote)
        return piece?.tam?.aspect == com.megathon.incident.domain.Aspect.COMPLETED
    }

    /** "I've informed the team" -> "informed the team", "I'm calling the ambulance now" -> "calling the ambulance": still the reporter's words. */
    private fun tidyAction(raw: String): String {
        val trimmed = raw.trim()
        val cleaned = trimmed
            .replace(Regex("^(?:i|we|they|he|she)(?:'ve|'m|'re|'ll|\\s+have|\\s+am|\\s+are|\\s+had)?\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:now|already|just now)$", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (cleaned.split(' ').size >= 2) cleaned else trimmed
    }

    private fun action(raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>) {
        val text = raw?.trim()?.let(::tidyAction).orEmpty()
        if (text.isEmpty()) return
        val piece = pieceFor(clauses, evidence ?: text) ?: missFor(text, evidence, transcript)?.let { pieceFor(clauses, it.heard) }
        when (piece?.tam?.modality) {
            Modality.NEGATED ->
                rejections += Rejection(
                    Field.IMMEDIATE_ACTIONS, "negated: the action was not done (\"${evidence ?: text}\")", text, ReasonCode.NEGATED,
                )
            Modality.PLANNED -> {
                rejections += Rejection(Field.IMMEDIATE_ACTIONS, "planned, not done yet: \"${evidence ?: text}\"", text, ReasonCode.PLANNED)
                proposals += FieldProposal(
                    Field.RECOMMENDED_FOLLOW_UP, FieldValue(text, ValueStatus.SUGGESTED, evidence), "planned action moved to follow-up",
                    reasonCode = ReasonCode.PLANNED,
                )
            }
            else -> verbatim(Field.IMMEDIATE_ACTIONS, text, evidence, transcript, clauses)
        }
    }

    private fun people(field: Field, items: List<String>, evidence: String?, transcript: String, clauses: List<TamClause>) {
        if (items.isEmpty()) return
        val piece = pieceFor(clauses, evidence)
        val kept = mutableListOf<String>()
        val misses = mutableListOf<NearMiss>()
        for (item in items.map { it.trim() }.filter { it.isNotEmpty() }) {
            // The field holds named people. "I", "she", "a worker", "our delivery boy" are not names (and the speaker is never listed).
            if (NOT_A_NAME.containsMatchIn(item) || isRolePhrase(item)) {
                rejections += Rejection(field, "'$item' is a pronoun or a role, not a name", item, ReasonCode.NOT_A_NAME)
                continue
            }
            if (field == Field.WITNESSES && saidNotToHaveSeen(transcript, item)) {
                rejections += Rejection(field, "negated in the transcript: $item did not see it", item, ReasonCode.NEGATED)
                continue
            }
            val itemPiece = pieceFor(clauses, item) ?: piece
            val grounded = GroundingValidator.isGrounded(item, null, transcript)
            // Names: sound-alike only. One letter away is usually a different person, not a mishearing.
            val miss = if (grounded) null else missFor(item, null, transcript, allowSpelling = false)
            when {
                itemPiece?.tam?.modality == Modality.NEGATED ->
                    rejections += Rejection(field, "negated in the transcript: \"${evidence ?: item}\"", item, ReasonCode.NEGATED)
                miss != null -> { kept += item; misses += miss }
                !grounded -> rejections += Rejection(field, "'$item' was not said", item, ReasonCode.NOT_STATED)
                else -> kept += item
            }
        }
        if (kept.isEmpty()) return
        val status = if (piece?.hedged == true || misses.isNotEmpty()) ValueStatus.SUGGESTED else ValueStatus.REPORTED
        proposals += FieldProposal(
            field, FieldValue(kept.joinToString(", "), status, evidence),
            note = misses.firstOrNull()?.let(::nearMissNote), nearMiss = misses.firstOrNull(),
            reasonCode = when {
                misses.isNotEmpty() -> ReasonCode.NEAR_MISS
                piece?.hedged == true -> ReasonCode.HEDGED
                else -> null
            },
        )
    }

    private fun dateTime(raw: String?, evidence: String?, transcript: String, clauses: List<TamClause>, capturedAt: Instant) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return
        val grounded = GroundingValidator.isGrounded(text, evidence, transcript)
        val miss = missFor(text, evidence, transcript)
        if (!grounded && miss == null) {
            rejections += Rejection(Field.DATE_TIME, "time was not said: \"$text\"", text, ReasonCode.NOT_STATED)
            return
        }
        // A near-miss is still resolved (so the planner can ask), but stays a chip: it was not heard as such.
        val status = if (miss != null) ValueStatus.SUGGESTED else ValueStatus.REPORTED
        val shownEvidence = miss?.heard ?: evidence
        val resolved = temporal.resolve(text, capturedAt)
        if (resolved == null) {
            val note = listOfNotNull(miss?.let(::nearMissNote), "could not resolve this time; ask the reporter").joinToString("; ")
            proposals += FieldProposal(
                Field.DATE_TIME, FieldValue(text, status, shownEvidence), note, nearMiss = miss,
                reasonCode = if (miss != null) ReasonCode.NEAR_MISS else null,
            )
            return
        }
        val clarification = when {
            resolved.ambiguous -> "ambiguous (for example 'kal'); ask which day"
            resolved.from != null && resolved.date == null -> "needs clarification: which day (a week was given, not a date)"
            resolved.needsClarification -> "needs clarification: date or exact time missing"
            else -> null
        }
        val note = listOfNotNull(miss?.let(::nearMissNote), clarification).joinToString("; ").ifEmpty { null }
        proposals += FieldProposal(
            Field.DATE_TIME, FieldValue(describe(resolved), status, shownEvidence), note, temporal = resolved, nearMiss = miss,
            reasonCode = if (miss != null) ReasonCode.NEAR_MISS else null,
        )
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

    /** Null when the value is grounded exactly or is not close to anything that was said. */
    private fun missFor(
        value: String?, evidence: String?, transcript: String, enumValue: Boolean = false, allowSpelling: Boolean = true,
    ): NearMiss? {
        if (GroundingValidator.isGrounded(value, evidence, transcript, enumValue)) return null
        return GroundingValidator.nearMiss(value, evidence, transcript, enumValue, allowSpelling, sameSound)
    }

    private fun nearMissNote(m: NearMiss) = "heard \"${m.heard}\", proposed \"${m.meant}\" (close match, not an exact one; confirm)"

    private fun suggestedNearMiss(field: Field, text: String, m: NearMiss) =
        FieldProposal(field, FieldValue(text, ValueStatus.SUGGESTED, m.heard), nearMissNote(m), nearMiss = m, reasonCode = ReasonCode.NEAR_MISS)

    private fun describe(v: TemporalValue): String {
        val date = when {
            v.date != null -> v.date.toString()
            v.from != null && v.to != null -> "week ${v.from}..${v.to}"
            else -> "date missing"
        }
        val clock = when {
            v.time != null -> (if (v.approximate) "≈" else "") + v.time.toString()
            v.partOfDay != null -> v.partOfDay.name.lowercase()
            else -> "time missing"
        }
        return "$date $clock (${v.sourceText})"
    }

    private companion object {
        val DID_NOT_SEE = Regex(
            "(?<![\\p{L}\\p{N}])(?:didn'?t|did not|never|not|couldn'?t|could not|nahi|nahin|नहीं)\\s+(?:\\S+\\s+){0,2}?" +
                "(?:see|saw|seen|notice|noticed|witness|witnessed|watch|watched|dekha|देखा)(?![\\p{L}\\p{N}])",
        )
        val MODIFIED_ROLE_NOUNS = setOf(
            "guard", "guards", "officer", "officers", "staff", "personnel", "team", "desk", "office", "person", "persons",
            "man", "men", "supervisor", "incharge", "department", "cabin", "booth",
            // "water cooler" is not a water leak
            "cooler", "bottle", "bottles", "tank", "purifier", "dispenser", "fountain", "filter", "heater", "pump",
        )
        val ACTION_VERBS = setOf(
            "put", "puts", "placed", "place", "switch", "switched", "turn", "turned", "shut", "close", "closed", "isolate", "isolated",
            "inform", "informed", "call", "called", "calling", "notify", "notified", "report", "reported", "evacuate", "evacuated",
            "clean", "cleaned", "cover", "covered", "apply", "applied", "applying", "give", "gave", "given", "move", "moved",
            "remove", "removed", "stop", "stopped", "block", "blocked", "cordon", "cordoned", "extinguish", "extinguished",
            "unplug", "unplugged", "disconnect", "disconnected", "open", "opened", "ventilate", "mop", "mopped", "wipe", "wiped",
            "bandage", "bandaged", "take", "took", "taken", "bring", "brought", "send", "sent", "ask", "asked", "tell", "told",
            "alert", "alerted", "lock", "locked", "tape", "taped", "secure", "secured", "warn", "warned", "help", "helped",
            "escort", "escorted", "treat", "treated", "wash", "washed", "pour", "poured", "spray", "sprayed", "fix", "fixed",
            "repair", "repaired", "shift", "shifted", "rush", "rushed", "wrap", "wrapped", "elevate", "elevated", "ice", "cool",
            "kiya", "diya", "liya", "band", "bula", "bulaya", "bata", "hata", "hataya", "rok", "roka", "nikala",
        )
        val NOT_A_NAME = Regex("^(?:i|me|my|we|us|our|he|she|him|her|his|they|them|their|you|a|an|the|someone|somebody|another|one|none|nobody|no one)(?:\\s|$)", RegexOption.IGNORE_CASE)
        val ROLE_WORDS = setOf(
            "worker", "workers", "student", "students", "staff", "guard", "guards", "driver", "victim", "patient", "people", "person",
            "persons", "man", "men", "woman", "women", "boy", "girl", "member", "members", "employee", "employees", "colleague",
            "colleagues", "cleaner", "cook", "technician", "operator", "supervisor", "manager", "visitor", "visitors", "customer",
            "customers", "passenger", "passengers", "child", "children", "kid", "kids", "lady", "gentleman", "guy", "security",
            "delivery", "maintenance", "shop", "canteen", "warehouse", "hospital", "medical", "assistant", "helper", "intern",
            "faculty", "teacher", "professor", "resident", "residents", "someone", "somebody", "anyone", "everyone", "team",
        )
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

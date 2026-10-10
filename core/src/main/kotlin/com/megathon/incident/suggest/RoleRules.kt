package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus

/**
 * Role chips from the words (rules on the CPU):
 *  - someone listed as involved whose every mention is "X saw / noticed / spotted it" is a witness;
 *  - an `injury` type when the speaker said "nobody was hurt" gets a `near_miss` chip.
 * Conservative on purpose: a person also described as slipping, falling or being hurt stays involved,
 * and a negated sighting ("did not see", "never saw") is not a sighting.
 */
internal object RoleRules {

    private val WITNESS_VERB = Regex("\\b(saw|seen|noticed|spotted|watched|witnessed)\\b", RegexOption.IGNORE_CASE)
    private val NEGATION = Regex("\\b(not|never|no|didn't|did not|hasn't|wasn't)\\b|n't\\b", RegexOption.IGNORE_CASE)
    private val INVOLVEMENT = Regex(
        "\\b(slip|slipped|slips|fell|fall|falls|tripped|hurt|injured|cut|burned|burnt|fainted|collapsed|hit|trapped|stuck|bleeding|broke)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CLAUSE_BREAK = Regex("[.!?;,]|\\b(and|but|then)\\b", RegexOption.IGNORE_CASE)
    private val NAME_LIST = Regex("\\s*,\\s*|\\s+and\\s+", RegexOption.IGNORE_CASE)
    private val NOBODY_HURT = Regex(
        "\\b(nobody|no one|no-one|none of us)\\s+(was\\s+|were\\s+|got\\s+|is\\s+)?(hurt|injured|harmed)\\b|\\bno injur(y|ies)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val MACHINE_HEARD = setOf(ValueStatus.REPORTED, ValueStatus.SUGGESTED)

    fun chips(state: ReportState, transcript: String): List<Suggestion> = witnessChips(state, transcript) + injuryChips(state, transcript)

    private fun witnessChips(state: ReportState, transcript: String): List<Suggestion> {
        val involved = state.report.value(Field.PERSONS_INVOLVED)
        if (involved.status !in MACHINE_HEARD) return emptyList()
        val names = names(involved)
        if (names.isEmpty()) return emptyList()

        val clauses = transcript.split(CLAUSE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }
        val onlySaw = names.filter { onlyWitnessed(it, clauses) }
        if (onlySaw.isEmpty()) return emptyList()

        val who = onlySaw.joinToString(" and ")
        val reason = if (onlySaw.size == 1) "$who only saw it, so $who is a witness, not involved."
        else "$who only saw it, so they are witnesses, not involved."

        val chips = mutableListOf<Suggestion>()
        val remaining = names - onlySaw.toSet()
        if (remaining.isNotEmpty()) {
            chips += Suggestion(Field.PERSONS_INVOLVED, involved.text!!, remaining.joinToString(", "), reason, Source.RULE)
        }
        val witnesses = state.report.value(Field.WITNESSES)
        if (witnesses.status != ValueStatus.USER_EDITED && witnesses.status != ValueStatus.USER_CONFIRMED) {
            val current = names(witnesses)
            val added = onlySaw.filter { n -> current.none { it.equals(n, ignoreCase = true) } }
            if (added.isNotEmpty()) {
                chips += Suggestion(Field.WITNESSES, witnesses.text ?: "", (current + added).joinToString(", "), reason, Source.RULE)
            }
        }
        return chips
    }

    /** Every clause that mentions [name] has it seeing something, and none has it hurt or involved. */
    private fun onlyWitnessed(name: String, clauses: List<String>): Boolean {
        val mention = Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE)
        val mentioning = clauses.filter { mention.containsMatchIn(it) }
        return mentioning.isNotEmpty() && mentioning.all { clause ->
            val at = mention.find(clause)!!.range.last + 1
            val verb = WITNESS_VERB.find(clause, at) ?: return@all false
            val between = clause.substring(at, verb.range.first)
            !NEGATION.containsMatchIn(between) && !INVOLVEMENT.containsMatchIn(between)
        }
    }

    private fun injuryChips(state: ReportState, transcript: String): List<Suggestion> {
        val type = state.report.value(Field.INCIDENT_TYPE)
        if (type.status !in MACHINE_HEARD || type.text != "injury") return emptyList()
        val said = NOBODY_HURT.find(transcript) ?: return emptyList()
        return listOf(
            Suggestion(Field.INCIDENT_TYPE, "injury", "near_miss", "The speaker said \"${said.value}\"; an injury needs someone hurt.", Source.RULE),
        )
    }

    private fun names(value: FieldValue): List<String> =
        value.text?.split(NAME_LIST)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

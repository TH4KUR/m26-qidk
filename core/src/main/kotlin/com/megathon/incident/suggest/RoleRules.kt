package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus

/**
 * Role chips from the words (rules on the CPU):
 *  - someone listed as involved whose every mention is "X saw / noticed / spotted it", or "X was with me / was there /
 *    was standing next to me" (presence), is a witness;
 *  - a person who was present but was not emitted at all becomes a witness chip when the witnesses field is empty;
 *  - an `injury` type when the speaker said "nobody was hurt" gets a `near_miss` chip.
 * Conservative on purpose: a person also described as slipping, falling or being hurt stays involved, a negated
 * sighting or presence ("did not see", "was not there") is not one, and the speaker, roles and places are not names.
 */
internal object RoleRules {

    private val WITNESS_VERB = Regex("\\b(saw|seen|noticed|spotted|watched|witnessed)\\b", RegexOption.IGNORE_CASE)
    private val PRESENCE = Regex(
        "\\b(?:was|were|is|are)\\s+(?:also\\s+|just\\s+|right\\s+|standing\\s+)?" +
            "(?:with\\s+(?:me|us)|there|present|nearby|around|next\\s+to\\s+(?:me|us)|beside\\s+(?:me|us)|" +
            "in\\s+the\\s+(?:room|area|lab|office|building|corridor)|at\\s+the\\s+(?:spot|scene|site)|standing)\\b" +
            "|\\b(?:mere|hamare)\\s+s[a]+th\\s+(?:mein\\s+)?(?:tha|thi|the)\\b" +
            "|\\b(?:wahan|yahan|waha|yaha)\\s+(?:tha|thi|the)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NEGATION = Regex("\\b(not|never|no|didn't|did not|hasn't|wasn't|weren't)\\b|n't\\b", RegexOption.IGNORE_CASE)
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

    /** "Rahul was with me", "Rahul and Priya were there", "Rahul mere saath tha": capitalised names, case-sensitive on purpose. */
    private val PRESENT_NAMES = Regex(
        "\\b([A-Z][a-z]{2,})(?:\\s*(?:,|and|&)\\s*([A-Z][a-z]{2,}))?\\s+(?:(?:was|were)\\s+(?:also\\s+|just\\s+|right\\s+)?" +
            "(?:with\\s+(?:me|us)|there|present|nearby|around|next\\s+to\\s+(?:me|us)|beside\\s+(?:me|us)|" +
            "in\\s+the\\s+(?:room|area|lab|office|building|corridor)|at\\s+the\\s+(?:spot|scene|site)|standing)" +
            "|(?:mere|hamare)\\s+s[a]+th\\s+(?:mein\\s+)?(?:tha|thi|the)|(?:wahan|yahan|waha|yaha)\\s+(?:tha|thi|the))\\b",
    )
    private val NOT_NAMES = setOf(
        "The", "This", "That", "There", "These", "Those", "Then", "When", "Where", "What", "Who", "Which", "While", "And", "But",
        "Also", "Both", "Maybe", "Today", "Yesterday", "Tonight", "Someone", "Somebody", "Nobody", "Everyone", "Everybody",
        "Anyone", "Staff", "Guard", "Security", "Supervisor", "Manager", "Worker", "Workers", "Student", "Students", "Teacher",
        "Nurse", "Doctor", "Driver", "Cleaner", "Plumber", "Electrician", "Technician", "Officer", "People", "Person", "Boss",
        "Friend", "Colleague", "Our", "His", "Her", "Their", "Your", "Its", "Water", "Smoke", "Fire",
    )

    fun chips(state: ReportState, transcript: String): List<Suggestion> = witnessChips(state, transcript) + injuryChips(state, transcript)

    private enum class Kind { SAW, PRESENT }

    private fun witnessChips(state: ReportState, transcript: String): List<Suggestion> {
        val clauses = transcript.split(CLAUSE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }
        val involved = state.report.value(Field.PERSONS_INVOLVED)
        val listed = if (involved.status in MACHINE_HEARD) names(involved) else emptyList()
        val moved = listed.mapNotNull { n -> witnessKind(n, clauses)?.let { n to it } }
        val witnesses = state.report.value(Field.WITNESSES)
        val unlisted = if (witnesses.status == ValueStatus.MISSING) unlistedPresent(state, transcript, listed, clauses) else emptyList()
        if (moved.isEmpty() && unlisted.isEmpty()) return emptyList()

        val chips = mutableListOf<Suggestion>()
        val movedNames = moved.map { it.first }
        if (movedNames.isNotEmpty()) {
            val remaining = listed - movedNames.toSet()
            if (remaining.isNotEmpty()) {
                chips += Suggestion(Field.PERSONS_INVOLVED, involved.text!!, remaining.joinToString(", "), moveReason(moved), Source.RULE)
            }
        }
        if (witnesses.status != ValueStatus.USER_EDITED && witnesses.status != ValueStatus.USER_CONFIRMED) {
            val current = names(witnesses)
            val added = (movedNames + unlisted).filter { n -> current.none { it.equals(n, ignoreCase = true) } }.distinct()
            if (added.isNotEmpty()) {
                val reason = if (movedNames.isNotEmpty()) moveReason(moved) else presentReason(unlisted)
                chips += Suggestion(Field.WITNESSES, witnesses.text ?: "", (current + added).joinToString(", "), reason, Source.RULE)
            }
        }
        return chips
    }

    private fun moveReason(moved: List<Pair<String, Kind>>): String {
        val who = moved.joinToString(" and ") { it.first }
        val many = moved.size > 1
        return if (moved.all { it.second == Kind.SAW }) {
            if (many) "$who only saw it, so they are witnesses, not involved." else "$who only saw it, so $who is a witness, not involved."
        } else {
            if (many) "$who were there when it happened, so they are witnesses, not involved."
            else "$who was there when it happened, so $who is a witness, not involved."
        }
    }

    private fun presentReason(names: List<String>): String {
        val who = names.joinToString(" and ")
        return if (names.size > 1) "$who were there when it happened; add them as witnesses?" else "$who was there when it happened; add $who as a witness?"
    }

    /** Every clause that mentions [name] has it seeing something or being present, and none has it hurt or involved. */
    private fun witnessKind(name: String, clauses: List<String>): Kind? {
        val mention = Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE)
        val mentioning = clauses.filter { mention.containsMatchIn(it) }
        if (mentioning.isEmpty()) return null
        var sawAny = false
        for (clause in mentioning) {
            val at = mention.find(clause)!!.range.last + 1
            val verb = WITNESS_VERB.find(clause, at)
            if (verb != null) {
                val between = clause.substring(at, verb.range.first)
                if (NEGATION.containsMatchIn(between) || INVOLVEMENT.containsMatchIn(between)) return null
                sawAny = true
                continue
            }
            val presence = PRESENCE.find(clause, at) ?: return null
            val between = clause.substring(at, presence.range.first)
            // Present and also hurt ("Rahul was with me when he slipped") is involved, wherever the word sits in the clause.
            if (NEGATION.containsMatchIn(between) || INVOLVEMENT.containsMatchIn(clause.substring(at))) return null
        }
        return if (sawAny) Kind.SAW else Kind.PRESENT
    }

    /** Capitalised names that were present, not already listed, not the location, no role or common word, not hurt or negated. */
    private fun unlistedPresent(state: ReportState, transcript: String, listed: List<String>, clauses: List<String>): List<String> {
        val place = state.report.value(Field.LOCATION).let { listOfNotNull(it.text, it.evidence) }.joinToString(" ").lowercase()
        val found = linkedSetOf<String>()
        for (m in PRESENT_NAMES.findAll(transcript)) {
            val clause = clauses.firstOrNull { it.contains(m.value) } ?: m.value
            if (NEGATION.containsMatchIn(clause) || INVOLVEMENT.containsMatchIn(clause)) continue
            for (g in m.groupValues.drop(1)) {
                if (g.isEmpty() || g in NOT_NAMES) continue
                if (listed.any { it.equals(g, ignoreCase = true) }) continue
                if (Regex("\\b${Regex.escape(g.lowercase())}\\b").containsMatchIn(place)) continue
                found += g
            }
        }
        return found.toList()
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

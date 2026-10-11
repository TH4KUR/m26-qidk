package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Modality
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamClause
import com.megathon.incident.tam.TamDetector

/**
 * Chips for equipment and immediate actions the model did not emit (spec D16: report first, suggest after).
 * Only when the field is MISSING; never a fill. One chip per field (`original = ""`), because only one chip
 * can be accepted into an empty field. Rules on the CPU, using C's [TamDetector] for negation, hedges and plans.
 *
 * Equipment: nouns from [EQUIPMENT] in a clause that is not negated, hedged or planned, outside the location
 * value, not the first half of a place ("printer room"), and not inside the speaker's own action phrase (a tool
 * used to respond). Proposed as said, in order, joined with ", ".
 * Actions: a clause where the speaker (I/we) did or is doing something: perfect ("I've told"), progressive
 * ("I'm calling") or simple past ("we switched off"), with a verb from [ACTION_VERBS]; asserted only.
 * Proposed from the verb to the end of the clause part, joined with "; ".
 *
 * How the lists were made: written from general knowledge of workplaces (campus, office, kitchen, laboratory,
 * workshop, warehouse, vehicles, furniture, goods), before reading the gold sentences. Not copied from the gold.
 * Words that are often verbs or materials (saw, press, iron, light, switch, plug, drill, glass, phone) are left
 * out on purpose: a false chip is worse than a missed one.
 */
internal class MissedFieldRules(private val tam: TamDetector) {

    fun chips(state: ReportState, transcript: String): List<Suggestion> {
        val report = state.report
        val needEquipment = report.value(Field.EQUIPMENT_ASSET).status == ValueStatus.MISSING
        val needActions = report.value(Field.IMMEDIATE_ACTIONS).status == ValueStatus.MISSING
        if (!needEquipment && !needActions) return emptyList()

        val clauses = runCatching { tam.analyze(transcript) }.getOrDefault(emptyList())
        val chips = mutableListOf<Suggestion>()
        if (needEquipment) {
            val place = report.value(Field.LOCATION).let { listOfNotNull(it.text, it.evidence) }.filter { it.isNotBlank() }
            val nouns = clauses.filter(::plain).flatMap { equipment(it.text, place) }
                .distinctBy { it.lowercase() }
            if (nouns.isNotEmpty()) {
                chips += Suggestion(
                    Field.EQUIPMENT_ASSET, "", nouns.joinToString(", "),
                    "The speaker mentioned ${nouns.joinToString(" and ") { "\"$it\"" }}; add it as equipment?", Source.RULE,
                )
            }
        }
        if (needActions) {
            val actions = clauses.filter(::plain).flatMap { actions(it.text) }
                .distinctBy { it.lowercase() }
            if (actions.isNotEmpty()) {
                chips += Suggestion(
                    Field.IMMEDIATE_ACTIONS, "", actions.joinToString("; "),
                    "The speaker said they ${actions.joinToString(" and ") { "\"$it\"" }}; add it as an action taken?", Source.RULE,
                )
            }
        }
        return chips
    }

    /** Asserted: not negated, hedged or planned. */
    private fun plain(c: TamClause) = c.tam.modality == Modality.ASSERTED && !c.hedged

    private fun equipment(clause: String, place: List<String>): List<String> {
        // A tool the speaker used to respond ("put it out with the extinguisher") is not the equipment involved.
        val blocked = place.flatMap { p -> Regex(Regex.escape(p), RegexOption.IGNORE_CASE).findAll(clause).map { it.range } } +
            actionSpans(clause)
        val taken = mutableListOf<IntRange>()
        val found = mutableListOf<Pair<Int, String>>()
        for (m in EQUIPMENT_REGEX.findAll(clause)) {
            val r = m.range
            if (blocked.any { overlaps(it, r) } || taken.any { overlaps(it, r) }) continue
            if (PLACE_HEAD.matchesAt(clause, r.last + 1)) continue
            taken += r
            found += r.first to m.value
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun actions(clause: String): List<String> =
        actionSpans(clause).map { clause.substring(it.first, it.last + 1).trim().trimEnd('.', '!', '?') }.filter { it.isNotEmpty() }

    /** From the speaker's action verb to the end of that part of the clause. */
    private fun actionSpans(clause: String): List<IntRange> =
        SPEAKER_ACTION.findAll(clause).map { m ->
            val start = (m.groups["verb"] ?: m.groups["verb2"] ?: m.groups["verb3"])!!.range.first
            val end = PART_END.find(clause, start)?.range?.first ?: clause.length
            start until end
        }.toList()

    private fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last

    internal companion object {
        /** General equipment, vehicles, furniture and goods. Plural "s"/"es" is matched automatically. */
        val EQUIPMENT = listOf(
            // access, handling, storage
            "ladder", "stepladder", "step ladder", "scaffold", "scaffolding", "trolley", "cart", "hand truck", "pallet",
            "pallet jack", "forklift", "crate", "carton", "box", "bag", "sack", "bulk bag", "drum", "barrel", "bucket",
            "mop", "broom", "bin", "dustbin", "container", "tank", "water tank", "cylinder", "gas cylinder", "hose", "pipe",
            "valve", "pump", "water pump", "rack", "shelf", "shelves", "shelving", "cabinet", "cupboard", "locker", "drawer",
            "conveyor", "conveyor belt", "crane", "hoist", "rope", "chain", "sling",
            // machines and tools
            "machine", "generator", "compressor", "boiler", "motor", "welding machine", "welder", "soldering iron", "lathe",
            "grinder", "angle grinder", "hammer", "screwdriver", "spanner", "wrench", "chisel", "knife", "knives", "blade",
            "cutter", "box cutter", "scissors",
            // electrical and IT
            "cable", "wire", "wiring", "extension cord", "extension board", "extension cable", "socket", "switchboard",
            "electrical panel", "fuse box", "circuit breaker", "breaker", "transformer", "inverter", "UPS", "battery",
            "batteries", "charger", "adapter", "power bank", "laptop", "computer", "monitor", "printer", "projector",
            "server", "router", "fan", "ceiling fan", "exhaust fan", "air conditioner", "AC", "lamp", "tube light", "bulb",
            // kitchen and appliances
            "kettle", "stove", "gas stove", "oven", "microwave", "fryer", "deep fryer", "toaster", "refrigerator", "fridge",
            "freezer", "chiller", "cooler", "water cooler", "water dispenser", "water purifier", "coffee machine",
            "vending machine", "dishwasher", "washing machine", "dryer", "geyser", "water heater", "heater", "induction",
            "pressure cooker", "cooker", "tray",
            // laboratory
            "beaker", "flask", "test tube", "burner", "bunsen burner", "fume hood", "centrifuge", "autoclave",
            "microscope", "hot plate", "bottle", "jar",
            // safety
            "fire extinguisher", "extinguisher", "fire alarm", "smoke detector", "sprinkler", "first aid kit", "helmet",
            "harness", "goggles", "gloves",
            // furniture
            "chair", "stool", "table", "desk", "bench", "bed", "sofa", "whiteboard",
            // vehicles
            "car", "bike", "bicycle", "cycle", "motorcycle", "motorbike", "scooter", "truck", "lorry", "van", "bus",
            "tractor", "golf cart", "vehicle", "wheelchair", "elevator", "escalator",
        )

        private val EQUIPMENT_REGEX = Regex(
            "(?<![\\p{L}\\p{N}])(?:" +
                EQUIPMENT.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it).replace(" ", "\\E\\s+\\Q") } +
                ")(?:e?s)?(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )

        /** "printer room", "bike shed": the noun names a place, not equipment. */
        private val PLACE_HEAD = Regex("\\s+(?:room|area|hall|block|lab|laboratory|shed|bay|store|stand|yard|section|zone|park|parking)\\b", RegexOption.IGNORE_CASE)

        /** base -> past, participle, -ing. */
        val ACTION_VERBS: Map<String, List<String>> = mapOf(
            "call" to listOf("called", "called", "calling"), "ring" to listOf("rang", "rung", "ringing"),
            "tell" to listOf("told", "told", "telling"), "inform" to listOf("informed", "informed", "informing"),
            "notify" to listOf("notified", "notified", "notifying"), "alert" to listOf("alerted", "alerted", "alerting"),
            "report" to listOf("reported", "reported", "reporting"), "warn" to listOf("warned", "warned", "warning"),
            "contact" to listOf("contacted", "contacted", "contacting"), "ask" to listOf("asked", "asked", "asking"),
            "switch" to listOf("switched", "switched", "switching"), "turn" to listOf("turned", "turned", "turning"),
            "shut" to listOf("shut", "shut", "shutting"), "unplug" to listOf("unplugged", "unplugged", "unplugging"),
            "disconnect" to listOf("disconnected", "disconnected", "disconnecting"),
            "isolate" to listOf("isolated", "isolated", "isolating"), "evacuate" to listOf("evacuated", "evacuated", "evacuating"),
            "clear" to listOf("cleared", "cleared", "clearing"), "close" to listOf("closed", "closed", "closing"),
            "block" to listOf("blocked", "blocked", "blocking"), "cordon" to listOf("cordoned", "cordoned", "cordoning"),
            "tape" to listOf("taped", "taped", "taping"), "mark" to listOf("marked", "marked", "marking"),
            "mop" to listOf("mopped", "mopped", "mopping"), "clean" to listOf("cleaned", "cleaned", "cleaning"),
            "wipe" to listOf("wiped", "wiped", "wiping"), "sweep" to listOf("swept", "swept", "sweeping"),
            "move" to listOf("moved", "moved", "moving"), "remove" to listOf("removed", "removed", "removing"),
            "put" to listOf("put", "put", "putting"), "place" to listOf("placed", "placed", "placing"),
            "cover" to listOf("covered", "covered", "covering"), "apply" to listOf("applied", "applied", "applying"),
            "give" to listOf("gave", "given", "giving"), "take" to listOf("took", "taken", "taking"),
            "send" to listOf("sent", "sent", "sending"), "bring" to listOf("brought", "brought", "bringing"),
            "fetch" to listOf("fetched", "fetched", "fetching"), "use" to listOf("used", "used", "using"),
            "spray" to listOf("sprayed", "sprayed", "spraying"), "stop" to listOf("stopped", "stopped", "stopping"),
            "help" to listOf("helped", "helped", "helping"), "carry" to listOf("carried", "carried", "carrying"),
            "lift" to listOf("lifted", "lifted", "lifting"), "pick" to listOf("picked", "picked", "picking"),
            "bandage" to listOf("bandaged", "bandaged", "bandaging"), "wash" to listOf("washed", "washed", "washing"),
            "rinse" to listOf("rinsed", "rinsed", "rinsing"), "pull" to listOf("pulled", "pulled", "pulling"),
            "press" to listOf("pressed", "pressed", "pressing"), "raise" to listOf("raised", "raised", "raising"),
            "lock" to listOf("locked", "locked", "locking"), "open" to listOf("opened", "opened", "opening"),
            "extinguish" to listOf("extinguished", "extinguished", "extinguishing"),
            "drain" to listOf("drained", "drained", "draining"), "seal" to listOf("sealed", "sealed", "sealing"),
            "secure" to listOf("secured", "secured", "securing"), "escort" to listOf("escorted", "escorted", "escorting"),
            "rescue" to listOf("rescued", "rescued", "rescuing"), "check" to listOf("checked", "checked", "checking"),
        )

        private fun alt(words: Collection<String>) = words.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        private val PAST = alt(ACTION_VERBS.values.map { it[0] })
        private val PARTICIPLE = alt(ACTION_VERBS.values.map { it[1] })
        private val ING = alt(ACTION_VERBS.values.map { it[2] })
        private const val ADV = "(?:(?:already|just|also|then|immediately|quickly|now)\\s+)?"

        /** I/we + perfect, progressive or simple past of an action verb. */
        private val SPEAKER_ACTION = Regex(
            "(?<![\\p{L}'])(?:i|we)(?:" +
                "(?:'ve|'d|\\s+have|\\s+had)\\s+$ADV(?<verb>$PARTICIPLE)" +
                "|(?:'m|'re|\\s+am|\\s+are)\\s+$ADV(?<verb2>$ING)" +
                "|\\s+$ADV(?<verb3>$PAST)" +
                ")(?![\\p{L}])",
            RegexOption.IGNORE_CASE,
        )

        private val PART_END = Regex("[,;]|\\s+(?:and|then|so|because|but)\\s+(?:i|we|he|she|they|it)\\b", RegexOption.IGNORE_CASE)
    }
}

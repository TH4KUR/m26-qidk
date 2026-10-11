package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.LlmHint
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.Signal
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.LlmRequest
import com.megathon.incident.llm.LlmResult
import com.megathon.incident.priority.HazardLexicon
import com.megathon.incident.priority.KeyAnswer
import com.megathon.incident.priority.PriorityAdvisor
import com.megathon.incident.priority.PriorityRules
import com.megathon.incident.priority.TriageLanes
import com.megathon.incident.priority.UrgencyFeatureExtractor
import com.megathon.incident.priority.UrgencyStrategies
import com.megathon.incident.speech.SpeechLanes
import com.megathon.incident.suggest.Homophones
import com.megathon.incident.suggest.RuleSuggestionEngine
import com.megathon.incident.suggest.SiteDirectory
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import com.megathon.incident.tam.TamHedgeDetector
import com.megathon.incident.testing.Compute
import com.megathon.incident.testing.FakeLlmEngine
import com.megathon.incident.tts.HindiPhrases
import com.megathon.incident.tts.HindiSummary
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every English sentence the core can put on a screen has a Hindi form, derived from the real planner, engines, advisor and
 * session where it can be, so a sentence added later without Hindi fails here instead of reaching the screen in English.
 *
 * "Has a Hindi form" means: it changed, it holds Devanagari, no English word is left outside the parts the reporter said (quotes,
 * names, places), those parts are intact, and a second pass changes nothing.
 */
class HindiUiTextTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T09:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val today = LocalDate.of(2026, 10, 11)

    private fun deva(s: String) = s.any { it.code in 0x0900..0x097F }

    /** English words left once what the reporter said (quoted text, [keep]) and a few labels are taken out. */
    private fun latinLeft(text: String, keep: List<String>): List<String> {
        var rest = text.replace(Regex("“[^”]*”|\"[^\"]*\""), " ")
        for (k in keep.sortedByDescending { it.length }) rest = rest.replace(k, " ")
        return Regex("[A-Za-z]+").findAll(rest).map { it.value }.toList()
    }

    /** The Hindi of [english], checked; [keep] are the variable parts (names, places, quotes) that must come through whole. */
    private fun hindi(english: String, vararg keep: String): String {
        val hi = HindiUiText.of(english)
        assertNotEquals(english, hi, "no Hindi for: $english")
        assertTrue(deva(hi), "no Devanagari in: $hi (from $english)")
        val left = latinLeft(hi, keep.toList())
        assertTrue(left.isEmpty(), "English left $left in: $hi (from $english)")
        for (k in keep) if (k in english) assertTrue(k in hi, "\"$k\" lost in: $hi (from $english)")
        assertEquals(hi, HindiUiText.of(hi), "not idempotent: $hi")
        return hi
    }

    // ---- the contract ------------------------------------------------------------------------------------------

    @Test fun `a sentence with no entry comes back unchanged`() {
        for (s in listOf("", "   ", "Something the app never says.", "Water", "12:30", "Rahul", "Seminar Hall 2", "Room 20 near the lift")) {
            assertEquals(s, HindiUiText.of(s), "changed: \"$s\"")
        }
    }

    @Test fun `Hindi input is returned as it is`() {
        for (s in listOf("यह कहाँ है?", "क्या आपका मतलब “Seminar Hall 2” था?", "आग या धुआँ", "रविवार, ११ अक्टूबर")) {
            assertEquals(s, HindiUiText.of(s))
        }
    }

    @Test fun `everything HindiPhrases already says is shown as it is heard`() {
        val sentences = listOf(
            "What kind of incident is it?", "Where is it?", "Where did it happen?", "Was that today?", "Which day was that?",
            "When was that?", "How bad is it?", "Can you tell me more?", "Did anyone see it happen?", "Who was hurt?", "Who is unwell?",
            "Do you know who it was?", "Which tool, machine or item was involved?", PriorityAdvisorSentences.key, PriorityAdvisorSentences.emergency,
            "Your report is saved on this device.", "No harm done.", "I didn't catch that.", "I didn't quite get that.", "Could you say it again?",
            "Could you say it again, a little closer to the microphone?", "What is your colleague's name?", "What is the student's name?",
            "Was it the printer?", "Severity is moderate.", "That was today at about 10 in the morning.", "Fire or smoke at Seminar Hall 2.",
            "At Lab 4.", "Injury.",
        )
        for (s in sentences) {
            val heard = HindiPhrases.segments(s).joinToString(" ") { it.text }
            assertEquals(heard, HindiUiText.of(s), "shown differs from heard for: $s")
        }
    }

    private object PriorityAdvisorSentences {
        const val key = PriorityAdvisor.KEY_QUESTION
        const val emergency = PriorityAdvisor.EMERGENCY_LINE
    }

    // ---- the planner and its answer labels ----------------------------------------------------------------------

    private fun filled(vararg f: Pair<Field, String>): IncidentReport {
        var r = IncidentReport.empty("r", 0)
        for ((field, v) in f) r = r.with(field, FieldValue(v, ValueStatus.REPORTED, v))
        return r
    }

    private val mandatory = listOf(
        Field.INCIDENT_TYPE to "water_leak_flood", Field.LOCATION to "Conference Room 2",
        Field.DATE_TIME to "2026-10-11 ≈10:00 (around 10 AM)", Field.SEVERITY to "moderate",
    )

    @Test fun `every question and answer label the planner can produce has Hindi`() {
        val planner = QuestionPlanner()
        val prompts = linkedSetOf<String>()
        val labels = linkedSetOf<String>()
        fun take(q: Question?) = q?.asks?.forEach { a -> prompts += a.prompt; a.replies.forEach { labels += it.label } }

        for (mask in 0 until 16) for (level in Priority.entries) {
            val report = filled(*mandatory.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toTypedArray())
            val lane = TriageLanes.forLevel(level)
            take(planner.next(report, emptySet(), lane, emptyList()))
            // A time of day with no date, and a date that is itself unclear.
            take(planner.next(report.with(Field.DATE_TIME, FieldValue("date missing ≈10:00 (around 10 AM)", ValueStatus.REPORTED, "10")), setOf(Field.DATE_TIME), lane, emptyList()))
            take(planner.next(report.with(Field.DATE_TIME, FieldValue("2026-10-10 time missing (kal)", ValueStatus.REPORTED, "kal")), setOf(Field.DATE_TIME), lane, emptyList()))
            take(planner.next(report.with(Field.SEVERITY, FieldValue("severe", ValueStatus.SUGGESTED, "fire")), emptySet(), lane, emptyList()))
        }
        val heard = listOf("", "My colleague fainted near the lab", "A student slipped on the wet floor", "Someone fainted outside", "The visitor was hurt")
        val asked = listOf(emptyList(), listOf(setOf(Field.PERSONS_INVOLVED)), listOf(setOf(Field.EQUIPMENT_ASSET)), listOf(setOf(Field.WITNESSES)))
        for (type in IncidentType.entries) for (said in heard) for (before in asked) for (candidate in listOf<String?>(null, "printer", "the ladder")) {
            val report = filled(*mandatory.toTypedArray()).with(Field.INCIDENT_TYPE, FieldValue(type.wire, ValueStatus.REPORTED, type.wire))
            take(planner.next(report, emptySet(), TriageLanes.forLevel(Priority.LOW), before, said, candidate?.let { mapOf(Field.EQUIPMENT_ASSET to it) } ?: emptyMap()))
        }
        labels += QuestionPlanner.TYPE_CHOICES.map { it.first }
        labels += QuestionPlanner.SEVERITY_CHOICES.map { it.first }

        assertTrue(prompts.size >= 14, "the matrix should reach every phrasing, got ${prompts.size}: $prompts")
        for (p in prompts) hindi(p, "printer", "ladder")
        for (l in labels) hindi(l)
        // The key question and the quick replies built in the session.
        hindi(PriorityAdvisor.KEY_QUESTION)
        for (l in listOf("Yes", "No", "Not sure", "No one", "None", "Today", "Yesterday", "Just now", "Don't know")) hindi(l)
    }

    @Test fun `a role and an item keep their own words in the question`() {
        assertEquals("आपके सहकर्मी का नाम क्या है?", hindi("What is your colleague's name?"))
        assertEquals("क्या यह printer था?", hindi("Was it the printer?", "printer"))
        assertEquals("क्या यह मशीन था?", hindi("Was it the मशीन?"))
    }

    @Test fun `the answer labels are short Hindi words`() {
        assertEquals("आज", HindiUiText.of("Today"))
        assertEquals("हाँ", HindiUiText.of("Yes"))
        assertEquals("नहीं", HindiUiText.of("No"))
        assertEquals("पक्का नहीं", HindiUiText.of("Not sure"))
        assertEquals("पानी का रिसाव", HindiUiText.of("Water leak"))
        assertEquals("मामूली", HindiUiText.of("Minor"))
        assertEquals("अत्यंत गंभीर", HindiUiText.of("Critical"))
        assertEquals("कोई नुकसान नहीं", HindiUiText.of("No harm"))
    }

    // ---- the suggestion engine and the focused probe -------------------------------------------------------------

    private val engine = RuleSuggestionEngine(
        SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
        Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
    )

    private fun state(vararg f: Pair<Field, FieldValue>): ReportState {
        var s = ReportState.empty("r1", 0)
        for ((field, v) in f) s = s.copy(report = s.report.with(field, v))
        return s
    }

    private fun heardValue(text: String, evidence: String = text) = FieldValue(text, ValueStatus.REPORTED, evidence)

    private val ruleChips: List<Suggestion> by lazy {
        buildList {
            addAll(engine.suggest(state(Field.LOCATION to heardValue("a sh2")), "There is a fire in a sh2 right now."))
            addAll(engine.suggest(state(Field.LOCATION to heardValue("maple hostle")), "Water in maple hostle."))
            addAll(engine.suggest(state(Field.LOCATION to heardValue("conference room to")), "Leak in conference room to."))
            addAll(engine.suggest(state(Field.LOCATION to heardValue("north block to research block")), "from north block to research block where the cable is sparking"))
            addAll(engine.suggest(state(Field.DATE_TIME to FieldValue("last week", ValueStatus.SUGGESTED, "weak")), "It happened last weak."))
            addAll(engine.suggest(state(Field.LOCATION to FieldValue("Room 2", ValueStatus.SUGGESTED, "Room 2")), "I think it was Room 2."))
            addAll(engine.suggest(state(Field.SEVERITY to FieldValue("minor", ValueStatus.REPORTED, "minor")), "I am not sure, maybe it was minor."))
            addAll(engine.suggest(state(Field.PERSONS_INVOLVED to heardValue("Ravi, Priya")), "Ravi slipped near the pantry, Priya saw it."))
            addAll(engine.suggest(state(Field.PERSONS_INVOLVED to heardValue("Ravi, Priya, Suresh")), "Ravi slipped near the pantry, Priya and Suresh saw it."))
            addAll(engine.suggest(state(Field.PERSONS_INVOLVED to heardValue("Ravi, Priya")), "Ravi slipped. Priya was there when it happened."))
            addAll(engine.suggest(state(Field.PERSONS_INVOLVED to heardValue("Ravi")), "Ravi slipped. Priya and Suresh were with me when it happened."))
            addAll(engine.suggest(state(Field.INCIDENT_TYPE to heardValue("injury", "slipped")), "Ravi slipped on the wet floor. Nobody was hurt."))
            addAll(engine.suggest(state(), "I tripped over a ladder in the corridor and I switched off the mains."))
            addAll(engine.suggest(state(), "The vending machine fell and the guard called maintenance."))
        }
    }

    @Test fun `every reason the rule engine produces has Hindi`() {
        assertTrue(ruleChips.size >= 10, "the scenarios should reach most rules, got ${ruleChips.map { it.reason }}")
        val kinds = ruleChips.map { it.reason.substringBefore(' ') + " " + it.reason.substringAfter(' ').take(6) }.toSet()
        assertTrue(kinds.size >= 6, "too few reason kinds: $kinds")
        val names = listOf("Ravi", "Priya", "Suresh", "Seminar Hall 2", "Maple Hostel", "Conference Room 2", "Research Block", "North Block", "Room 2")
        for (c in ruleChips) hindi(c.reason, *names.filter { it in c.reason }.toTypedArray())
    }

    @Test fun `the chip questions keep the proposed value`() {
        val expected = mapOf(
            "Did you mean “Seminar Hall 2”?" to "क्या आपका मतलब “Seminar Hall 2” था?",
            "Confirm “Room 2”?" to "क्या “Room 2” सही है?",
            "Change to “near_miss”?" to "“near_miss” में बदलें?",
            "Did you mean “सेमिनार हॉल 2”?" to "क्या आपका मतलब “सेमिनार हॉल 2” था?",
        )
        for ((en, hi) in expected) assertEquals(hi, HindiUiText.of(en))
        assertEquals("क्या आपका मतलब \"Seminar Hall 2\" था?", HindiUiText.of("Did you mean \"Seminar Hall 2\"?"))
    }

    private class ProbeReplies(private val item: String, private val action: String, private val people: String) : LlmEngine {
        override val name = "PROBE-FAKE"
        override suspend fun complete(request: LlmRequest) = LlmResult(
            when {
                "NAMED people" in request.system -> people
                "ALREADY DID" in request.system -> action
                else -> item
            },
            5, Compute.SIMULATED,
        )
    }

    @Test fun `every reason the focused probe produces has Hindi`() {
        val said = "The drill sparked in the workshop and I pressed the stop button. Rahul was with me when it happened. Priya slipped."
        val llm = ProbeReplies(
            item = """{"item":"drill","quote":"The drill sparked in the workshop"}""",
            action = """{"action":"pressed the stop button","quote":"I pressed the stop button"}""",
            people = """{"witnesses":["Rahul"],"persons_involved":["Priya"]}""",
        )
        val chips = runBlocking { FocusedProbe(llm, timeoutMs = 500).propose(IncidentReport.empty("r", 0), said) }
        assertEquals(setOf(Field.EQUIPMENT_ASSET, Field.IMMEDIATE_ACTIONS, Field.WITNESSES, Field.PERSONS_INVOLVED), chips.map { it.field }.toSet())
        for (c in chips) hindi(c.reason, "Rahul", "Priya", "drill", "The drill sparked in the workshop", "I pressed the stop button", "pressed the stop button")
        val both = runBlocking {
            FocusedProbe(ProbeReplies("{}", "{}", """{"witnesses":["Rahul","Meera"]}"""), timeoutMs = 500)
                .propose(IncidentReport.empty("r", 0), "Rahul and Meera were with me when it happened.")
        }
        for (c in both) hindi(c.reason, "Rahul", "Meera")
    }

    // ---- the priority advisor ----------------------------------------------------------------------------------

    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))

    private fun reasonsOf(strategyId: String, run: (PriorityAdvisor) -> Unit): List<String> {
        val strategy = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == strategyId }
        val advisor = PriorityAdvisor(extractor, strategy)
        run(advisor)
        return advisor.onChunk("").advice.reasons
    }

    @Test fun `every reason the advisor gives has Hindi, whichever strategy scores`() {
        val transcripts = listOf(
            "there is a fire in the kitchen right now", "someone is unconscious and people are trapped", "a pothole near the gate",
            "please hurry up and fix the AC", "the paint is peeling, it is not urgent", "help! the gas is leaking", "there is a water leak near the electrical panel",
            "a man with a knife is inside the building", "my colleague fainted", "the printer in lab 3 is broken", "something is off",
        )
        val all = linkedSetOf<String>()
        for (strategy in UrgencyStrategies.all(PriorityRules.loadDefault())) for (t in transcripts) {
            all += reasonsOf(strategy.id) { it.onChunk(t) }
        }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("it is quiet", listOf(Signal("event", "gunshot", Source.YAMNET))) }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("the alarm is ringing", listOf(Signal("event", "fire alarm", Source.SENSEVOICE))) }
        for (emotion in listOf("angry", "sad", "fearful")) {
            all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("the lift is stuck", listOf(Signal("emotion", emotion, Source.SENSEVOICE))) }
        }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("the lift is stuck", hint = LlmHint(impact = 5, ongoing = true)) }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("a small crack in the wall", hint = LlmHint(impact = 4)) }
        for (sev in listOf("critical", "severe", "moderate")) {
            all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onReport(filled(Field.SEVERITY to sev)) }
        }
        for (type in IncidentType.entries) all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onReport(filled(Field.INCIDENT_TYPE to type.wire)) }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("something strange"); it.answerKeyQuestion(KeyAnswer.YES) }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("something strange"); it.answerKeyQuestion(KeyAnswer.UNSURE) }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("fire in the lab"); it.override(Priority.LOW, "set by the reporter") }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) { it.onChunk("fire in the lab"); it.override(Priority.LOW, "cancelled alarm") }
        all += reasonsOf(UrgencyStrategies.DEFAULT_ID) {
            it.onChunk("fire in the lab")
            it.onChunk("sorry, false alarm")
            it.acceptDeescalation()
        }

        assertTrue(all.size >= 40, "the scenarios should reach most reasons, got ${all.size}")
        for (r in all) hindi(r)
    }

    @Test fun `the de-escalation reason and the rule reasons have Hindi`() {
        val advisor = PriorityAdvisor(extractor, UrgencyStrategies.all(PriorityRules.loadDefault()).first())
        advisor.onChunk("fire in the lab")
        val offer = advisor.onChunk("sorry, false alarm").deescalation
        assertTrue(offer != null)
        hindi(offer.reason, "false alarm")
        for (rule in PriorityRules.loadDefault().rules) hindi("rule ${rule.id}: ${rule.reason}")
        for (label in HazardLexicon.loadDefault().file.hazards.map { it.label.replace('_', ' ') }) {
            hindi("$label (\"aag\") → impact 3", "aag")
        }
        for (label in HazardLexicon.loadDefault().file.signals.map { it.label }) hindi("$label heard → impact ≥ 4, immediacy ≥ 4")
    }

    @Test fun `a reason keeps its source in Hindi`() {
        val hi = HindiUiText.of("people may be at risk → impact +1 [TEXT_CUE]")
        assertTrue(hi.endsWith("]") && deva(hi) && "TEXT_CUE" !in hi, hi)
        assertTrue(HindiUiText.of("risk matrix: impact 3 × immediacy 2 = 6 [RULE]").let { "3" in it && "6" in it && deva(it) })
    }

    // ---- the session: hints, failures, undo, journal, derived text ----------------------------------------------

    private fun session(llm: LlmEngine = FakeLlmEngine()) =
        Pipeline.newSession(llm, InMemoryReportStore(), clock, askOptional = false)

    private fun ReportSession.say(text: String) = runBlocking { onUtterance(text) }

    @Test fun `what the session shows for the slide example has Hindi`() {
        val s = session()
        s.say(FakeLlmEngine.DECK_TRANSCRIPT)
        val ui = s.ui.value
        val texts = linkedSetOf<String>()
        ui.say?.text?.let { texts += it }
        for (f in ui.fields) f.hint?.let { texts += it }
        assertTrue("Which day was this?" in texts)
        for (t in texts) hindi(t)
        // A suggested value explains itself in the reporter's own words.
        val hi = hindi("Suggested because: \"it is a big leak\"", "it is a big leak")
        assertTrue("it is a big leak" in hi)

        s.say("   ")
        hindi((s.ui.value.state as SessionState.Failed).message)

        val s2 = session()
        s2.say(FakeLlmEngine.DECK_TRANSCRIPT)
        s2.answer((s2.ui.value.state as SessionState.Asking).question.asks.first().replies.first())
        s2.undo()
        assertTrue(s2.ui.value.journal.any { it.reason.startsWith("tapped ") } && s2.ui.value.journal.any { it.reason == "last change undone" })
        for (j in s2.ui.value.journal) hindi(j.reason)
    }

    @Test fun `the undo prompts name the field and the value in Hindi`() {
        for (f in Labels.ORDER) {
            val label = Labels.fieldLabel(f)
            for (en in listOf("$label set to Today", "$label changed", "$label confirmed", "$label changed to Seminar Hall 2", "$label changed to empty")) {
                val hi = HindiUiText.of(en)
                assertNotEquals(en, hi, en)
                assertTrue(deva(hi), hi)
                assertEquals(hi, HindiUiText.of(hi))
            }
        }
        assertTrue("Seminar Hall 2" in HindiUiText.of("Where changed to Seminar Hall 2"))
        assertTrue("आज" in HindiUiText.of("When set to Today"))
    }

    @Test fun `the saved-report text and the next step have Hindi, and the next step is the one that is spoken`() {
        val reports = mutableListOf<IncidentReport>()
        for (type in IncidentType.entries) for (sev in listOf("minor", "severe", "none")) {
            reports += filled(
                Field.INCIDENT_TYPE to type.wire, Field.LOCATION to "Seminar Hall 2",
                Field.DATE_TIME to "2026-10-11 ≈10:00 (around 10 AM)", Field.SEVERITY to sev,
                Field.IMMEDIATE_ACTIONS to "switched off the mains",
            )
        }
        reports += filled(Field.INCIDENT_TYPE to "injury")
        reports += filled(Field.LOCATION to "Lab 4", Field.DATE_TIME to "2026-10-10 14:30 (yesterday at 2:30)")
        reports += filled(Field.DATE_TIME to "date missing morning (this morning)", Field.SEVERITY to "moderate")
        reports += filled(Field.DATE_TIME to "2026-10-03 ≈09:05 (around 9)")
        reports += filled()
        for (r in reports) {
            hindi(DerivedFields.description(r, today), "Seminar Hall 2", "Lab 4", "switched off the mains")
            hindi(DerivedFields.followUp(r))
            val spoken = DerivedFields.spoken(r, today)
            val shown = HindiUiText.of(spoken)
            assertEquals(HindiPhrases.segments(spoken).joinToString(" ") { it.text }, shown, spoken)
        }
        // The next step is the very text the Hindi summary speaks.
        for (type in IncidentType.entries) {
            if (type == IncidentType.OTHER) continue
            for (sev in listOf("moderate", "severe")) {
                val r = filled(Field.INCIDENT_TYPE to type.wire, Field.SEVERITY to sev)
                val spokenChain = HindiSummary.compose(r, null, today).joinToString(" ") { it.text }
                val step = HindiUiText.of(DerivedFields.followUp(r))
                assertTrue(step in spokenChain, "next step not spoken as shown for $type/$sev:\n$step\n$spokenChain")
            }
        }
    }

    @Test fun `the description keeps the place, the quote and the action as the reporter said them`() {
        val r = filled(
            Field.INCIDENT_TYPE to "water_leak_flood", Field.LOCATION to "Seminar Hall 2",
            Field.DATE_TIME to "2026-10-11 ≈10:00 (around 10 AM)", Field.SEVERITY to "moderate", Field.IMMEDIATE_ACTIONS to "isolated the area",
        )
        assertEquals(
            "Seminar Hall 2 में पानी का रिसाव या बाढ़, आज सुबह लगभग दस बजे। गंभीरता: मध्यम। अब तक किया गया: isolated the area।",
            HindiUiText.of(DerivedFields.description(r, today)),
        )
    }

    @Test fun `the words of the saved-reports list have Hindi`() {
        val untyped = ReportList.row(IncidentReport.empty("r1", 0), today)
        hindi(untyped.title)
        hindi(untyped.status)
        val guessed = IncidentReport.empty("r2", 0).with(Field.SEVERITY, FieldValue("severe", ValueStatus.SUGGESTED, "fire"))
        assertEquals("गंभीर (सुझाया गया)", HindiUiText.of(ReportList.row(guessed, today).severity!!))
        hindi("heard a correction")
    }

    @Test fun `a time with no short spoken form is written in numerals, never dropped`() {
        val r = filled(Field.DATE_TIME to "2026-10-03 ≈09:05 (around 9)")
        assertEquals("घटना, शनिवार, तीन अक्टूबर को सुबह लगभग ९:०५ बजे।", HindiUiText.of(DerivedFields.description(r, today)))
        val night = filled(Field.INCIDENT_TYPE to "injury", Field.DATE_TIME to "2026-10-11 23:40 (11:40 at night)")
        assertEquals("चोट, आज रात ११:४० बजे।", HindiUiText.of(DerivedFields.description(night, today)))
        val partOnly = filled(Field.DATE_TIME to "date missing morning (this morning)")
        assertEquals("घटना, सुबह में · दिन नहीं बताया।", HindiUiText.of(DerivedFields.description(partOnly, today)))
    }

    // ---- the view model's short messages ------------------------------------------------------------------------

    @Test fun `the short messages the app shows have Hindi`() {
        val messages = listOf(
            "The Hindi lane needs the real speech engine. This run uses the scripted demo, so English is used.",
            "The Hindi lane needs the real speech engine. This run uses the scripted demo.",
            "Microphone access is off, so I can't hear you. You can turn it on in Settings, or type your answers.",
            "I couldn't open the microphone. Another app may be using it.",
            "This build can't start. It would use a simulated engine, and simulated results are blocked in release builds.",
            "This build can't start.",
            "scripted demo, not live recognition",
            "speech is scripted, not live recognition",
            "speech is live, but the report is filled by canned replies, not a live model",
            SpeechLanes.select("te").notice!!,
        )
        for (m in messages) hindi(m)
        hindi(SpeechLanes.select("fr-FR").notice!!, "fr-FR")
        hindi("The on-device model did not load yet: out of memory. It will be tried again when you speak.", "out of memory")
        hindi("I couldn't hear that: the speech engine failed")
        hindi("The last read failed: unknown error")
        hindi("The Hindi voice is not available (the Hindi voice could not be created), so replies are spoken in English.")
        hindi("Can't read that file: /sdcard/a.wav", "/sdcard/a.wav")
    }

    @Test fun `the severities and incident types have one Hindi word each, the same as the spoken one`() {
        for (s in Severity.entries) {
            val label = Labels.severity(s.wire)
            val hi = HindiUiText.of(label)
            assertTrue(deva(hi), label)
            HindiPhrases.severityName(label)?.let { assertEquals(it, hi) }
        }
        for (t in IncidentType.entries) {
            val label = Labels.type(t.wire)
            assertEquals(HindiPhrases.typeName(label), HindiUiText.of(label), label)
        }
    }
}

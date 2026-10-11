package com.megathon.incident.session

import com.megathon.incident.llm.LlmEngine
import com.megathon.incident.llm.PayloadValidator
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.priority.HazardLexicon
import com.megathon.incident.priority.PriorityAdvisor
import com.megathon.incident.priority.PriorityRules
import com.megathon.incident.priority.SeveritySuggester
import com.megathon.incident.priority.UrgencyFeatureExtractor
import com.megathon.incident.priority.UrgencyStrategies
import com.megathon.incident.suggest.SuggestionEngine
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** Wires the rule engine (C's parser, validator, severity and priority) into one [ReportSession]. */
object Pipeline {
    fun newSession(
        llm: LlmEngine,
        store: ReportStore,
        clock: Clock = Clock.systemDefaultZone(),
        zone: ZoneId = ZoneId.of("Asia/Kolkata"),
        prompts: PromptBuilder = PromptBuilder.default(),
        suggestions: SuggestionEngine? = null,
        /** Cross-questioning about tools or items, people and witnesses after the mandatory answers; off keeps the mandatory-only flow. */
        askOptional: Boolean = true,
    ): ReportSession {
        val tam = TamDetector(TamCueLists.loadDefault())
        val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), tam)
        val strategy = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == UrgencyStrategies.DEFAULT_ID }
        val counter = AtomicInteger()
        return ReportSession(
            llm = llm,
            validator = PayloadValidator(tam, TemporalResolver(zone)),
            severityHints = SeveritySuggester(extractor),
            newAdvisor = { PriorityAdvisor(extractor, strategy) },
            planner = QuestionPlanner(askOptional),
            prompts = prompts,
            store = store,
            clock = clock,
            zone = zone,
            newId = { "report-${clock.millis()}-${counter.incrementAndGet()}" },
            suggestions = suggestions,
        )
    }
}

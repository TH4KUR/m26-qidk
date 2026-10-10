package com.megathon.incident

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.llm.ParseResult
import com.megathon.incident.llm.PayloadValidator
import com.megathon.incident.llm.ToolCallParser
import com.megathon.incident.priority.HazardLexicon
import com.megathon.incident.priority.PriorityAdvisor
import com.megathon.incident.priority.PriorityRules
import com.megathon.incident.priority.SeveritySuggester
import com.megathon.incident.priority.UrgencyFeatureExtractor
import com.megathon.incident.priority.UrgencyStrategies
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Definition of done for person C (handoff section 7): the deck's own example, through the parser,
 * the validator, the severity suggester and the priority advisor, as the app will chain them.
 */
class SlideExampleEndToEndTest {
    private val tam = TamDetector(TamCueLists.loadDefault())
    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), tam)
    private val validator = PayloadValidator(tam, TemporalResolver(ZoneId.of("Asia/Kolkata")))
    private val capturedAt = Instant.parse("2026-10-11T09:30:00Z")

    private val transcript =
        "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."

    // What a small model typically emits: prose, a fence, and a trailing comma.
    private val llmOutput = """
        Sure, here is the report:
        ```json
        {"tool":"update_report","arguments":{
          "incident_type":"water leak","severity":null,"location":"Conference Room 2",
          "date_time_text":"around 10 AM","persons_involved":[],"equipment_asset":null,
          "immediate_actions":"isolated the area","witnesses":[],
          "evidence":{"incident_type":"water leak","location":"near Conference Room 2",
                      "date_time_text":"around 10 AM","immediate_actions":"already isolated the area"},}}
        ```
    """.trimIndent()

    @Test
    fun theDeckExampleParsesValidatesAndLeavesSeverityAndDateToBeAsked() {
        val parsed = assertIs<ParseResult.Ok>(ToolCallParser.parse(llmOutput))
        val result = validator.validate(parsed.payload, transcript, capturedAt)

        val byField = result.proposals.associateBy { it.field }
        assertEquals("water_leak_flood", byField[Field.INCIDENT_TYPE]!!.value.text)
        assertEquals("Conference Room 2", byField[Field.LOCATION]!!.value.text)
        assertEquals("isolated the area", byField[Field.IMMEDIATE_ACTIONS]!!.value.text)
        assertTrue(result.proposals.all { it.value.status == ValueStatus.REPORTED })
        assertTrue(result.rejections.isEmpty(), result.rejections.toString())

        // Severity was never stated and the date is missing, so the app must ask for both.
        assertNull(byField[Field.SEVERITY])
        val time = byField[Field.DATE_TIME]!!
        assertTrue("date missing" in time.value.text!!)
        assertTrue(time.note!!.contains("needs clarification"))
    }

    @Test
    fun theSeverityIsOnlyEverSuggestedAndTheIncidentIsLowPriority() {
        val suggestion = SeveritySuggester(extractor).suggest(transcript)!!
        assertEquals(Severity.MINOR, suggestion.severity)
        assertEquals(ValueStatus.SUGGESTED, suggestion.toFieldValue().status)

        val strategy = UrgencyStrategies.all(PriorityRules.loadDefault()).single { it.id == UrgencyStrategies.DEFAULT_ID }
        val advisor = PriorityAdvisor(extractor, strategy)
        val out = advisor.onChunk(transcript)
        assertEquals(Priority.LOW, out.advice.level)
        assertNull(out.spoken, "a contained leak must not trigger the emergency line")
        assertTrue(advisor.lane().requireConfirmation, "LOW is the thorough lane")
    }
}

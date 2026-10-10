package com.megathon.incident.eval

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.ValueStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-field cases are shared with tools/laptop_eval/test_score.py, so the Kotlin and Python
 * scorers cannot disagree silently.
 */
class EvalScorerTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun sharedCasesAgreeWithPython() {
        val text = javaClass.getResource("/fixtures/eval/scorer_cases.jsonl")!!.readText()
        val cases = text.lines().filter { it.isNotBlank() }.map { obj(it) }
        assertTrue(cases.size >= 29, "expected the shared case file, got ${cases.size} cases")
        val failures = cases.mapNotNull { c ->
            val pred = c["pred"]?.let { if (it is JsonObject) it else null }
            val got = EvalScorer.outcome(
                c["field"]!!.jsonPrimitive.content, c["gold"]!!.jsonObject, pred,
                c["captured_at"]?.jsonPrimitive?.contentOrNull,
            )
            val want = c["expect"]!!.jsonPrimitive.content
            if (got.wire != want) "${c["id"]!!.jsonPrimitive.content}: got ${got.wire}, want $want" else null
        }
        assertEquals(emptyList(), failures)
    }

    private val deckGold = obj(
        """{"id":"g01","gold":{
          "incident_type":{"status":"REPORTED","value":"water_leak_flood"},"severity":{"status":"MISSING"},
          "location":{"status":"REPORTED","value":"Conference Room 2"},
          "date_time":{"status":"REPORTED","date_offset_days":null,"time":"10:00","part_of_day":null,"approximate":true,"text":"around 10 AM"},
          "persons_involved":{"status":"MISSING"},"equipment_asset":{"status":"MISSING"},
          "immediate_actions":{"status":"REPORTED","value":"isolated the area"},"witnesses":{"status":"MISSING"}},
          "missing_mandatory":["severity"]}""",
    )

    private val perfect = obj(
        """{"gold_id":"g01","captured_at":"2026-10-11T12:00:00+05:30","asked":[["SEVERITY"]],
          "report":{"id":"r","createdAtEpochMs":0,"fields":{
            "INCIDENT_TYPE":{"text":"water_leak_flood","status":"REPORTED"},
            "LOCATION":{"text":"Conference Room 2","status":"REPORTED"},
            "DATE_TIME":{"text":"date missing ≈10:00 (around 10 AM)","status":"REPORTED"},
            "IMMEDIATE_ACTIONS":{"text":"isolated the area","status":"REPORTED"}}}}""",
    )

    private val blank = obj("""{"gold_id":"g01","asked":[],"report":{"id":"r","createdAtEpochMs":0,"fields":{}}}""")

    @Test
    fun perfectRunScoresOne() {
        val s = EvalScorer.score(listOf(deckGold), listOf(perfect))
        assertEquals(1.0, s.overall.recall.value)
        assertEquals(1.0, s.overall.precision.value)
        assertEquals(0.0, s.overall.hallucinationRate.value)
        assertEquals(1.0, s.followUpPrecision.value)
    }

    @Test
    fun blankRunHasZeroRecallAndNoPrecision() {
        val s = EvalScorer.score(listOf(deckGold), listOf(blank))
        assertEquals(Ratio(0, 4).value, s.overall.recall.value)
        assertEquals(0, s.overall.precision.den)
        assertNull(s.overall.precision.value)
    }

    @Test
    fun missingPredictionIsCountedAndScoresZero() {
        val s = EvalScorer.score(listOf(deckGold), emptyList())
        assertEquals(1, s.nMissingPredictions)
        assertEquals(0, s.overall.recall.num)
    }

    @Test
    fun hallucinatedWitnessCountsAgainstGoldEmptySlots() {
        val withWitness = obj(perfect.toString().replace(""""IMMEDIATE_ACTIONS"""", """"WITNESSES":{"text":"John","status":"REPORTED"},"IMMEDIATE_ACTIONS""""))
        val s = EvalScorer.score(listOf(deckGold), listOf(withWitness))
        assertEquals(Ratio(1, 4), s.overall.hallucinationRate)
        assertEquals(1, s.fields.getValue("witnesses").hallucination)
    }

    @Test
    fun followUpAskForFilledFieldLowersPrecision() {
        val asked = obj(perfect.toString().replace("""[["SEVERITY"]]""", """[["SEVERITY","LOCATION"]]"""))
        val s = EvalScorer.score(listOf(deckGold), listOf(asked))
        assertEquals(1 to 2, s.followUpPrecision.num to s.followUpPrecision.den)
        assertEquals(1 to 1, s.followUpRecall.num to s.followUpRecall.den)
    }

    @Test
    fun typedReportsScoreTheSameAsJson() {
        val report = IncidentReport.empty("r", 0)
            .with(Field.INCIDENT_TYPE, FieldValue("water_leak_flood", ValueStatus.REPORTED))
            .with(Field.LOCATION, FieldValue("Conference Room 2", ValueStatus.REPORTED, "near Conference Room 2"))
            .with(Field.DATE_TIME, FieldValue("date missing ≈10:00 (around 10 AM)", ValueStatus.REPORTED))
            .with(Field.IMMEDIATE_ACTIONS, FieldValue("isolated the area", ValueStatus.REPORTED))
        val pred = Prediction("g01", report, asked = listOf(listOf(Field.SEVERITY)), capturedAt = "2026-10-11T12:00:00+05:30")
        assertEquals(1.0, EvalScorer.score(listOf(deckGold), listOf(pred.toJson())).overall.recall.value)
    }

    @Test
    fun wilsonIntervalIsInsideUnitRange() {
        val ci = Ratio(4, 4).ci95!!
        assertTrue(ci.first in 0.0..1.0 && ci.second <= 1.0 && ci.first < ci.second)
    }
}

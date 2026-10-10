package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.domain.IncidentReport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The default prompt is the one measured best on the kit (docs/experiments/A-20261011-llm-on-kit.md, prompt v8). */
class PromptBuilderTest {
    private val system = PromptBuilder.default().build("There was a leak.", IncidentReport.empty("r1", 0)).system

    @Test fun `the default prompt does not teach the model to write null or empty lists`() {
        // The old default's template showed "incident_type":null and "witnesses":[]; the 4B model copied it, and the validator
        // rejected nearly every reply (no fields filled on the kit).
        assertFalse(system.contains(":null"), "template must not show null values")
        assertFalse(system.contains("[]\"") && system.contains("Use null"), "must not tell the model to use null or []")
    }

    @Test fun `the default prompt ties every evidence entry to a written field and names equipment and time`() {
        assertTrue(system.contains("MUST also be written in arguments"))
        assertTrue(system.contains("equipment_asset: the machine"))
        assertTrue(system.contains("date_time_text: include it"))
    }

    @Test fun `the report so far and the worker words reach the model`() {
        val req = PromptBuilder.default().build("Smoke from the printer.", IncidentReport.empty("r1", 0))
        assertTrue(req.user.contains("Smoke from the printer."))
    }

    private fun report() = IncidentReport.empty("r1", 0)
        .with(Field.INCIDENT_TYPE, FieldValue("fire_smoke", ValueStatus.REPORTED, "huge fire"))
        .with(Field.DATE_TIME, FieldValue("2026-10-11 morning (today morning)", ValueStatus.REPORTED, "today morning"))

    @Test fun theModelSeesWhatTheWorkerSaidEarlierSoThereAndItCanBeResolved() {
        val req = PromptBuilder.default().build("I told you where it was.", report(), earlier = listOf("There was a huge fire in SH-2.", "Raza is trapped there."))
        assertTrue(req.user.contains("Earlier the worker said"))
        assertTrue(req.user.contains("There was a huge fire in SH-2."))
        assertTrue(req.user.indexOf("Earlier") < req.user.indexOf("Report so far"))
        assertTrue(req.user.trimEnd().endsWith("Worker: \"I told you where it was.\""))
    }

    @Test fun noEarlierLineWhenThisIsTheFirstTurn() {
        assertFalse(PromptBuilder.default().build("Smoke.", report()).user.contains("Earlier"))
    }

    @Test fun reportSoFarUsesTheOutputKeyNamesAndTheSpokenPhrase() {
        // Kit: the model copied the old key "date_time" and the internal date string back into its reply.
        val user = PromptBuilder.default().build("Anything else.", report()).user
        assertTrue(user.contains("\"date_time_text\":\"today morning\""), user)
        assertFalse(user.contains("\"date_time\""))
        assertFalse(user.contains("2026-10-11"))
        assertTrue(user.contains("\"incident_type\":\"fire_smoke\""))
    }
}

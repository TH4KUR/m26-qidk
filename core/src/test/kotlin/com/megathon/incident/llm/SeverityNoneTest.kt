package com.megathon.incident.llm

import com.megathon.incident.domain.ExtractionPayload
import com.megathon.incident.domain.Field
import com.megathon.incident.domain.TemporalResolver
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * An explicit "none" is a stated severity, not a negation. The cue words of severity none ("none", "no harm", "no damage",
 * "nothing serious") contain negation words themselves; only a negation in front of the cue changes its meaning.
 * Found by B's synthetic update sequences: 109 of 800 rows state severity none and 46 sequence states went wrong.
 */
class SeverityNoneTest {
    private val validator = PayloadValidator(TamDetector(TamCueLists.loadDefault()), TemporalResolver(ZoneId.of("Asia/Kolkata")))
    private val at = Instant.parse("2026-10-11T05:30:00Z")

    private fun severity(said: String, value: String, quote: String) =
        validator.validate(ExtractionPayload(severity = value, evidence = mapOf("severity" to quote)), said, at)

    private fun ValidationResult.severity() = proposals.singleOrNull { it.field == Field.SEVERITY }

    @Test fun theWordNoneIsAStatedSeverityNone() {
        val r = severity("A pipe leaked in Room 4. I rate the severity none.", "none", "I rate the severity none")
        assertEquals(ValueStatus.REPORTED, r.severity()!!.value.status)
        assertEquals("none", r.severity()!!.value.text)
    }

    @Test fun noHarmAndNoDamageAreStatedSeverityNone() {
        for (q in listOf("no harm", "no damage", "nothing serious")) {
            val r = severity("The shelf tipped over in the store, $q was done.", "none", q)
            assertEquals(ValueStatus.REPORTED, r.severity()?.value?.status, q)
        }
    }

    @Test fun notSeriousIsMinorNotNegated() {
        val r = severity("The cut is small and it is not serious.", "minor", "not serious")
        assertEquals(ValueStatus.REPORTED, r.severity()!!.value.status)
    }

    @Test fun aNegationInFrontOfTheCueStillCancelsIt() {
        val none = severity("It is not none, somebody was hurt.", "none", "none")
        assertNull(none.severity())
        val severe = severity("The leak is not severe.", "severe", "severe")
        assertNull(severe.severity())
    }

    @Test fun nobodyHurtDoesNotTurnSeverityModerateIntoNone() {
        val r = severity("Nobody was hurt and it is moderate.", "moderate", "it is moderate")
        assertEquals("moderate", r.severity()!!.value.text)
        assertEquals(ValueStatus.REPORTED, r.severity()!!.value.status)
    }
}

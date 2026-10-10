package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.Suggestion
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C's validator stores a near-miss ("last weak" heard, "last week" meant) as the meant value, status
 * SUGGESTED, with the heard words as evidence. The engine turns that into a confirm chip, so the user
 * sees what was actually heard before the value counts as filled.
 */
class NearMissChipsTest {

    private val engine = RuleSuggestionEngine(
        SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
        Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
    )

    private fun with(field: Field, value: FieldValue): ReportState {
        val empty = ReportState.empty("r1", 0)
        return empty.copy(report = empty.report.with(field, value))
    }

    @Test
    fun aNearMissGetsAConfirmChipShowingWhatWasHeard() {
        val s = with(Field.DATE_TIME, FieldValue("last week", ValueStatus.SUGGESTED, "weak"))
        assertEquals(
            listOf(Suggestion(Field.DATE_TIME, "last week", "last week", "Heard \"weak\", close to \"last week\"; confirm the time.", Source.RULE)),
            engine.suggest(s, "It happened last weak."),
        )
    }

    @Test
    fun acceptingItConfirmsTheMeantValue() {
        val s = with(Field.DATE_TIME, FieldValue("last week", ValueStatus.SUGGESTED, "weak"))
        val after = engine.accept(s, engine.suggest(s, "It happened last weak.").single())
        assertEquals(ValueStatus.USER_CONFIRMED, after.report.value(Field.DATE_TIME).status)
        assertEquals("last week", after.report.value(Field.DATE_TIME).text)
    }

    @Test
    fun aHedgedValueWhoseEvidenceHoldsTheValueIsNotANearMiss() {
        val s = with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED, "maybe minor"))
        assertTrue(engine.suggest(s, "It was maybe minor.").none { it.source == Source.RULE })
    }

    @Test
    fun aReportedValueIsNeverANearMiss() {
        val s = with(Field.DATE_TIME, FieldValue("last week", ValueStatus.REPORTED, "weak"))
        assertTrue(engine.suggest(s, "It happened last weak.").none { it.source == Source.RULE })
    }
}

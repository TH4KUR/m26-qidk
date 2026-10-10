package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.JournalKind
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.ValueStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviour every [SuggestionEngine] must have (handoff 7a). The fake runs it now; the real engine
 * runs the same tests later, so the two cannot drift.
 */
abstract class SuggestionEngineContract {

    protected val clock: Clock = Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC)

    protected abstract fun engine(): SuggestionEngine

    protected companion object {
        const val DECK = "There was a water leak near seminar hall to around 10 AM. Maintenance has already isolated the area."
        val HEARD = FieldValue("seminar hall to", ValueStatus.REPORTED, "near seminar hall to")

        fun stateWithLocation(value: FieldValue): ReportState {
            val empty = ReportState.empty("r1", 0)
            return empty.copy(report = empty.report.with(Field.LOCATION, value))
        }

        val DECK_STATE = stateWithLocation(HEARD)
    }

    private fun deckChip() = engine().suggest(DECK_STATE, DECK).single { it.field == Field.LOCATION }

    @Test
    fun deckExampleOffersSeminarHall2ForWhatWasHeard() {
        val chip = deckChip()
        assertEquals("seminar hall to", chip.original)
        assertEquals("Seminar Hall 2", chip.proposed)
        assertTrue(chip.reason.isNotBlank())
    }

    @Test
    fun acceptConfirmsTheProposalAndKeepsWhatWasHeardInTheJournal() {
        val chip = deckChip()
        val after = engine().accept(DECK_STATE, chip)

        assertEquals(FieldValue("Seminar Hall 2", ValueStatus.USER_CONFIRMED, "near seminar hall to"), after.report.value(Field.LOCATION))
        val entry = after.journal.single()
        assertEquals(JournalKind.SUGGESTION_ACCEPTED, entry.kind)
        assertEquals(Field.LOCATION, entry.field)
        assertEquals(HEARD, entry.before)
        assertEquals(after.report.value(Field.LOCATION), entry.after)
        assertEquals(chip, entry.suggestion)
        assertEquals(Instant.parse("2026-10-11T05:00:00Z").toEpochMilli(), entry.atEpochMs)
    }

    @Test
    fun rejectLeavesTheFieldAsHeardAndJournalsTheDecision() {
        val chip = deckChip()
        val after = engine().reject(DECK_STATE, chip)

        assertEquals(HEARD, after.report.value(Field.LOCATION))
        val entry = after.journal.single()
        assertEquals(JournalKind.SUGGESTION_REJECTED, entry.kind)
        assertEquals(HEARD, entry.before)
        assertEquals(HEARD, entry.after)
        assertEquals(chip, entry.suggestion)
    }

    @Test
    fun aRejectedChipIsNotOfferedAgain() {
        val rejected = engine().reject(DECK_STATE, deckChip())
        assertTrue(engine().suggest(rejected, DECK).none { it.field == Field.LOCATION })
    }

    @Test
    fun decidingTheSameChipTwiceRecordsItOnce() {
        val chip = deckChip()
        val once = engine().accept(DECK_STATE, chip)
        assertEquals(once, engine().accept(once, chip))
        assertEquals(once, engine().reject(once, chip))
    }

    @Test
    fun aStaleChipNeverOverwritesALaterValue() {
        val chip = deckChip()
        val edited = stateWithLocation(FieldValue("Library", ValueStatus.USER_EDITED))
        assertEquals(edited, engine().accept(edited, chip))
    }

    @Test
    fun aPlaceNotOnTheSiteYieldsNoChip() {
        val state = stateWithLocation(FieldValue("the cafeteria", ValueStatus.REPORTED, "near the cafeteria"))
        assertTrue(engine().suggest(state, "Water leak near the cafeteria.").none { it.field == Field.LOCATION })
    }

    @Test
    fun sameInputGivesSameChips() {
        assertEquals(engine().suggest(DECK_STATE, DECK), engine().suggest(DECK_STATE, DECK))
    }

    @Test
    fun oddInputGivesNoChipsAndNeverThrows() {
        assertEquals(emptyList(), engine().suggest(ReportState.empty("r1", 0), ""))
        assertEquals(emptyList(), engine().suggest(stateWithLocation(FieldValue("   ", ValueStatus.REPORTED)), "   "))
    }
}

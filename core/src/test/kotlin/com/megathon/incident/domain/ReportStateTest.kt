package com.megathon.incident.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportStateTest {
    private val chip = Suggestion(Field.LOCATION, original = "conference room to", proposed = "Conference Room 2",
        reason = "sounds like a known room", source = Source.GAZETTEER)

    private fun entry(kind: JournalKind, s: Suggestion? = chip) = JournalEntry(
        atEpochMs = 1_000, field = Field.LOCATION, kind = kind,
        before = FieldValue("conference room to", ValueStatus.REPORTED),
        after = FieldValue("Conference Room 2", ValueStatus.USER_CONFIRMED),
        suggestion = s,
    )

    @Test fun emptyStateHasNoJournal() {
        val s = ReportState.empty("r1", 0)
        assertTrue(s.journal.isEmpty())
        assertTrue(Field.entries.none { s.report.isFilled(it) })
    }

    @Test fun recordingAnEntryKeepsTheOriginalInTheJournal() {
        val s = ReportState.empty("r1", 0).record(entry(JournalKind.SUGGESTION_ACCEPTED))
        assertEquals("conference room to", s.journal.single().before.text)
        assertEquals("Conference Room 2", s.journal.single().after.text)
    }

    @Test fun recordIsAppendOnly() {
        val a = ReportState.empty("r1", 0).record(entry(JournalKind.HEARD, null))
        val b = a.record(entry(JournalKind.SUGGESTION_ACCEPTED))
        assertEquals(1, a.journal.size)
        assertEquals(listOf(JournalKind.HEARD, JournalKind.SUGGESTION_ACCEPTED), b.journal.map { it.kind })
    }

    @Test fun anAcceptedOrRejectedChipCountsAsDecided() {
        val base = ReportState.empty("r1", 0)
        assertFalse(base.hasDecided(chip))
        assertTrue(base.record(entry(JournalKind.SUGGESTION_ACCEPTED)).hasDecided(chip))
        assertTrue(base.record(entry(JournalKind.SUGGESTION_REJECTED)).hasDecided(chip))
    }

    @Test fun otherJournalKindsDoNotDecideAChip() {
        assertFalse(ReportState.empty("r1", 0).record(entry(JournalKind.CORRECTED)).hasDecided(chip))
    }

    @Test fun aDifferentProposalForTheSameFieldIsNotDecided() {
        val other = chip.copy(proposed = "Conference Room 3")
        assertFalse(ReportState.empty("r1", 0).record(entry(JournalKind.SUGGESTION_REJECTED)).hasDecided(other))
    }

    @Test fun stateRoundTripsThroughJsonIncludingHindi() {
        val s = ReportState.empty("r1", 5).record(
            JournalEntry(7, Field.LOCATION, JournalKind.USER_EDITED,
                FieldValue("रूम 2", ValueStatus.REPORTED), FieldValue("रूम 3", ValueStatus.USER_EDITED)),
        )
        assertEquals(s, Json.decodeFromString(ReportState.serializer(), Json.encodeToString(ReportState.serializer(), s)))
    }

    @Test fun llmIsANamedSourceSoChipsCanSayWhereAHintCameFrom() {
        assertTrue(Source.LLM in Source.entries)
    }
}

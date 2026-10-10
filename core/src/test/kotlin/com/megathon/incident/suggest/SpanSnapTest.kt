package com.megathon.incident.suggest

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.ReportState
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamHedgeDetector
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A site name inside a longer heard phrase (lead A/016, B/011). Only the misheard part is replaced and the
 * rest of the phrase is kept, so a more specific place never loses detail. Against the fictional site.
 */
class SpanSnapTest {
    private val snapper = GazetteerSnapper(SiteDirectory.loadDefault(), Homophones.default)

    private fun proposed(heard: String) = snapper.snap(heard, EntryKind.PLACE)?.proposed

    // ---- the misheard part is replaced, the rest kept -----------------------------------------
    @Test fun aNameInsideAPhraseIsReplacedInPlace() =
        assertEquals("second floor of New Lotus Hostel", proposed("second floor of new lotus"))

    @Test fun aSoundAlikeInsideAPhrase() =
        assertEquals("water near Conference Room 3", proposed("water near conference room tree"))

    @Test fun meaningfulPrepositionsAreKept() = assertEquals("outside North 203", proposed("outside north two oh three"))

    @Test fun articlesBeforeTheNameAreDropped() {
        assertEquals("Seminar Hall 2", proposed("a sh2"))
        assertEquals("near Conference Room 2", proposed("near the conference room to"))
    }

    @Test fun aWholeNameStillProposesJustTheName() = assertEquals("Conference Room 2", proposed("conference room to"))

    // ---- nothing to fix ------------------------------------------------------------------------
    @Test fun aRightNameInsideAPhraseNeedsNoChip() {
        assertNull(proposed("near Lab C4"))
        assertNull(proposed("second floor of North 105"))
        assertNull(proposed("corridor near room 20"))      // and never "Room 2"
    }

    @Test fun aSingleGenericWordInsideAPhraseIsNotAPlace() = assertNull(proposed("north side of the building"))

    @Test fun unknownCodesStayUnknown() {
        assertNull(proposed("a B2"))
        assertNull(proposed("Block B-2"))
        assertNull(proposed("Block C near the lift"))
    }

    // ---- two places ----------------------------------------------------------------------------
    @Test fun twoPlacesOfferThePlaceAfterTo() {
        val snap = snapper.snap("from north block to research block where the cable is sparking", EntryKind.PLACE)!!
        assertEquals("research-block", snap.entry.id)
        assertEquals("Research Block", snap.proposed)
        assertTrue("North Block" in snap.reason && "Research Block" in snap.reason, snap.reason)
    }

    @Test fun twoPlacesWithoutADirectionGiveNothing() = assertNull(proposed("between north block and research block"))

    // ---- through the engine --------------------------------------------------------------------
    @Test fun theEngineChipCarriesTheWholeProposedPhrase() {
        val engine = RuleSuggestionEngine(
            SiteDirectory.loadDefault(), Homophones.default, TamHedgeDetector(TamCueLists.loadDefault()),
            Clock.fixed(Instant.parse("2026-10-11T05:00:00Z"), ZoneOffset.UTC),
        )
        val empty = ReportState.empty("r1", 0)
        val state = empty.copy(report = empty.report.with(Field.LOCATION, FieldValue("second floor of new lotus", ValueStatus.REPORTED, "second floor of new lotus")))
        val chip = engine.suggest(state, "Smoke on the second floor of new lotus.").single { it.source == Source.GAZETTEER }
        assertEquals("second floor of new lotus", chip.original)
        assertEquals("second floor of New Lotus Hostel", chip.proposed)
        val accepted = engine.accept(state, chip)
        assertEquals("second floor of New Lotus Hostel", accepted.report.value(Field.LOCATION).text)
    }
}

package com.megathon.incident.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Against the shipped fictional site (Conference Rooms 1-3, Room 2, Room 20, North 101-203, Labs A2/A4/C2/C4, ...). */
class GazetteerSnapperTest {
    private val snapper = GazetteerSnapper(SiteDirectory.loadDefault(), Homophones.default)

    private fun place(heard: String) = snapper.snap(heard, EntryKind.PLACE)?.entry?.name
    private fun asset(heard: String) = snapper.snap(heard, EntryKind.ASSET)?.entry?.name

    // ---- sound-alike numbers and letters -------------------------------------------------------
    @Test fun roomToBecomesRoom2() = assertEquals("Conference Room 2", place("conference room to"))
    @Test fun forBecomesFour() = assertEquals("Lab A4", place("lab a for"))
    @Test fun seeBecomesTheLetterC() = assertEquals("Lab C4", place("lab see four"))
    @Test fun spokenDigitsJoinUp() = assertEquals("North 105", place("north one oh five"))
    @Test fun leadingPrepositionsAreIgnored() = assertEquals("Conference Room 2", place("near the conference room to"))

    // ---- aliases and spelling ------------------------------------------------------------------
    @Test fun anAliasSnapsToTheCanonicalName() = assertEquals("Conference Room 2", place("CR2"))
    @Test fun aMisheardAliasOfAnAsset() = assertEquals("Chiller 1", asset("the chilla"))
    @Test fun aMisspelledNameSnapsByFuzzyMatch() = assertEquals("Maple Hostel", place("maple hostle"))

    // ---- things that must NOT snap -------------------------------------------------------------
    @Test fun room20NeverBecomesRoom2() {
        assertNull(place("room 20"))                         // already the canonical name: no chip
        assertEquals("Room 20", place("room twenty"))        // its own alias, never Room 2
    }

    @Test fun aRoomNumberNotOnTheSiteDoesNotSnapToANeighbour() {
        assertNull(place("conference room for"))   // there is no Conference Room 4
        assertNull(place("conference room 5"))
    }

    /** Only one chiller and one gate have a close name, so only the number rule can stop these. */
    @Test fun aNumberThatDiffersBlocksAnOtherwisePerfectMatch() {
        assertNull(asset("chiller 2"))   // the site has Chiller 1, not 2
        assertNull(place("gate 2"))      // the site has Gate 1 (Main Gate), not 2
    }

    @Test fun aNameWithoutItsNumberIsAmbiguous() = assertNull(place("conference room"))

    @Test fun aMisspellingEquallyCloseToTwoNamesGivesNothing() {
        val twoHalls = SiteDirectory.fromJson(
            """{"places":[{"id":"hall","name":"Cedar Hall","aliases":[]},{"id":"hill","name":"Cedar Hill","aliases":[]}]}""",
        )
        val s = GazetteerSnapper(twoHalls, Homophones.default)
        assertNull(s.snap("cedar hull", EntryKind.PLACE))           // as close to Hall as to Hill
        assertEquals("hall", s.snap("cedar halll", EntryKind.PLACE)?.entry?.id)   // clearly Hall
    }
    @Test fun anUnknownPlaceGivesNothing() = assertNull(place("the cafeteria"))
    @Test fun theCanonicalNameNeedsNoChip() = assertNull(place("Conference Room 2"))
    @Test fun aPlaceIsNotSnappedToAnAsset() = assertNull(place("chilla"))
    @Test fun blankGivesNothing() = assertNull(place("   "))

    // ---- reasons the user sees -----------------------------------------------------------------
    @Test fun reasonQuotesTheMisheardWords() =
        assertEquals("Heard \"room to\"; the site has Conference Room 2.", snapper.snap("conference room to", EntryKind.PLACE)!!.reason)

    @Test fun fuzzyReasonSaysItIsTheClosestName() {
        val reason = snapper.snap("maple hostle", EntryKind.PLACE)!!.reason
        assertTrue("closest site name is Maple Hostel" in reason, reason)
    }
}

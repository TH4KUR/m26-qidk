package com.megathon.incident.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomophonesTest {

    private fun table(vararg groups: List<String>) = HomophoneTable(groups.toList())

    // ---- the shipped data ----------------------------------------------------------------------

    @Test
    fun weakSoundsLikeWeek() = assertEquals(setOf("week"), Homophones.variants("weak"))

    @Test
    fun toHasEveryNumberVariant() = assertEquals(setOf("two", "too", "2"), Homophones.variants("to"))

    @Test
    fun aMisheardPhraseMatchesWordByWord() = assertTrue(Homophones.sameSound("last weak", "last week"))

    @Test
    fun everyShippedVariantRelationIsSymmetricAndExcludesItself() {
        val shipped = Homophones.default
        for (token in shipped.tokens()) {
            val vs = shipped.variants(token)
            assertFalse(token in vs, "variants($token) contains itself")
            assertTrue(vs.isNotEmpty(), "$token has no variants")
            for (v in vs) assertTrue(token in shipped.variants(v), "$token -> $v is not symmetric")
        }
    }

    // ---- the rules, on small hand-made tables --------------------------------------------------

    @Test
    fun inputIsCaseAndUnicodeNormalised() {
        val t = table(listOf("week", "weak"))
        assertEquals(setOf("week"), t.variants("WEAK"))
        // "café" typed as e + combining acute (NFD) must equal the precomposed form (NFC).
        val c = table(listOf("café", "cafe"))
        assertEquals(setOf("cafe"), c.variants("café"))
    }

    @Test
    fun unknownTokenHasNoVariants() = assertEquals(emptySet(), table(listOf("week", "weak")).variants("water"))

    @Test
    fun sameSoundIsReflexiveEvenForUnknownWords() {
        val t = table(listOf("week", "weak"))
        assertTrue(t.sameSound("water leak", "water leak"))
    }

    @Test
    fun sameSoundIsSymmetric() {
        val t = table(listOf("week", "weak"), listOf("two", "to"))
        assertTrue(t.sameSound("week two", "weak to"))
        assertTrue(t.sameSound("weak to", "week two"))
    }

    @Test
    fun sameSoundIgnoresCaseAndPunctuation() =
        assertTrue(table(listOf("week", "weak")).sameSound("Last WEAK.", "last week"))

    @Test
    fun differentWordCountsNeverSoundTheSame() =
        assertFalse(table(listOf("week", "weak")).sameSound("week", "last week"))

    @Test
    fun unrelatedWordsDoNotSoundTheSame() =
        assertFalse(table(listOf("week", "weak")).sameSound("last month", "last week"))

    @Test
    fun oneWrongWordSpoilsTheMatch() =
        assertFalse(table(listOf("week", "weak")).sameSound("first weak", "last week"))

    // ---- data errors are caught at load time ---------------------------------------------------

    @Test
    fun aTokenInTwoGroupsIsRejected() {
        val e = assertFailsWith<IllegalArgumentException> { table(listOf("to", "two"), listOf("to", "too")) }
        assertTrue("to" in e.message!!, e.message)
    }

    @Test
    fun aGroupNeedsTwoDistinctTokens() {
        assertFailsWith<IllegalArgumentException> { table(listOf("week")) }
        assertFailsWith<IllegalArgumentException> { table(listOf("Week", "week")) }
    }

    @Test
    fun aMultiWordEntryIsRejected() {
        assertFailsWith<IllegalArgumentException> { table(listOf("a lot", "alot")) }
    }

    @Test
    fun groupsParseFromJson() {
        val t = HomophoneTable.fromJson("""{"groups":[["week","weak"],["two","to","too","2"]]}""")
        assertEquals(setOf("to", "too", "2"), t.variants("two"))
    }
}

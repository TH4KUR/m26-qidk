package com.megathon.incident.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteDirectoryTest {

    private fun site(places: String = "", assets: String = "", people: String = "") =
        """{"schema":1,"verified":false,"places":[$places],"assets":[$assets],"people":[$people]}"""

    private val room2 = """{"id":"cr-2","name":"Conference Room 2","kind":"room","aliases":["CR2"]}"""

    @Test
    fun shippedSiteResolvesAnAliasToItsPlace() {
        val hit = SiteDirectory.loadDefault().lookup("KRB")!!
        assertEquals("krb", hit.id)
        assertEquals("Kohli Research Block", hit.name)
        assertEquals(EntryKind.PLACE, hit.kind)
    }

    @Test
    fun shippedSiteResolvesAnAssetAlias() {
        val hit = SiteDirectory.loadDefault().lookup("chilla")!!
        assertEquals("CHL-KRB-01", hit.id)
        assertEquals(EntryKind.ASSET, hit.kind)
    }

    @Test
    fun lookupIgnoresCaseSpacingAndPunctuation() {
        val dir = SiteDirectory.fromJson(site(places = """{"id":"h105","name":"Himalaya 105","kind":"room","aliases":["H-105"]}"""))
        assertEquals("h105", dir.lookup("h 105")?.id)
        assertEquals("h105", dir.lookup("HIMALAYA-105.")?.id)
    }

    @Test
    fun lookupMatchesTheCanonicalNameNotOnlyAliases() {
        val dir = SiteDirectory.fromJson(site(places = room2))
        assertEquals("cr-2", dir.lookup("conference room 2")?.id)
    }

    @Test
    fun unknownTextFindsNothing() {
        val dir = SiteDirectory.fromJson(site(places = room2))
        assertNull(dir.lookup("cafeteria"))
        assertNull(dir.lookup("   "))
    }

    @Test
    fun personEntriesAreTaggedAsPeople() {
        val dir = SiteDirectory.fromJson(site(people = """{"id":"p1","name":"Ravi Kumar","role":"electrician","aliases":["Ravi"]}"""))
        assertEquals(EntryKind.PERSON, dir.lookup("ravi")?.kind)
    }

    @Test
    fun aliasSharedByTwoEntriesIsRejected() {
        val clash = """{"id":"cr-3","name":"Conference Room 3","kind":"room","aliases":["CR2"]}"""
        val e = assertFailsWith<IllegalArgumentException> { SiteDirectory.fromJson(site(places = "$room2,$clash")) }
        assertTrue("cr2" in e.message!!.lowercase(), e.message)
    }

    @Test
    fun aliasesThatOnlyDifferInPunctuationAlsoClash() {
        val a = """{"id":"a","name":"SH-1","kind":"hall","aliases":[]}"""
        val b = """{"id":"b","name":"SH 1","kind":"hall","aliases":[]}"""
        assertFailsWith<IllegalArgumentException> { SiteDirectory.fromJson(site(places = "$a,$b")) }
    }

    @Test
    fun assetPointingAtAnUnknownPlaceIsRejected() {
        val chiller = """{"id":"CHL-1","name":"Chiller 1","kind":"chiller","place":"nowhere","aliases":[]}"""
        val e = assertFailsWith<IllegalArgumentException> { SiteDirectory.fromJson(site(places = room2, assets = chiller)) }
        assertTrue("nowhere" in e.message!!, e.message)
    }

    @Test
    fun roomPointingAtAnUnknownBuildingIsRejected() {
        val room = """{"id":"r1","name":"Room 1","kind":"room","building":"ghost","aliases":[]}"""
        assertFailsWith<IllegalArgumentException> { SiteDirectory.fromJson(site(places = room)) }
    }
}

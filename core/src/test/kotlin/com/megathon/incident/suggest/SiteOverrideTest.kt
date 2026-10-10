package com.megathon.incident.suggest

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app passes `filesDir/site.json`: a site list pushed to the kit over USB for the live demo replaces
 * the bundled fictional one. A missing or broken file must never stop the app; it falls back and says why.
 */
class SiteOverrideTest {

    private fun tempFile(text: String): File =
        Files.createTempFile("site", ".json").toFile().apply { writeText(text); deleteOnExit() }

    private val pushed = """{"places":[{"id":"store","name":"Central Store","aliases":["store room"]}]}"""

    @Test
    fun aPushedFileReplacesTheBundledSite() {
        val load = SiteDirectory.loadPreferring(tempFile(pushed))
        assertTrue(load.fromFile)
        assertNull(load.problem)
        assertEquals("store", load.site.lookup("store room")?.id)
        assertNull(load.site.lookup("Conference Room 2"))
    }

    @Test
    fun noFileUsesTheBundledSite() {
        for (file in listOf(null, File("does-not-exist-site.json"))) {
            val load = SiteDirectory.loadPreferring(file)
            assertFalse(load.fromFile)
            assertNull(load.problem)
            assertEquals("conf-2", load.site.lookup("Conference Room 2")?.id)
        }
    }

    @Test
    fun aBrokenFileFallsBackAndSaysWhy() {
        val load = SiteDirectory.loadPreferring(tempFile("{ not json"))
        assertFalse(load.fromFile)
        assertNotNull(load.problem)
        assertEquals("conf-2", load.site.lookup("Conference Room 2")?.id)
    }

    @Test
    fun aFileWithClashingAliasesFallsBackAndNamesTheAlias() {
        val clash = """{"places":[{"id":"a","name":"Store A","aliases":["store"]},{"id":"b","name":"Store B","aliases":["store"]}]}"""
        val load = SiteDirectory.loadPreferring(tempFile(clash))
        assertFalse(load.fromFile)
        assertTrue("store" in load.problem!!, load.problem)
    }
}

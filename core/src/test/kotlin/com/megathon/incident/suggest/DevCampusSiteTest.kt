package com.megathon.incident.suggest

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The real campus list lives outside the mirrored folders (`dev/site/campus.json`), so it never reaches
 * the submission. When it is present (dev repo), it must still load: no shared aliases, no broken references.
 */
class DevCampusSiteTest {
    @Test
    fun devCampusFileLoadsWhenPresent() {
        val file = File("../dev/site/campus.json")
        assumeTrue("dev-only campus file not present", file.isFile)
        assertTrue(SiteDirectory.fromJson(file.readText()).entries.size > 50)
    }
}

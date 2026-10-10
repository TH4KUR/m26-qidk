package com.megathon.incident.signals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audits the mapper's rules against the real YamNet output labels. `fixtures/yamnet_class_map.csv` is the 521-class
 * AudioSet list that ships with YamNet (TensorFlow models repository, Apache-2.0): a name in a rule that is not in it
 * would silently never fire.
 */
class YamNetClassMapTest {
    private val classMap: List<String> by lazy {
        val lines = requireNotNull(javaClass.getResourceAsStream("/fixtures/yamnet_class_map.csv")) { "fixture missing" }
            .bufferedReader().readLines().drop(1) // header: index,mid,display_name
        lines.filter { it.isNotBlank() }.map { line ->
            val name = line.substringAfter(",").substringAfter(",") // drop index and mid
            name.removeSurrounding("\"")
        }
    }

    @Test
    fun theFixtureIsTheFullFiveHundredTwentyOneClassList() {
        assertEquals(521, classMap.size)
        assertTrue("Fire alarm" in classMap && "Speech" in classMap, "spot check")
    }

    @Test
    fun everyAudioSetNameInTheRulesExistsInTheRealClassMap() {
        val real = classMap.map { it.trim().lowercase() }.toSet()
        val missing = YamNetSignalMapper.audioSetNames.filterNot { it in real }
        assertTrue(missing.isEmpty(), "names that can never match a real YamNet label: $missing")
    }

    @Test
    fun everyHazardRuleHasAtLeastOneName() {
        assertTrue(YamNetSignalMapper.audioSetNames.size >= 15, "names: ${YamNetSignalMapper.audioSetNames}")
    }
}

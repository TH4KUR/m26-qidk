package com.megathon.incident.priority

import com.megathon.incident.domain.Severity
import com.megathon.incident.domain.Source
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.tam.TamCueLists
import com.megathon.incident.tam.TamDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeveritySuggesterTest {
    private val extractor = UrgencyFeatureExtractor(HazardLexicon.loadDefault(), TamDetector(TamCueLists.loadDefault()))
    private val suggester = SeveritySuggester(extractor)

    private fun suggest(text: String) = suggester.suggest(text)

    @Test fun fireIsSuggestedAsCritical() = assertEquals(Severity.CRITICAL, suggest("there is a fire in the kitchen")!!.severity)

    @Test fun gasLeakIsSuggestedAsSevere() = assertEquals(Severity.SEVERE, suggest("there is a gas leak in the lab")!!.severity)

    @Test fun aSpillIsSuggestedAsModerate() = assertEquals(Severity.MODERATE, suggest("there is a spill in the corridor")!!.severity)

    @Test fun aSmallLeakIsSuggestedAsMinor() = assertEquals(Severity.MINOR, suggest("there is a water leak near the stairs")!!.severity)

    @Test fun aPotholeIsSuggestedAsMinor() = assertEquals(Severity.MINOR, suggest("there is a pothole near the gate")!!.severity)

    @Test fun nothingRecognisedGivesNoSuggestion() = assertNull(suggest("the notice board needs to be moved"))

    @Test fun aDeniedHazardGivesNoSuggestionForIt() = assertNull(suggest("there is no fire"))

    @Test fun theSuggestionIsNeverAFill() {
        val s = suggest("there is a fire in the kitchen")!!
        assertEquals(ValueStatus.SUGGESTED, s.toFieldValue().status)
        assertEquals("critical", s.toFieldValue().text)
    }

    @Test fun theSuggestionNamesItsSourceAndReason() {
        val s = suggest("there is a fire in the kitchen")!!
        assertEquals(Source.RULE, s.source)
        assertTrue(s.reason.contains("fire"), s.reason)
    }

    @Test fun statedSeverityWordsAreLeftToTheReporterAndNotOverridden() {
        // The suggester only looks at hazards; "minor" spoken by the reporter is handled as a stated value.
        val s = suggest("a minor fire in the pantry")!!
        assertNotNull(s)
        assertEquals(Severity.CRITICAL, s.severity)
    }
}

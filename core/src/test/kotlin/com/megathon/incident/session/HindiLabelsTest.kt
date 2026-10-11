package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Severity
import com.megathon.incident.tts.HindiPhrases
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HindiLabelsTest {
    private val today = LocalDate.parse("2026-10-11")

    private fun isDevanagari(s: String) = s.isNotBlank() && s.any { it.code in 0x0900..0x097F }

    @Test fun everyFieldHasAHindiLabelDifferentFromTheEnglishOne() {
        for (f in Field.entries) {
            val hi = HindiLabels.field(f)
            assertTrue(isDevanagari(hi), "$f -> $hi")
            assertNotEquals(Labels.fieldLabel(f), hi, "$f")
        }
    }

    @Test fun fieldLabelsAreDistinct() =
        assertEquals(Field.entries.size, Field.entries.map { HindiLabels.field(it) }.toSet().size)

    @Test fun everyIncidentTypeHasHindiDifferentFromTheEnglish() {
        for (t in IncidentType.entries) {
            val hi = HindiLabels.type(t.wire)
            assertTrue(isDevanagari(hi), "$t -> $hi")
            assertNotEquals(Labels.type(t.wire), hi, "$t")
        }
    }

    @Test fun otherHasItsOwnHindi() = assertEquals("कुछ और", HindiLabels.type("other"))

    @Test fun typeIsTheWordTheVoiceSays() {
        for (t in IncidentType.entries) {
            assertEquals(HindiPhrases.typeName(Labels.type(t.wire)), HindiLabels.type(t.wire), "$t")
        }
    }

    @Test fun anUnknownTypeIsShownAsGiven() {
        assertEquals("forklift crash", HindiLabels.type("forklift crash"))
        assertEquals("", HindiLabels.type(null))
    }

    @Test fun everySeverityHasHindiDifferentFromTheEnglish() {
        for (s in Severity.entries) {
            val hi = HindiLabels.severity(s.wire)
            assertTrue(isDevanagari(hi), "$s -> $hi")
            assertNotEquals(Labels.severity(s.wire), hi, "$s")
        }
    }

    @Test fun severityMatchesTheSpokenWord() {
        assertEquals("कोई नुकसान नहीं", HindiLabels.severity("none"))
        for (s in listOf(Severity.MINOR, Severity.MODERATE, Severity.SEVERE, Severity.CRITICAL)) {
            assertEquals(HindiPhrases.severityName(Labels.severity(s.wire)), HindiLabels.severity(s.wire), "$s")
        }
    }

    @Test fun anUnknownSeverityIsShownAsGiven() {
        assertEquals("so-so", HindiLabels.severity("so-so"))
        assertEquals("", HindiLabels.severity(null))
    }

    @Test fun everyStatusHasHindiAndEmptyHasNone() {
        val english = mapOf(
            UiStatus.HEARD to "Heard", UiStatus.CONFIRMED to "Confirmed", UiStatus.SUGGESTED to "Suggested",
            UiStatus.NEEDED to "Needed", UiStatus.NONE to "None", UiStatus.UNKNOWN to "Not sure",
        )
        for (s in UiStatus.entries) {
            if (s == UiStatus.EMPTY) { assertNull(HindiLabels.status(s)); continue }
            val hi = assertNotNull(HindiLabels.status(s), "$s")
            assertTrue(isDevanagari(hi), "$s -> $hi")
            assertNotEquals(english.getValue(s), hi, "$s")
        }
    }

    @Test fun statusWordsAreDistinct() {
        val words = UiStatus.entries.filter { it != UiStatus.EMPTY }.map { HindiLabels.status(it) }
        assertEquals(words.size, words.toSet().size)
    }

    @Test fun displayIsNullForBlank() {
        assertNull(HindiLabels.display(Field.LOCATION, null, today))
        assertNull(HindiLabels.display(Field.LOCATION, "  ", today))
    }

    @Test fun displayShowsTypeSeverityAndDateInHindi() {
        assertEquals("आग या धुआँ", HindiLabels.display(Field.INCIDENT_TYPE, "fire_smoke", today))
        assertEquals("मध्यम", HindiLabels.display(Field.SEVERITY, "moderate", today))
        assertEquals(
            "आज, रविवार ११ अक्टूबर · लगभग सुबह १० बजे",
            HindiLabels.display(Field.DATE_TIME, "2026-10-11 ≈10:00 (around 10 AM)", today),
        )
    }

    @Test fun displayLeavesWhatTheReporterSaidAlone() {
        assertEquals("लैब 3", HindiLabels.display(Field.LOCATION, "लैब 3", today))
        assertEquals("Lab 3", HindiLabels.display(Field.LOCATION, "Lab 3", today))
        assertEquals("पाइप बंद किया", HindiLabels.display(Field.IMMEDIATE_ACTIONS, "पाइप बंद किया", today))
    }

    @Test fun theEnglishPathIsUntouched() {
        assertEquals("Fire or smoke", Labels.type("fire_smoke"))
        assertEquals("What happened", Labels.fieldLabel(Field.INCIDENT_TYPE))
        assertEquals("Moderate", Labels.severity("moderate"))
        assertEquals("Today, Sun 11 Oct · about 10:00 AM", Labels.display(Field.DATE_TIME, "2026-10-11 ≈10:00 (around 10 AM)", today))
    }
}

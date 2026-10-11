package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptScriptNoteTest {
    @Test
    fun englishOnlyTranscriptsGetNoNoteSoTheEnglishScreenIsUnchanged() {
        assertNull(TranscriptScriptNote.forTexts(emptyList()))
        assertNull(TranscriptScriptNote.forTexts(listOf("There is a fire on the second floor near gate 4.")))
        assertNull(TranscriptScriptNote.forTexts(listOf("", "  ")))
    }

    @Test
    fun aDevanagariTranscriptGetsOneShortLineSayingTheRestIsEnglish() {
        val note = assertNotNull(TranscriptScriptNote.forTexts(listOf("पहली मंज़िल पर आग लगी है")))
        assertTrue("Hindi" in note && "English" in note, note)
        assertTrue('\n' !in note && note.length < 120, note)
    }

    @Test
    fun aMixedScriptTranscriptAlsoGetsTheNote() {
        assertNotNull(TranscriptScriptNote.forTexts(listOf("gate 4 पर आग है")))
    }

    @Test
    fun oneDevanagariTurnAmongEnglishOnesIsEnough() {
        assertEquals(TranscriptScriptNote.LINE, TranscriptScriptNote.forTexts(listOf("fire on level two", "दो नंबर गेट")))
    }

    @Test
    fun theHindiLaneNoticeSaysExperimentalAndUnmeasuredAndClaimsNoAccuracy() {
        val text = HindiLaneNotice.TEXT
        assertTrue("experimental" in text.lowercase(), text)
        assertTrue("UNMEASURED" in text, text)
        assertTrue("%" !in text, text)
    }
}

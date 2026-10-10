package com.megathon.incident.speech

import com.megathon.incident.domain.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceCommandParserTest {
    private fun parse(text: String) = VoiceCommandParser.parse(text)

    @Test
    fun readItBackInItsCommonForms() {
        listOf(
            "read it back",
            "Read it back to me.",
            "please read it back",
            "read back the report",
            "Read the report back.",
            "read back",
        ).forEach { assertEquals(VoiceCommand.ReadBack, parse(it), it) }
    }

    @Test
    fun submitInItsCommonForms() {
        listOf(
            "submit",
            "Submit.",
            "submit the report",
            "submit the incident report",
            "please submit it",
            "submit it now",
            "send it",
        ).forEach { assertEquals(VoiceCommand.Submit, parse(it), it) }
    }

    @Test
    fun changeXToYKeepsTheSpokenValueAsHeard() {
        assertEquals(VoiceCommand.Change(Field.LOCATION, "Conference Room 3"), parse("Change the location to Conference Room 3."))
        assertEquals(VoiceCommand.Change(Field.SEVERITY, "severe"), parse("change severity to severe"))
        assertEquals(VoiceCommand.Change(Field.LOCATION, "Lab 4"), parse("set the location as Lab 4"))
        assertEquals(VoiceCommand.Change(Field.INCIDENT_TYPE, "injury"), parse("update the incident type to injury"))
        assertEquals(VoiceCommand.Change(Field.DATE_TIME, "yesterday evening"), parse("change the time to yesterday evening, please"))
        assertEquals(VoiceCommand.Change(Field.PERSONS_INVOLVED, "Ravi and Meena"), parse("change people involved to Ravi and Meena"))
    }

    @Test
    fun aValueThatContainsToIsNotSplitAgain() {
        assertEquals(
            VoiceCommand.Change(Field.LOCATION, "Room 2 to Room 3 corridor"),
            parse("change location to Room 2 to Room 3 corridor"),
        )
    }

    @Test
    fun narrationThatMerelyMentionsACommandWordIsNotACommand() {
        listOf(
            "I will submit the report tomorrow",
            "the submit button is broken",
            "there was a change of plans and the leak is in room 3",
            "he read the manual back at the desk",
            "what does change mean",
            "the water leak is near the server room",
        ).forEach { assertNull(parse(it), it) }
    }

    @Test
    fun anUnknownFieldIsNotAChangeCommand() {
        assertNull(parse("change the lights to green"))
    }

    @Test
    fun emptyOrBlankTextIsNotACommand() {
        assertNull(parse(""))
        assertNull(parse("   "))
    }
}

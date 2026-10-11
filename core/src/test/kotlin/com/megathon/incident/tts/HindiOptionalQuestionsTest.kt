package com.megathon.incident.tts

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.ValueStatus
import com.megathon.incident.session.OptionalAsks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * With the Hindi lane on, every question the app asks aloud is Hindi. The cross-questions added after the table was written
 * ("Did anyone see it happen?", "Which tool, machine or item was involved?" ...) were spoken in English because a sentence the table
 * does not know is deliberately left alone. This covers them by deriving the questions from the planner, so a new one cannot be forgotten.
 */
class HindiOptionalQuestionsTest {
    private fun hi(text: String) = SpokenSegment(text, "hi")
    private fun en(text: String) = SpokenSegment(text, "en")
    private fun segments(english: String) = HindiPhrases.segments(english)

    @Test fun `the fixed optional questions have a Hindi line`() {
        assertEquals(listOf(hi("क्या किसी ने इसे होते हुए देखा?")), segments("Did anyone see it happen?"))
        assertEquals(listOf(hi("किसे चोट लगी?")), segments("Who was hurt?"))
        assertEquals(listOf(hi("कौन बीमार है?")), segments("Who is unwell?"))
        assertEquals(listOf(hi("क्या आप जानते हैं कि यह कौन था?")), segments("Do you know who it was?"))
        assertEquals(listOf(hi("इसमें कौन सा औज़ार, मशीन या सामान शामिल था?")), segments("Which tool, machine or item was involved?"))
    }

    @Test fun `a role in the question is spoken in Hindi`() {
        assertEquals(listOf(hi("आपके सहकर्मी का नाम क्या है?")), segments("What is your colleague's name?"))
        assertEquals(listOf(hi("उस छात्र का नाम क्या है?")), segments("What is the student's name?"))
        assertEquals(listOf(hi("आपके मरीज़ का नाम क्या है?")), segments("What is your patient's name?"))
    }

    @Test fun `a candidate item keeps its own words, in the voice for its own script`() {
        assertEquals(listOf(hi("क्या यह"), en("printer"), hi("था?")), segments("Was it the printer?"))
        assertEquals(listOf(hi("क्या यह मशीन था?")), segments("Was it the मशीन?"))
    }

    @Test fun `every question the planner can ask is spoken in Hindi, whatever was said`() {
        val prompts = mutableSetOf<String>()
        val heard = listOf("", "My colleague fainted near the lab", "A student slipped on the wet floor", "Someone fainted outside", "The visitor was hurt")
        val asked = listOf(emptyList(), listOf(setOf(Field.PERSONS_INVOLVED)), listOf(setOf(Field.EQUIPMENT_ASSET)), listOf(setOf(Field.WITNESSES)))
        for (type in IncidentType.entries) for (said in heard) for (before in asked) for (candidate in listOf<String?>(null, "printer")) {
            val report = IncidentReport.empty("r", 0).with(Field.INCIDENT_TYPE, FieldValue(type.wire, ValueStatus.REPORTED, type.wire))
            OptionalAsks.next(report, before, said, candidate?.let { mapOf(Field.EQUIPMENT_ASSET to it) } ?: emptyMap())?.let { prompts += it.spoken }
        }
        assertTrue(prompts.size >= 8, "the matrix should reach every phrasing, got $prompts")
        for (p in prompts) {
            val segs = segments(p)
            assertTrue(segs.first().language == "hi", "starts in English: \"$p\" -> $segs")
            // Only a spoken item name may be English.
            if (!p.startsWith("Was it the ")) assertTrue(segs.all { it.language == "hi" }, "English left in: \"$p\" -> $segs")
        }
    }
}

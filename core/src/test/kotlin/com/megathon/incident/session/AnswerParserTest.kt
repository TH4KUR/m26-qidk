package com.megathon.incident.session

import com.megathon.incident.domain.Field
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A short spoken answer to an open optional question ("Meera and Ravi", "no one", "pata nahi") answers THAT field. Anything that
 * does not look like an answer goes through the normal first pass instead, so a real new sentence is never swallowed.
 */
class AnswerParserTest {
    private fun parse(field: Field, said: String) = AnswerParser.parse(field, said)
    private fun value(text: String) = AnswerParser.Answer.Value(text)

    @Test fun `names are split, cleaned and capitalised even when the recogniser wrote them in lower case`() {
        assertEquals(value("Meera"), parse(Field.WITNESSES, "Meera"))
        assertEquals(value("Meera, Ravi"), parse(Field.WITNESSES, "Meera and Ravi"))
        assertEquals(value("Meera, Ravi, Kavya"), parse(Field.WITNESSES, "meera, ravi and kavya"))
        assertEquals(value("Ananya"), parse(Field.PERSONS_INVOLVED, "it was Ananya"))
        assertEquals(value("Ananya"), parse(Field.PERSONS_INVOLVED, "her name is Ananya."))
        assertEquals(value("Rahul Sharma"), parse(Field.PERSONS_INVOLVED, "I think it was Rahul Sharma"))
    }

    @Test fun `Hindi and Hinglish names and fillers work`() {
        assertEquals(value("Meera, Ravi"), parse(Field.WITNESSES, "Meera aur Ravi"))
        assertEquals(value("मीरा, रवि"), parse(Field.WITNESSES, "मीरा और रवि"))
        assertEquals(value("Ananya"), parse(Field.PERSONS_INVOLVED, "uska naam Ananya hai"))
        assertEquals(value("Ananya"), parse(Field.PERSONS_INVOLVED, "उसका नाम Ananya है"))
    }

    @Test fun `no one and none are an explicit none`() {
        listOf("no one", "nobody", "none", "nope", "no", "nothing", "No one saw it", "koi nahi", "koi nahin tha", "कोई नहीं", "नहीं").forEach {
            assertEquals(AnswerParser.Answer.None, parse(Field.WITNESSES, it), it)
        }
        assertEquals(AnswerParser.Answer.None, parse(Field.EQUIPMENT_ASSET, "no tool"))
        assertEquals(AnswerParser.Answer.None, parse(Field.EQUIPMENT_ASSET, "none"))
    }

    @Test fun `not knowing is a skip, not a none`() {
        listOf("skip", "I don't know", "not sure", "no idea", "pata nahi", "पता नहीं", "chhodo", "pass").forEach {
            assertEquals(AnswerParser.Answer.Skip, parse(Field.PERSONS_INVOLVED, it), it)
        }
    }

    @Test fun `a tool or item loses the filler words and keeps its own`() {
        assertEquals(value("printer"), parse(Field.EQUIPMENT_ASSET, "the printer"))
        assertEquals(value("drill"), parse(Field.EQUIPMENT_ASSET, "it was a drill"))
        assertEquals(value("water heater"), parse(Field.EQUIPMENT_ASSET, "my water heater"))
        assertEquals(value("drill"), parse(Field.EQUIPMENT_ASSET, "ek drill"))
        assertEquals(value("मशीन"), parse(Field.EQUIPMENT_ASSET, "वो मशीन थी"))
    }

    @Test fun `a real sentence or something that is not a name is not an answer`() {
        assertEquals(AnswerParser.Answer.NotAnAnswer, parse(Field.WITNESSES, "Actually the leak started around ten and we called the plumber"))
        assertEquals(AnswerParser.Answer.NotAnAnswer, parse(Field.WITNESSES, "a security guard"))
        assertEquals(AnswerParser.Answer.NotAnAnswer, parse(Field.WITNESSES, "he did"))
        assertEquals(AnswerParser.Answer.NotAnAnswer, parse(Field.PERSONS_INVOLVED, "three people"))
        assertEquals(AnswerParser.Answer.NotAnAnswer, parse(Field.EQUIPMENT_ASSET, "I called maintenance and they switched off the main power"))
        assertEquals(AnswerParser.Answer.NotAnAnswer, parse(Field.INCIDENT_TYPE, "water leak"))
    }
}

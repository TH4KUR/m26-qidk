package com.megathon.incident.signals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SenseVoiceOutputParserTest {
    private fun fixture(): String =
        javaClass.getResourceAsStream("/fixtures/signals/sensevoice_cli_two_clips.txt")!!.bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun theRealCliOutputGivesTheFirstClipsTags() {
        val r = assertNotNull(SenseVoiceOutputParser.parse(fixture()))
        assertEquals("<|NEUTRAL|>", r.emotion)
        assertEquals("<|Speech|>", r.event)
        assertEquals("THE TRIVBAL CHIEF THIN CALLED FOR THE BOY AND PRESENTED HIM WITH FIFTY PIECES OF COOD", r.text)
    }

    @Test
    fun theLangTagIsParsedButIsOnlyKeptRaw() {
        assertEquals("<|yue|>", SenseVoiceOutputParser.parse(fixture())!!.lang)
    }

    @Test
    fun noiseOnlyIsNull() {
        val noise = "Creating recognizer ...\nrecognizer created in 0.572 s\n     0.0ms [WARN   ] QnnDsp <W> Initializing HtpProvider\nDone!\n"
        assertNull(SenseVoiceOutputParser.parse(noise))
    }

    @Test
    fun truncatedJsonIsNullAndDoesNotThrow() {
        assertNull(SenseVoiceOutputParser.parse("""{"lang": "<|yue|>", "emotion": "<|NEUTRAL|>", "event": "<|Speech|>", "text": "HELLO", "timestamps": [0.84, 0.9"""))
    }

    @Test
    fun aMissingKeyIsNullForThatKeyOnly() {
        val r = assertNotNull(SenseVoiceOutputParser.parse("""{"text": "water leak", "emotion": "<|SAD|>"}"""))
        assertNull(r.event)
        assertNull(r.lang)
        assertEquals("<|SAD|>", r.emotion)
        assertEquals("water leak", r.text)
    }

    @Test
    fun aJsonLineWithNoneOfTheThreeKeysIsSkipped() {
        assertNull(SenseVoiceOutputParser.parse("""{"lang": "<|en|>", "tokens": ["a"]}"""))
        val r = assertNotNull(SenseVoiceOutputParser.parse("{\"x\": 1}\n{\"event\": \"<|Cry|>\"}\n"))
        assertEquals("<|Cry|>", r.event)
        assertEquals("", r.text)
    }

    @Test
    fun emptyAndBlankInputIsNull() {
        assertNull(SenseVoiceOutputParser.parse(""))
        assertNull(SenseVoiceOutputParser.parse("  \r\n \n"))
    }

    @Test
    fun crlfLineEndingsAreTolerated() {
        val out = "Started\r\n{\"emotion\": \"<|ANGRY|>\", \"event\": \"<|Speech|>\", \"text\": \"hello\"}\r\nDone!\r\n"
        val r = assertNotNull(SenseVoiceOutputParser.parse(out))
        assertEquals("<|ANGRY|>", r.emotion)
        assertEquals("hello", r.text)
    }

    @Test
    fun aVeryLongTimestampsArrayIsFine() {
        val stamps = (0 until 200_000).joinToString(", ") { "0.${it % 100}" }
        val out = "{\"lang\": \"<|yue|>\", \"emotion\": \"<|HAPPY|>\", \"event\": \"<|Laughter|>\", \"text\": \"ha\", \"timestamps\": [$stamps]}\n"
        val r = assertNotNull(SenseVoiceOutputParser.parse(out))
        assertEquals("<|Laughter|>", r.event)
    }

    @Test
    fun jsonAfterALogPrefixOnTheSameLineIsStillFound() {
        val r = assertNotNull(SenseVoiceOutputParser.parse("     0.0ms [INFO] {\"emotion\": \"<|SAD|>\", \"event\": \"<|Speech|>\", \"text\": \"no\"}\n"))
        assertEquals("<|SAD|>", r.emotion)
    }

    @Test
    fun wrongTypedValuesAreTreatedAsAbsent() {
        val r = assertNotNull(SenseVoiceOutputParser.parse("""{"emotion": 5, "event": null, "text": "ok"}"""))
        assertNull(r.emotion)
        assertNull(r.event)
        assertEquals("ok", r.text)
    }
}

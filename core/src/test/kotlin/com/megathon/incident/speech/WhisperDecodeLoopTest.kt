package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WhisperDecodeLoopTest {
    // Small synthetic ids so each rule is visible: vocabulary of 120 tokens, timestamps start at 100.
    private val ids = WhisperTokenIds(
        eot = 1, sot = 2, transcribe = 3, noTimestamps = 4, timestampBegin = 100,
        languages = mapOf("hi" to 10, "te" to 11, "en" to 12),
        suppress = setOf(7), beginSuppress = setOf(5),
    )
    private val vocab = 120

    /** A decoder that records every (input id, position) and answers from a script of token ids to prefer. */
    private class Scripted(private val vocab: Int, private val answers: (call: Int) -> FloatArray) : WhisperDecoderStep {
        val calls = mutableListOf<Pair<Int, Int>>()
        override fun step(inputId: Int, position: Int): FloatArray {
            calls += inputId to position
            return answers(calls.size - 1)
        }
    }

    private fun prefer(vocab: Int, vararg ranked: Int): FloatArray =
        FloatArray(vocab) { -10f }.also { logits -> ranked.forEachIndexed { rank, id -> logits[id] = (ranked.size - rank).toFloat() } }

    /** After the 4 prompt steps, answer with these tokens in order, then end of text. */
    private fun sayThenStop(vararg tokens: Int) = Scripted(vocab) { call ->
        val index = call - 3 // call 3 is the last prompt step; its logits give the first generated token
        prefer(vocab, if (index in tokens.indices) tokens[index] else ids.eot)
    }

    private val loop = WhisperDecodeLoop(ids)

    @Test
    fun theLanguageIsForcedByFeedingThePromptInOrderAtPositionsZeroToThree() {
        val decoder = sayThenStop(20)
        loop.decode("hi", decoder)
        assertEquals(listOf(2 to 0, 10 to 1, 3 to 2, 4 to 3), decoder.calls.take(4))
    }

    @Test
    fun teluguAndEnglishUseTheirOwnLanguageTokens() {
        val te = sayThenStop(20).also { loop.decode("te", it) }
        val en = sayThenStop(20).also { loop.decode("en", it) }
        assertEquals(11, te.calls[1].first)
        assertEquals(12, en.calls[1].first)
    }

    @Test
    fun itGeneratesGreedilyUntilEndOfTextAndFeedsEachTokenBack() {
        val decoder = sayThenStop(20, 21, 22)
        assertEquals(listOf(20, 21, 22), loop.decode("hi", decoder))
        // The first generated token is fed at position 4, the next at 5, and so on.
        assertEquals(listOf(20 to 4, 21 to 5, 22 to 6), decoder.calls.drop(4).take(3))
    }

    @Test
    fun suppressedAndTimestampTokensAreNeverChosenEvenWhenTheyScoreHighest() {
        val decoder = Scripted(vocab) { call ->
            if (call == 3) prefer(vocab, 7, 105, 20) // suppressed, timestamp, then a real token
            else prefer(vocab, ids.eot)
        }
        assertEquals(listOf(20), loop.decode("hi", decoder))
    }

    @Test
    fun theBeginSuppressionOnlyAppliesToTheFirstGeneratedToken() {
        val decoder = Scripted(vocab) { call ->
            when (call) {
                3 -> prefer(vocab, 5, 20) // 5 is begin-suppressed, so 20 wins
                4 -> prefer(vocab, 5, 21) // later it is allowed
                else -> prefer(vocab, ids.eot)
            }
        }
        assertEquals(listOf(20, 5), loop.decode("hi", decoder))
    }

    @Test
    fun itStopsAtTheLengthCapCountingThePromptToo() {
        var next = 20
        val endless = Scripted(vocab) { prefer(vocab, 20 + (next++ % 5)) } // varied, so the repeat guard is not what stops it
        val tokens = WhisperDecodeLoop(ids, maxTokens = 12).decode("hi", endless)
        assertEquals(8, tokens.size, "a sequence of 12 minus the 4 prompt tokens")
        assertTrue(endless.calls.size <= 12, "no more decoder steps than the cap: ${endless.calls.size}")
    }

    @Test
    fun aRunOfTheSameTokenIsCutOffAndKeptOnce() {
        // Byte-level BPE on Indic text can loop on one token forever; stop it instead of filling 200 steps.
        val decoder = Scripted(vocab) { call -> if (call == 3) prefer(vocab, 20) else if (call == 4) prefer(vocab, 21) else prefer(vocab, 30) }
        val tokens = loop.decode("hi", decoder)
        assertEquals(listOf(20, 21, 30), tokens)
        assertTrue(decoder.calls.size < 30, "it must stop soon, not run to the cap: ${decoder.calls.size} steps")
    }

    @Test
    fun aShortLegitimateRepeatIsKept() {
        val decoder = sayThenStop(20, 30, 30, 30, 21)
        assertEquals(listOf(20, 30, 30, 30, 21), loop.decode("hi", decoder))
    }

    @Test
    fun anUnsupportedLanguageIsRejectedBeforeTheDecoderRuns() {
        val decoder = sayThenStop(20)
        assertFailsWith<IllegalArgumentException> { loop.decode("fr", decoder) }.also {
            assertTrue("fr" in it.message.orEmpty(), it.message)
        }
        assertTrue(decoder.calls.isEmpty())
    }

    @Test
    fun theRealMultilingualIdsMatchTheTokenizer() {
        // Read from openai/whisper-tiny's tokenizer on 2026-10-10: <|hi|> 50276, <|te|> 50299, prompt [50258, 50276, 50359, 50363].
        val real = WhisperTokenIds.multilingual()
        assertEquals(50276, real.languages["hi"])
        assertEquals(50299, real.languages["te"])
        assertEquals(50259, real.languages["en"])
        assertEquals(listOf(50258, 50276, 50359, 50363), real.prompt("hi"))
        assertEquals(50257, real.eot)
        assertEquals(50364, real.timestampBegin)
        assertEquals(88, real.suppress.size)
        assertEquals(setOf(220, 50257), real.beginSuppress)
    }
}

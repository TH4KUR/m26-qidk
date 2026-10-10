package com.megathon.incident.suggest

import kotlin.test.Test
import kotlin.test.assertEquals

/** Reference values from Winkler (1990) and the usual textbook examples, to 3 decimals. */
class JaroWinklerTest {
    private fun jw(a: String, b: String) = Math.round(JaroWinkler.similarity(a, b) * 1000) / 1000.0

    @Test fun transposedLettersScoreHigh() = assertEquals(0.961, jw("martha", "marhta"))
    @Test fun commonPrefixIsRewarded() = assertEquals(0.840, jw("dwayne", "duane"))
    @Test fun differentLengths() = assertEquals(0.813, jw("dixon", "dicksonx"))
    @Test fun identicalIsOne() = assertEquals(1.0, jw("maple hostel", "maple hostel"))
    @Test fun nothingInCommonIsZero() = assertEquals(0.0, jw("abc", "xyz"))
    @Test fun emptyIsZero() = assertEquals(0.0, jw("", "abc"))
    @Test fun symmetric() = assertEquals(jw("maple hostle", "maple hostel"), jw("maple hostel", "maple hostle"))
}

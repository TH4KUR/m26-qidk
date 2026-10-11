package com.megathon.incident.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Hindi/Telugu lane (A/020, experimental, D19): English site names said in Hindi or Telugu and written by the
 * recogniser in Devanagari or Telugu script, plus Hindi/Telugu number words (script and Roman). The chip proposes
 * the English site name, same contract as English. All spellings are model-authored and unreviewed.
 */
class IndicSnapTest {
    private val snapper = GazetteerSnapper(SiteDirectory.loadDefault(), Homophones.default)

    private fun proposed(heard: String) = snapper.snap(heard, EntryKind.PLACE)?.proposed

    // ---- Devanagari ------------------------------------------------------------------------------
    @Test fun hindiScriptSiteName() = assertEquals("Seminar Hall 2", proposed("सेमिनार हॉल दो"))

    @Test fun hindiPostpositionIsKept() = assertEquals("Seminar Hall 2 के पास", proposed("सेमिनार हॉल दो के पास"))

    @Test fun hindiLetterAndNumberCode() = assertEquals("Lab A2", proposed("लैब ए दो"))

    @Test fun hindiDigitByDigitRoom() = assertEquals("North 203", proposed("नॉर्थ दो शून्य तीन"))

    @Test fun hindiNamedHostel() = assertEquals("New Lotus Hostel", proposed("न्यू लोटस हॉस्टल"))

    @Test fun hindiCodeNotOnTheSiteGivesNothing() = assertNull(proposed("लैब बी दो"))

    @Test fun hindiNumberNotOnTheSiteGivesNothing() = assertNull(proposed("सेमिनार हॉल पांच"))

    @Test fun aLoneGenericWordGivesNothing() = assertNull(proposed("हॉल"))

    // ---- Telugu script ---------------------------------------------------------------------------
    @Test fun teluguScriptSiteName() = assertEquals("Seminar Hall 2", proposed("సెమినార్ హాల్ రెండు"))

    @Test fun teluguLetterAndNumberCode() = assertEquals("Lab C4", proposed("ల్యాబ్ సి నాలుగు"))

    @Test fun teluguPostpositionIsKept() = assertEquals("Conference Room 3 దగ్గర", proposed("కాన్ఫరెన్స్ రూమ్ మూడు దగ్గర"))

    @Test fun zeroWidthJoinersDoNotSplitAWord() = assertEquals("Seminar Hall 2", proposed("ఎస్‌హెచ్ రెండు"))

    @Test fun teluguNumberNotOnTheSiteGivesNothing() = assertNull(proposed("సెమినార్ హాల్ ఐదు"))

    // ---- Roman transliteration ---------------------------------------------------------------------
    @Test fun hinglishNumberWord() = assertEquals("Seminar Hall 2", proposed("seminar hall do"))

    @Test fun hinglishDigitByDigit() = assertEquals("North 203", proposed("north do zero teen"))

    @Test fun hinglishCode() = assertEquals("Lab C4", proposed("lab c chaar"))

    @Test fun teluguRomanNumberWord() = assertEquals("Seminar Hall 2", proposed("seminar hall rendu"))

    // ---- English unchanged -------------------------------------------------------------------------
    @Test fun englishWordsThatAreRomanNumbersStayEnglish() {
        assertNull(proposed("I do not know"))
        assertNull(proposed("the teen lounge"))
    }
}

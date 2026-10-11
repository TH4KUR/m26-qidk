package com.megathon.incident.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HindiTranscriptTest {
    private fun n(text: String) = HindiTranscript.normalize(text)

    @Test fun hourWordsBeforeBajeBecomeDigitsSoTheResolverCanReadThem() {
        assertEquals("आज सुबह 10 बजे कमरे में पानी लीक हो गया।", n("आज सुबह दस बजे कमरे में पानी लीक हो गया।"))
        assertEquals("कल रात 9 बजे", n("कल रात नौ बजे"))
        assertEquals("शाम 6 बजे", n("शाम छह बजे"))
        assertEquals("12 बजे", n("बारह बजे"))
    }

    @Test fun theHalfAndQuarterHourFormsBecomeClockTimes() {
        assertEquals("2:30 बजे", n("ढाई बजे"))
        assertEquals("1:30 बजे", n("डेढ़ बजे"))
        assertEquals("8:30 बजे", n("साढ़े आठ बजे"))
        assertEquals("9:15 बजे", n("सवा नौ बजे"))
        assertEquals("11:45 बजे", n("पौने बारह बजे"))
        assertEquals("12:45 बजे", n("पौने एक बजे"))
    }

    @Test fun minutesAndHoursAgoBecomeDigits() {
        assertEquals("2 घंटे पहले", n("दो घंटे पहले"))
        assertEquals("15 मिनट पहले", n("पंद्रह मिनट पहले"))
        assertEquals("30 मिनट पहले", n("आधे घंटे पहले"))
        assertEquals("45 मिनट पहले", n("पैंतालीस मिनट पहले"))
    }

    @Test fun devanagariDigitsBecomeAsciiDigits() {
        assertEquals("10 बजे", n("१० बजे"))
    }

    @Test fun textThatIsNotATimeIsLeftAlone() {
        for (text in listOf("दस लोग घायल हो गए", "मेरे घर पे भूखंप आ रहा है", "दो मशीनें खराब हैं", "Fire near Conference Room 2 at 10 AM", "")) {
            assertEquals(text, n(text))
        }
    }

    @Test fun theTimePhraseIsTheReportersOwnWordsInOrder() {
        assertEquals("आज सुबह 10 बजे", HindiTranscript.timePhrase("आज सुबह 10 बजे कमरे में पानी लीक हो गया।"))
        assertEquals("कल रात 2:30 बजे", HindiTranscript.timePhrase("कल रात 2:30 बजे आग लगी थी"))
        assertEquals("लगभग 10 बजे", HindiTranscript.timePhrase("लगभग 10 बजे लैब में धुआँ दिखा"))
        assertEquals("2 घंटे पहले", HindiTranscript.timePhrase("2 घंटे पहले पाइप फट गया"))
        assertEquals("आज सुबह", HindiTranscript.timePhrase("आज सुबह पाइप फट गया"))
        assertEquals("कल रात", HindiTranscript.timePhrase("कल रात बिजली गई थी"))
        assertEquals("अभी", HindiTranscript.timePhrase("अभी पानी बह रहा है"))
    }

    @Test fun noTimePhraseWhenNoneWasSaid() {
        assertNull(HindiTranscript.timePhrase("कमरे में पानी लीक हो गया"))
        assertNull(HindiTranscript.timePhrase("अभी भी पानी बह रहा है"))
        assertNull(HindiTranscript.timePhrase("Water leak at 10 AM"))
        assertNull(HindiTranscript.timePhrase("रात का गार्ड आया"))
    }
}

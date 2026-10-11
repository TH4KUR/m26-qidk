package com.megathon.incident.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HindiPhrasesTest {
    private fun hi(text: String) = SpokenSegment(text, "hi")
    private fun en(text: String) = SpokenSegment(text, "en")
    private fun segments(english: String) = HindiPhrases.segments(english)

    @Test
    fun everyQuestionThePlannerAsksHasAHindiLine() {
        val expected = mapOf(
            "What kind of incident is it?" to "यह किस तरह की घटना है?",
            "Where is it?" to "यह कहाँ है?",
            "Where did it happen?" to "यह कहाँ हुआ?",
            "Was that today?" to "क्या यह आज हुआ था?",
            "Which day was that?" to "यह किस दिन हुआ था?",
            "When was that?" to "यह कब हुआ था?",
            "How bad is it?" to "यह कितना गंभीर है?",
            "Can you tell me more?" to "क्या आप कुछ और बता सकते हैं?",
        )
        for ((english, hindi) in expected) assertEquals(listOf(hi(hindi)), segments(english), english)
    }

    @Test
    fun theAdvisorsKeyQuestionAndEmergencyLineAreTranslated() {
        assertEquals(listOf(hi("क्या अभी कोई घायल है या खतरे में है?")), segments("Is anyone hurt or in danger right now?"))
        // The emergency number is said one digit at a time, as the English line does.
        assertEquals(listOf(hi("अगर कोई खतरे में है, तो अभी एक एक दो पर कॉल करें।")), segments("If anyone is in danger, call 112 now."))
    }

    @Test
    fun questionsAskedTogetherBecomeOneHindiSegment() {
        assertEquals(listOf(hi("यह कितना गंभीर है? यह कब हुआ था?")), segments("How bad is it? When was that?"))
    }

    @Test
    fun aKeyQuestionThenAnEmergencyLineStayInTheOrderTheyWereSpoken() {
        assertEquals(
            listOf(hi("अगर कोई खतरे में है, तो अभी एक एक दो पर कॉल करें। क्या अभी कोई घायल है या खतरे में है?")),
            segments("If anyone is in danger, call 112 now. Is anyone hurt or in danger right now?"),
        )
    }

    @Test
    fun typeAtPlaceUsesHindiWordOrderAndKeepsADevanagariPlaceAsHeard() {
        assertEquals(listOf(hi("कमरे में पानी का रिसाव या बाढ़।")), segments("Water leak or flood at कमरे."))
        assertEquals(listOf(hi("आग या धुआँ।")), segments("Fire or smoke."))
        assertEquals(listOf(hi("लैब में चोट।")), segments("Injury at लैब."))
    }

    @Test
    fun aPlaceWrittenInLatinLettersIsSpokenInEnglishNotForcedThroughTheHindiVoice() {
        assertEquals(
            listOf(en("Conference Room 2"), hi("में आग या धुआँ।")),
            segments("Fire or smoke at Conference Room 2."),
        )
        assertEquals(listOf(en("SH2"), hi("पर।")), segments("At SH2."))
    }

    @Test
    fun severityIsTranslatedAndNoHarmIsItsOwnLine() {
        assertEquals(listOf(hi("गंभीरता मध्यम है।")), segments("Severity is moderate."))
        assertEquals(listOf(hi("गंभीरता अत्यंत गंभीर है।")), segments("Severity is critical."))
        assertEquals(listOf(hi("गंभीरता मामूली है।")), segments("Severity is minor."))
        assertEquals(listOf(hi("गंभीरता गंभीर है।")), segments("Severity is severe."))
        assertEquals(listOf(hi("कोई नुकसान नहीं हुआ।")), segments("No harm done."))
    }

    @Test
    fun theSavedReportSummaryIsWhollyHindiWhenItsPartsAreKnown() {
        val said = "Your report is saved on this device. Water leak or flood at कमरे. That was today at about 10 in the morning. Severity is moderate."
        assertEquals(
            listOf(hi("आपकी रिपोर्ट इस डिवाइस पर सेव हो गई है। कमरे में पानी का रिसाव या बाढ़। यह आज सुबह लगभग दस बजे हुआ था। गंभीरता मध्यम है।")),
            segments(said),
        )
    }

    @Test
    fun timesOfDayAreSpokenTheHindiWay() {
        assertEquals(listOf(hi("यह कल दोपहर ढाई बजे हुआ था।")), segments("That was yesterday at 2:30 in the afternoon."))
        assertEquals(listOf(hi("यह आज शाम आठ बजे हुआ था।")), segments("That was today at 8 in the evening."))
        assertEquals(listOf(hi("यह आज रात सवा नौ बजे हुआ था।")), segments("That was today at 9:15 at night."))
        assertEquals(listOf(hi("यह आज रात पौने बारह बजे हुआ था।")), segments("That was today at 11:45 at night."))
        assertEquals(listOf(hi("यह आज रात साढ़े बारह बजे हुआ था।")), segments("That was today at 12:30 at night."))
        assertEquals(listOf(hi("यह आज दोपहर डेढ़ बजे हुआ था।")), segments("That was today at 1:30 in the afternoon."))
        assertEquals(listOf(hi("यह सुबह लगभग दस बजे हुआ था।")), segments("That was at about 10 in the morning."))
    }

    @Test
    fun aDayAndAPartOfDayWithoutAClock() {
        assertEquals(listOf(hi("यह आज सुबह हुआ था।")), segments("That was today in the morning."))
        assertEquals(listOf(hi("यह कल रात हुआ था।")), segments("That was yesterday at night."))
        assertEquals(listOf(hi("यह रात में हुआ था।")), segments("That was at night."))
    }

    @Test
    fun anExplicitDateIsSpokenWithWordsForTheDayNumber() {
        assertEquals(listOf(hi("यह शनिवार, तीन अक्टूबर को हुआ था।")), segments("That was on Saturday 3 October."))
        assertEquals(
            listOf(hi("यह शुक्रवार, इकतीस जनवरी को सुबह नौ बजे हुआ था।")),
            segments("That was on Friday 31 January at 9 in the morning."),
        )
    }

    @Test
    fun aMinuteThatHasNoSimpleHindiWordIsLeftInEnglishRatherThanSpokenWrong() {
        assertEquals(listOf(en("That was today at 9:05 in the morning.")), segments("That was today at 9:05 in the morning."))
    }

    @Test
    fun aSentenceItDoesNotKnowIsKeptInEnglishAndMarkedEnglish() {
        assertEquals(listOf(en("Something nobody planned for.")), segments("Something nobody planned for."))
    }

    @Test
    fun anUnknownSentenceThatIsAlreadyHindiGoesToTheHindiVoice() {
        assertEquals(listOf(hi("पानी बंद कर दिया गया है।")), segments("पानी बंद कर दिया गया है।"))
        assertEquals(listOf(en("Please hold on."), hi("कृपया रुकिए।")), segments("Please hold on. कृपया रुकिए।"))
    }

    @Test
    fun aKnownSentenceBetweenUnknownOnesSplitsTheSegmentsInOrder() {
        assertEquals(
            listOf(en("Please hold on."), hi("यह कब हुआ था?"), en("Thank you.")),
            segments("Please hold on. When was that? Thank you."),
        )
    }

    @Test
    fun blankTextGivesNothing() {
        assertEquals(emptyList(), segments(""))
        assertEquals(emptyList(), segments("   "))
    }

    @Test
    fun noWordOfTheInputIsEverLost() {
        // A dynamic value is spoken as it was heard: the reporter's own Hindi words reach the voice unchanged.
        val joined = segments("Water leak or flood at पुराने भवन की दूसरी मंज़िल.").joinToString(" ") { it.text }
        assertTrue("पुराने भवन की दूसरी मंज़िल" in joined, joined)
    }
}

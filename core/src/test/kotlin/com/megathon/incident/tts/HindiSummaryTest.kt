package com.megathon.incident.tts

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.FieldValue
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.ValueStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HindiSummaryTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun heard(text: String) = FieldValue(text, ValueStatus.REPORTED, text)

    private fun report(
        type: String? = "water_leak_flood", place: String? = "कमरे", whenText: String? = "2026-10-11 ≈10:00 (around 10 AM)",
        severity: String? = "moderate",
    ): IncidentReport {
        var r = IncidentReport.empty("r", 0)
        type?.let { r = r.with(Field.INCIDENT_TYPE, heard(it)) }
        place?.let { r = r.with(Field.LOCATION, heard(it)) }
        whenText?.let { r = r.with(Field.DATE_TIME, heard(it)) }
        severity?.let { r = r.with(Field.SEVERITY, heard(it)) }
        return r
    }

    private fun advice(level: Priority) = PriorityAdvice(level, emptyList())
    private fun joined(r: IncidentReport, level: Priority = Priority.MEDIUM) =
        HindiSummary.compose(r, advice(level), today).joinToString(" ") { it.text }

    @Test
    fun aNormalReportIsOneCoherentHindiChainWithNoEmergencyCall() {
        assertEquals(
            "आपकी रिपोर्ट इस डिवाइस पर सेव हो गई है। " +
                "आपने बताया कि आज सुबह लगभग दस बजे कमरे में पानी का रिसाव या बाढ़ की घटना हुई, और गंभीरता मध्यम बताई गई है। " +
                "कृपया इन निर्देशों का पालन करें: रखरखाव टीम से पुष्टि करवाएँ कि रिसाव रुक गया है, और आसपास के बिजली के उपकरणों में नमी की जाँच करें।",
            joined(report()),
        )
    }

    @Test
    fun aSevereReportAddsEscalationAndTheEmergencyCallWithItsReason() {
        val text = joined(report(type = "fire_smoke", severity = "severe"), Priority.HIGH)
        assertTrue("आज ही सुरक्षा अधिकारी को सूचित करें।" in text, text)
        assertTrue(
            text.endsWith("अगर कोई खतरे में है, तो अभी एक एक दो पर कॉल करें, क्योंकि आपने बताया कि यह आग या धुआँ का मामला है और गंभीरता गंभीर है।"),
            text,
        )
    }

    @Test
    fun theEmergencyCallComesAfterTheNextStepsSoItIsTheLastThingHeard() {
        val text = joined(report(type = "injury", severity = "critical"), Priority.URGENT)
        assertTrue(text.indexOf("कृपया इन निर्देशों") < text.indexOf("एक एक दो"), text)
    }

    @Test
    fun aHighPriorityWithNothingStatedAsAReasonCallsWithoutInventingOne() {
        val text = joined(report(type = null, severity = null), Priority.URGENT)
        assertTrue("एक एक दो पर कॉल करें।" in text, text)
        assertFalse("क्योंकि" in text, text)
    }

    @Test
    fun aMildReportNeverSendsAnyoneToTheEmergencyNumber() {
        assertFalse("एक एक दो" in joined(report(severity = "minor"), Priority.LOW))
        assertFalse("एक एक दो" in joined(report(severity = "moderate"), Priority.MEDIUM))
    }

    @Test
    fun aPlaceInLatinLettersIsSpokenByTheEnglishVoiceInsideTheHindiSentence() {
        val segs = HindiSummary.compose(report(place = "Conference Room 2"), advice(Priority.MEDIUM), today)
        val i = segs.indexOfFirst { it.language == "en" }
        assertEquals("Conference Room 2", segs[i].text)
        assertTrue(segs[i - 1].text.endsWith("आपने बताया कि आज सुबह लगभग दस बजे"), segs[i - 1].text)
        assertTrue(segs[i + 1].text.startsWith("में पानी का रिसाव या बाढ़ की घटना हुई"), segs[i + 1].text)
    }

    @Test
    fun missingPartsAreLeftOutRatherThanGuessed() {
        val text = joined(report(place = null, whenText = null, severity = null))
        assertTrue("आपने बताया कि पानी का रिसाव या बाढ़ की घटना हुई।" in text, text)
        assertFalse("गंभीरता" in text, text)
    }

    @Test
    fun anUnknownTypeStillGetsAGenericNextStep() {
        val text = joined(report(type = "other"))
        assertTrue("क्षेत्र के सुपरवाइज़र को बताएँ" in text, text)
    }

    @Test
    fun aTimeWithNoSimpleHindiFormIsLeftOutOfTheSentenceNotSpokenWrong() {
        val text = joined(report(whenText = "2026-10-11 09:05 (9:05)"))
        assertFalse("बजे" in text, text)
        assertTrue("कमरे में पानी का रिसाव" in text, text)
    }

    @Test
    fun aValueTheAppOnlySuggestedIsNeverReadBackAsSomethingTheReporterSaid() {
        val r = report(severity = null).with(Field.SEVERITY, FieldValue("minor", ValueStatus.SUGGESTED, null))
        val text = joined(r)
        assertFalse("गंभीरता" in text, text)
    }
}

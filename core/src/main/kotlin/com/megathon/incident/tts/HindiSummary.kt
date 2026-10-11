package com.megathon.incident.tts

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentReport
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.domain.Priority
import com.megathon.incident.domain.PriorityAdvice
import com.megathon.incident.domain.Severity
import com.megathon.incident.session.DateTimeDisplay
import com.megathon.incident.session.Labels
import java.time.LocalDate

/**
 * The spoken end of a Hindi report, as one connected piece of speech instead of the English sentences translated one by one:
 * the report is saved, what the reporter said (place, kind of incident, time, how serious), the steps to follow, and, when the
 * incident is serious, the call to 112 with the reason the reporter gave.
 *
 * Everything is built from what the reporter said. A part that is not known, or a time with no simple Hindi form, is left out; the
 * reason for the 112 call is only ever the incident type and the severity they stated, never a guess.
 */
object HindiSummary {
    fun compose(report: IncidentReport, priority: PriorityAdvice?, today: LocalDate): List<SpokenSegment> {
        val type = IncidentType.fromWire(stated(report, Field.INCIDENT_TYPE))
        val severity = Severity.fromWire(stated(report, Field.SEVERITY))
        val serious = severity == Severity.SEVERE || severity == Severity.CRITICAL
        val typeName = type?.takeIf { it != IncidentType.OTHER }?.let { HindiPhrases.typeName(Labels.type(it.wire)) }
        val severityName = severity?.takeIf { it != Severity.NONE }?.let { HindiPhrases.severityName(Labels.severity(it.wire)) }
        val whenWords = stated(report, Field.DATE_TIME)?.takeIf { it.isNotBlank() }
            ?.let { HindiPhrases.whenWords(DateTimeDisplay.spoken(it, today)) }
        val place = stated(report, Field.LOCATION)?.trim()?.takeIf { it.isNotEmpty() }

        val out = mutableListOf<SpokenSegment>()
        fun hi(text: String) = out.add(SpokenSegment(text, "hi"))
        hi("आपकी रिपोर्ट इस डिवाइस पर सेव हो गई है।")

        if (typeName != null || place != null || whenWords != null) {
            hi("आपने बताया कि" + (whenWords?.let { " $it" } ?: ""))
            place?.let {
                val latinOnly = it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } && it.none { c -> c.code in 0x0900..0x097F }
                if (latinOnly) { out.add(SpokenSegment(it, "en")); hi("में") } else hi("$it में")
            }
            val what = typeName?.let { "$it की घटना" } ?: "एक घटना"
            hi(what + " हुई" + (severityName?.let { ", और गंभीरता $it बताई गई है" } ?: "") + "।")
        }

        type?.let {
            val steps = (if (serious) "आज ही सुरक्षा अधिकारी को सूचित करें। " else "") + (NEXT_STEP[it] ?: GENERIC_STEP)
            hi("कृपया इन निर्देशों का पालन करें: $steps")
        }

        val loweredByUser = priority?.overriddenByUser == true && (priority.level < Priority.HIGH)
        val callEmergency = !loweredByUser && ((priority != null && priority.level >= Priority.HIGH) || serious)
        if (callEmergency) {
            val reasons = listOfNotNull(
                typeName?.let { "यह $it का मामला है" },
                severityName?.takeIf { serious }?.let { "गंभीरता $it है" },
            )
            hi(
                "अगर कोई खतरे में है, तो अभी एक एक दो पर कॉल करें" +
                    (if (reasons.isNotEmpty()) ", क्योंकि आपने बताया कि ${reasons.joinToString(" और ")}" else "") + "।",
            )
        }
        return merge(out)
    }

    private fun merge(segments: List<SpokenSegment>): List<SpokenSegment> =
        segments.fold(mutableListOf()) { acc, seg ->
            val last = acc.lastOrNull()
            if (last != null && last.language == seg.language) acc[acc.lastIndex] = SpokenSegment(last.text + " " + seg.text, seg.language) else acc += seg
            acc
        }

    private const val GENERIC_STEP = "क्षेत्र के सुपरवाइज़र को बताएँ और जाँच होने तक जगह को सुरक्षित रखें।"

    /** The Hindi of `DerivedFields.followUp`'s advice, by incident type. */
    private val NEXT_STEP = mapOf(
        IncidentType.WATER_LEAK_FLOOD to "रखरखाव टीम से पुष्टि करवाएँ कि रिसाव रुक गया है, और आसपास के बिजली के उपकरणों में नमी की जाँच करें।",
        IncidentType.FIRE_SMOKE to "पुष्टि करें कि इलाका सुरक्षित है और अग्नि सुरक्षा अधिकारी को सूचित करें।",
        IncidentType.ELECTRICAL to "लोगों को उपकरण से दूर रखें और किसी इलेक्ट्रीशियन से उसकी जाँच करवाएँ।",
        IncidentType.SPILL_CHEMICAL to "जब तक फैलाव सुरक्षित तरीके से साफ़ न हो जाए, उस जगह को घेरकर रखें।",
        IncidentType.INJURY to "पक्का करें कि घायल व्यक्ति को चिकित्सा सहायता मिले, और उनके सुपरवाइज़र को बताएँ।",
        IncidentType.ILLNESS to "पक्का करें कि व्यक्ति को चिकित्सा सहायता मिले, और उनके सुपरवाइज़र को बताएँ।",
        IncidentType.EQUIPMENT_FAILURE to "जाँच होने तक उपकरण का उपयोग बंद रखें।",
    )
}

/**
 * The value the reporter actually gave (heard, confirmed or typed). A value the app only suggested is not something they said,
 * so it is never read back as if they had: no part of the saved-report summary comes from a guess.
 */
private fun stated(report: com.megathon.incident.domain.IncidentReport, field: Field): String? =
    if (report.isFilled(field)) report.value(field).text else null

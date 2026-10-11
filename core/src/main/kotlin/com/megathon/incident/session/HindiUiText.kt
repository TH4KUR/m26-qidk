package com.megathon.incident.session

import com.megathon.incident.domain.Field
import com.megathon.incident.domain.IncidentType
import com.megathon.incident.tts.HindiPhrases
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Hindi of the English sentences the core generates for the screen: the planner's questions and their answer labels, the
 * suggestion and probe chips, the advisor's reasons, field hints, the saved-report text and next step, undo prompts, journal
 * reasons and the app's short messages. A sentence that has no Hindi comes back unchanged.
 *
 * What the reporter said (a place, a name, a quote) is never translated: it is placed into the Hindi sentence as it is, in the
 * quotes it came in. Spoken lines are built by [HindiPhrases]; wherever it already has a sentence it is called, never copied, so
 * what is heard equals what is shown. Hindi input comes back as it is, so applying this twice changes nothing.
 *
 * Model-authored Hindi, not reviewed by a native speaker.
 */
object HindiUiText {
    fun of(english: String): String {
        if (english.none { it in 'A'..'Z' || it in 'a'..'z' }) return english
        val s = english.trim()
        exact(s)?.let { return it }
        template(s)?.let { return it }
        val parts = s.split(SENTENCE_BREAK).filter { it.isNotBlank() }
        if (parts.size > 1) {
            var changed = false
            val out = parts.map { p -> sentence(p)?.also { changed = true } ?: p }
            return if (changed) out.joinToString(" ") else english
        }
        return phrases(s) ?: english
    }

    private val SENTENCE_BREAK = Regex("(?<=[.?!])\\s+")

    private fun sentence(s: String): String? = exact(s) ?: template(s) ?: phrases(s)

    /** What [HindiPhrases] says for a sentence it knows (the same words the voice uses), or null when it does not know it. */
    private fun phrases(s: String): String? {
        val joined = HindiPhrases.segments(s).joinToString(" ") { it.text }
        return joined.takeIf { it != s && it.any(::isDevanagari) }
    }

    private fun isDevanagari(c: Char) = c.code in 0x0900..0x097F

    // ---- exact words and sentences ------------------------------------------------------------------------------

    private fun exact(s: String): String? =
        SENTENCES[s] ?: LABELS[s.lowercase()] ?: HindiPhrases.typeName(s) ?: HindiPhrases.severityName(s)

    /** Short answer labels and words, found whatever their case ("Today", "No one", "Don't know"). */
    private val LABELS: Map<String, String> = mapOf(
        "today" to "आज",
        "yesterday" to "कल",
        "just now" to "अभी-अभी",
        "yes" to "हाँ",
        "no" to "नहीं",
        "not sure" to "पक्का नहीं",
        "no one" to "कोई नहीं",
        "none" to "कोई नहीं",
        "don't know" to "पता नहीं",
        "no harm" to "कोई नुकसान नहीं",
        "empty" to "खाली",
        "unknown error" to "अज्ञात त्रुटि",
        "the speech engine failed" to "स्पीच इंजन नहीं चला",
        "the hindi voice could not be created" to "हिंदी आवाज़ बन नहीं सकी",
    )

    private val SENTENCES: Map<String, String> = mapOf(
        // Field hints.
        "Which day was this?" to "यह किस दिन हुआ था?",
        // The list of saved reports.
        "Incident report" to "घटना रिपोर्ट",
        "Saved" to "सेव हुई",
        // Journal reasons.
        "answered by voice" to "बोलकर जवाब दिया",
        "edited by the reporter" to "आपने बदला",
        "suggestion accepted" to "सुझाव मंज़ूर किया",
        "last change undone" to "पिछला बदलाव वापस लिया गया",
        "heard a correction" to "सुधार सुना गया",
        "written by the assistant" to "सहायक ने लिखा",
        // Priority.
        "set by the reporter" to "आपने खुद तय किया",
        "cancelled alarm" to "अलार्म रद्द किया",
        "cleared: new evidence raised the automatic level" to "हटाया गया: नई जानकारी से अपने आप तय स्तर बढ़ गया",
        "no rule fired; minimum score" to "कोई नियम लागू नहीं हुआ; न्यूनतम स्कोर",
        "reporter was unsure whether anyone is in danger; treated as danger" to "आप पक्का नहीं थे कि कोई खतरे में है या नहीं; इसे खतरा माना गया",
        "the language model says it is still continuing → immediacy 3" to "मॉडल के अनुसार यह अभी जारी है → तात्कालिकता 3",
        "reporter says it is not urgent → immediacy 1" to "आपने कहा कि यह ज़रूरी नहीं है → तात्कालिकता 1",
        "people may be at risk → impact +1" to "लोग खतरे में हो सकते हैं → असर +1",
        "distress in the report → immediacy +1" to "रिपोर्ट में घबराहट है → तात्कालिकता +1",
        // The next step shown on the report (the same words the Hindi summary speaks).
        "Escalate to the safety officer today." to "आज ही सुरक्षा अधिकारी को सूचित करें।",
        "Have maintenance confirm the leak is stopped, and check nearby electrical equipment for damp." to
            "रखरखाव टीम से पुष्टि करवाएँ कि रिसाव रुक गया है, और आसपास के बिजली के उपकरणों में नमी की जाँच करें।",
        "Confirm the area is safe and inform the fire safety officer." to "पुष्टि करें कि इलाका सुरक्षित है और अग्नि सुरक्षा अधिकारी को सूचित करें।",
        "Keep people away from the equipment and have an electrician inspect it." to "लोगों को उपकरण से दूर रखें और किसी इलेक्ट्रीशियन से उसकी जाँच करवाएँ।",
        "Keep the area cordoned off until the spill is cleaned up safely." to "जब तक फैलाव सुरक्षित तरीके से साफ़ न हो जाए, उस जगह को घेरकर रखें।",
        "Make sure the injured person has medical attention, and tell their supervisor." to "पक्का करें कि घायल व्यक्ति को चिकित्सा सहायता मिले, और उनके सुपरवाइज़र को बताएँ।",
        "Make sure the person has medical attention, and tell their supervisor." to "पक्का करें कि व्यक्ति को चिकित्सा सहायता मिले, और उनके सुपरवाइज़र को बताएँ।",
        "Tag the equipment out of use until it is checked." to "जाँच होने तक उपकरण का उपयोग बंद रखें।",
        "Tell the area supervisor and keep the area safe until it is looked at." to "क्षेत्र के सुपरवाइज़र को बताएँ और जाँच होने तक जगह को सुरक्षित रखें।",
        // The app's short messages.
        "The Hindi lane needs the real speech engine." to "हिंदी के लिए असली स्पीच इंजन चाहिए।",
        "This run uses the scripted demo, so English is used." to "यह रन स्क्रिप्टेड डेमो है, इसलिए अंग्रेज़ी इस्तेमाल हो रही है।",
        "This run uses the scripted demo." to "यह रन स्क्रिप्टेड डेमो है।",
        "Microphone access is off, so I can't hear you." to "माइक्रोफ़ोन की अनुमति बंद है, इसलिए आपकी आवाज़ सुनाई नहीं दे रही।",
        "You can turn it on in Settings, or type your answers." to "आप इसे सेटिंग्स में चालू कर सकते हैं, या अपने जवाब टाइप कर सकते हैं।",
        "I couldn't open the microphone." to "माइक्रोफ़ोन नहीं खुल पाया।",
        "Another app may be using it." to "हो सकता है कोई दूसरा ऐप इसे इस्तेमाल कर रहा हो।",
        "This build can't start." to "यह ऐप शुरू नहीं हो पा रहा।",
        "It would use a simulated engine, and simulated results are blocked in release builds." to
            "इसमें नकली इंजन इस्तेमाल होता, और रिलीज़ बिल्ड में नकली नतीजे रोक दिए जाते हैं।",
        "scripted demo, not live recognition" to "स्क्रिप्टेड डेमो, असली पहचान नहीं",
        "speech is scripted, not live recognition" to "आवाज़ पहले से तय है, असली पहचान नहीं",
        "speech is live, but the report is filled by canned replies, not a live model" to
            "आवाज़ असली है, पर रिपोर्ट पहले से तय जवाबों से भरी गई है, असली मॉडल से नहीं",
        "Telugu is not supported in this build, so English is used." to "इस बिल्ड में तेलुगु समर्थित नहीं है, इसलिए अंग्रेज़ी इस्तेमाल हो रही है।",
        "The Hindi model is never used for Telugu." to "तेलुगु के लिए हिंदी मॉडल कभी इस्तेमाल नहीं होता।",
    )

    // ---- templates: a sentence with a part the reporter said --------------------------------------------------------

    /** One quoted piece, in the quotes it came in: `“X”` or `"X"`. */
    private const val Q = "(?:“[^”]*”|\"[^\"]*\")"

    /** One or more quoted pieces joined by "and". */
    private const val QS = "$Q(?: and $Q)*"

    private class Rule(val regex: Regex, val build: (List<String>) -> String?)

    private fun rule(pattern: String, build: (List<String>) -> String?) = Rule(Regex(pattern), build)

    private fun template(s: String): String? {
        SOURCE_SUFFIX.matchEntire(s)?.let { m ->
            bare(m.groupValues[1])?.let { return "$it [${SOURCE[m.groupValues[2]]}]" }
        }
        return bare(s) ?: description(s)
    }

    private fun bare(s: String): String? = SENTENCES[s] ?: reason(s)

    private val SOURCE_SUFFIX = Regex("^(.+) \\[(TEXT_CUE|SENSEVOICE|YAMNET|GAZETTEER|RULE|LLM|USER)]$")

    private val SOURCE = mapOf(
        "TEXT_CUE" to "आपके शब्द", "SENSEVOICE" to "आवाज़", "YAMNET" to "आवाज़ की पहचान", "GAZETTEER" to "साइट सूची",
        "RULE" to "नियम", "LLM" to "मॉडल", "USER" to "आप",
    )

    private fun reason(s: String): String? {
        for (r in RULES) {
            val m = r.regex.matchEntire(s) ?: continue
            r.build(m.groupValues)?.let { return it }
        }
        return null
    }

    /** "X" and "Y" -> “X” और “Y”: the quotes stay, the joining word is Hindi. */
    private fun and(s: String) = s.replace(Regex("(?<=[”\"]) and (?=[“\"])"), " और ")

    private fun names(s: String) = s.replace(" and ", " और ")

    private fun unquote(q: String) = q.substring(1, q.length - 1)

    private fun chipField(word: String): String? = CHIP_FIELD[word]

    private val CHIP_FIELD = mapOf(
        "incident type" to "घटना का प्रकार", "severity" to "गंभीरता", "location" to "जगह", "time" to "समय",
        "people involved" to "शामिल लोग", "tool or item" to "औज़ार या सामान", "actions taken" to "की गई कार्रवाई", "witnesses" to "गवाह",
    )

    private val RULES: List<Rule> = listOf(
        // ---- suggestion chips ----
        rule("^Did you mean ($Q)\\?$") { "क्या आपका मतलब ${it[1]} था?" },
        rule("^Confirm ($Q)\\?$") { "क्या ${it[1]} सही है?" },
        rule("^Change to ($Q)\\?$") { "${it[1]} में बदलें?" },
        rule("^Add ($Q) as a tool or item\\?$") { "${it[1]} को औज़ार या सामान में जोड़ें?" },
        rule("^Add ($Q) as an action taken\\?$") { "${it[1]} को की गई कार्रवाई में जोड़ें?" },
        // ---- why a chip was made ----
        rule("^Heard ($Q); the site has (.+)\\.$") { "मैंने ${it[1]} सुना; साइट की सूची में ${it[2]} है।" },
        rule("^Heard ($Q); closest site name is (.+)\\.$") { "मैंने ${it[1]} सुना; साइट का सबसे मिलता-जुलता नाम ${it[2]} है।" },
        rule("^Heard two places, ($QS); the incident seems to be at (.+)\\.$") { "दो जगहें सुनाई दीं, ${and(it[1])}; लगता है घटना ${it[2]} पर हुई।" },
        rule("^The speaker said ($Q); confirm the (.+)\\.$") { m ->
            chipField(m[2])?.let { "आपने ${m[1]} कहा; $it की पुष्टि करें।" }
        },
        rule("^Heard ($Q), close to ($Q); confirm the (.+)\\.$") { m ->
            chipField(m[3])?.let { "मैंने ${m[1]} सुना, जो ${m[2]} से मिलता-जुलता है; $it की पुष्टि करें।" }
        },
        rule("^The speaker mentioned ($QS); add it as a tool or item\\?$") { "आपने ${and(it[1])} का ज़िक्र किया; इसे औज़ार या सामान में जोड़ें?" },
        rule("^The speaker said they ($QS); add it as an action taken\\?$") { "आपने बताया: ${and(it[1])}; इसे की गई कार्रवाई में जोड़ें?" },
        rule("^The speaker said ($Q); an injury needs someone hurt\\.$") { "आपने ${it[1]} कहा; चोट की घटना में किसी का घायल होना ज़रूरी है।" },
        rule("^([^.?!;]+) only saw it, so (?:they are witnesses|[^.?!;]+ is a witness), not involved\\.$") {
            "${names(it[1])} ने सिर्फ़ देखा, इसलिए वे गवाह हैं, घटना में शामिल नहीं।"
        },
        rule("^([^.?!;]+) (?:was|were) there when it happened, so (?:they are witnesses|[^.?!;]+ is a witness), not involved\\.$") {
            "${names(it[1])} घटना के समय वहाँ थे, इसलिए वे गवाह हैं, घटना में शामिल नहीं।"
        },
        rule("^([^.?!;]+) (?:was|were) there when it happened; add (?:them as witnesses|[^.?!;]+ as a witness)\\?$") {
            "${names(it[1])} घटना के समय वहाँ थे; इन्हें गवाह के रूप में जोड़ें?"
        },
        rule("^I may have missed it: you said ($Q)\\. Add ($Q) as a tool or item\\?$") {
            "शायद मुझसे छूट गया: आपने ${it[1]} कहा था। ${it[2]} को औज़ार या सामान के रूप में जोड़ें?"
        },
        rule("^I may have missed it: you said ($Q)\\. Add ($Q) as an action taken\\?$") {
            "शायद मुझसे छूट गया: आपने ${it[1]} कहा था। ${it[2]} को की गई कार्रवाई के रूप में जोड़ें?"
        },
        rule("^I may have missed it: ($QS) saw it or was there\\. Add as a witness\\?$") {
            "शायद मुझसे छूट गया: ${and(it[1])} वहाँ मौजूद थे या उन्होंने इसे देखा। गवाह के रूप में जोड़ें?"
        },
        rule("^I may have missed it: ($QS) is named as someone it happened to\\. Add as involved\\?$") {
            "शायद मुझसे छूट गया: ${and(it[1])} वह व्यक्ति हैं जिनके साथ यह हुआ। शामिल व्यक्ति के रूप में जोड़ें?"
        },
        // ---- field hints, journal, undo ----
        // The evidence is the reporter's own words and stays as it is; only the app's own reason (a severity guess) is translated.
        rule("^Suggested because: (.+)$") { "इसलिए सुझाया गया: ${reason(it[1]) ?: it[1]}" },
        rule("^(.+?) \\(($Q)\\) is a hazard of impact (\\d) of 5$") { "${hazard(it[1])} (${it[2]}) खतरा है; असर 5 में से ${it[3]}" },
        rule("^(.+) \\(suggested\\)$") { "${of(it[1])} (सुझाया गया)" },
        rule("^tapped (.+)$") { "${of(it[1])} दबाया" },
        rule("^suggestion accepted: (.+)$") { "सुझाव मंज़ूर किया: ${of(it[1])}" },
        rule("^(.+) set to (.+)$") { m -> fieldLabel(m[1])?.let { "$it अब ${of(m[2])} है" } },
        rule("^(.+) changed to (.+)$") { m -> fieldLabel(m[1])?.let { "$it अब ${of(m[2])} है" } },
        rule("^(.+) changed$") { m -> fieldLabel(m[1])?.let { "$it बदल गया" } },
        rule("^(.+) confirmed$") { m -> fieldLabel(m[1])?.let { "$it की पुष्टि हुई" } },
        // ---- priority and de-escalation ----
        rule("^no hazard phrase matched; default impact (\\d)$") { "कोई खतरे वाला शब्द नहीं मिला; मानक असर ${it[1]}" },
        rule("^(.+?) \\(($Q)\\) → impact (\\d)$") { "${hazard(it[1])} (${it[2]}) → असर ${it[3]}" },
        rule("^happening now → immediacy (\\d)$") { "अभी हो रहा है → तात्कालिकता ${it[1]}" },
        rule("^present or unresolved → immediacy (\\d)$") { "अभी मौजूद या अनसुलझा → तात्कालिकता ${it[1]}" },
        rule("^the language model suggests impact (\\d)(?:, capped at (\\d) by the rules)?$") {
            "मॉडल के हिसाब से असर ${it[1]}" + if (it[2].isNotEmpty()) ", नियमों से ${it[2]} तक सीमित" else ""
        },
        rule("^(.+) heard → impact ≥ (\\d), immediacy ≥ (\\d)$") { "${hazard(it[1])} सुनाई दिया → असर ≥ ${it[2]}, तात्कालिकता ≥ ${it[3]}" },
        rule("^(.+) voice → distress$") { m -> EMOTION[m[1].lowercase()]?.let { "$it आवाज़ → घबराहट" } },
        rule("^risk matrix: impact (\\d) × immediacy (\\d) = (\\d+)$") { "जोखिम तालिका: असर ${it[1]} × तात्कालिकता ${it[2]} = ${it[3]}" },
        rule("^additive: impact points (\\d+) \\+ immediacy points (\\d+)$") { "जोड़ पद्धति: असर अंक ${it[1]} + तात्कालिकता अंक ${it[2]}" },
        rule("^protocol tree: impact (\\d), immediacy (\\d) → (\\d+)$") { "प्रोटोकॉल वृक्ष: असर ${it[1]}, तात्कालिकता ${it[2]} → ${it[3]}" },
        rule("^rule ([\\w-]+): (.+)$") { m -> RULE_REASON[m[2]]?.let { "नियम: $it" } },
        rule("^reporter called it (critical|severe|moderate)$") { "आपने इसे ${HindiPhrases.severityName(it[1])} बताया" },
        rule("^incident type (\\S+) is a hazard in itself$") {
            "घटना का प्रकार ${HindiPhrases.typeName(Labels.type(it[1])) ?: it[1]} अपने आप में खतरा है"
        },
        rule("^reporter answered yes to ($Q)$") { "आपने ${quoteLike(it[1])} का जवाब हाँ दिया" },
        rule("^reporter override: (.+)$") { "आपका बदलाव: ${of(it[1])}" },
        rule("^reporter correction: (.+)$") { "आपने सुधारा: ${of(it[1])}" },
        rule("^reporter said ($Q)$") { "आपने ${it[1]} कहा" },
        // ---- the app's short messages ----
        rule("^The on-device model did not load yet: (.+)\\. It will be tried again when you speak\\.$") {
            "डिवाइस पर चलने वाला मॉडल अभी लोड नहीं हुआ: ${of(it[1])}। आपके बोलने पर इसे फिर से आज़माया जाएगा।"
        },
        rule("^I couldn't hear that: (.+)$") { "मुझे सुनाई नहीं दिया: ${of(it[1])}" },
        rule("^The last read failed: (.+)$") { "पिछली बार पढ़ना विफल रहा: ${of(it[1])}" },
        rule("^Can't read that file: (.+)$") { "वह फ़ाइल पढ़ी नहीं जा सकी: ${it[1]}" },
        rule("^The Hindi voice is not available \\((.+)\\), so replies are spoken in English\\.$") {
            "हिंदी आवाज़ उपलब्ध नहीं है (${of(it[1])}), इसलिए जवाब अंग्रेज़ी में बोले जाएँगे।"
        },
        rule("^Language ($Q) is not supported \\(only en and hi are known; base $Q\\), so English is used\\.$") {
            "भाषा ${it[1]} समर्थित नहीं है (केवल अंग्रेज़ी और हिंदी ज्ञात हैं), इसलिए अंग्रेज़ी इस्तेमाल हो रही है।"
        },
    )

    /** The label of a field as the English screen shows it, in Hindi; null for text that is not a field label. */
    private fun fieldLabel(english: String): String? =
        Field.entries.firstOrNull { Labels.fieldLabel(it) == english }?.let { HindiLabels.field(it) }

    /** A quoted sentence the app itself wrote, translated inside its quotes. */
    private fun quoteLike(q: String): String = "“" + of(unquote(q)) + "”"

    private fun hazard(label: String): String = HAZARD[label] ?: label

    private val HAZARD = mapOf(
        "alarm" to "अलार्म", "blockage" to "रुकावट", "broken glass" to "टूटा काँच", "chemical" to "रसायन", "cosmetic" to "ऊपरी खराबी",
        "crime" to "अपराध", "crying" to "रोने की आवाज़", "equipment" to "उपकरण की खराबी", "explosion" to "धमाका", "fainted" to "बेहोशी",
        "fight" to "झगड़ा", "fire" to "आग", "fire alarm" to "फ़ायर अलार्म", "flood" to "बाढ़", "gas leak" to "गैस रिसाव",
        "glass breaking" to "काँच टूटने की आवाज़", "gunshot" to "गोली की आवाज़", "intruder inside" to "अंदर घुसपैठिया", "leak" to "रिसाव",
        "leak near electrics" to "बिजली के पास रिसाव", "life threat" to "जान को खतरा", "live wire" to "करंट वाला तार",
        "minor injury" to "मामूली चोट", "person fell" to "व्यक्ति गिरा", "power outage" to "बिजली गुल", "screaming" to "चीख",
        "serious injury" to "गंभीर चोट", "siren" to "सायरन", "smell" to "गंध", "smoke" to "धुआँ", "sparks" to "चिंगारियाँ",
        "spill" to "फैलाव", "structural collapse" to "इमारत का ढहना", "suspicious" to "संदिग्ध", "trapped" to "फँसा हुआ",
        "unsafe structure" to "असुरक्षित ढाँचा", "weapon" to "हथियार", "coughing" to "खाँसी", "sneezing" to "छींक", "laughter" to "हँसी",
        "breathing" to "साँस की आवाज़",
    )

    private val EMOTION = mapOf("angry" to "गुस्से भरी", "sad" to "उदास", "fearful" to "डरी हुई", "fear" to "डरी हुई")

    /** The reason text of each authored priority rule (`policies/priority_rules.json`). */
    private val RULE_REASON = mapOf(
        "life-threatening hazard, present or unresolved" to "जानलेवा खतरा, अभी मौजूद या अनसुलझा",
        "life-threatening hazard type, reported as contained" to "जानलेवा खतरे का प्रकार, काबू में बताया गया",
        "serious hazard happening now" to "गंभीर खतरा, अभी हो रहा है",
        "serious hazard, present or unresolved" to "गंभीर खतरा, अभी मौजूद या अनसुलझा",
        "serious hazard type, no longer active" to "गंभीर खतरे का प्रकार, अब सक्रिय नहीं",
        "hazard to people happening now" to "लोगों के लिए खतरा, अभी हो रहा है",
        "hazard to people, present or unresolved" to "लोगों के लिए खतरा, अभी मौजूद या अनसुलझा",
        "hazard type, no longer active" to "खतरे का प्रकार, अब सक्रिय नहीं",
        "service or property fault that is continuing" to "सेवा या संपत्ति में खराबी, जो जारी है",
        "service or property fault needing action soon" to "सेवा या संपत्ति में खराबी, जल्दी कार्रवाई ज़रूरी",
        "service or property fault" to "सेवा या संपत्ति में खराबी",
        "cosmetic or minor nuisance" to "ऊपरी या मामूली परेशानी",
    )

    // ---- the saved-report text -----------------------------------------------------------------------------------

    private val TYPE_LABELS: List<String> = (IncidentType.entries.map { Labels.type(it.wire) } + "Incident").sortedByDescending { it.length }

    private val WHEN_TAIL = Regex(
        ", ((?:today|yesterday), [a-z]{3} \\d{1,2} [a-z]{3}(?: · .+)?|[a-z]{3} \\d{1,2} [a-z]{3}(?: · .+)?|" +
            "(?:about )?(?:\\d{1,2}:\\d{2} [ap]m|morning|afternoon|evening|night) · day not given)$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * `DerivedFields.description`: `<what>[ at <where>][, <when>].[ Severity: <x>.][ Done so far: <y>.]`. The place and what was
     * done are the reporter's words and stay as they are.
     */
    private fun description(s: String): String? {
        var rest = s
        var done: String? = null
        rest.indexOf(" Done so far: ").takeIf { it >= 0 }?.let { at ->
            done = rest.substring(at + " Done so far: ".length).removeSuffix(".")
            rest = rest.substring(0, at)
        }
        var severity: String? = null
        rest.lastIndexOf(" Severity: ").takeIf { it >= 0 }?.let { at ->
            severity = rest.substring(at + " Severity: ".length).removeSuffix(".")
            rest = rest.substring(0, at)
        }
        if (!rest.endsWith(".")) return null
        rest = rest.removeSuffix(".")
        val label = TYPE_LABELS.firstOrNull { rest == it || rest.startsWith("$it at ") || rest.startsWith("$it, ") } ?: return null
        var tail = rest.removePrefix(label)
        val whenMatch = WHEN_TAIL.find(tail)
        var whenText: String? = null
        if (whenMatch != null) {
            whenText = friendlyWhen(whenMatch.groupValues[1]) ?: return null
            tail = tail.removeRange(whenMatch.range)
        }
        val where = when {
            tail.isEmpty() -> null
            tail.startsWith(" at ") -> tail.removePrefix(" at ")
            else -> return null
        }
        val what = if (label == "Incident") "घटना" else HindiPhrases.typeName(label) ?: return null
        return buildString {
            where?.let { append(it).append(" में ") }
            append(what)
            whenText?.let { append(", ").append(it) }
            append("।")
            severity?.let { append(" गंभीरता: ").append(severityWord(it)).append("।") }
            done?.let { append(" अब तक किया गया: ").append(it).append("।") }
        }
    }

    private fun severityWord(lower: String): String = LABELS[lower.lowercase()] ?: HindiPhrases.severityName(lower) ?: lower

    /**
     * The "when" of `DateTimeDisplay.friendly` (`Today, Sun 11 Oct · about 10:00 AM`, `morning · day not given`) in the same
     * Hindi words the voice uses. A time with no short spoken form (9:05) is written with Devanagari numerals instead; null only
     * when the text is not in that shape, so the caller keeps it as it is.
     */
    private fun friendlyWhen(text: String): String? {
        val parts = text.split(" · ")
        val dayNotGiven = parts.size == 2 && parts[1].equals("day not given", ignoreCase = true)
        val day: String? = if (dayNotGiven) null else {
            val m = Regex("^(?:(today|yesterday), )?([a-z]{3}) (\\d{1,2}) ([a-z]{3})$", RegexOption.IGNORE_CASE).matchEntire(parts[0]) ?: return null
            m.groupValues[1].lowercase().ifEmpty {
                val weekday = DayOfWeek.entries.firstOrNull { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equals(m.groupValues[2], true) } ?: return null
                val month = Month.entries.firstOrNull { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equals(m.groupValues[4], true) } ?: return null
                "on ${weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${m.groupValues[3]} ${month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            }
        }
        val timeText = if (dayNotGiven) parts[0] else parts.getOrNull(1)
        var spokenTime: String? = null
        var partOfDay: String? = null // "in the morning" ...
        var clockWritten: String? = null // "about 9:05", for a time with no short spoken form
        if (timeText != null) {
            val clock = Regex("^(about )?(\\d{1,2}):(\\d{2}) ([ap]m)$", RegexOption.IGNORE_CASE).matchEntire(timeText)
            val word = timeText.lowercase()
            when {
                clock != null -> {
                    val h12 = clock.groupValues[2].toInt()
                    val minute = clock.groupValues[3].toInt()
                    val h24 = h12 % 12 + if (clock.groupValues[4].equals("pm", true)) 12 else 0
                    partOfDay = when (h24) { in 5..11 -> "in the morning"; in 12..16 -> "in the afternoon"; in 17..20 -> "in the evening"; else -> "at night" }
                    val about = clock.groupValues[1].isNotEmpty()
                    spokenTime = "at ${if (about) "about " else ""}${if (minute == 0) "$h12" else "%d:%02d".format(h12, minute)} $partOfDay"
                    clockWritten = (if (about) "लगभग " else "") + devanagariDigits("%d:%02d".format(h12, minute)) + " बजे"
                }
                word == "night" -> spokenTime = "at night"
                word in setOf("morning", "afternoon", "evening") -> spokenTime = "in the $word"
                else -> return null
            }
        }
        val hi = HindiPhrases.whenWords(listOfNotNull(day, spokenTime).joinToString(" "))
            ?: if (clockWritten != null && partOfDay != null) {
                // The day as the voice says it, then the part of the day and the clock in numerals.
                val dayHi = day?.let { HindiPhrases.whenWords(it) ?: return null }
                val part = HindiPhrases.whenWords(partOfDay)?.removeSuffix(" में") ?: return null
                listOfNotNull(dayHi, part, clockWritten).joinToString(" ")
            } else return null
        return if (dayNotGiven) "$hi · दिन नहीं बताया" else hi
    }

    private fun devanagariDigits(s: String) = s.map { if (it in '0'..'9') '०' + (it - '0') else it }.joinToString("")
}

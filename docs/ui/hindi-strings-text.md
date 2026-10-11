# Hindi strings for the core-generated text (`HindiUiText`)

Every Hindi string authored for `core/src/main/kotlin/com/megathon/incident/session/HindiUiText.kt`, the table behind
`HindiUiText.of(english)`. All of it is model-authored, short, spoken-style Devanagari with no native review: a native speaker
should read it before it is shown to a reporter. `X`, `“X”` and `NAME` are the parts the reporter said (a place, a name, a quote,
an action): they are never translated and keep the quotes they came in.

How a string is resolved: an exact entry, then a template, then `HindiPhrases` (the same Hindi the voice speaks, so what is shown
equals what is heard), then the English comes back unchanged. A text with several sentences is resolved sentence by sentence.

Where a sentence is spoken, the Hindi is the existing `HindiPhrases` entry and is **not** listed here: the planner questions, the
cross-questions, the advisor's two lines, the saved-report sentences and the "I didn't catch that" family. No `HindiPhrases` entry
is missing for a spoken question; everything below is text that is shown only.

Two things to align by hand (they are not in `HindiPhrases`, and `HindiSummary.NEXT_STEP` is private):

* The next-step sentences ("Have maintenance confirm ...", "Tag the equipment ...", "Escalate to the safety officer today.") are
  copied from `HindiSummary.NEXT_STEP` / `GENERIC_STEP`; a test pins them equal to the spoken chain.
* The saved-report summary that is heard is the connected chain `HindiSummary.compose`, not these sentences one by one. To show
  what is heard, the screen should show that chain's text rather than `HindiUiText.of(spokenSummary)`.
* The severity and incident-type words come from `HindiPhrases.severityName` / `typeName`; `HindiLabels.severity` / `type` should use
  the same words.

## Answer labels and short words

Matched whatever the case. The incident-type labels (“Water leak”, “Fire or smoke”, ...) and the severity labels (“Minor”, “Critical”, ...) are not repeated: they use `HindiPhrases.typeName` and `severityName`.

| English | Hindi | source |
|---|---|---|
| today | आज | model-authored, `HindiUiText` |
| yesterday | कल | model-authored, `HindiUiText` |
| just now | अभी-अभी | model-authored, `HindiUiText` |
| yes | हाँ | model-authored, `HindiUiText` |
| no | नहीं | model-authored, `HindiUiText` |
| not sure | पक्का नहीं | model-authored, `HindiUiText` |
| no one | कोई नहीं | model-authored, `HindiUiText` |
| none | कोई नहीं | model-authored, `HindiUiText` |
| don't know | पता नहीं | model-authored, `HindiUiText` |
| no harm | कोई नुकसान नहीं | model-authored, `HindiUiText` |
| empty | खाली | model-authored, `HindiUiText` |
| unknown error | अज्ञात त्रुटि | model-authored, `HindiUiText` |
| the speech engine failed | स्पीच इंजन नहीं चला | model-authored, `HindiUiText` |
| the hindi voice could not be created | हिंदी आवाज़ बन नहीं सकी | model-authored, `HindiUiText` |

## Sentences (exact)

| English | Hindi | source |
|---|---|---|
| Which day was this? | यह किस दिन हुआ था? | model-authored, `HindiUiText` |
| Incident report | घटना रिपोर्ट | model-authored, `HindiUiText` |
| Saved | सेव हुई | model-authored, `HindiUiText` |
| answered by voice | बोलकर जवाब दिया | model-authored, `HindiUiText` |
| edited by the reporter | आपने बदला | model-authored, `HindiUiText` |
| suggestion accepted | सुझाव मंज़ूर किया | model-authored, `HindiUiText` |
| last change undone | पिछला बदलाव वापस लिया गया | model-authored, `HindiUiText` |
| heard a correction | सुधार सुना गया | model-authored, `HindiUiText` |
| written by the assistant | सहायक ने लिखा | model-authored, `HindiUiText` |
| set by the reporter | आपने खुद तय किया | model-authored, `HindiUiText` |
| cancelled alarm | अलार्म रद्द किया | model-authored, `HindiUiText` |
| cleared: new evidence raised the automatic level | हटाया गया: नई जानकारी से अपने आप तय स्तर बढ़ गया | model-authored, `HindiUiText` |
| no rule fired; minimum score | कोई नियम लागू नहीं हुआ; न्यूनतम स्कोर | model-authored, `HindiUiText` |
| reporter was unsure whether anyone is in danger; treated as danger | आप पक्का नहीं थे कि कोई खतरे में है या नहीं; इसे खतरा माना गया | model-authored, `HindiUiText` |
| the language model says it is still continuing → immediacy 3 | मॉडल के अनुसार यह अभी जारी है → तात्कालिकता 3 | model-authored, `HindiUiText` |
| reporter says it is not urgent → immediacy 1 | आपने कहा कि यह ज़रूरी नहीं है → तात्कालिकता 1 | model-authored, `HindiUiText` |
| people may be at risk → impact +1 | लोग खतरे में हो सकते हैं → असर +1 | model-authored, `HindiUiText` |
| distress in the report → immediacy +1 | रिपोर्ट में घबराहट है → तात्कालिकता +1 | model-authored, `HindiUiText` |
| Escalate to the safety officer today. | आज ही सुरक्षा अधिकारी को सूचित करें। | model-authored, `HindiUiText` |
| Have maintenance confirm the leak is stopped, and check nearby electrical equipment for damp. | रखरखाव टीम से पुष्टि करवाएँ कि रिसाव रुक गया है, और आसपास के बिजली के उपकरणों में नमी की जाँच करें। | model-authored, `HindiUiText` |
| Confirm the area is safe and inform the fire safety officer. | पुष्टि करें कि इलाका सुरक्षित है और अग्नि सुरक्षा अधिकारी को सूचित करें। | model-authored, `HindiUiText` |
| Keep people away from the equipment and have an electrician inspect it. | लोगों को उपकरण से दूर रखें और किसी इलेक्ट्रीशियन से उसकी जाँच करवाएँ। | model-authored, `HindiUiText` |
| Keep the area cordoned off until the spill is cleaned up safely. | जब तक फैलाव सुरक्षित तरीके से साफ़ न हो जाए, उस जगह को घेरकर रखें। | model-authored, `HindiUiText` |
| Make sure the injured person has medical attention, and tell their supervisor. | पक्का करें कि घायल व्यक्ति को चिकित्सा सहायता मिले, और उनके सुपरवाइज़र को बताएँ। | model-authored, `HindiUiText` |
| Make sure the person has medical attention, and tell their supervisor. | पक्का करें कि व्यक्ति को चिकित्सा सहायता मिले, और उनके सुपरवाइज़र को बताएँ। | model-authored, `HindiUiText` |
| Tag the equipment out of use until it is checked. | जाँच होने तक उपकरण का उपयोग बंद रखें। | model-authored, `HindiUiText` |
| Tell the area supervisor and keep the area safe until it is looked at. | क्षेत्र के सुपरवाइज़र को बताएँ और जाँच होने तक जगह को सुरक्षित रखें। | model-authored, `HindiUiText` |
| The Hindi lane needs the real speech engine. | हिंदी के लिए असली स्पीच इंजन चाहिए। | model-authored, `HindiUiText` |
| This run uses the scripted demo, so English is used. | यह रन स्क्रिप्टेड डेमो है, इसलिए अंग्रेज़ी इस्तेमाल हो रही है। | model-authored, `HindiUiText` |
| This run uses the scripted demo. | यह रन स्क्रिप्टेड डेमो है। | model-authored, `HindiUiText` |
| Microphone access is off, so I can't hear you. | माइक्रोफ़ोन की अनुमति बंद है, इसलिए आपकी आवाज़ सुनाई नहीं दे रही। | model-authored, `HindiUiText` |
| You can turn it on in Settings, or type your answers. | आप इसे सेटिंग्स में चालू कर सकते हैं, या अपने जवाब टाइप कर सकते हैं। | model-authored, `HindiUiText` |
| I couldn't open the microphone. | माइक्रोफ़ोन नहीं खुल पाया। | model-authored, `HindiUiText` |
| Another app may be using it. | हो सकता है कोई दूसरा ऐप इसे इस्तेमाल कर रहा हो। | model-authored, `HindiUiText` |
| This build can't start. | यह ऐप शुरू नहीं हो पा रहा। | model-authored, `HindiUiText` |
| It would use a simulated engine, and simulated results are blocked in release builds. | इसमें नकली इंजन इस्तेमाल होता, और रिलीज़ बिल्ड में नकली नतीजे रोक दिए जाते हैं। | model-authored, `HindiUiText` |
| scripted demo, not live recognition | स्क्रिप्टेड डेमो, असली पहचान नहीं | model-authored, `HindiUiText` |
| speech is scripted, not live recognition | आवाज़ पहले से तय है, असली पहचान नहीं | model-authored, `HindiUiText` |
| speech is live, but the report is filled by canned replies, not a live model | आवाज़ असली है, पर रिपोर्ट पहले से तय जवाबों से भरी गई है, असली मॉडल से नहीं | model-authored, `HindiUiText` |
| Telugu is not supported in this build, so English is used. | इस बिल्ड में तेलुगु समर्थित नहीं है, इसलिए अंग्रेज़ी इस्तेमाल हो रही है। | model-authored, `HindiUiText` |
| The Hindi model is never used for Telugu. | तेलुगु के लिए हिंदी मॉडल कभी इस्तेमाल नहीं होता। | model-authored, `HindiUiText` |

## Templates (a part the reporter said)

| English | Hindi | source |
|---|---|---|
| Did you mean “X”? | क्या आपका मतलब “X” था? | model-authored |
| Confirm “X”? | क्या “X” सही है? | model-authored |
| Change to “X”? | “X” में बदलें? | model-authored |
| Add “X” as a tool or item? | “X” को औज़ार या सामान में जोड़ें? | model-authored |
| Add “X” as an action taken? | “X” को की गई कार्रवाई में जोड़ें? | model-authored |
| Heard “X”; the site has NAME. | मैंने “X” सुना; साइट की सूची में NAME है। | model-authored |
| Heard “X”; closest site name is NAME. | मैंने “X” सुना; साइट का सबसे मिलता-जुलता नाम NAME है। | model-authored |
| Heard two places, “A” and “B”; the incident seems to be at NAME. | दो जगहें सुनाई दीं, “A” और “B”; लगता है घटना NAME पर हुई। | model-authored |
| The speaker said “X”; confirm the F. | आपने “X” कहा; F की पुष्टि करें। | model-authored |
| Heard “X”, close to “Y”; confirm the F. | मैंने “X” सुना, जो “Y” से मिलता-जुलता है; F की पुष्टि करें। | model-authored |
| The speaker mentioned “A” and “B”; add it as a tool or item? | आपने “A” और “B” का ज़िक्र किया; इसे औज़ार या सामान में जोड़ें? | model-authored |
| The speaker said they “A” and “B”; add it as an action taken? | आपने बताया: “A” और “B”; इसे की गई कार्रवाई में जोड़ें? | model-authored |
| The speaker said “X”; an injury needs someone hurt. | आपने “X” कहा; चोट की घटना में किसी का घायल होना ज़रूरी है। | model-authored |
| NAME only saw it, so NAME is a witness, not involved. | NAME ने सिर्फ़ देखा, इसलिए वे गवाह हैं, घटना में शामिल नहीं। | model-authored |
| NAME was there when it happened, so NAME is a witness, not involved. | NAME घटना के समय वहाँ थे, इसलिए वे गवाह हैं, घटना में शामिल नहीं। | model-authored |
| NAME was there when it happened; add NAME as a witness? | NAME घटना के समय वहाँ थे; इन्हें गवाह के रूप में जोड़ें? | model-authored |
| I may have missed it: you said “Q”. Add “V” as a tool or item? | शायद मुझसे छूट गया: आपने “Q” कहा था। “V” को औज़ार या सामान के रूप में जोड़ें? | model-authored |
| I may have missed it: you said “Q”. Add “V” as an action taken? | शायद मुझसे छूट गया: आपने “Q” कहा था। “V” को की गई कार्रवाई के रूप में जोड़ें? | model-authored |
| I may have missed it: “A” and “B” saw it or was there. Add as a witness? | शायद मुझसे छूट गया: “A” और “B” वहाँ मौजूद थे या उन्होंने इसे देखा। गवाह के रूप में जोड़ें? | model-authored |
| I may have missed it: “A” is named as someone it happened to. Add as involved? | शायद मुझसे छूट गया: “A” वह व्यक्ति हैं जिनके साथ यह हुआ। शामिल व्यक्ति के रूप में जोड़ें? | model-authored |
| Suggested because: W | इसलिए सुझाया गया: W | model-authored (W is the reporter's words, kept) |
| H (“P”) is a hazard of impact N of 5 | H (“P”) खतरा है; असर 5 में से N | model-authored |
| S (suggested) | S (सुझाया गया) | model-authored |
| tapped L | L दबाया | model-authored |
| suggestion accepted: R | सुझाव मंज़ूर किया: R | model-authored |
| FIELD set to V | FIELD अब V है | model-authored (FIELD is `HindiLabels.field`) |
| FIELD changed to V | FIELD अब V है | model-authored |
| FIELD changed | FIELD बदल गया | model-authored |
| FIELD confirmed | FIELD की पुष्टि हुई | model-authored |
| no hazard phrase matched; default impact N | कोई खतरे वाला शब्द नहीं मिला; मानक असर N | model-authored |
| H (“P”) → impact N | H (“P”) → असर N | model-authored |
| happening now → immediacy N | अभी हो रहा है → तात्कालिकता N | model-authored |
| present or unresolved → immediacy N | अभी मौजूद या अनसुलझा → तात्कालिकता N | model-authored |
| the language model suggests impact N[, capped at M by the rules] | मॉडल के हिसाब से असर N[, नियमों से M तक सीमित] | model-authored |
| H heard → impact ≥ A, immediacy ≥ B | H सुनाई दिया → असर ≥ A, तात्कालिकता ≥ B | model-authored |
| E voice → distress | E आवाज़ → घबराहट | model-authored |
| risk matrix: impact A × immediacy B = C | जोखिम तालिका: असर A × तात्कालिकता B = C | model-authored |
| additive: impact points A + immediacy points B | जोड़ पद्धति: असर अंक A + तात्कालिकता अंक B | model-authored |
| protocol tree: impact A, immediacy B → C | प्रोटोकॉल वृक्ष: असर A, तात्कालिकता B → C | model-authored |
| rule ID: R | नियम: R (R from "Rule reasons"; the id is dropped) | model-authored |
| reporter called it critical / severe / moderate | आपने इसे अत्यंत गंभीर / गंभीर / मध्यम बताया | model-authored |
| incident type T is a hazard in itself | घटना का प्रकार T अपने आप में खतरा है | model-authored |
| reporter answered yes to “Q” | आपने “Q (Hindi)” का जवाब हाँ दिया | model-authored |
| reporter override: R | आपका बदलाव: R | model-authored |
| reporter correction: R | आपने सुधारा: R | model-authored |
| reporter said “X” | आपने “X” कहा | model-authored |
| L [TEXT_CUE] (any reason followed by its source) | L [source word, see "Source words"] | model-authored |
| The on-device model did not load yet: X. It will be tried again when you speak. | डिवाइस पर चलने वाला मॉडल अभी लोड नहीं हुआ: X। आपके बोलने पर इसे फिर से आज़माया जाएगा। | model-authored |
| I couldn't hear that: X | मुझे सुनाई नहीं दिया: X | model-authored |
| The last read failed: X | पिछली बार पढ़ना विफल रहा: X | model-authored |
| Can't read that file: X | वह फ़ाइल पढ़ी नहीं जा सकी: X | model-authored |
| The Hindi voice is not available (X), so replies are spoken in English. | हिंदी आवाज़ उपलब्ध नहीं है (X), इसलिए जवाब अंग्रेज़ी में बोले जाएँगे। | model-authored |
| Language “X” is not supported (only en and hi are known; base “Y”), so English is used. | भाषा “X” समर्थित नहीं है (केवल अंग्रेज़ी और हिंदी ज्ञात हैं), इसलिए अंग्रेज़ी इस्तेमाल हो रही है। | model-authored |

## Saved-report text (`DerivedFields.description`)

The description is parsed back into its parts and written as: `PLACE में WHAT, WHEN। गंभीरता: SEVERITY। अब तक किया गया: DONE।`
The place and what was done are the reporter's words and stay as they are; WHAT, SEVERITY and WHEN use the `HindiPhrases` words
(the same as the voice). A time with no short spoken form is written with Devanagari numerals.

| English | Hindi | source |
|---|---|---|
| Water leak or flood at Seminar Hall 2, today, Sun 11 Oct · about 10:00 AM. Severity: moderate. Done so far: isolated the area. | Seminar Hall 2 में पानी का रिसाव या बाढ़, आज सुबह लगभग दस बजे। गंभीरता: मध्यम। अब तक किया गया: isolated the area। | model-authored |
| Incident at Lab 4, yesterday, Sat 10 Oct · 2:30 PM. | Lab 4 में घटना, कल दोपहर ढाई बजे। | model-authored |
| Incident, morning · day not given. Severity: moderate. | घटना, सुबह में · दिन नहीं बताया। गंभीरता: मध्यम। | model-authored |
| Incident, Sat 3 Oct · about 9:05 AM. | घटना, शनिवार, तीन अक्टूबर को सुबह लगभग ९:०५ बजे। | model-authored |
| Incident | घटना | model-authored |
| Incident report | घटना रिपोर्ट | model-authored |
| Saved | सेव हुई | model-authored |

## Hazard labels (priority reasons)

The label of the hazard family or sound the rule matched, as in `H (“P”) → impact N` and `H heard → ...`.

| English | Hindi | source |
|---|---|---|
| alarm | अलार्म | model-authored, `HindiUiText` |
| blockage | रुकावट | model-authored, `HindiUiText` |
| broken glass | टूटा काँच | model-authored, `HindiUiText` |
| chemical | रसायन | model-authored, `HindiUiText` |
| cosmetic | ऊपरी खराबी | model-authored, `HindiUiText` |
| crime | अपराध | model-authored, `HindiUiText` |
| crying | रोने की आवाज़ | model-authored, `HindiUiText` |
| equipment | उपकरण की खराबी | model-authored, `HindiUiText` |
| explosion | धमाका | model-authored, `HindiUiText` |
| fainted | बेहोशी | model-authored, `HindiUiText` |
| fight | झगड़ा | model-authored, `HindiUiText` |
| fire | आग | model-authored, `HindiUiText` |
| fire alarm | फ़ायर अलार्म | model-authored, `HindiUiText` |
| flood | बाढ़ | model-authored, `HindiUiText` |
| gas leak | गैस रिसाव | model-authored, `HindiUiText` |
| glass breaking | काँच टूटने की आवाज़ | model-authored, `HindiUiText` |
| gunshot | गोली की आवाज़ | model-authored, `HindiUiText` |
| intruder inside | अंदर घुसपैठिया | model-authored, `HindiUiText` |
| leak | रिसाव | model-authored, `HindiUiText` |
| leak near electrics | बिजली के पास रिसाव | model-authored, `HindiUiText` |
| life threat | जान को खतरा | model-authored, `HindiUiText` |
| live wire | करंट वाला तार | model-authored, `HindiUiText` |
| minor injury | मामूली चोट | model-authored, `HindiUiText` |
| person fell | व्यक्ति गिरा | model-authored, `HindiUiText` |
| power outage | बिजली गुल | model-authored, `HindiUiText` |
| screaming | चीख | model-authored, `HindiUiText` |
| serious injury | गंभीर चोट | model-authored, `HindiUiText` |
| siren | सायरन | model-authored, `HindiUiText` |
| smell | गंध | model-authored, `HindiUiText` |
| smoke | धुआँ | model-authored, `HindiUiText` |
| sparks | चिंगारियाँ | model-authored, `HindiUiText` |
| spill | फैलाव | model-authored, `HindiUiText` |
| structural collapse | इमारत का ढहना | model-authored, `HindiUiText` |
| suspicious | संदिग्ध | model-authored, `HindiUiText` |
| trapped | फँसा हुआ | model-authored, `HindiUiText` |
| unsafe structure | असुरक्षित ढाँचा | model-authored, `HindiUiText` |
| weapon | हथियार | model-authored, `HindiUiText` |
| coughing | खाँसी | model-authored, `HindiUiText` |
| sneezing | छींक | model-authored, `HindiUiText` |
| laughter | हँसी | model-authored, `HindiUiText` |
| breathing | साँस की आवाज़ | model-authored, `HindiUiText` |

## Voice emotion (priority reasons)

| English | Hindi | source |
|---|---|---|
| angry | गुस्से भरी | model-authored, `HindiUiText` |
| sad | उदास | model-authored, `HindiUiText` |
| fearful | डरी हुई | model-authored, `HindiUiText` |
| fear | डरी हुई | model-authored, `HindiUiText` |
| life-threatening hazard, present or unresolved | जानलेवा खतरा, अभी मौजूद या अनसुलझा | model-authored, `HindiUiText` |
| life-threatening hazard type, reported as contained | जानलेवा खतरे का प्रकार, काबू में बताया गया | model-authored, `HindiUiText` |
| serious hazard happening now | गंभीर खतरा, अभी हो रहा है | model-authored, `HindiUiText` |
| serious hazard, present or unresolved | गंभीर खतरा, अभी मौजूद या अनसुलझा | model-authored, `HindiUiText` |
| serious hazard type, no longer active | गंभीर खतरे का प्रकार, अब सक्रिय नहीं | model-authored, `HindiUiText` |
| hazard to people happening now | लोगों के लिए खतरा, अभी हो रहा है | model-authored, `HindiUiText` |
| hazard to people, present or unresolved | लोगों के लिए खतरा, अभी मौजूद या अनसुलझा | model-authored, `HindiUiText` |
| hazard type, no longer active | खतरे का प्रकार, अब सक्रिय नहीं | model-authored, `HindiUiText` |
| service or property fault that is continuing | सेवा या संपत्ति में खराबी, जो जारी है | model-authored, `HindiUiText` |
| service or property fault needing action soon | सेवा या संपत्ति में खराबी, जल्दी कार्रवाई ज़रूरी | model-authored, `HindiUiText` |
| service or property fault | सेवा या संपत्ति में खराबी | model-authored, `HindiUiText` |
| cosmetic or minor nuisance | ऊपरी या मामूली परेशानी | model-authored, `HindiUiText` |

## Rule reasons (`policies/priority_rules.json`)

| English | Hindi | source |
|---|---|---|
| life-threatening hazard, present or unresolved | जानलेवा खतरा, अभी मौजूद या अनसुलझा | model-authored, `HindiUiText` |
| life-threatening hazard type, reported as contained | जानलेवा खतरे का प्रकार, काबू में बताया गया | model-authored, `HindiUiText` |
| serious hazard happening now | गंभीर खतरा, अभी हो रहा है | model-authored, `HindiUiText` |
| serious hazard, present or unresolved | गंभीर खतरा, अभी मौजूद या अनसुलझा | model-authored, `HindiUiText` |
| serious hazard type, no longer active | गंभीर खतरे का प्रकार, अब सक्रिय नहीं | model-authored, `HindiUiText` |
| hazard to people happening now | लोगों के लिए खतरा, अभी हो रहा है | model-authored, `HindiUiText` |
| hazard to people, present or unresolved | लोगों के लिए खतरा, अभी मौजूद या अनसुलझा | model-authored, `HindiUiText` |
| hazard type, no longer active | खतरे का प्रकार, अब सक्रिय नहीं | model-authored, `HindiUiText` |
| service or property fault that is continuing | सेवा या संपत्ति में खराबी, जो जारी है | model-authored, `HindiUiText` |
| service or property fault needing action soon | सेवा या संपत्ति में खराबी, जल्दी कार्रवाई ज़रूरी | model-authored, `HindiUiText` |
| service or property fault | सेवा या संपत्ति में खराबी | model-authored, `HindiUiText` |
| cosmetic or minor nuisance | ऊपरी या मामूली परेशानी | model-authored, `HindiUiText` |

## Source words (the `[SOURCE]` after a priority reason)

| English | Hindi | source |
|---|---|---|
| TEXT_CUE | आपके शब्द | model-authored, `HindiUiText` |
| SENSEVOICE | आवाज़ | model-authored, `HindiUiText` |
| YAMNET | आवाज़ की पहचान | model-authored, `HindiUiText` |
| GAZETTEER | साइट सूची | model-authored, `HindiUiText` |
| RULE | नियम | model-authored, `HindiUiText` |
| LLM | मॉडल | model-authored, `HindiUiText` |
| USER | आप | model-authored, `HindiUiText` |

## Chip field words (`confirm the F`)

| English | Hindi | source |
|---|---|---|
| incident type | घटना का प्रकार | model-authored, `HindiUiText` |
| severity | गंभीरता | model-authored, `HindiUiText` |
| location | जगह | model-authored, `HindiUiText` |
| time | समय | model-authored, `HindiUiText` |
| people involved | शामिल लोग | model-authored, `HindiUiText` |
| tool or item | औज़ार या सामान | model-authored, `HindiUiText` |
| actions taken | की गई कार्रवाई | model-authored, `HindiUiText` |
| witnesses | गवाह | model-authored, `HindiUiText` |


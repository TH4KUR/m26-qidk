# Hindi strings: field labels, types, severities, status words, dates

Model-authored, no native review yet. Source: `core/src/main/kotlin/com/megathon/incident/session/HindiLabels.kt`
(`HindiLabels`, `HindiDate`). Incident-type and severity words are not authored here: they are the words
`HindiPhrases` already speaks, so what is heard equals what is shown.

## Field labels (`HindiLabels.field`)

| English | Hindi | where |
|---|---|---|
| What happened | क्या हुआ | field label, INCIDENT_TYPE |
| Where | कहाँ | field label, LOCATION |
| When | कब | field label, DATE_TIME |
| How severe | कितना गंभीर | field label, SEVERITY |
| People involved | शामिल लोग | field label, PERSONS_INVOLVED |
| Tools / items | औज़ार / सामान | field label, EQUIPMENT_ASSET |
| What was done | क्या किया गया | field label, IMMEDIATE_ACTIONS |
| Witnesses | गवाह | field label, WITNESSES |
| Summary | सारांश | field label, DESCRIPTION |
| Recommended next step | सुझाया गया अगला कदम | field label, RECOMMENDED_FOLLOW_UP |

## Incident types (`HindiLabels.type`, from `HindiPhrases.typeName`)

| English | Hindi | where |
|---|---|---|
| Water leak or flood | पानी का रिसाव या बाढ़ | value of INCIDENT_TYPE |
| Fire or smoke | आग या धुआँ | value of INCIDENT_TYPE |
| Electrical problem | बिजली की समस्या | value of INCIDENT_TYPE |
| Spill or chemical | रसायन या तरल का फैलाव | value of INCIDENT_TYPE |
| Injury | चोट | value of INCIDENT_TYPE |
| Illness | बीमारी | value of INCIDENT_TYPE |
| Near miss | बाल-बाल बचने की घटना | value of INCIDENT_TYPE |
| Property damage | संपत्ति को नुकसान | value of INCIDENT_TYPE |
| Equipment failure | उपकरण में खराबी | value of INCIDENT_TYPE |
| Security issue | सुरक्षा संबंधी समस्या | value of INCIDENT_TYPE |
| Environmental issue | पर्यावरण संबंधी समस्या | value of INCIDENT_TYPE |
| Something else | कुछ और | value of INCIDENT_TYPE |

## Severities (`HindiLabels.severity`)

| English | Hindi | where |
|---|---|---|
| No harm | कोई नुकसान नहीं | value of SEVERITY (authored here; spoken as a full sentence elsewhere) |
| Minor | मामूली | value of SEVERITY (`HindiPhrases.severityName`) |
| Moderate | मध्यम | value of SEVERITY (`HindiPhrases.severityName`) |
| Severe | गंभीर | value of SEVERITY (`HindiPhrases.severityName`) |
| Critical | अत्यंत गंभीर | value of SEVERITY (`HindiPhrases.severityName`) |

## Status words (`HindiLabels.status`)

| English | Hindi | where |
|---|---|---|
| Heard | सुना गया | status pill, HEARD |
| Confirmed | पुष्टि हुई | status pill, CONFIRMED |
| Suggested | सुझाया गया | status pill, SUGGESTED |
| Needed | ज़रूरी | status pill, NEEDED |
| None | कोई नहीं | status pill, NONE |
| Not sure | पक्का नहीं | status pill, UNKNOWN |

## Dates and times (`HindiDate.friendly`)

Part of day by the 24-hour clock: 5-11 सुबह, 12-15 दोपहर, 16-19 शाम, 20-4 रात. Hours are written on a 12-hour face
(0 is १२, 13 is १) in Devanagari numerals, with बजे.

| English | Hindi | where |
|---|---|---|
| Today, Sun 11 Oct · about 10:00 AM | आज, रविवार ११ अक्टूबर · लगभग सुबह १० बजे | value of DATE_TIME |
| Yesterday, Sat 10 Oct · 2:30 PM | कल, शनिवार १० अक्टूबर · दोपहर २:३० बजे | value of DATE_TIME |
| Sat 3 Oct · 9:05 AM | शनिवार ३ अक्टूबर · सुबह ९:०५ बजे | value of DATE_TIME |
| 12 at night (00:00) | रात १२ बजे | value of DATE_TIME |
| 12 at noon (12:00) | दोपहर १२ बजे | value of DATE_TIME |
| Fri 9 Oct (time missing) | शुक्रवार ९ अक्टूबर | value of DATE_TIME |
| Today, Sun 11 Oct · morning | आज, रविवार ११ अक्टूबर · सुबह | value of DATE_TIME (part of day only) |
| About 10:00 AM · day not given | लगभग सुबह १० बजे · दिन नहीं बताया | value of DATE_TIME |
| about | लगभग | prefix of an approximate time |
| today / yesterday | आज / कल | day prefix |
| day not given | दिन नहीं बताया | suffix when the date is missing |
| Mon Tue Wed Thu Fri Sat Sun | सोमवार मंगलवार बुधवार गुरुवार शुक्रवार शनिवार रविवार | weekday |
| Jan to Dec | जनवरी फरवरी मार्च अप्रैल मई जून जुलाई अगस्त सितंबर अक्टूबर नवंबर दिसंबर | month |
| morning / afternoon / evening / night | सुबह / दोपहर / शाम / रात | part of day |

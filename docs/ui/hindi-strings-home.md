# Hindi strings: Home, Record, Reports, tabs, top bar

Model-authored, short and spoken in style, not reviewed by a native speaker. Every row is a literal that appears when the
app language is Hindi; the English is what appears otherwise (unchanged). Strings that come from the core tables
(`HindiLabels`, `HindiDate`, `HindiUiText`) are not repeated here.

| English | Hindi | where |
|---|---|---|
| Bol do, bayaan ho gaya. | बोल दो, बयान हो गया। | Home, line under the wordmark (`app_tagline`) |
| Your voice. | आपकी आवाज़। | Home headline, while recording |
| Taking shape. | आकार ले रही है। | Home headline (accent line), while recording |
| A little clarity. | थोड़ी स्पष्टता। | Home headline, while working |
| Coming together. | बात बन रही है। | Home headline (accent line), while working |
| A moment. | एक पल। | Home headline, at rest |
| A clear report. | एक साफ़ रिपोर्ट। | Home headline (accent line), at rest |
| Tell it as it happened. Tap to finish when you’re ready. | जैसा हुआ, वैसा बताइए। पूरा होने पर टैप करें। | Home supporting line, while recording |
| Organizing your words, right here on this device. | आपकी बात इसी डिवाइस पर व्यवस्थित हो रही है। | Home supporting line, while working |
| Tell it as it happened. We’ll take care of the structure. | जैसा हुआ, वैसा बताइए। ढाँचा हम संभाल लेंगे। | Home supporting line, at rest |
| LISTENING  ·  0:12 | सुन रहे हैं  ·  ०:१२ | Home status line under the microphone (timer in Devanagari numerals) |
| SHAPING YOUR REPORT | रिपोर्ट बन रही है | Home status line |
| MODEL WARMING UP | मॉडल तैयार हो रहा है | Home status line |
| Tap to finish your story | अपनी बात पूरी करने के लिए टैप करें | Home, under the microphone, while recording |
| A few details. One clear report. | कुछ ब्यौरे। एक साफ़ रिपोर्ट। | Home, under the microphone, while working |
| You can start while it warms up | तैयार होते समय भी आप शुरू कर सकते हैं | Home, under the microphone, model warming up |
| Tap to tell your story | अपनी बात बताने के लिए टैप करें | Home, under the microphone, at rest |
| Transcribing your recording… | आपकी रिकॉर्डिंग लिखी जा रही है… | Home, transcript card while the speech is being read |
| Organizing the report… | रिपोर्ट व्यवस्थित हो रही है… | Home, transcript card while the report is being shaped |
| No special format. Just say what happened. | कोई खास तरीका नहीं। बस बताइए क्या हुआ। | Home example row, open |
| Not sure where to start? See a quick example | शुरू कैसे करें, पता नहीं? एक छोटा उदाहरण देखें | Home example row, closed |
| Hide example | उदाहरण छिपाएँ | Home example row, icon description |
| Show example | उदाहरण दिखाएँ | Home example row, icon description |
| There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area. | कॉन्फ़्रेंस रूम २ के पास सुबह लगभग १० बजे पानी का रिसाव हो गया। मेंटेनेंस ने वह जगह पहले ही बंद कर दी है। | Home example row, the example sentence |
| हिन्दी (experimental) | हिन्दी (प्रायोगिक) | Home, the Hindi switch row |
| This build can’t start | यह बिल्ड शुरू नहीं हो सकता | Home blocked card title (only when the reason below it has Hindi; otherwise the whole card stays English) |
| Airplane mode on | एयरप्लेन मोड चालू | Privacy line |
| 12.3 KB sent | १२.३ केबी भेजा गया | Privacy line (units: बाइट, केबी, एमबी) |
| Network on | नेटवर्क चालू | Privacy line |
| On-device · … | इसी डिवाइस पर · … | Privacy line prefix |
| not available | उपलब्ध नहीं | Data-sent size when unknown (`formatBytes`, used by the proof sheet) |
| Back | वापस | Top bar, back button description |
| How this ran | यह कैसे चला | Top bar, info button description |
| SIMULATED · … / Simulated. … | नकली · … / नकली। …। | Top bar, strip under it and its screen-reader text |
| scripted demo, not live recognition | स्क्रिप्ट वाला डेमो, असली पहचान नहीं | Simulated strip, note |
| speech is scripted, not live recognition | आवाज़ स्क्रिप्ट से आ रही है, असली पहचान नहीं | Simulated strip, note |
| speech is live, but the report is filled by canned replies, not a live model | आवाज़ असली है, पर रिपोर्ट पहले से तय जवाबों से भरी गई है, असली मॉडल से नहीं | Simulated strip, note |
| New report | नई रिपोर्ट | Bottom tab |
| Reports | रिपोर्ट | Bottom tab, Reports top bar title |
| No reports yet | अभी कोई रिपोर्ट नहीं | Reports list, empty state |
| Reports you save appear here. They stay on this device. | आपकी सहेजी रिपोर्ट यहाँ दिखेंगी। वे इसी डिवाइस पर रहती हैं। | Reports list, empty state |
| Open this report | यह रिपोर्ट खोलें | Reports list row, click label for screen readers |
| Incident report (a report with no type) | घटना की रिपोर्ट | Reports list row title |
| Saved | सहेजी गई | Reports list row, status pill |
| Severity: Minor | गंभीरता: मामूली | Reports list row, severity pill (the value comes from `HindiLabels.severity`; a suggested one gets `HindiLabels.status(SUGGESTED)` in brackets) |
| Saved 11 Oct, 3:45 pm | ११ अक्टूबर, दोपहर ३:४५ बजे सहेजी गई | Reports list row, saved time (`HindiSavedAt`, Asia/Kolkata) |
| Saved report | सहेजी हुई रिपोर्ट | Saved report top bar title |
| Saved on this device | इस डिवाइस पर सहेजी गई रिपोर्ट | Saved report panel title |
| This report has no details. | इस रिपोर्ट में कोई ब्यौरा नहीं है। | Saved report, empty |
| Record clip | क्लिप रिकॉर्ड करें | Record screen top bar (debug data collection) |
| 16 kHz mono PCM16. Tap to start, tap again to stop. Nothing is recorded until you tap. | १६ किलोहर्ट्ज़, मोनो, PCM16। शुरू करने के लिए टैप करें, रोकने के लिए फिर टैप करें। आपके टैप करने तक कुछ भी रिकॉर्ड नहीं होता। | Record screen |
| Recording... tap to stop | रिकॉर्डिंग हो रही है... रोकने के लिए टैप करें | Record screen |
| Saving | सहेजा जा रहा है | Record screen |
| Tap to record again (overwrites) | दोबारा रिकॉर्ड करने के लिए टैप करें (पुराना मिट जाएगा) | Record screen |
| Tap to record | रिकॉर्ड करने के लिए टैप करें | Record screen |
| Could not save: … | सहेजा नहीं जा सका: … | Record screen error (the system message after the colon is shown as it is) |
| The microphone could not be opened (permission missing, or another app is using it). | माइक खुल नहीं सका (अनुमति नहीं है, या कोई और ऐप उसे इस्तेमाल कर रहा है)। | Record screen error |
| Microphone permission was denied. | माइक की अनुमति नहीं दी गई। | Record screen error |
| Saved | सहेजा गया | Record screen, after a clip is written |
| Duration 3.2 s | अवधि ३.२ सेकंड | Record screen |
| Peak -3.1 dBFS · Mean -39.0 dBFS | शिखर -३.१ डीबीएफ़एस · औसत -३९.० डीबीएफ़एस | Record screen |
| Clipping: move back or speak softer. | आवाज़ कट रही है: थोड़ा पीछे हटें या धीमे बोलें। | Record screen level hint |
| Very quiet: get closer to the mic and speak up. | बहुत धीमी: माइक के पास आएँ और ज़ोर से बोलें। | Record screen level hint |
| A bit quiet: speak up or move closer (about -39 dBFS mean is typical on this kit). | थोड़ी धीमी: ज़ोर से बोलें या पास आएँ (इस किट पर औसत लगभग -३९ डीबीएफ़एस सामान्य है)। | Record screen level hint |
| Level looks fine. | स्तर ठीक है। | Record screen level hint |

## Saved-at time (`HindiSavedAt`)

Day number and month name, then the part of the day, then the 12-hour clock, all digits in Devanagari.

| English part of day (hours) | Hindi |
|---|---|
| morning (05-11) | सुबह |
| afternoon (12-16) | दोपहर |
| evening (17-20) | शाम |
| night (21-04) | रात |

Months: जनवरी, फरवरी, मार्च, अप्रैल, मई, जून, जुलाई, अगस्त, सितंबर, अक्टूबर, नवंबर, दिसंबर.

## Left as they are on purpose

- The place and every other thing the reporter said, in the saved report and in the list rows (a value is shown as it was said).
- A blocked-build reason or a message with no Hindi in `HindiUiText`: the whole card stays English, so the card is never half English.
- "Bayaan" (name) and the unit and standard names PCM16 on the Record screen.

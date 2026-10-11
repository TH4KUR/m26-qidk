# Hindi strings: proof sheet and audio score

Every user-visible literal on these screens goes through `tr(en, hi)`. Format: `English | Hindi | where`.
Technical labels that come straight from the rule list or an engine (a voice tag such as "angry", a sound such as
"gunshot", an engine name, `NPU`, `CPU`, `dBFS`, `ms`) are shown as they are.

## How this ran (`ui/components/ProofSheet.kt`)

| English | Hindi | where |
| --- | --- | --- |
| How this ran | यह कैसे चला | sheet title |
| This session | यह सत्र | view switch |
| All sessions | सभी सत्र | view switch |
| Part of this run is simulated: {note}. Simulated results are labelled wherever they appear. | इस रन का एक हिस्सा नकली (सिम्युलेटेड) है: {note}। नकली नतीजों पर जहाँ भी वे दिखते हैं, लेबल लगा है। | simulated banner |
| see the steps below | नीचे के चरण देखें | simulated banner |
| Where each step ran | हर चरण कहाँ चला | This session |
| Heard with | सुनने में | This session, steps |
| Understood with | समझने में | This session, steps |
| Spoken with | बोलने में | This session, steps |
| Not run yet | अभी नहीं चला | steps, compute counts |
| Uses the Piper voice (an open model run by sherpa-onnx), offline, on the CPU. | Piper आवाज़ (sherpa-onnx पर चलने वाला खुला मॉडल) का उपयोग, ऑफ़लाइन, CPU पर। | steps |
| Uses an offline system voice, on the CPU. | ऑफ़लाइन सिस्टम आवाज़ का उपयोग, CPU पर। | steps |
| Uses the system voice, on the CPU. No offline voice was confirmed. | सिस्टम आवाज़ का उपयोग, CPU पर। ऑफ़लाइन आवाज़ की पुष्टि नहीं हुई। | steps |
| Where the work ran | काम कहाँ हुआ | both views |
| Hindi (experimental) | हिन्दी (प्रयोगात्मक) | This session |
| Voice tags (suggestions only) | आवाज़ के टैग (केवल सुझाव) | This session |
| None heard | कोई नहीं सुना | voice tags |
| Voice / Sound | आवाज़ / ध्वनि | voice tags, score terms |
| from SenseVoice | SenseVoice से | voice tags |
| Network | नेटवर्क | This session |
| Airplane mode | एयरप्लेन मोड | network |
| On / Off | चालू / बंद | network |
| Network available | नेटवर्क उपलब्ध | network |
| Yes / No | हाँ / नहीं | network |
| Data sent by this app | इस ऐप द्वारा भेजा गया डेटा | network, All sessions summary |
| Reports are saved only on this device. | रिपोर्ट केवल इसी डिवाइस पर सहेजी जाती हैं। | network |
| What the assistant did | सहायक ने क्या किया | This session |
| Nothing yet. | अभी कुछ नहीं। | tool log |
| Changes you and the assistant made | आपने और सहायक ने क्या बदला | This session |
| No values have been changed. | कोई मान नहीं बदला गया। | journal |
| The original wording is kept for every change. | हर बदलाव के साथ मूल शब्द सुरक्षित रखे जाते हैं। | journal |
| (empty) | (खाली) | journal |
| Hide details / Show details | विवरण छिपाएँ / विवरण दिखाएँ | tool log row |

## Audio score card (This session)

| English | Hindi | where |
| --- | --- | --- |
| Audio score | ऑडियो स्कोर | card title |
| Heuristic | अनुमान-आधारित | card pill |
| No audio has been scored in this report yet. Speak, and the score appears here, term by term. | इस रिपोर्ट में अभी किसी ऑडियो का स्कोर नहीं बना है। बोलिए, तो स्कोर यहाँ एक-एक मद के साथ दिखेगा। | empty state |
| The latest of {n} spoken turn(s) in this report. | इस रिपोर्ट के {n} बोले गए चरणों में से नवीनतम। | card |
| Capture quality | रिकॉर्डिंग की गुणवत्ता | card |
| How well the device heard the voice: five measured terms, each worth a fixed number of points. | डिवाइस ने आवाज़ को कितना अच्छा सुना: पाँच मापी गई मदें, हर एक के निश्चित अंक। | card |
| Measured | मापा गया | term row |
| not measured | मापा नहीं गया | anywhere a value is not available |
| Peak level | चरम स्तर | term |
| Average level (RMS) | औसत स्तर (RMS) | term |
| Clipped samples | कटे हुए नमूने | term |
| Length | अवधि | term |
| Silence | खामोशी | term |
| {n} points at or above {x} dBFS, falling in a straight line to 0 at {y} dBFS. | {n} अंक {x} dBFS या उससे ऊपर, फिर सीधी रेखा में घटकर {y} dBFS पर 0। | peak rule |
| {n} points from {a} to {b} dBFS; fewer below {a} (0 at {c}) and fewer above {b} (0 at {d}). | {n} अंक {a} से {b} dBFS के बीच; {a} से नीचे कम ({c} पर 0) और {b} से ऊपर कम ({d} पर 0)। | mean rule |
| {n} points at 0 %, falling to 0 at {p} % of samples at full scale. | {n} अंक 0 % पर, घटकर पूर्ण स्तर पर पहुँचे नमूनों के {p} % पर 0। | clipping rule |
| {n} points at {a} s or longer, falling to 0 at {b} s. | {n} अंक {a} सेकंड या उससे लंबे पर, घटकर {b} सेकंड पर 0। | length rule |
| {n} points at {a} % or less of {f} ms frames below {t} dBFS, falling to 0 at {z} %. | {n} अंक, जब {t} dBFS से नीचे के {f} ms फ्रेम {a} % या कम हों; {z} % पर 0। | silence rule |
| Recognition | पहचान | card section |
| Engine | इंजन | recognition |
| Ran on | कहाँ चला | recognition, All sessions header |
| Time to recognise | पहचानने में लगा समय | recognition |
| Audio length | ऑडियो की लंबाई | recognition |
| Speed against real time | वास्तविक समय की तुलना में गति | recognition |
| real time | वास्तविक समय | recognition ("4.4× real time") |
| Audio length divided by processing time; higher is faster. Where an engine did not report its time, it says not measured. | ऑडियो की लंबाई को प्रोसेसिंग समय से भाग देने पर; जितना अधिक, उतना तेज़। जिस इंजन ने समय नहीं बताया, वहाँ "मापा नहीं गया" लिखा है। | recognition |
| Words recognised | पहचाने गए शब्द | recognition |
| Recognition confidence | पहचान का भरोसा | recognition |
| None of the speech engines in this app reports a confidence number, so none is shown. | इस ऐप का कोई भी स्पीच इंजन भरोसे की संख्या नहीं देता, इसलिए कोई संख्या नहीं दिखाई गई। | recognition |
| How the voice fed the priority | आवाज़ ने प्राथमिकता को कैसे प्रभावित किया | card section |
| Priority score (1 to 20) = impact (1 to 5) × immediacy (1 to 4). ... Nothing lowers the score. | प्राथमिकता स्कोर (1 से 20) = प्रभाव (1 से 5) × तात्कालिकता (1 से 4)। ... कुछ भी स्कोर घटाता नहीं। | formula (full text in ProofSheet.kt) |
| Levels: 1 to 4 LOW, 5 to 9 MEDIUM, 10 to 14 HIGH, 15 to 20 URGENT. | स्तर: 1 से 4 कम, 5 से 9 मध्यम, 10 से 14 उच्च, 15 से 20 अत्यावश्यक। | thresholds |
| No cue was found in the words and no voice tag was heard. | शब्दों में कोई संकेत नहीं मिला और कोई आवाज़ का टैग नहीं सुना गया। | priority terms |
| Words | शब्द | term kind |
| On the rule list: impact at least {i}, immediacy at least {m}. | नियम-सूची में है: प्रभाव कम से कम {i}, तात्कालिकता कम से कम {m}। | sound term |
| No priority rule uses this sound, so it changes nothing. | कोई प्राथमिकता-नियम इस ध्वनि का उपयोग नहीं करता, इसलिए इससे कुछ नहीं बदलता। | sound term |
| Counts as distress: immediacy goes up by 1, at most 4. | संकट माना जाता है: तात्कालिकता 1 बढ़ती है, अधिकतम 4। | voice term |
| No priority rule uses this voice, so it changes nothing. | कोई प्राथमिकता-नियम इस आवाज़ का उपयोग नहीं करता, इसलिए इससे कुछ नहीं बदलता। | voice term |
| Found in the recognised words; with the other cues it makes the words-only score. | पहचाने गए शब्दों में मिला; बाकी संकेतों के साथ मिलकर केवल-शब्द स्कोर बनता है। | words term |
| points | अंक | term points ("+4 points") |
| no change | कोई बदलाव नहीं | term points |
| from | स्रोत | term source |
| Words alone | केवल शब्द | priority summary |
| Words and voice tags | शब्द और आवाज़ के टैग | priority summary |
| Voice tags added | आवाज़ के टैग से जुड़ा | priority summary |
| Level from these rules | इन नियमों से स्तर | priority summary |
| LOW / MEDIUM / HIGH / URGENT | कम / मध्यम / उच्च / अत्यावश्यक | level name |
| A replay of the app's own rules on this report's words and voice tags. The live priority can be higher: ... | इस रिपोर्ट के शब्दों और आवाज़ के टैग पर ऐप के अपने नियमों का दोहराव। असली प्राथमिकता इससे अधिक हो सकती है: ... | priority note (full text in ProofSheet.kt) |
| A heuristic written out in the open: ... not a measure of speech accuracy or of how serious the report is. | खुले रूप में लिखा गया एक अनुमान-आधारित (हीयूरिस्टिक) नियम: ... यह बोली की सटीकता या रिपोर्ट की गंभीरता का माप नहीं है। | card footnote (full text in ProofSheet.kt) |

## All sessions

| English | Hindi | where |
| --- | --- | --- |
| Read from the log kept on this device: one line for every saved report, numbers only (no words, names or places). It is never sent anywhere. | इस डिवाइस पर रखे लॉग से पढ़ा गया: हर सहेजी गई रिपोर्ट की एक पंक्ति, केवल संख्याएँ (कोई शब्द, नाम या स्थान नहीं)। यह कहीं भेजा नहीं जाता। | header note |
| Reading the log… | लॉग पढ़ा जा रहा है… | loading |
| Summary | सारांश | section |
| Reports saved | सहेजी गई रिपोर्टें | summary |
| Average audio score | औसत ऑडियो स्कोर | summary |
| No report has been saved yet. A line is added here each time you save one. | अभी कोई रिपोर्ट सहेजी नहीं गई है। हर बार रिपोर्ट सहेजने पर यहाँ एक पंक्ति जुड़ती है। | empty state |
| Typical time per step (median) | हर चरण का सामान्य समय (मध्यिका) | section |
| Speech to text | बोली से लिखित पाठ | stage name (Hear) |
| Understanding the report (model) | रिपोर्ट समझना (मॉडल) | stage name (Read) |
| Voice tags (emotion, sounds) | आवाज़ के टैग (भाव, ध्वनियाँ) | stage name (Tags) |
| Spoken replies | बोले गए उत्तर | stage name (Speak) |
| The middle value over every timed run of the step; the number in brackets is how many runs. | उस चरण के हर समयबद्ध रन का बीच का मान; कोष्ठक की संख्या रन की गिनती है। | medians note |
| steps | चरण | compute mix |
| Latest reports | हाल की रिपोर्टें | section |
| When | कब | recent header |
| Audio | ऑडियो | recent header |
| Speech · Model | वाणी · मॉडल | recent header |

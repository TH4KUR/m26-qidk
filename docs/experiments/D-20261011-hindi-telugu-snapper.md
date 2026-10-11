# D · 2026-10-11 · Hindi/Telugu site snapping (A/020 items 1-3), experimental lane

- **Who:** D · **When:** 06:00-06:25 IST · **Lane:** experimental (D19); English is the headline and is unchanged.
- **Goal:** English site names said in Hindi or Telugu, written by the recogniser in Devanagari or Telugu script, or Roman Hinglish with Hindi/Telugu number words, give the same chip as English ("सेमिनार हॉल दो" -> Seminar Hall 2).
- **Code:** `suggest/IndicTokens.kt` + `suggest/indic_tokens.json` (341 tokens: Devanagari and Telugu spellings of the site vocabulary and the letters A-Z, Hindi and Telugu number words 0-20 and the tens in script and Roman). `GazetteerSnapper` tries these readings the same way as sound-alikes, so **a chip still needs an exact site match**: a wrong table entry can cost a chip but cannot invent a place. Zero-width joiners no longer split a word. Tests: `IndicSnapTest` (18, written first, 13 failed before the code).
- **Table and set are model-authored and unreviewed** (`label_source: model_draft_unreviewed`). Written from general knowledge, not from any gold file or recording.
- **English unchanged:** full `:core:test` green, and the English dev snapper measurement re-run gives exactly run 6 of `D-20261011-snapper.md` (clean 55: 5 right chip / 21 right no-chip / 0 wrong / 0 missed / 0 false alarm; synthetic 10: 8/1/0/0/0; misheard 9: 4/0/0/4/0).
- **Data:** `data/indic_draft_v1/indic_draft_v1.jsonl` (40 items: 15 hi, 15 te, 10 hi-en), committed in `6f4606c` **before** any measurement on it; split by number fixed in its README (dev = hi01-10, te01-10, hn01-06; test = the rest). Measured on the spoken place phrase (`location_said`) of **dev items only**; the test split was not run.
- **Harness:** `core/src/test/kotlin/.../suggest/IndicSnapEvaluation.kt`, `INDIC_EVAL_SET=<abs path> INDIC_EVAL_OUT=<report> ./gradlew --no-daemon :core:test --tests "*IndicSnapEvaluation"`.
- **Looks at dev results: 1. Rule changes after looking: 0.**

## Result (dev, n = 26, model-authored text, not recogniser output, not reviewed)
| language | n | RIGHT_CHIP | WRONG_CHIP | RIGHT_NO_CHIP | MISSED | FALSE_ALARM |
|---|---|---|---|---|---|---|
| hi | 10 | 8 | 0 | 1 | 1 | 0 |
| te | 10 | 8 | 0 | 1 | 1 | 0 |
| hn | 6 | 3 | 0 | 3 | 0 | 0 |

| id | said | site | proposed | outcome |
|---|---|---|---|---|
| hi01 | सेमिनार हॉल दो में | Seminar Hall 2 | Seminar Hall 2 में | RIGHT_CHIP |
| hi02 | लैब सी चार में | Lab C4 | Lab C4 में | RIGHT_CHIP |
| hi03 | नॉर्थ दो शून्य चार में | North 204 | North 204 में | RIGHT_CHIP |
| hi04 | न्यू लोटस हॉस्टल के बाहर | New Lotus Hostel | New Lotus Hostel के बाहर | RIGHT_CHIP |
| hi05 | मेन गेट के पास | Main Gate | Main Gate के पास | RIGHT_CHIP |
| hi06 | सेंट्रल मेस की रसोई में | Central Mess | Central Mess की रसोई में | RIGHT_CHIP |
| hi07 | लाइब्रेरी की दूसरी मंजिल पर | Library |  | MISSED |
| hi08 | स्पोर्ट्स ग्राउंड पर | Sports Ground | Sports Ground पर | RIGHT_CHIP |
| hi09 | रूम बीस में | Room 20 | Room 20 में | RIGHT_CHIP |
| hi10 | पार्किंग एरिया में | (not on site) |  | RIGHT_NO_CHIP |
| te01 | సెమినార్ హాల్ రెండు లో | Seminar Hall 2 | Seminar Hall 2 లో | RIGHT_CHIP |
| te02 | ల్యాబ్ సి నాలుగు లో | Lab C4 | Lab C4 లో | RIGHT_CHIP |
| te03 | నార్త్ రెండు సున్నా రెండు లో | North 202 | North 202 లో | RIGHT_CHIP |
| te04 | మేపుల్ హాస్టల్ బయట | Maple Hostel | Maple Hostel బయట | RIGHT_CHIP |
| te05 | మెయిన్ గేట్ దగ్గర | Main Gate | Main Gate దగ్గర | RIGHT_CHIP |
| te06 | సెంట్రల్ మెస్ వంటగదిలో | Central Mess | Central Mess వంటగదిలో | RIGHT_CHIP |
| te07 | లైబ్రరీ రెండో అంతస్తులో | Library |  | MISSED |
| te08 | స్పోర్ట్స్ గ్రౌండ్ లో | Sports Ground | Sports Ground లో | RIGHT_CHIP |
| te09 | రూమ్ ఇరవై లో | Room 20 | Room 20 లో | RIGHT_CHIP |
| te10 | పార్కింగ్ ఏరియాలో | (not on site) |  | RIGHT_NO_CHIP |
| hn01 | seminar hall do mein | Seminar Hall 2 | Seminar Hall 2 mein | RIGHT_CHIP |
| hn02 | lab c chaar mein | Lab C4 | Lab C4 mein | RIGHT_CHIP |
| hn03 | north do zero chaar mein | North 204 | North 204 mein | RIGHT_CHIP |
| hn04 | new lotus hostel ke bahar | New Lotus Hostel |  | RIGHT_NO_CHIP |
| hn05 | main gate ke paas | Main Gate |  | RIGHT_NO_CHIP |
| hn06 | central mess ki kitchen mein | Central Mess |  | RIGHT_NO_CHIP |

- Both misses are "Library" inside a longer phrase ("लाइब्रेरी की दूसरी मंजिल पर", "లైబ్రరీ రెండో అంతస్తులో"): the existing rule never snaps a lone word without a digit inside a phrase, in English too. Left as is.
- Hinglish right no-chips: the English name is already said in Roman script ("new lotus hostel ke bahar"), so no chip is needed.
- **Labels for every number above:** experimental, dev split, small n, authored text written by the same model that wrote the table, not reviewed by a native speaker. It shows the mechanism works on the spellings D expected; it says nothing about what a real Hindi/Telugu recogniser writes. Real transcripts (B / the app session) are the next test.

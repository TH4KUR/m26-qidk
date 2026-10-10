From: A   To: C   Date: 2026-10-11 03:32 IST   Re: A/014
Type: info (kit evidence for your review of `lead/recall-salvage`)

The app session ran your branch on the kit: real Qwen3-4B on the NPU, prompt v8 plus its Earlier-turns context, E's 27 dev cases (34 turns), held-out 13 untouched, E's scorer. Baseline = its branch without the patch (run dev10-earlier); patch = merged `lead/recall-salvage` (run dev11-salvage). Core tests on the merged branch: 804 run, 1 failure (`ToolCallParserTest.wrongValueTypeFails`, left for you).

| Measure | Baseline | With patch |
|---|---|---|
| Overall correct | 64/135 (47%) | **88/135 (65%)** |
| Overall precision | 64/73 (88%) | 88/92 (96%) |
| Mandatory-field recall | 56/86 (65%) | **74/86 (86%)** |
| Mandatory-field precision | 56/63 (89%) | 74/77 (96%) |
| Hallucination | 2/81 (2%) | 1/81 (1%) |
| Valid call, first / after one retry | 27/34 (79%) / 30/34 (88%) | 31/34 (91%) / 32/34 (94%) |
| Median per turn | 5.0 s | 4.8 s |

Per field recall, baseline to patch: severity 5/9 to 8/9, location 73% to 85%, date/time 12/24 to 22/24, immediate actions 14% to 38%, incident type 74% to 81%. Equipment is unchanged (1/19): that is the model's gap, not the parser. Persons 4/7.

**Caveats (keep them in any report):** one run each, not repeated; the dev set is the set the prompt and the patch's time/persons rules were tuned on, so the 13 held-out cases are the fair check; dev barely exercises multi-turn. A positive result, not a verified gain on unseen data.

**What I ask:** merge what holds into `c/core-rules`, update `wrongValueTypeFails` deliberately, and tell me the commit and the test count. If you drop a rule, say which, so the app session can re-run. I will test and merge to master after that (target before about 09:30).

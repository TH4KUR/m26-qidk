From: B   To: C, A (cc D, E)   Date: 2026-10-11 08:18 IST   Re: B/018, C/021, C/019, A/032
Type: done (the four items announced in B/018) + review of prompt v12. All on `b/speech-engines` `b785c29`. See also B/019 (800-row set abandoned).

## 1. Review of `extract_v12.txt` (C/021 item 2; wording only, nothing run on a model)
Diff against v11 read: one rule sentence and one worked example. Four points:
1. **"Was with me" versus "who are handling it".** persons_involved is still defined as people "who are handling it", and the new sentence sends anyone with the speaker to witnesses. A companion who also acts ("Tomas was there with me and helped her up") sits between both. Decide it in the text. In the presence gold below it is item `p03`, tagged `judgement_call` (gold: persons_involved); drop it if you disagree.
2. **"Did not see it".** A/032's defect (dev item g20, "Rahul was there but he didn't see her fall", gold witnesses empty) has a prompt twin: v11 already says witnesses "saw it or were there", and v12 strengthens "with the speaker". Neither tells the model that someone present who did not see it is not a witness. Gold item `p04` covers it.
3. **The worked example is the sentence that motivated the fix** ("my friend Rahul was with me when it happened"), which is also the likeliest wording in the 24-sentence probe. A model that copies the example scores perfectly on that sentence and shows nothing about generalisation. Use a different construction.
4. **Size.** v12 is 4,270 B against v11's 3,747 B (about +130 tokens at 4 B/token). A/022 showed the median turn growing with prompt length (v8 4.2 s; longer candidates 5.4 and 6.5 s against the 6 s gate), so the added text has a latency price.

**Alternative wording** (replace the persons/witnesses line, and the new example; everything else as v12). Size 4,130 B: 140 B shorter than v12, still 383 B over v11, most of it the example (drop the example if latency matters). **Untested on a model**; send it through your same probe if you want it compared.
```
- persons_involved: named people the incident happened to or who are handling it. witnesses: named people who saw it, or who were only with the speaker, nearby or there ("my friend Meera was with me"). Not a witness: someone hurt or affected (persons_involved), someone who did not see it, a role such as "security guard" or "nurse", or the speaker.

Worker: "Smoke near the kitchen exhaust. Meera was standing next to me."
{"tool":"update_report","arguments":{"incident_type":"fire_smoke","location":"the kitchen exhaust","witnesses":["Meera"],"evidence":{"incident_type":"Smoke","location":"the kitchen exhaust","witnesses":"Meera was standing next to me"}}}
```
Checked: that example envelope goes through the real `ToolCallParser` and `PayloadValidator` on this branch with 3 grounded proposals and 0 rejections (a throwaway test, not committed).

## 2. Presence gold for the v11 versus v12-presence kit run (A/032)
`docs/experiments/B-20261011-presence-gold/presence_v1.jsonl`: 16 fictional items in `gold_v1` item schema; 10 positives (different "was with me / next to me / nearby / there" wordings, one two-turn), 6 controls (did not see it, a role, speaker alone, explicitly absent, hurt person, and the `p03` judgement call). `split`/`source` are `authored_after_failures` (E's scorer needs a loader that accepts that value; nothing of E's was edited). Labelled "authored after seeing failures, not dev, not held-out"; report positives and controls separately with denominators; 16 items cannot show significance. I read only dev rows for the format, never held-out. Names checked against `tools/blindcheck.words`. README and 6 unittest checks beside it (also verified to fail when a gold value is corrupted). No model was run.

## 3. Paced replay for the batch versus live kit comparison (B/017 request 3)
`PacedReplay` (core, 4 tests) plays a clip at real-time pace through one reused buffer. Debug hook, **uncompiled until A builds**: `--es replay <wav> [--es live off] [--ei loops N]` plays into the live path (or, with `live off`, straight into batch at "Stop") N times and logs `latency mode=live-replay|batch-replay stop_to_ready_ms=...`. Three runs on the same clip: `--es live off --es speech_worker cli`, `--es live off`, and the default. Also fixed there: "New report" no longer resets the Loading/Ready label.

## 4. Line endings (C/010, C/019)
`.gitattributes` (new): `data/synthetic_gold/** text eol=lf` and `core/src/main/resources/prompts/*.txt text eol=lf`, so byte-comparing tests (`SyntheticGoldContractTest`, `DefaultPromptTest`) do not fail on CRLF checkouts. A: it touches no content; take it or not when you merge.

## Verified / not verified
`:core:test` 947 tests, 1 failing (`SyntheticGoldContractTest`, the validator defect fixed on `c/core-rules` `35a96dc`). App and worker still unbuilt on Android (B/017 requests to A stand: SDK build, worker build, kit slot).

## Still open and not done by B
A/021 FLEURS Hindi/Telugu subset (needs the user's go for the download), A/022 place_context_v9 review (the file is only in the lead's scratchpad: commit it or paste the two changed sentences), E/011 phone-clip contest (needs the user to download the Drive folder).

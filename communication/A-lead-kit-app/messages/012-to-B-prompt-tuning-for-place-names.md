From: A   To: B   Date: 2026-10-11 03:19 IST   Re: A/006, request from the app session (kit operator)
Type: task

The app session asked that prompt tuning for place names go to you. You do **not** need the kit: you write prompt files, the app session runs them on the kit against E's 27 dev cases (held-out 13 stay untouched, about 3.5 min per prompt) and sends you the score table. Send the files by path in your next message.

**What is broken (measured on the kit, real Whisper plus Qwen3-4B):**
1. Location keeps filler: Whisper heard "a sh2" and the model wrote location "a sh2" (article kept; the code is spoken as one token). Several location names are misread.
2. The model never writes `equipment_asset` (0% on dev) and rarely `immediate_actions` (about 5 to 14%). It also skipped "right now" as a time.
3. Speed: a warm turn is about 6 to 7 s (Whisper 2.5 to 3.4 s, SenseVoice 0.7 to 0.9 s, LLM 2 to 3 s). Decode is about 23 tok/s, so shorter output is the lever.

**Your asks:**
- (a) A prompt for place names (place name only, no articles or filler; keep codes as spoken) and for equipment and actions, plus 3 to 5 hard test sentences that break the current prompt (an article before a code, a place next to a landmark, two places in one sentence). **Use the fictional site list** in committed files (D's `site.json` on master). Real names stay in uncommitted local notes.
- (b) Normalising the place against `site.json` is a **post-step and is D's code, not a prompt**. It is already on master (`SiteDirectory`, `Homophones`, snapper, D's 55-value measurement). D's known gap is a site name inside a longer phrase (5 misses): exactly "a sh2". Tell D the exact phrases; do not rebuild it.
- (c) A shorter-output variant with the same validity (fewer evidence tokens).

**Hard constraints (C's validator; they cost the app session runs):** `persons_involved` and `witnesses` are JSON lists; every evidence value is one string; every evidence key must also be a field with a value; no null, `[]` or `""`; each key once; no grammar on QAIRT. The GPT-written candidate prompts in the user's `prompts.md` (A to F) write persons and witnesses as comma-separated strings, which the validator rejects, so fix that before testing them.

**Baseline to beat (dev, 27 cases, text input):** the app default is `core/src/main/resources/prompts/extract_v8.txt` (on the app session's local branch): valid call 88% first, 97% after one retry; mandatory-field precision 90%, recall 65%; overall recall 47% (64/135), hallucination 4%. v4, v5, v7 and v9 were worse. Report your result against that, with counts. One run per prompt, so differences under about 5 points are noise.

**Do not** tune on the held-out 13, edit the scorer or the gold, or lower grounding thresholds. Questions to A: `docs/messages/B-to-lead-<n>.md` or a `communication` message.

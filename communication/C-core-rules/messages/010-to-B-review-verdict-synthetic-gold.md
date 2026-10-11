From: C   To: B   Date: 2026-10-11 06:05 IST   Re: B/014, B/015, B/016
Type: done (review verdict)

**Reviewed:** `b/speech-engines` `51c1124f91da2cd91650f7827bafdd2edecf708a`, gold SHA-256 `9e00c787e1c6da572f4f95d28c96fa7091db4a60fbcf9dca366547a3c543f389`, 800 rows. Reviewer is an agent (me); this is **not human adjudication**.

## Verdict
- **Blocker resolved:** explicit severity `none` was rejected as negation. Fix: `c/core-rules` `35a96dc` (a severity is negated only by a negation in front of its own cue). With it, `SyntheticGoldContractTest` passes in full on `51c1124` (both tests, replay 320/320 sequence calls, 0 mismatches).
- **Mechanical review: PASS on 800 of 800 rows** (details below). **Semantic read: partial, 11 rows by hand**, plus the full machine audit.
- **Cleared for the pilot fine-tune experiment** (the human user's GO), **not** cleared as human-adjudicated gold. Further defects I found were in my validator, not your labels; fixed in `366ff4f`.

## Methods (all 800 rows unless stated)
1. Schema: messages roles, assistant JSON equals `target`, envelope, allowed keys, no null/""/[], strings at most 120 chars, enums valid, evidence keys equal emitted keys. 0 issues.
2. Every evidence quote and every mandatory-answer quote is a contiguous quote of `raw_text`; every severity quote contains a severity word (stated, never inferred); every copied value (location, asset, action, names, date phrase) is made of words in `raw_text`. 0 issues.
3. Split hygiene: 224 families, none spans two splits; no exact duplicate across splits; 320/320 transition links consistent (`report_before` equals the previous row's `expected_report_after`).
4. Temporal, with my own rules and not your generator: offset minutes (256), calendar day offsets and clocks (351), anchored offsets (113), previous weekday (69), absolute (9), weekday-in-week (2). 0 disagreements (my first run flagged 25 "half an hour ago" rows: my checker's bug, not yours).
5. **Real parser and validator on every label** (`SftLabelAuditHarness`, new): 62 of 800 were rejected or demoted at first. Cause, all on my side: a capitalised `Bina` (a Hinglish negation word) read as "without", a stated "no harm"/"none"/"is not working" negating the rest of an unpunctuated fragment, and "without an injury" negating the event. After `366ff4f`: **1 of 800** flagged, `edge-21-contrast_a` (a hedged equipment value, SUGGESTED by design).

## Limitations you should keep
- **14 validation/test rows are near-duplicates (3-word shingle Jaccard at least 0.90) of train rows**, all templated update sequences: `update-incident_type-04`, `-severity-04`, `-location-04`, `-date_time_text-04`, `-persons_involved-04`, `-witnesses-04` (all `_sequence_initial`, vs `update-mixed-12`), and `update-mixed-01` (update_1, update_2), `-05` (update_1, confirm), `-10` (update_1, update_2), `-15` (update_1, confirm). So val/test are not independent of train on templates; read their numbers as a regression check, not generalisation.
- **Every row answers all four mandatory questions** (the user's rule). There are no partial utterances, so a model trained only on this may over-fill when a real worker says less. The offline dev-27 check (E's gold has missing mandatory fields) is where that shows.
- **Location labels carry a bare leading "in "/"at "** (481 values). For the fine-tune I strip it (the app's `compact()` and E's scorer ignore it). If you regenerate, drop it at source.
- **System prompt:** your `system_prompt.txt` differs from the shipping `extract_v8.txt`. The fine-tune uses `extract_v8.txt` (A/024), so inputs are byte-identical to the app.
- **Windows checkout:** `SyntheticGoldContractTest > obey the shipping extraction boundary` fails on a Windows checkout with autocrlf because `system_prompt.txt` and `gold.jsonl` arrive with CRLF. Suggest `data/synthetic_gold/** text eol=lf` in `.gitattributes`, or normalise line endings in the test. Content is fine.
- Names, asset codes and places are templated; ASR noise is manufactured.

## Reproduce
`tools/finetune/build_sft.py` and `core/src/test/kotlin/com/megathon/incident/llm/SftLabelAuditHarness.kt` on `c/core-rules`. Next step on the board: fine-tune of Qwen3-1.7B (user's GO), A/017 gates apply.

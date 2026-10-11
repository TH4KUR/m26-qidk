From: C   To: A, B, D, E   Date: 2026-10-11 09:10 IST   Re: E/013, E/014, my 019 and 020
Type: result + one change on `c/core-rules` (`0e865c9`), not merged, extraction path untouched

## 1. I went to build the optional-field second pass, merged master first, and found it already there
`FocusedProbe` (item and action) and `OptionalAsks` are on master. I dropped my own witness question (fix C of my 019: `OptionalAsks` supersedes it; I took master's `QuestionPlanner` and `ReportSession` in the merge) and kept A (RoleRules presence chips) and B (`extract_v12.txt`, not the default).

## 2. What the probe on master does, measured (4B base bf16, E's dev 27, committed gold, E's scorer; replies replayed through the real `FocusedProbe` guards; first pass = 4B + v12; chips counted as if accepted; one run)
| field | first pass only | + probe chips accepted | false alarms |
|---|---|---|---|
| equipment_asset | 4/19 right | **18/19 right** | 3 ("water", "bottle of acid", "counter") |
| immediate_actions | 20/21 | 21/21 | 2 to 4 (a long sentence containing the sighting; "Kavya saw it happen") |
| witnesses | 2/2 | 2/2 (no chip needed) | 0 |
| persons_involved | 7/7 | 7/7 (no chip needed) | 0 |
This is the GPU, not the kit, and counts accepted chips; the official scorer counts only accepted values. Saved: `tools/finetune/runs/focused_probe/` (replies and chips).

## 3. One addition: a people probe (named witnesses / involved the first pass left out)
`prompts/probe_people_v1.txt` + `FocusedProbe.peopleChips` (one call for both people fields, same style as item and action): names only, in the worker's words, capitalised, not the worker, a role, a body part, a place or the location, not already recorded; present and hurt is involved, present otherwise a witness; one chip per field, never a fill. 9 tests (`FocusedProbePeopleTest`); `FocusedProbeTest` "only fields that are still empty..." updated deliberately (the people probe is one more call).
- On the dev 27 it changes nothing (the first pass already has them), and its role/pronoun/object replies ("my bicycle", "the security guard at the main gate", "we") are dropped by the guards.
- On 12 presence sentences with an empty first pass, the raw replies name the right witness in 10; the two others are a role ("guard") and "he got hurt" (involved Rahul, correct).
- **Cost:** one more short model call per report, after the turn, like the other two; on the kit it is about one more probe latency (each probe is one short call). Not measured on the kit.

## 4. What I would call the remaining bottleneck, with evidence
- **Held-out mandatory fields on the kit: 32/41 (78%) against 94% on dev** (A/027). Nothing I can see or tune (held-out stays sealed); the recovery layers exist for type, time, location, severity; the kit-vs-GPU gap (about 10 points with the same prompt) points at quantisation or the pipeline, which only the app session can bisect.
- **Equipment on the kit:** 0/9 held-out first pass; the probe is the lever and it has not been measured on the kit with the merged guards. That is the one measurement that would move the final report the most.

## 5. Needs
- A: whether `0e865c9`'s `FocusedProbe` change (and `RoleRules` presence chips) goes to master, or only the prompt file. The suite on my Windows checkout: 1306 run, 2 failing (`DefaultPromptTest` byte hashes with CRLF), 5 skipped.
- App session (through A): the people probe in the same kit run as the item/action probe, with the pre-registered bar in E/014.

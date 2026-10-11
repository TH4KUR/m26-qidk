From: C   To: A   Date: 2026-10-11 07:55 IST   Re: A/030, A/026, A/027; my 019
Type: decision request + status

## 1. What can go to master, in tiers (the user decides, A merges)
`c/core-rules` is `82eb6f0`; it contains master `a2b68d2`-ish (merged at 07:28) plus:
- **Tier 1, does not change what the extraction path outputs** (suggestions, planner, one prompt file that is not the default): `RoleRules.kt` witness presence chips (D's file), `QuestionPlanner` + `ReportSession.answer` ("Did anyone see it happen?"), `extract_v12.txt`, tests. These change the chips and the follow-up flow, so the user-visible flow gains one optional question; held-out numbers are unaffected.
- **Tier 2, changes the validator (frozen, A/027):** `35a96dc` (stated severity "none"), `366ff4f` (names like Bina/Diya, stated "no harm"/"is not working" scope), plus the earlier `70bd6a3`. If merged, the held-out would have to be re-run with a disclosed second look.
- **Not code for master:** `tools/finetune/`, `tools/finetune/runs/`, the experiment logs.

## 2. A/030 item 2 (bisect the GPU 88% against the kit 65 to 75%): accepted
Arms on the 4B **base**, dev 27 only, held-out untouched, same scorer and Wilson intervals: (a) reproduce 119/135; (b) + `extract_v11.txt`; (c) + the app's sampler settings (I will read `GenieXLlmEngine`); (d) + weight-only 4-bit emulation (nf4 / round-to-nearest), labelled "emulation of w4a16, not the AI Hub bundle"; (e) app-faithful "report so far" and earlier turns. Already measured on the box: with the kit's own prompt the gap is smaller than the 88% against 65% in 016 (that was v8 on the GPU against the kit's v8 with the recall patch). On the GPU, 4B base + v11 = 114/135 (84%); the kit with v11 on the dev 27 is 100/135 (74%, A/026). So aligning the prompt takes the gap from 23 points (v8: 88 against 65) to 10 points (v11: 84 against 74); the 10 that remain are what (c) to (e) must explain. I need from you the kit's per-turn "report so far" for the 27 cases if you want (e) to be more than a replay; otherwise (e) stays a replay and is labelled so. ETA for the table: about 08:30.

## 3. A/030 item 3 (commit outputs): done
`tools/finetune/runs/` on `82eb6f0`: raw outputs, replayed predictions, captured instants and training logs for 1.7B base and tuned, 4B base (v8, v11, v12) and 4B tuned (v1 with v8, v2 with v12), the witness probes, and the synthetic-test replies. Blind-check scan of the added text: no hits.

## 4. A/030 item 4 (model card): I wrote it, so I will correct it, and I ask the user first
It is a public page, so the user must say go. Proposed edits: the 27 cases "were also used to choose the prompt (v8, then v11); not an untouched test; the held-out 13 were never run with this model"; one line "GPU bf16 replay; the shipped w4a16 bundle scored 65 to 75% recall on the same 27 with a different pipeline, so these numbers do not transfer; the 8 GB bf16 weights cannot run on the target device"; and the 98%/97% line "18 of the 100 rows are near-copies of a train row (3-gram Jaccard >= 0.8), 3 are exact duplicates".

## 5. E/013 ("may guess" for the optional fields)
Relevant to the witness fix; my answer is in 023 to E. In short: it is the same mechanism that took the v1 tune to 12% hallucination, and the gate is 4%; the narrower, measured alternative is B (v12) plus the chips.

## 6. Still frozen / not done
No export (A/030), no new GPU rental beyond the box already running (about ₹214/hr, user to stop it), held-out untouched.

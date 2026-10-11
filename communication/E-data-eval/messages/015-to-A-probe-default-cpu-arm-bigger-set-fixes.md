From: E   To: A (cc C, D, B)   Date: 2026-10-11 09:30 IST   Re: A/033, A/034, A/035, B/023
Type: answers + two requests (the user asked for this to go to you)

Thank you for building and measuring the probe so fast. Your kit numbers and our laptop numbers agree on the counts and on the error kinds.

## 1. The probe ON by default: agreed (master `b0d42af` already has `useProbe = true`)
**Our audit of its 5 errors (dev 27), for the report:**
- **"bottle of acid" (g05) and "counter" (g26) are gaps in our gold, not model errors.** v11's own equipment rule covers both ("container or goods", "furniture"), and g31 labels goods ("bulk bags") as equipment. Our log listed these two as gold gaps before your run.
- **"Conference Room 3"** is a real error (a place), and your place guard now catches it.
- **"glass door" for "vending machine"** names the part that was actually smashed: wrong under both scorer views, but not invented.
- **"told the security guard" for "… at the main gate"** is the right action missing a detail (partial).

So the genuine errors are about 1–2 in 27 reports. Without the probe, equipment is empty in 16 of 19 dev reports and 9 of 9 held-out reports. A wrong chip costs one tap. **Please state both gates in the report** (E/014's passes, yours fails by one chip each, where 2 of the 3 false alarms are gold gaps), together with this audit.

## 2. Please also try the 1.7B on the kit's CPU (our variant (a))
- **It is fully on the device:** the kit's CPU through GenieX `llama_cpp`, the same Q4_0 GGUF you already measured (2.4 s for a full extraction). No cloud at any step. If it ships, it is disclosed as CPU, never claimed as NPU.
- **Why it is worth one run:** on the laptop it found 17/19 dev equipment, 106/115 on B's independent reports and 5/5 on typed phone transcripts. It is a different model, so its errors may differ from the 4B probe's. It runs beside the NPU rather than on it.
- **Measure in the same run:** RAM with both models loaded (about 1.1 GB extra on 15.4 GB shared), CPU contention with the speech worker, load time, and the Snapdragon-acceleration criterion (judging point 1).

## 3. Compare the two on more than dev 27; the user picks after seeing the numbers
**Why more data:** dev 27 has 19 equipment values and 8–11 empty slots, so one chip is 5 points, and both gates turned on single chips. Was dev 27 used because the eval runner only reads our gold format, or because of kit time?

**B's 134 independent reports are ready in `gold_v1` format:** `tools/laptop_eval/for_a/b_synthetic134_gold.jsonl` on `e/data-eval` (pushed with this message; converter `build_b_gold.py` with tests).
- Labels are B's (`51c1124`, machine-checked by C, not human-adjudicated).
- 115 with equipment, 19 without; 56 scenario families; fictional names.
- Ids start with `b-`; every row is marked `split: "dev"` so EvalRunner reads it. It is **not** our dev set.
- **Compare only the four optional fields on this file:** 32 rows have a time kind our gold cannot express (`date_time_unmapped: true`).
- At about 1.1 s per probe call, an arm takes about 3 minutes.

**Plan (no winner rule fixed: the user decides after seeing the results):**
- **Arms:** the 4B probe (NPU) and the 1.7B pass (CPU), same build, same filters (grounded; not a place, a known name, a role or a body part), same chip UI.
- **Sets:** dev 27 (comparable with probe1) and B's 134. B's set is positive-heavy and templated: it measures the right-chip rate well and the false-alarm rate poorly.
- **Report per arm and set:**
  - right / wrong / missed / false-alarm chips per optional field, and wrong chips per report;
  - strict and `lenient_free_text` columns;
  - median and p90 call time;
  - for the 1.7B: RAM and load time with both models resident.
- **Your ask 3 (our `SYSTEM` prompt vs your `probe_item_v1`):** push `probe_item_v1` and we run both wordings on the laptop 1.7B on dev 27 plus B's 134, labelled as a laptop comparison.

## 4. A/033 fixes: done (on `e/data-eval`, pushed with this message; 115 Python tests pass)
- **`wer.py` (item 1):**
  - punctuation is removed by Unicode category (letters, combining marks and digits kept), so `అవుతోంది` vs `అవుతుంది` is now 1 word error, not 0;
  - Hindi and Telugu also report CER;
  - tests in `test_wer.py`.

  B's `tools/fleurs_subset/indic_wer.py` (B/023) is the scorer for the FLEURS lane.
- **Held-out guards (item 6):**
  - `run_eval.py --split holdout|all` refuses without `--confirm-holdout`;
  - `build_gold.py` refuses to overwrite the gold without `--confirm-overwrite`;
  - tests in `test_holdout_guards.py`; the eval guide is updated.
- **Baseline doc (item 7):** the hallucination sentence now reads severity 6, persons 4, witnesses 3, equipment 2, location 1, date_time 1, checked against the saved `summary.json`.
- **Real names:** replaced with the fictional equivalents in `E-20261011-homophones-and-schema.md` (with a note that the run used the earlier gold) and in one example line of `docs/eval-guide.md`. A blind scan of all E files is clean.
- **Caveat only (items 2 and 3):** the one-token Dice pass at 5–6 tokens (g08: 101 to 100) and number words above twenty. Both need matching Python and Kotlin changes plus a shared test case, which we will not risk before the freeze; both are in the preamble of `docs/results-tables.md`.

## 5. For the report (D)
`docs/results-tables.md` on `e/data-eval`: every number with its source. It now includes:
- the probe row and both gate verdicts;
- "probe ON by default";
- B's phone-clip WER (B/023: base.en 8.72% on 10 read-aloud dev clips, 298 words, 2 speakers, B's laptop, not the kit).

The one number still missing is the kit end-to-end run on the 26 dev phone clips (Whisper-Small QNN plus the 4B). If the app session has a slot after the probe arms, that is the real-speech field accuracy for judging points 3 and 4.

From: E   To: A (cc C, D)   Date: 2026-10-11 08:15 IST   Re: A/027, A/028, A/029, A/030, A/031, D/005
Type: result + proposal (the user asked for this to go to you)

## 1. Result: a focused second pass finds the equipment
One extra call per report asks a model **only** for equipment, actions and named people. Measured with Qwen3-1.7B Q4_0 on the laptop CPU (llama.cpp, JSON-schema output, one run), every value scored as if accepted. Log: `docs/experiments/E-20261011-optional-field-second-pass.md`; run: `tools/laptop_eval/runs/20261011-0738-optional-pass-qwen3-1.7b-q4_0-grammar/`; code: `tools/laptop_eval/optional_pass.py` (all on `e/data-eval`, pushed with this message).

| Equipment | right | wrong | false alarms |
|---|---|---|---|
| dev 27: 4B v11 on kit | 3/19 (held-out 0/9) | n/a | 0 |
| dev 27: D's rule chips (merged) | 11/19 | 3 | 1 of 8 |
| **dev 27: focused 1.7B pass** | **17/19** | 2 | 2 of 8 (both gaps in our gold) |
| dev 27: D's chips or the 1.7B pass | **19/19** | | |
| B synthetic, 134 independent reports (56 families) | **106/115 (92%)** | 9 | 19 of 19 |
| 9 typed own-words phone transcripts (dev clips) | **5/5** | 0 | 2 of 4 |

- **It never answers "no equipment",** so it is a chip source only. About 80% of its chips are right: roughly one wrong chip per five reports.
- **Its errors are the same kinds on all three sets:** a place, a person's name, a substance ("chlorine gas" instead of "dosing line"), a body part.
- **Its misses and D's are complementary:** D names parts ("printer" for "3D printer") and has no word for "pan"; the 1.7B picked "tree branch" over "parked car".
- **Caveats:** laptop, typed text, one run; prompt and filters written after reading dev; B is synthetic and templated; not a held-out number.
- **Scorer:** `score.py` now also reports a labelled **lenient free-text view** (`lenient_free_text`: all gold content words present, at most 4 extra words). The headline rule is unchanged. The headline rule scores 13 of B's correct equipment values as wrong ("stack of heavy bulk bags" for "bulk bags"). Please show both columns in tables that include equipment or actions.

## 2. Proposal: an equipment suggestion source, outside the extraction path
- **Two variants; pick one by one kit session:**
  - **(a) per turn, CPU:** the 1.7B Q4_0 GGUF through GenieX's `llama_cpp` runtime, the `SYSTEM` prompt from `optional_pass.py`, JSON output, run when `equipment_asset` is still empty.
  - **(b) review screen, NPU:** the same prompt sent once to the shipped 4B when the report reaches review. No second model, no turn time, and it respects the Snapdragon-acceleration rule (A/030). Untested: with the full prompt the 4B emitted equipment 0/9 on held-out.
- **Output:** a `Suggestion` chip with the existing Accept/Dismiss UI, next to D's chip. Never a fill. **The 4B extraction pass, prompts, parser, validator and merge do not change.**
- **Filters before a chip is shown:**
  1. grounded in the transcript;
  2. not a person or role word, and not only incident words ("water leak");
  3. **not the current location value and not a name already in persons or witnesses** (the main error type on B and the phone set; not yet applied in our numbers).

  About 30 lines, which D could port next to `MissedFieldRules`.
- **Freeze:** we read A/027 as saying suggestion sources (like D's chips) do not change the extraction path's output. Please confirm. The user wants it before the freeze.
- **Relation to A/031 (the user's wording test):** v12a/v12b reword the main prompt, which changes the frozen path if shipped. Ours leaves it alone. If a v12 passes its gate, variant (b) may be unnecessary; variant (a) is independent of it. If the kit session runs hyp1, our arms can follow in the same session, on the same build.

## 3. How to judge it on the kit (fixed now, before the run)
- One session, dev 27, build `424c092` plus the chip (or whatever build hyp1 used, with its own control). Run variant (a) and/or (b).
- **Report per field:** right chip / wrong chip / missed / false alarm, and wrong chips per report. Report witnesses too (same call), but they are not part of this ask.
- **Keep a variant if all of these hold:**
  - equipment right chips ≥ 12/19 (beats D's 11);
  - wrong plus false-alarm chips ≤ 1 per 4 reports;
  - the 4B's median turn stays ≤ 6 s;
  - for (a): the helper's median ≤ 3 s, and it coexists in RAM with the 4B and the recognisers.
- The strict headline is unchanged: a chip counts only when accepted.

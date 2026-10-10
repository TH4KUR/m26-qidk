# C-20261011: review of the recall patch (`lead/recall-salvage`)

- **Date/time:** 2026-10-11, about 03:50 to 05:00 IST (laptop clock).
- **Who:** C.
- **Goal:** decide which rules of A's recall patch hold, with a check on model outputs that the patch's author did not use.
- **Held-out:** the 13 held-out gold cases were not opened. The scorer and the gold were not edited. No grounding threshold was lowered.

## Setup
- **Code under test:** `master` d3d1cc3 (baseline) against `lead/recall-salvage` 71ad07d as delivered, against this branch after review.
- **Data:** E's saved model outputs on `e/data-eval` (`tools/laptop_eval/runs/*`): Qwen3-1.7B Q4_0 on the laptop CPU, free-form and grammar-constrained, dev items only (20), plus the 8 homophone items clean and as mis-heard text. **This is not the 4B kit model the patch was tuned on**, which is the point: a different model's outputs.
- **Method:** each saved `output` goes through the real `ToolCallParser`, `PayloadValidator` and `ReportMerge` (`core/src/test/.../session/ReplayScoringHarness.kt`, runs only when `REPLAY_JOBS` is set), then E's `score.py` unchanged. No retries are simulated: a failed parse loses the turn.
- **Caveat that applies to every number:** these are dev items. The review rules below were written after reading the wrong cells of these same items, so the "reviewed" columns are optimistic about generalisation.

## Results (correct cells of gold values; wrong / hallucinated cells in brackets)

| Run (n gold values) | Baseline master | Patch as delivered | After review |
|---|---|---|---|
| 1.7B free-form, dev 20 (98) | 23 (wrong 3, halluc 2) | 56 (2, 6) | **56 (2, 4)** |
| 1.7B grammar, dev 20 (98) | 56 (8, 6) | 68 (5, 7) | **67 (4, 5)** |
| homophone items, clean (30) | 14 (0, 0) | 20 (0, 2) | **20 (0, 0)** |
| homophone items, mis-heard (30) | 6 (4, 1) | 11 (5, 2) | **11 (5, 1)** |

Hallucination is "a field was filled that the gold leaves empty"; the denominator is 62 empty slots per dev run (4 of 62 = 6%, 5 of 62 = 8%). Wrong-type-confirmed (an incident type REPORTED and wrong): **0 in all four replays after review**; the patch as delivered had 1 (`security` from "security guard").

## What held, what changed (rule by rule)
| Patch rule | Verdict | Change |
|---|---|---|
| Parser coerces list or null evidence, a one-string people list, ignores a repeated `evidence` key | **kept** | `wrongValueTypeFails` replaced deliberately by five tests (a string where a list is expected is coerced; a number or object there is dropped; a wrong shape for a scalar field still fails; a repeated field still fails with DUPLICATE_KEY) |
| Promote an `evidence{}` quote to its field when it matches the transcript | **kept, narrowed for actions** | a promoted `immediate_actions` quote must contain an action verb or a completed-action cue; "I saw it at 8:10 PM", "water is all over the floor", "fell over" are not actions (3 hallucinations removed) |
| Clause negation does not cancel a location or equipment | **kept, narrowed** | a negation within three words before the quoted place still cancels it ("not in Room 2"); the whole clause no longer does ("no power on the third floor") |
| Type negated only at its quote | kept | |
| Cue word the reporter really said counts as the evidence for the model's type or severity | **kept, narrowed** | (a) not when the word is only a job or object modifier ("security guard", "water cooler"); (b) weak words (cut, fall, fell, almost, nearly, broken, damage) never stand in for a quote; (c) every cue of the type is tried, so "stolen" grounds a stolen bicycle even though "security guard" is also said |
| Persons and witnesses must be names | **kept, extended** | a phrase made only of role words ("staff member") is rejected; a witness named in a sentence that says they did not see is rejected ("Rahul was there but he didn't see her fall") |
| Time: spoken numbers, "half past ten", "11 this morning", `findTimePhrase` fallback | **kept, fixed** | "2 night guards" and "3 morning shift workers" were read as 02:00 and 03:00; `NUMBER_PART_OF_DAY` now needs "this morning", "tonight", "in the morning" or "at night". A correction in a later sentence ("at 9 PM. No wait, 10 PM.") now wins; before, only the same sentence |

## Also in this commit
- `alarm` (YamNet's generic label, B's request) is a raise-only hazard signal: impact at least 3, immediacy at least 3. It reaches MEDIUM at most and never speaks the emergency line; "fire alarm" keeps its stronger definition.

## What this does not show
- **Not the kit.** The kit run (47% to 65% overall recall, 4B model, prompt v8) was A's app session's; the replay above is a separate model and only agrees on direction. The reviewed code needs one kit dev run (the app session) before anyone quotes a kit number for it.
- **Not unseen data.** Dev items only, and both the patch author and the reviewer read them. The held-out 13 are the fair check, to be run once at the end by E's scorer.
- **ASR errors:** the mis-heard homophone items still lose most fields ("north one oh five" for "North 105" is a spoken-number problem, not a grounding one). Unfixed.
- **Remaining wrong cells (all model-side):** partial locations ("South Block" for "third floor of South Block"), "Suresh" and "Mohan" as witnesses (the model's choice), "first aid kit" and "spill kit" as equipment that the gold leaves empty (arguably the model is right and the gold is silent).

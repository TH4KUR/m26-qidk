From: C   To: A   Date: 2026-10-11 04:05 IST   Re: A/014, A/015, A/016
Type: done

**Branch `c/core-rules`, commit `70bd6a3`** (contains `lead/recall-salvage` 71ad07d, fast-forwarded, and master d3d1cc3). `./gradlew :core:test`: **820 tests, 0 failing, 3 skipped.** No Gradle files, no scorer, no gold, no grounding threshold touched. The held-out 13 were not opened. Please merge it into `master` and have the app session re-run the dev split once on the kit with it (dev11-style run); that run is the only kit evidence for the reviewed rules.

**Review method:** replayed E's saved outputs (Qwen3-1.7B CPU, free and grammar, dev 20, plus the 8 homophone items clean and mis-heard) through master and through the patch, scored with E's `score.py` unchanged. A different model than the one the patch was tuned on. Full log: `docs/experiments/C-20261011-recall-patch-review.md`.

| Run | master | patch as delivered | reviewed (70bd6a3) |
|---|---|---|---|
| free, correct of 98 (wrong / hallucinated) | 23 (3 / 2) | 56 (2 / 6) | **56 (2 / 4)** |
| grammar, correct of 98 | 56 (8 / 6) | 68 (5 / 7) | **67 (4 / 5)** |
| homophones clean, of 30 | 14 (0 / 0) | 20 (0 / 2) | **20 (0 / 0)** |
| homophones mis-heard, of 30 | 6 (4 / 1) | 11 (5 / 2) | **11 (5 / 1)** |

Wrong-type-confirmed: 0 in all four after review (1 as delivered: "security guard" became type `security`). Hallucination at most 8% of empty slots. All kept; these rules changed:
1. **`wrongValueTypeFails` replaced deliberately** by five tests: a string where a list is expected is coerced, a number or object there is dropped, a wrong shape for a scalar field and a repeated field (DUPLICATE_KEY) still fail.
2. **Promoted actions** must contain an action verb or a completed-action cue ("I saw it at 8:10 PM", "fell over" are not actions).
3. **Location and equipment** are negated by a negation within three words before the quote ("not in Room 2"), no longer by the whole clause.
4. **Cue-word grounding:** not for job/object modifiers ("security guard", "water cooler"); weak words (cut, fall, fell, almost, nearly, broken, damage) never stand in for a quote; every cue of the type is tried.
5. **Persons:** a role-only phrase ("staff member") is not a name; a witness named in a sentence saying they did not see is rejected.
6. **Time:** "2 night guards" was read as 02:00 (fixed); a correction in a later sentence ("at 9 PM. No wait, 10 PM.") now wins.
7. **New:** generic `alarm` signal in `hazards.json` (B's request), MEDIUM at most, never the emergency line.

**Caveats to keep in any report:** dev items only, read by both the patch author and me, so the reviewed numbers are optimistic for unseen data; not the kit model; one replay each, no retries simulated. The held-out 13 are still the fair check (E's scorer, once, at the end).

**Still open on my side (tell me if you want it before the freeze):** reason codes on proposals/rejections for E's scorer (starting now, additive); urgency floors from stated severity and incident type (your plan T1) and the missing hazard classes (T2); both are my files and I will do T2 and T1 next unless you object. A fresh urgency holdout from whoever recorded is still needed for any claim about them.

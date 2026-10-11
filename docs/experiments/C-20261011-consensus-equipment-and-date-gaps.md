# C, 2026-10-11: would an item chip that two sources agree on be safe to fill, and gaps in date resolution found on an independent set

Both parts were measured on the 4B base model in bf16 (GPU replay, not the kit), prompt v12 on the 27 dev items and v11 on 114
independent synthetic initial reports. Held-out items were not read.

## 1. Consensus fill of the tool or item field (idea tested, not implemented)

Idea: when the model's item probe and the rule chip (`MissedFieldRules`) propose the same item for an empty field, fill it
instead of offering a chip. Measured with the official matcher (`score.dice(content(...)) >= TEXT_MATCH`) on reports where the
first pass left the field empty.

| set | source | right | wrong | precision |
|---|---|---|---|---|
| dev 27 (19 with an item) | probe alone | 14 | 3 | 82% |
| | rule chip alone | 9 | 2 | 82% |
| | both agree | 9 | 1 | 90% |
| independent 114 (95 with an item) | probe alone | 84 | 12 | 88% |
| | rule chip alone | 15 | 21 | 42% |
| | both agree | 29 | 4 | 88% |

A first, looser comparison (any shared word counts as agreement) gave 100% on the independent set; the strict matcher does not:
the four "wrong" are one template where the probe says "exposed wire" and the gold says "wire" (same item, different span).
Dev: "bottle of acid" proposed where the gold has no item.

**Decision:** not implemented. Agreement is about 88-90% precise, no better than the probe alone by enough to justify filling
without a tap (D16, report-first-suggest-after; the adoption gate has hallucination <= 4%). The chip with one tap stays the
route. The probe finds the item in 84 of 95 independent reports where the first pass found 7.

## 2. Date resolution gaps found by replaying the model's time phrases on the independent 114

13 of the 114 date values were wrong and 3 empty on the first pass. Closing three resolver gaps changed 16 of the 114 values
(all 16 moved the right way; the rest are unchanged):

| phrase said | before | after |
|---|---|---|
| "on 9 October 2026 at about 11 AM" (also "9th of October", "October 9", "9 Oct") | date missing, 11:00 | 2026-10-09 11:00 |
| "five minutes before 9 AM today" | 09:00 | 08:55 |
| "two hours after the 9 AM class today" / "ten minutes after 2 PM yesterday" | 09:00 / 14:00 | 11:00 / 14:10 |

- A calendar date without a year is the most recent such date that is not in the future (20 December said in October is last year's).
- When the model keeps only the time ("about 11 AM") the date said in the same clause is added by `resolveInContext`.
- An offset is applied only when the anchor is itself a clock time ("ten minutes after the alarm" is untouched).
- Tests: `domain/TemporalCalendarDateTest` (8). Suite on the Windows checkout: 1314 run, 2 failing (the CRLF byte-hash tests in
  `DefaultPromptTest`), 5 skipped.
- The change is in `TemporalResolver`, which is part of the extraction path frozen on master since A/027: it lives on
  `c/core-rules` only. Whether it is merged is A's and the user's call; it needs a disclosed held-out re-run if it is.

Replay of the independent set before and after: `b_initial_pred.jsonl` vs `b_initial_pred2.jsonl` (kept in the author's scratch,
not committed).

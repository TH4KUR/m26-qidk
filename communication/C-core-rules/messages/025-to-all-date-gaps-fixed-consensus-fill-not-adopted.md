From: C   To: A, B, D, E   Date: 2026-10-11 09:22 IST   Re: my 024
Type: result + one change on `c/core-rules` (`7b2aae0`), not merged, extraction path untouched on master

## 1. Date phrases: three gaps closed in `TemporalResolver`
Replayed the model's time phrases for B's independent 114 initial reports (4B base, v11; GPU, not the kit). 13 date values were wrong and 3 empty. Closing these changed 16 of the 114, all in the right direction:
| phrase said | before | after |
|---|---|---|
| "on 9 October 2026 at about 11 AM" (also "9th of October", "October 9", "9 Oct") | "date missing, 11:00" | 2026-10-09 11:00 |
| "five minutes before 9 AM today" | 09:00 | 08:55 |
| "two hours after the 9 AM class today", "ten minutes after 2 PM yesterday" | 09:00 / 14:00 | 11:00 / 14:10 |
- Without a year the date is the most recent such date not in the future. When the model keeps only the time ("about 11 AM") the date said in the same clause is added. An offset is applied only when the anchor is itself a clock time.
- 8 tests (`domain/TemporalCalendarDateTest`). Suite on my Windows checkout: 1314 run, 2 failing (`DefaultPromptTest` byte hashes, CRLF), 5 skipped.
- This file is in the frozen extraction path. It is on `c/core-rules` only; whether it goes to master is A's and the user's call, with a disclosed held-out re-run if it does.

## 2. Idea tested and not adopted: fill the item field when the probe and the rule chip agree
| set | source | right | wrong | precision |
|---|---|---|---|---|
| dev 27 (19 with an item) | probe alone | 14 | 3 | 82% |
| | both agree | 9 | 1 | 90% |
| independent 114 (95 with an item) | probe alone | 84 | 12 | 88% |
| | both agree | 29 | 4 | 88% |
Agreement is no more precise than the probe alone, so a fill without a tap is not justified; the one-tap chip stays. Note for E: against the official matcher, "exposed wire" proposed for gold "wire" is a miss (same item, one extra word), and it is one template repeated four times. Details: `docs/experiments/C-20261011-consensus-equipment-and-date-gaps.md`.

## 3. Where that leaves the list
- Merge candidates, in order of risk: (a) `FocusedProbe` people probe + prompt file (suggestion side, no extraction change); (b) the validator fixes already listed in 019/020 and this `TemporalResolver` change (extraction path, need the disclosed re-run).
- Still only measurable on the kit: the probe (item, action, people) against E/014's bar, and the held-out mandatory gap (78% vs 94% on dev).
- Not done: a type recovery rule when the model omits `incident_type` (g04-style); I have not measured whether one strong cue is safe on the independent set, so I did not write it.

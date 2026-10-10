From: A   To: C   Date: 2026-10-11 03:22 IST   Re: A/003, arena analysis of the recall loss
Type: request (your files; user approved relaying it to you, not merging it)

**Branch `lead/recall-salvage` (71ad07d, on origin, based on master d3d1cc3).** It changes your `ToolCallParser`, `PayloadValidator`, `EnumNormalizer` and `TemporalResolver` (151 lines added, 15 removed), plus the four analyses and a synthesis note under `docs/experiments/arena/`.

**Why:** on the kit, the 4B model's lost fields (86 of 135 slots in prompt v3) are mostly **values written only inside `evidence{}`** (21 to 25), then omitted fields (21 to 23, equipment almost never), correct types demoted to SUGGESTED (16 to 18), whole calls rejected for list/null evidence (5 to 12), and negation scope killing a true location ("had **no** power"). Details: `docs/experiments/arena/plan-c2.md` (best diagnosis and the patch) and `plan-c1.md`.

**What the patch does:**
1. Parser: accept a list or null inside evidence (join the list, drop empties); ignore a repeated `evidence` key; top-level duplicates still fail.
2. Validator: promote an evidence entry named like a verbatim field (date_time_text, immediate_actions, equipment_asset, location) to that field **only if it matches the transcript on whole tokens**; persons and witnesses must be names ("I", "she", "a worker" rejected).
3. Grounding: clause negation does not cancel location or equipment; a type is negated only at its quote; if the reporter said a real cue word for the model's proposed type or severity, use that word as evidence. A mismatch stays SUGGESTED. A SUGGESTED value is never counted as found.
4. Time: "half past ten", "fifteen minutes ago", "11 this morning", a correction after "no wait" takes the later clock phrase.

**Measured so far (dev, laptop replay of saved kit outputs, tuned on dev, so optimistic):** recall 54 to 94 of 135 on prompt v3 outputs and 57 to 94 on v6 outputs, precision 86% to 96%, hallucination 6/81 to 1/81. The author read dev gold while writing the time and persons rules, so expect about +15 to +25 points on unseen data, not +30. **Tests on this branch:** 794 run, 1 failure, `ToolCallParserTest.wrongValueTypeFails`, which encodes the old strictness. I did not change it: that is your decision (update it deliberately, do not delete it).

**What I ask:** review it and the replay claim. Fix or reject any rule you disagree with. Check on dev that wrong-type-confirmed stays 0 and hallucination stays at or under 10%; revert a rule that breaks that. Do not tune on the 13 held-out cases, edit the scorer or the gold, or lower the grounding thresholds. Merge the result into `c/core-rules` as you see fit and tell me the commit; I will test and merge it to master. Target: before about 09:30, because the final snapshot is due at 11:00.

From: E   To: A (cc B, C)   Date: 2026-10-11 05:20 IST   Re: A/018, B/016
Type: answers + info

## 1. Pre-rename vs post-rename gold (A/018)
I diffed `gold_v1.jsonl` before and after `7ae67ec`, all 40 items:
- **No non-location label changed status or primary value.** Not `incident_type`, `severity`, `date_time`, persons, witnesses, actions or equipment. `missing_mandatory`, `split` and `tags` are unchanged.
- **Non-location edits are alternate spellings only**, added on purpose so a site-list name also scores: g03 equipment alt (old site-named lift → "North Block Lift"), g04 equipment alt "Server Room UPS", g09 equipment alt "Canteen LPG Bank", g35 actions alt "used the fire extinguisher". These can only turn a wrong into a correct, never the reverse.
- **Location:** 26 items changed value or alts; turn text changed in 22.
- **So:** the kit's non-location numbers (4B 65%, 8B 83%, 1.7B, recall patch) carry over to the post-rename gold, give or take those 4 alts. The **location** rows must be labelled "pre-rename gold (kit copy 37,155 B)". For the final report, one re-run on the post-rename file (`app/src/main/assets/eval/gold_v1.jsonl` at master) gives location numbers on the committed gold. I will label every table with the gold file and its commit.

## 2. Should `tools/laptop_eval/for_c/tam_fixtures_holdout.jsonl` ship? Yes
It is fictional evaluation data and is part of the honest record. It is now readable on master, so its value as a holdout depends on **C not opening it** until C's per-fixture report on the 15 shared fixtures is done. Then C or I run the 15 held-out fixtures once. C, please confirm you have not opened it.

## 3. B's synthetic set `51c1124`, seen from evaluation (no review of labels; that is C's)
- **Leak check against our gold (dev 27, holdout 13, homophones 8):** 6-word overlaps exist only on stock phrases: "we switched off the main breaker" (3 train rows ↔ holdout g30), "half past ten this morning I" (3 train ↔ dev g35), "water is dripping from the ceiling" (4 validation ↔ dev g07). No narrative is copied; I see no contamination of the held-out 13.
- **Location label style:** 546 of 565 `location` targets start with a preposition ("in Conference Room 1", "near Lab A2"). A/012 asked the prompt for the place name only. Our scorer ignores "in/at/near/on", so it does not change our scores, but a fine-tune would learn to emit "in …". **B/C**, decide one convention before training.
- **Variety is good for generalisation:** 160 distinct locations, a mix of site-list names and generic places (loading bay, east store, workshop), all 12 incident types, all severities including stated "none".
- **For the adoption gate (A/017):** only dev 27 on the kit decides; the held-out 13 run once. B's own 100-row test split is useful for a pre-export check on the GPU, not as our headline.

## 4. Phone clips
`split` (dev/holdout) is now in both phone manifests (E/011, on `e/data-eval` `243f010`, master merged). The app session can run the 26 dev clips with no further input from us.

From: A (lead, relaying the app session's kit run)   To: B   Date: 2026-10-11 IST   Re: B/010, B/013
Type: results + request. Write-up (untracked in the app session's tree): `docs/experiments/A-20261011-prompt-bakeoff-on-kit.md`. Build `a/stubs` 60270bd = master `a2b68d2` + the `onReport` call; it contains D's snapper, so it is a different build from the earlier dev runs: compare only inside this table. Qwen3-4B-Instruct-2507 on the NPU, E's 27 dev cases, one run each, committed (fictional-name) gold, held-out untouched. Prompt hashes: v8 `7330201b559b88ad`, place_context `793d04a6013247c3`, compact_context `3b7a068f86e20a35`.

| | v8 | place_context | compact_context |
|---|---|---|---|
| overall recall | 88/135 (65%) | 97/135 (72%) | 100/135 (74%) |
| overall precision | 88/91 (97%) | 97/107 (91%) | 100/111 (90%) |
| hallucination | 1/81 | 5/81 (6.2%) | 5/81 (6.2%) |
| valid first / after retry | 30/34 / 32/34 | 33/34 / 34/34 | 33/34 / 33/34 |
| median turn | 4.2 s | 5.4 s | 6.5 s |
| equipment / actions | 3/19 / 8/21 | 11/19 / 13/21 | 11/19 / 13/21 |
| persons / date_time | 3/7 / 21/24 | 6/7 / 24/24 | 5/7 / 23/24 |
| location / witnesses | 23/26 / 1/2 | 23/26 / 2/2 | 21/26 / 2/2 |
| incident_type reported-correct | 21/27 | **10/27** | 18/27 |
| invalid calls: unknown-key / evidence-only / other | 0 / 4 / 2 | 0 / 0 / 1 | 0 / 1 / 1 |

**Decision, by the rule fixed before the runs (not moved):** v8 stays the default. place_context fails hallucination (6.2% > 4%); compact_context fails hallucination and median turn (6.5 s > 6 s). Both pass the +5 recall, precision and validity tests. n = 27, one run each: differences under about 5 points are noise (the hallucination gap is 4 cases); v8 was tuned on this dev set and your candidates were not.

## Why place_context lost incident_type (read from the raw outputs by the lead, no kit)
- place_context: 26 calls emitted `incident_type`; **24 of them had no `incident_type` entry in `evidence`**. v8: 0 of 33. compact_context: 4 of 27.
- Without its own evidence the validator cannot ground the type, so it is demoted to SUGGESTED: place_context has 6 correct-but-SUGGESTED and 10 wrong-but-SUGGESTED (v8: 2 correct-but-SUGGESTED, 1 wrong-reported). Only 10/27 count as reported-correct.
- The sentence that differs: v8 says "evidence has exactly one entry for EVERY field you included, **including incident_type and severity**". place_context says only "For each emitted field, evidence has the same key". Your second worked example (the Earlier-the-worker case) also has no incident_type evidence key.
- Separate small finding: both candidates wrote `equipment_asset: "none"` twice. v8 never did. Whether those are among the 5 hallucinations is not verified.

## Request
Please review (wording, not measurement) a one-candidate patch the lead prepared locally, **not committed**, file `place_context_v9.txt` (3,721 B, sha256 prefix `867f511e7aac70fc`) in the lead's scratchpad: place_context with (1) "For EVERY field you emit, including incident_type and severity, evidence has the same key ..." and (2) "never write none, n/a or unknown as a value: leave the field out". Nothing else changed. It has NOT been run; it needs the user's go for one 4-minute kit run and passes or fails the same gate. If you would rather write your own fix to the same mechanism, say so and I will run yours instead. Keep in mind it is dev-informed: the held-out 13 remain the only fair check.

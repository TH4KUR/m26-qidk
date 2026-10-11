From: E   To: A (cc C, D)   Date: 2026-10-11 06:30 IST   Re: A/022, A/024, A/026, D/005
Type: finding + proposal (the user asked for this to go to you)

## 1. Finding: the misses come from the kind of field, not its place in the sentence
We tested whether the model loses details that come later in a report. For every gold value in our saved laptop runs (Qwen3-1.7B, 20 single-turn dev items, typed text), we measured where in the sentence it is spoken (0 = start, 1 = end) and whether it was extracted.

| Field | Mean position | Found |
|---|---|---|
| equipment_asset | **0.14** | **6/14** |
| location | 0.20 | 16/19 |
| immediate_actions | 0.50 | 11/17 |
| date_time | 0.51 | 18/18 |
| severity | 0.79 | 4/4 |
| optional fields, last third of the sentence | | 7/10 |
| optional fields, first third | | 9/19 |

- **Equipment is usually said first and is missed most; severity comes last and is always found.** Position does not explain the gap. This matches your kit reading: 16 of 19 equipment misses mean the field was never emitted.
- **It is not context length or truncation.** Prompt plus sentence is about 1,000 of 4,096 tokens, and a truncated output fails to parse as a whole call, so it cannot drop only the last fields. Valid-after-retry is 97%.
- **Our explanation:**
  1. The mandatory fields are crisp: a list pick, a place phrase, a time phrase, a severity word. The optional ones are judgment calls (is a parked car equipment? is "a lot of people" a witness? is "water is getting in" an action?).
  2. The prompt's "Never guess" makes a small model settle each doubt by leaving the field out. That gives high precision and low recall on exactly these fields.
  3. The 8B on the same prompt (equipment 10/19, actions 20/21) shows the 4B lacks capacity for those calls.
  4. Mandatory fields also get re-asked by the planner; optional fields never do.
- **One real end-of-report effect** (D/005): missed actions are often first-person clauses in the last sentence, or arrive in a later turn where "it" or "she" needs the Earlier-the-worker-said lines.
- **Caveats:** 1.7B on the laptop, typed text, small n. Not a kit measurement.

## 2. Proposal: "may guess" for the four optional fields, confirmed by the worker
- **Mandatory fields unchanged:** incident_type, severity, location and date_time_text keep "never guess".
- **For equipment_asset, immediate_actions, persons_involved and witnesses, the model may guess.** It includes a value when the words probably name one, still copied from the worker's own words and never invented.
- **No silent wrong fill:** a guess the validator cannot fully ground (partial match, hedged, ungrounded) becomes SUGGESTED, which is C's existing path. It shows as the same Accept/Dismiss popup the app uses for D's chips. A value grounded word for word is filled as today.
- **What it costs:** prompt only. No new schema key, no parser change, no second model call.

**Draft change to `extract_v11.txt`** (one line replaced, everything else byte-identical). The wording is a suggestion; the prompt files and the kit run are yours.
```
- Never guess. Never invent names.
```
becomes
```
- incident_type, severity, location, date_time_text: never guess.
- equipment_asset, immediate_actions, persons_involved, witnesses: when the worker's words probably name one, include it even if you are not sure, copied from their words; the worker is asked to confirm. Never invent a name or a thing that was not said.
```

**Question for the app session:** does the report screen show a validator-SUGGESTED value as an Accept/Dismiss popup today, the way it shows D's chips? If not, that wiring is the only app change this needs.

## 3. How to judge it (same gate, fixed before the run)
- **One kit run**, about 4 minutes: 4B, dev 27, committed gold, same build as `bo-v11`. Held-out 13 untouched.
- **Same adoption gate as v11:** recall +5 points, precision ≥ 92%, **hallucination ≤ 4%**, valid-after-retry ≥ 95%, median turn ≤ 6 s. The hallucination bound guards against over-filling: if "may guess" makes the 4B fill empty slots, it fails and v11 stays.
- **Report two columns, both fixed now, before the run:**
  - **Strict** (scorer unchanged, the headline): only filled values count; a SUGGESTED popup is neither found nor wrong. Guesses grounded word for word are filled, so they count here, and the hallucination gate covers them.
  - **Every popup accepted:** each SUGGESTED value is scored as if the worker tapped Accept. Right counts toward recall; wrong counts as *wrong* (gold has a value) or *hallucination* (gold empty).
  - Also give the per-field popup table (right, wrong, missed, false alarm), as D did for chips.
  - Reading: if both columns pass the gate, the change clearly helps. If only the "accepted" column improves, the popups help the worker, but the guesses are not good enough to fill automatically.
- It is **dev-informed** (written after reading the dev misses), so the held-out 13 is the fair check.

We dropped the idea of a second, optional-fields-only model call: an extra call per turn may not fit the kit's time budget.

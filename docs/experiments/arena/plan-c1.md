# Plan: extraction recall and urgency triage (c1)

Data: E's `score.py`, `gold_v1.jsonl` (**dev only**), and the kit `raw.jsonl` / `predictions.jsonl` of dev2 (null-template prompt) and v3–v7 (`4020479d…/scratchpad/runs/`). Prompts v4–v8 were already run on the kit; the A doc does not list them. Best measured: **v8, 64/135 (47%)** (`batch1.out`).

## 1. Diagnosis: run 3's 86 lost slots (75 missed + 11 wrong)

| Cause | n | Fields | Evidence |
|---|---|---|---|
| Value written **only in `evidence`** | 21 | actions 10, date 8, equip 2, persons 1 | g02 time only in evidence |
| Field left out | 21 | **equip 15**, actions 4, date 2 | equipment_asset is an argument in 0 calls in every compact prompt v3–v8 |
| Grounding demoted to SUGGESTED | 16 | type 12, loc 2, sev 2 | label quoted as evidence |
| **Parser rejected the call** | 12 | all of g14, g16 | all 7 dev3 rejections: an evidence entry was a list (`["Suresh"]`, `[]`) |
| Wrong value | 11 | date 9 (8 from the interim day rule, gone in master), loc 1, persons 1 | g35 "no wait, half past ten" |
| Negation scope | 3 | loc, type | g04 "has had **no** power" kills the location |
| Rule chip only | 2 | sev, type | g02 says "minor", suggester says "severe" |
| Speech error | 0 | n/a | typed turns: **ASR loss not measured yet** |
| Gold issue | 0 confirmed | n/a | g25 borderline; leave it |

By field: equipment 19/19 lost, date 22/24, actions 15/21, type 16/27, location 7/26. v4–v8 fix the demotions, but omissions rise (41 in v6): the "omit empty" format drops low-salience fields. Only the null template (dev2) wrote equipment, and most of its calls died on null or list evidence.

**Replay estimate.** I re-implemented grounding in Python and ran it over the recorded outputs on the laptop, with no kit time. It scores dev3 at 54 against the 49 measured, so it runs about 5 slots high:

| Outputs | as is | + tolerant parser | + salvage |
|---|---|---|---|
| dev3 | 54 | 59 | 78 |
| v4 | 59 | 78 | 89 |
| v7 | 54 | 58 | 71 |
| dev2 | 34 | **95** (precision 83%, hallucination 11/81) | 95 |

## 2. Ranked extraction plan

| # | Change (file) | Metric | Est. gain | Where / when |
|---|---|---|---|---|
| 0 | **Replay harness**: build a deck from `raw.jsonl` → the existing `testing/ReplayLlmEngine` → `Pipeline` → `predictions.jsonl` → `score.py` | must reproduce 49/135 | enabler | laptop, 45 min, tonight |
| 1 | **ToolCallParser tolerance**: inside evidence, join lists and drop null/[]/""; drop null arguments; turn a string persons/witnesses into a one-item list; a duplicate key *inside evidence* keeps the first (field duplicates still rejected) | recall, valid on 1st call | +2 to +19 slots; fewer retries | laptop, tonight |
| 2 | **Evidence salvage** (`PayloadValidator`): a verbatim field missing from the arguments but quoted in evidence takes that quote as its value, then the normal grounding and TAM checks run | recall, precision, hallucination | +11 to +19; hallucination flat in the replay | laptop, tonight |
| 3 | **Backstops**: a time-phrase finder → `TemporalResolver` when the model gives no time; a severity said outright ("it's minor") → REPORTED, and `SeveritySuggester` never contradicts it; "no wait / actually" keeps the last phrase; "half past" support | date and severity recall and precision | date +3 to +7, sev +2 to +4 | laptop, tonight |
| 4 | `ReportMerge.saidNow` matches tokens, not a raw substring (g34, g38); negation cancels a location only when it is next to it (g04, g13); `people()` drops pronouns and generic roles | recall, persons precision | +3 to +5 | laptop, final day |
| 5 | **Full-key prompt**: 8 fixed keys with null allowed + v8 rules; evidence only for type/severity/names (needs #1) | recall, hallucination, median turn | equipment about 9/19; overall about 60–65%; risk: hallucination up to about 11/81, +1.5 s/turn | **kit, one 6-min run**, final day; ship only if hallucination ≤ 8% and median ≤ 6 s (gates set before the run) |

All gains are estimates (slots of 135) that the harness then confirms. **Integrity:** grounding thresholds, scorer and gold stay unchanged. Salvage uses only the model's own quote, and that quote must pass the same transcript check. A SUGGESTED value is never counted as found. The held-out 13 are opened once, at the end.

## 3. Urgency under-triage (unseen n=40: 25% under, 25% over)

Half the misses (5/10) had no recognised hazard; the other half were one band too low.

- **U1, switch on the LLM hint.** `PriorityAdvisor` already takes `impact_hint`/`ongoing` (raise-only, at most +1 over the rules, never URGENT alone), but **no prompt asks for it: 0 hints in all runs.** Add one rule line with a 5-step anchored scale (about +6 tokens ≈ 0.3 s, est.).
- **U2, field floors** (`UrgencyFeatureExtractor`): a grounded incident_type sets a minimum impact (fire 4; injury, spill, electrical 3); a named injured person sets people-at-risk; "ambulance", "hospital" or "bleeding" sets impact ≥ 4. Raise-only, and shown as a reason. This targets the no-hazard half.
- **U3, asymmetric rounding**: when nothing was recognised or the reporter hedged, a score one point below a band boundary rounds up, and the floor is MEDIUM. This follows cost = 3×under + over.

**Measurement:** dev (53) is saturated (0 under), so holdout 4 (20 reports, written blind, two labellers) comes before any change. Pre-registered: under ≤ 0.15, over ≤ 0.35. Hints come from one kit pass over the scenario texts (about 9 min, the session of #5) and are then replayed in `ScenarioEvaluationTest`. Estimate: under 25% → 15–20%, over +5 points, with a wide interval at n=20.

## 4. Cheap wins and schedule
- **Latency:** run 3's four slowest turns were list-evidence retries, so #1 cuts the maximum. Evidence only for enum/name fields saves about 1 s (est.). Decode fell from 23 to 19.5 tok/s over back-to-back runs (thermal? unverified).
- **Snapper:** span-level matching (n-gram windows of the same word count) for D's 5 misses; gate: 0 wrong chips in `SnapperEvaluation`.

**Tonight:** #0–#3, plus one confirmation kit run (v8 with the new parser). **Final day:** #4, #5, U1–U3, snapper. **Skip:** grammar decoding (not in the runtime), two LLM calls per turn, lower grounding thresholds, new gold alternatives.

# Plan: recall and triage (C2). Dev split only (27 cases); held-out 13 never opened.

## 1. Diagnosis: where the 86 missed fields go
Run `dev3-compact` (49/135), re-read cell by cell against `gold_v1.jsonl` with E's `score.py`. Input was typed, so **speech-error loss is 0 here and unmeasured**; real runs will be worse.

| Cause | Cells | Detail |
|---|---|---|
| Value written under `evidence{}`, not the field | 25 | 11 actions, 9 date, 2 equipment, 2 persons, 1 witness; nothing reads it |
| Model omitted | 23 | 17 equipment (0/19 in every run v3 to v7), 4 actions, 2 date |
| Grounding demotes to SUGGESTED | 18 | 13 type (in 6 of 12 right-named cases the reporter said a cue word, e.g. "leak"), 3 severity, 2 location |
| Wrong value | 11 | 9 date (interim "assume today"), 1 location, 1 persons (gold: Mohan is the driver; arguably a gold issue) |
| Emitted, then dropped | 9 | 5 whole turns lost to parse failure (list written as evidence, repeated `"evidence"` key: g14, g16); 3 true location/type rejected by clause negation ("had **no** power", "**without** looking": g04, g13); 1 date |

Lost by field: date 22, equipment 19, type 16, actions 15, location 7, severity 3, persons 3, witnesses 1. Later uncommitted runs (dev4 to dev7) show prompts only trade fields (v6: type 21/27 but actions 1/21, date 6/24); misplaced evidence stays 14 to 16 cells. Rule-side recovery is prompt-independent.

## 2. Measured on the laptop (no kit)
I replayed saved `raw.jsonl` model outputs through a scratch copy of `:core` (real parser, validator, merge) and scored with E's scorer. The harness reproduces v6 57/135 exactly; current core on dev3 is 54/135 (40%). Correct cells, cumulative:

| Step | dev3 outputs | dev6 outputs |
|---|---|---|
| baseline | 54 | 57 |
| 1 tolerant parse + evidence promotion | 78 | 73 |
| 2 negation scope + cue-word grounding | 86 | 76 |
| 3 resolver gaps, time-phrase fallback, action tidy | 94 | 93 |
| 4 persons must be names | 94, precision 86%->96%, hallucination 6/81->1/81 | 94, precision 93%, hallucination 2/81->1/81 |

**Caveat:** steps 3 and 4 were written after reading dev gold, so dev is optimistic (date 24/24 is overfit). Estimate for unseen: +15 to +25 points recall, not +30. Patch: `scratchpad/arena/c2_work/replay_patch.diff` (4 files). `:core:test` 795/796; the failure (`wrongValueTypeFails`) encodes the old strictness and must be changed deliberately.

## 3. Ranked plan (gain x probability / effort)
| # | Change | Measure | Estimate | When |
|---|---|---|---|---|
| 1 | **Tolerant parse + promote `evidence[key]` to a missing field** (`ToolCallParser`: coerce list/string shapes, ignore repeated `evidence`; `PayloadValidator`: use the quote, still checked verbatim against the transcript) | replay recall, hallucination | +16 to +24 cells (measured, dev) | tonight, laptop, 1 h |
| 2 | **Persons/witnesses must be names** (reject "I", "she", "a worker"), shipped with #1 because #1 exposes them | precision, hallucination rows | 5 false fills removed (measured) | tonight |
| 3 | **Grounding** (`PayloadValidator`, `EnumNormalizer`): clause negation does not apply to location/equipment; type negated only at the quote; no usable quote but the reporter said a cue word for that type/severity: use that real word as evidence. Mismatch stays SUGGESTED. | type, severity recall | +3 to +8 (measured) | tonight |
| 4 | **Time** (`TemporalResolver`): "fifteen/twenty minutes ago", "half past ten", "11 this morning", correction after "no wait"; `findTimePhrase` fallback only when the model attempted the turn | date recall/precision | +8 to +17 (dev, overfit risk) | tonight |
| 5 | **Prompt v8, one kit run** (about 4 min): no `evidence` for verbatim fields (kept only for type), one-line gloss for `equipment_asset` | valid-first-call, tokens/turn, equipment recall | latency about -1.5 s/turn (estimate: evidence is 63% of output chars); equipment effect unknown | final day |

Retries: replay parse failures fall 9 -> 1, so fewer 9 s double calls. Skip: a rule asset finder (a list built from dev gold nouns would be tuning on dev); snapper span snapping is D's own final-day step.

## 4. Urgency and under-triage
C's unseen holdouts: 25% under-triage, half with no hazard recognised, so hazard recognition, not the formula, is the bottleneck. Running E's 27 dev transcripts through `PriorityAdvisor` (my judgement, **not ground truth**): g22 (hit by forklift, ankle broken) LOW, g04 (floor without power, UPS beeping) LOW, g32 (shock and burns) MEDIUM, g31 (broken rib, ambulance, "critical") HIGH. Over-triage too: g02, g14, g35 (fires already out) stay URGENT; the score only rises.

| # | Change | When |
|---|---|---|
| T1 | Stated severity (REPORTED only) as a raise-only floor: critical 15, severe 10, moderate 5. Validated incident type as impact floor (fire, electrical, injury, spill, security, illness at least 3; reaches HIGH, never URGENT alone). The prompt never requests `impact_hint`, so that hook is always empty on the kit. (`PriorityAdvisor`, `ReportSession`) | tonight, laptop |
| T2 | `hazards.json` classes: broken/fractured + body part, dislocated, burns, got a shock, hit by + vehicle/object, hit head, dizzy | final day |
| T3 | No hazard recognised: floor at MEDIUM unless "not urgent" is said; costs over-triage | decide after measuring |
| T4 | Cancel cues ("put it out", "extinguished") offer the existing de-escalation chip; the user taps, never automatic | final day |

Measurement: cost 3 x under + over (C's metric), under-triage rate with Wilson interval, plus "under by 2 bands". Needs a **fresh holdout 4**: 20+ reports written by a teammate before seeing T1 to T3, two labellers (holdouts 1 to 3 are spent). Target under <= 10%, over <= 30%; at n=20 the interval is about +/-20 points, so claim a trend only. Run once after code freeze.

## 5. Risks and integrity
- Promotion could fill from a quote the model did not mean; the verbatim check stays. Replay hallucination fell.
- Cue-word grounding can make a keyword a REPORTED wrong type ("security guard" gave g13 "security"): 1 to 2 cells of precision; the user can still fix it.
- T1/T3 raise over-triage; de-escalation chip and Submit-never-blocked stay.
- No scorer change, no gold edit, held-out untouched. Confirm with one kit dev run, then one held-out run at the end.

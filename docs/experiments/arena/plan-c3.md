# Plan: raise extraction recall and urgency triage (dev split, 27 cases / 34 turns)

## 1. Diagnosis (from gold_v1 dev, A's run-3 numbers, PayloadValidator/GroundingValidator code)
Raw `raw.jsonl`/`predictions.jsonl` live on the kit and are not on the laptop. Per-field splits marked (inf) are my inferences from the aggregates; step 0 replaces them with counts.

Gold slots in dev (counted from the file): incident_type 27, location 27, date_time 24, immediate_actions 21, equipment_asset 19, persons 10 (a few are "none"), severity 9, witnesses 2. Scorer denominator is 135.

| Bucket | Slots | Recall | Main cause |
|---|---|---|---|
| incident_type | 27 | 11/27 (measured) | 11 of 16 misses: right label, **grounding demoted to SUGGESTED** (no evidence, or label quoted as evidence). 5: wrong label or omitted (gold ambiguity possible: g10 property_damage vs environmental, g13 near_miss) |
| date_time | 24 | 2 correct of 11 filled (measured); about 13 never filled (inf) | 8 of 9 wrong = interim "assume today" rule; 1 resolver miss (g35 "no wait"); remainder omitted |
| location + severity | 36 | about 25/36 (inf: 38 mandatory correct minus 11 type minus 2 date) | severity omitted when stated; location partly omitted or cut |
| non-mandatory (actions, equipment, persons, witnesses) | about 49 | about 11/49 = 22% (inf: 49 total correct minus 38) | **model omits** (mean output 76 tokens with evidence, so about 3 fields per turn), plus grounding/hedge demotion |

Cause split (inf, to be confirmed in step 0): model omits about 45%, grounding demotes about 20%, wrong date rule about 8%, resolver/parser a few, gold ambiguity a few. Speech error: about 0, the dev run feeds typed gold text.

**Step 0 (tonight, 20 min, no kit run):** whoever holds the kit does one read-only pull of `files/eval/<run3>/`. A small script on the laptop classifies every unmatched gold slot as omitted / SUGGESTED-demoted / parser-rejected (from `rejections`) / wrong value / gold-doubtful. This fixes the table above.

## 2. Ranked plan
Score = (gain x probability) / effort. All gains are **estimates**, measured with `score_run.py` on the dev split only.

| # | Do | Layer / file | Measure | Est. | When |
|---|---|---|---|---|---|
| 1 | Prompt v5: evidence only for incident_type and severity. Verbatim fields (location, equipment, actions, date_time_text) need none: the validator already accepts them at 0.8 overlap with the transcript. Add a 3rd example filling all 8 fields. | `chat_json_system.txt`, `llm-prompts/` | Recall, mandatory recall, tokens/turn, median s/turn | Output about -35 tokens (about -1.5 s/turn, est.); non-mandatory recall 22% to about 35% (est., high variance) | Tonight, 1 kit run (about 3 min) |
| 2 | Type evidence mined by rule. If the model's type has no usable evidence, find a transcript span through a type-to-cue list (e.g. fire_smoke: smoke, fire, burning). Accept only when the cue's type equals the model's type; otherwise stay SUGGESTED. The span is a real quote of the speaker, not invented. | `PayloadValidator.enumField` + a small `type_cues.json` | Type recall 11/27; wrong-type confirmed count; hallucination | Type 11 to about 18-22 of 27 (est.); +7-11 slots = about +5-8 recall points | Tonight; **replay on laptop** (ReplayLlmEngine over pulled raw outputs), no kit |
| 3 | Date/time: remove the "assume today" rule (replies helper), and in `TemporalResolver` let a self-correction ("no wait", "I mean", "sorry") take the last time phrase. | `TemporalResolver.kt`, unit test g35 | Date/time precision (2/11) | to about 6-8/11 (est.) | Tonight, laptop only |
| 4 | Urgency: ask for `impact_hint` and `ongoing` in the prompt. Both are wired (`LlmHint`, extractor caps one step, raise-only) but **no prompt requests them, so the hint is never produced.** Add a type floor: a model-named type (even SUGGESTED) raises impact to at least 3 for fire_smoke, electrical, spill_chemical, injury, illness. Raise-only. | prompt, `UrgencyFeatureExtractor` hint block | Under-triage, band accuracy, over-triage, cost = 3 x under + over, on a fresh holdout 4 | Under 25% to about 15% (est.); over may rise 5 to 10 pts (risk) | Hint: tonight bundled with run 1 (+about 10 tokens). Floor and thresholds: final day |
| 5 | Snapper span-level matching (site alias inside a longer phrase). | `GazetteerSnapper` | D's `SnapperEvaluation`: 5 misses, wrong chips must stay 0 | misses 5 to about 1-2 (est.) | Final day, laptop only |

Skip for now: rule fallbacks for equipment and actions; biggest precision risk, revisit after step 0.

## 3. Triage detail (under-triage)
- Finding from C: 5 of 10 unseen under-triaged reports had **no recognised hazard**; the other 5 had a hazard with a too-low band. The first group is what items 4 (hint, type floor) and the existing key question target; the second is calibration.
- Asymmetric cost stays: cost = 3 x under + over. Accept a change only if under falls and cost falls.
- Raise-only rules stay (D17/D18): hint cannot lower, cannot reach URGENT alone, user override still wins.
- Data problem: holdouts 1-3 are spent. Write **holdout 4 (20+ reports, labelled by someone who has not seen the rules) before changing thresholds.** With n=20 the interval is about +/-20 points, so report counts, not claims. Pooled 40 unseen reports are the only evidence we have.

## 4. Risks
- Item 2 weakens the "model must justify the type" rule. Guard: cue type must equal model type, negation (TAM NEGATED) still rejects, and wrong-type confirmed is a tracked metric. If it rises above 1, revert.
- Item 1: watch hallucination (now 6/81) and precision (74%); revert if hallucination exceeds 10%.
- Type floor over-triages (a fire_smoke "was extinguished" report becomes MEDIUM). Resolved-cue handling must stay.
- n=27 and one run per prompt: differences under about 5 points are noise. Do not tune on the 13 held-out cases; do not edit the scorer.

## 5. Schedule
Tonight before 04:00: step 0, items 3 and 2 on the laptop, one kit run of v5 (item 1 plus the hint). Report v3, v4, v5 as separate rows. Final day: item 4 floor on holdout 4, item 5, a single run on the 13 held-out at the end.

# Evaluation guide: gold set, labelling rules, scoring

Owner: E · Branch `e/data-eval` · 2026-10-11. Code: `tools/laptop_eval/` (Python) and, once C's types are on master, `core/.../eval/EvalScorer.kt`. Both scorers run the same cases in `core/src/test/resources/fixtures/eval/scorer_cases.jsonl`, so they cannot disagree silently.

## 1. Files

| File | What |
|---|---|
| `app/src/main/assets/eval/gold_v1.jsonl` | 40 gold items. **Generated** from `tools/laptop_eval/build_gold.py`: edit there, then run `python3 build_gold.py`. |
| `app/src/main/assets/eval/manifest.jsonl` | One line per recorded clip (section 5). |
| `core/src/test/resources/fixtures/eval/scorer_cases.jsonl` | Hand-computed scoring cases shared by both scorers. |
| `tools/laptop_eval/runs/<run_id>/` | `raw.jsonl` (every model output), `predictions.jsonl`, `summary.json`, `summary.md`. |
| `tools/laptop_eval/prompts/` | Candidate system prompt and the `update_report` JSON schema used for grammar mode (A and C own the app's copy). |

## 2. Gold item

```json
{"id":"g01","split":"dev","source":"handwritten","language":"en","turns":["There was a water leak ..."],
 "gold":{"incident_type":{"status":"REPORTED","value":"water_leak_flood"},
         "location":{"status":"REPORTED","value":"Conference Room 2","alts":["..."]},
         "date_time":{"status":"REPORTED","date_offset_days":null,"time":"10:00","part_of_day":null,"approximate":true,"text":"around 10 AM"},
         "severity":{"status":"MISSING"}, "...": "all 8 fields present"},
 "missing_mandatory":["severity"],"tags":["deck"]}
```
- **Fields** (spec §4 wire names): `incident_type, severity, location, date_time, persons_involved, equipment_asset, immediate_actions, witnesses`. `description` and `recommended_follow_up` are composed by the app and are not labelled.
- **Status** per field, matching C's `ValueStatus`: `REPORTED` (a value was said), `SUGGESTED` (said but hedged, so the app should offer a chip, not fill), `EXPLICIT_NONE` (stated none; only witnesses and persons involved), `UNKNOWN` ("I don't know"), `MISSING` (never mentioned).
- **`turns`**: the utterances in order. Gold is the state **after the last turn**. `missing_mandatory` is what the app should ask after the **first** turn (mandatory: incident type, location, date/time, severity; D6).
- **`split`**: every third item is `holdout` (13 of 40). Holdout is scored **once**, after the prompt is frozen, and never used for tuning.
- **`source`**: `handwritten` (10 facilities cases; `g01` is the deck example verbatim), `broad` (10 non-injury settings), `osha` (12 spoken rewrites of `Simar123456/spectra-osha-sir`, record id in `source_ref`), `multiturn` (8). The OSHA share is capped because that data is injury-only.
- **Campus names are placeholders** until D's `site.json` exists (list in `build_gold.py`, `SITE_NAMES`).

## 3. Labelling rules

1. **Label what was said, not what is true.** If the speaker says "Conference Room to", the gold is still the canonical "Conference Room 2" (that is what the suggestion layer is scored on), with spelling variants in `alts`.
2. **Incident type** is always labelled when it can be inferred. If two types are defensible, the first is `value`, the others go in `alts` (for example, a stabbing: `security`, alt `injury`).
3. **Severity** only when the speaker states it ("minor", "it's serious" → `severe`). Words like "badly" or "a lot of blood" are not a stated severity: `MISSING`. Keyword-rule severity is a suggestion in the app and never counts as filled.
4. **Location** is the place phrase as specific as said ("third floor of Vindhya"), with shorter accepted forms in `alts`.
5. **Date and time** as parts: `date_offset_days` (0 today, -1 yesterday, **null when no day word was said**: C keeps the date missing rather than guessing), `time` (`HH:MM`, 24 h), `part_of_day` (morning, afternoon, evening, night) when no clock time was said, `minutes_ago` for "N minutes ago", and `text` as spoken.
6. **Persons involved** = **named** people the incident happened to or who caused it. The speaker ("I") and unnamed people ("a student") are not labelled. **Witnesses** = named people who saw it. "Rahul was there but didn't see it" → not a witness (negation).
7. **"Nobody was hurt" / "no injuries"** → `persons_involved: EXPLICIT_NONE`, unless a named person was involved (g14: Suresh put the fire out, unhurt → `REPORTED ["Suresh"]`, tagged `named_but_unhurt`). "Nobody saw it" → `witnesses: EXPLICIT_NONE`.
8. **Immediate actions** = actions already done or being done now ("I'm calling the ambulance now" counts). **Plans do not count** ("we will call the plumber tomorrow" → `MISSING`, tagged `planned`).
9. **Equipment / asset** = a machine, device, vehicle or fixture involved in the incident. Substances (acid, oil) and places are not equipment; a nearby object that played no part ("near the fume hood") is not equipment (g05, tagged `trap_equipment`).
10. **Hedges** ("I think", "maybe", "not sure") on a value → `SUGGESTED`: the correct app behaviour is a chip, not a fill.
11. **Corrections** take the last value ("ten, no wait, half past ten" → 10:30; "Actually it was Conference Room 3" → Room 3).
12. **No real teammate names**, no college or city names anywhere in the data (`test_gold.py` scans for `tools/blindcheck.words`).

## 4. Scoring

Per field and item, the outcome is:

| Gold | Prediction | Outcome |
|---|---|---|
| `REPORTED` | filled, matches | correct |
| `REPORTED` | filled, does not match | wrong |
| `REPORTED` | not filled | miss |
| `MISSING` / `UNKNOWN` / `EXPLICIT_NONE` / `SUGGESTED` | filled | hallucination |
| `MISSING` / `UNKNOWN` / `EXPLICIT_NONE` / `SUGGESTED` | not filled | correct empty |

- **Filled** = `REPORTED`, `USER_CONFIRMED`, `USER_EDITED` (C's `isFilled`). `SUGGESTED`, `UNKNOWN`, `EXPLICIT_NONE`, `MISSING` (absent key) are not filled.
- **Precision** = correct / filled; **recall** = correct / gold values; **hallucination rate** = hallucinations / gold-empty slots. Each is printed as `num/den (pct)` with a Wilson 95% interval in `summary.json`. A ratio with no denominator prints `n/a (0/0)`, so a blank run has recall 0, not a perfect score.
- **Status agreement**: for gold `EXPLICIT_NONE`, `UNKNOWN` and `SUGGESTED`, how often the prediction has exactly that status.
- **Matching**:
  - Enums: exact wire value, or one of the gold `alts`.
  - Free text: tokens are NFC, lower-case, split on non-letters/digits, with number words turned into digits ("two" = "2") and C's grounding stop-words removed. A match needs Dice overlap ≥ 0.8 with `value` or any alt ("Conference Room 3" against "Conference Room 2" fails).
  - Lists: split on `,`, `&` and "and"; the set of names must be equal.
  - Date/time: the prediction's structured parts if present, else C's display string `<date | date missing> <≈time | part of day | time missing> (<words>)`. The date offset and the time (or part of day) must both match; `minutes_ago` allows ±1 minute against `captured_at`. In the **raw** configuration no resolver runs, so the spoken phrase is compared with gold `text` instead.
- **Follow-up**: precision = |asked ∩ gold missing| / |asked|, recall = |asked ∩ gold missing| / |gold missing|, over the union of fields asked in the item. Text-only runs without D's planner use the mandatory fields left unanswered.
- **Speech**: WER uses the same normalisation as B's accent contest (`tools/accent_contest/contest.py`), so the numbers line up. Hindi and Telugu also get CER, and the normalisation used is stated.
- **Never compare** GPU-server latency with kit latency. Server runs report output tokens as the proxy for NPU decode cost.

## 5. Recordings

`app/src/main/assets/eval/manifest.jsonl`, one line per clip. It uses B's keys (`clip_id`, `wav`, `reference`), so `tools/accent_contest/contest.py` reads it unchanged:
```json
{"clip_id":"S1_03","wav":"audio/S1_03.wav","reference":"there was a water leak ...","gold_id":"g01","device":"kit",
 "mic":"headset-3.5mm","speaker":"S1","mode":"scripted","language":"en","condition":"clean",
 "captured_at":"2026-10-11T04:12:00+05:30","duration_s":7.4}
```
- `device`: `kit` or `phone` (the scorecard always says which). `mode`: `scripted` (read a gold line) or `prompted` (spoke freely from a keyword card; `reference` is typed afterwards by ear, never taken from a Whisper draft without correction). `condition`: `clean`, `stressed`, `noise`, `alarm` or `correction`.
- Speakers are `S1` to `S5`, never names. WAVs are 16 kHz mono (`ffmpeg -i in.m4a -ac 1 -ar 16000 out.wav`), **not committed** (shared via the team drive).

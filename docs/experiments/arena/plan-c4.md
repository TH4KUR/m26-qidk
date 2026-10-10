# Plan: recall and triage (c4)

**Data:** E's `gold_v1.jsonl` (dev only) and kit run `dev3-compact` (raw/predictions, in session 4020479d's scratchpad, not in git). E's `score.py` reproduces 49/135. Causes come from my script comparing raw output with predictions per gold field. The run is text-only: speech error is unmeasured, so real recall will be lower.

## 1. Where the 86 lost fields go (dev3)

| Field | gold | lost | main cause |
|---|---|---|---|
| equipment_asset | 19 | 19 | model never writes it top-level (0 of 36 calls; also 0 in later runs dev4/dev6/v7) |
| date_time | 24 | 22 | 9 wrong (8 = interim assume-date rule, 1 = resolver on "no wait"), 9 only inside `evidence{}`, 4 omitted/dropped |
| incident_type | 27 | 16 | 13 right type demoted to SUGGESTED (no or label evidence), 3 dropped |
| immediate_actions | 21 | 15 | 11 only inside `evidence{}`, 4 omitted |
| location | 26 | 7 | 2 demoted, 4 dropped, 1 merge miss |
| severity / persons / witnesses | 9/7/2 | 3/3/1 | grounding reject; "a worker" instead of a name; nested |

| Cause | fields | share |
|---|---|---|
| **A. Model put the value only inside `evidence{}`, no top-level key (parser ignores it)** | 25 | 29% |
| B. Model omitted it (equipment 17, actions 4, time 2) | 23 | 27% |
| C. Grounding demoted to SUGGESTED / severity rejected | 18 | 21% |
| D. Wrong value (assumed date 8, others 3) | 11 | 13% |
| E. Valid output but dropped: both calls invalid on 2 turns (g14, g16), and PayloadValidator rejects location/type as "negated" because the clause contains "no power" or "without looking" (g04, g13) | 9 | 10% |

Also: (1) invalid first calls (5/34) are mostly `[]` or lists inside `evidence`, or duplicate `evidence` keys; a retry can answer worse (g40 lost its time). (2) Gold is arguable for 4 fields (g08/g29/g40 type, g22 persons); do not edit it. (3) `impact_hint`/`ongoing` exist in the parser but prompts v3/v4 never ask for them: no hint ever reaches the advisor. (4) Prompt-only plateaus: later scratch runs (v4-v7) reach 32-43% recall.

## 2. Ranked actions (gain x probability / effort)

Gains are **estimates** until replayed.
**Rig first (20 min):** a Kotlin test replays frozen `dev3` raw outputs through current master core (ReplayLlmEngine, real pipeline) and runs `score.py`. No kit. It also re-baselines master (the no-assumed-date fix should already remove most of D).

| # | Change | Layer | Est. gain (dev) | Measure | When | Kit |
|---|---|---|---|---|---|---|
| 1 | Salvage before retry: drop empty/list evidence values, tolerate duplicate `evidence` keys (top-level duplicates still fail), **promote** `evidence` entries named like a verbatim field (date_time_text, immediate_actions, equipment_asset, location) to the field when whole-token grounded | `ToolCallParser`, `PayloadValidator` | recall +9 to +13 pp; fewer retries (5/34 turns) | replay; recall, hallucination (must stay <=8%), retries | tonight | no |
| 2 | Deterministic enum grounding: severity word said in this utterance ("It's moderate", "Moderate.") and an incident-type cue for the model's *proposed* type found in the transcript become REPORTED with the real quote. Reject a label-only quote. Make negation reject a location only when the cue is adjacent; compare "said now" by tokens | `PayloadValidator`, `EnumNormalizer` | +4 to +7 pp | replay; incident_type recall, wrong-type count must stay 0 new | tonight | no |
| 3 | Equipment rule: site-asset gazetteer plus a general facilities-noun list, with a stoplist for response items (extinguisher, ice pack, kit). Fill only when the model gave none | `suggest/` or `PayloadValidator` | +5 to +8 pp (field is 0/19) | replay; equipment precision | tonight if time, else final-day AM | no |
| 4 | Urgency, see section 3 | `priority/` | under-triage down | section 3 | tonight: labels; final-day: code | partly |
| 5 | Flat schema: evidence only for incident_type and severity (verbatim fields grounded by value overlap). Cuts tokens (est. 76 to about 50, about -1 s/turn) and removes the nesting trap | prompt (A's lane) | recall +/-; latency -1 s | one dev run, about 3 min | final-day (or a tonight slot if A is free) | yes |

**Skip:** flipping SUGGESTED to REPORTED (inflates recall by gaming D7), lowering the 0.5/0.8 grounding thresholds, editing gold or the scorer, tuning on the 13 held-out, fine-tuning, Hindi.

## 3. Urgency and under-triage
Baseline: unseen band accuracy 50%, under-triage 25% (10/40), half of the misses had no recognised hazard. Cost = 3 x under + over (C's assumption, not measured).
- **Type prior:** when the rules find no hazard, the model's incident type (even SUGGESTED, because a false raise is cheaper than a miss) sets an impact floor and "present" immediacy for fire, electrical, injury, illness, security, chemical. Capped at HIGH; never URGENT alone; raise-only; reason shown. The table comes from hazard definitions, not fitted to scores.
- **Stated severity** ("critical", "severe", "serious") raises impact; "minor" never lowers (D17/D18).
- **Hint:** ask for `impact_hint` and `ongoing` in the prompt (about +12 tokens, +0.5 s); the existing +1 cap applies. Check JSON validity does not drop.
- **Measure:** (a) tonight, two people label urgency band for the 27 dev reports blind (about 30 min), (b) a fresh holdout 4 written before any code, run once. Report under, severe under (>=2 bands), over, cost, with and without the change. Pass only if under falls and over rises by <=5 pp.
- Risk: a wrong model type over-triages; labels are single-labeller (agreement 0.76), so do not chase band accuracy.

## 4. Cheap wins elsewhere
- Latency: item 1 removes retries (max 9.4 s); item 5 trims tokens.
- Date/time precision: replay confirms the assumed-date fix; add a rule that "no wait/I mean/sorry" takes the later clock phrase (g35).
- Snapper: span-level matching of a site name inside a longer phrase (5 misses), final-day; chips do not count toward recall.

## 5. Integrity
Rules stay generic (no gold strings). Dev replay is tuning data, labelled "dev, tuned"; the 13 held-out run once, at the end. Scorer unchanged; SUGGESTED stays a miss. Watch hallucination (6/81) and persons precision (4/10): filter pronouns and role-only phrases. **Tonight:** rig, 1, 2 (about 2 h, two laptops).

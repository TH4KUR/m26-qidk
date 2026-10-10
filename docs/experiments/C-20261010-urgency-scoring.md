# C-20261010: urgency scoring on hand-labelled reports

- **Date/time:** 2026-10-10, about 21:50 to 22:40 IST (laptop clock). Runs were repeated while fixing; the numbers below are the first run of each set unless stated.
- **Who:** C.
- **Goal:** find out whether impact × immediacy (1–20) can be estimated from the transcript with CPU rules, which of four scoring formulas is best, and how well it generalises to reports it was not tuned on.

## Setup
- **Code:** branch `c/core-rules`, package `com.megathon.incident.priority`; `./gradlew :core:test` (JDK 17, Gradle 8.14). CPU only, no model, no device.
- **Features:** `UrgencyFeatureExtractor` over `hazards.json` + `TamDetector` (impact 1–5, immediacy 1–4).
- **Strategies, same features:** `risk-matrix` (I×M), `additive`, `protocol-tree`, `authored-rules`.
- **Metric:** band = LOW 1–4, MEDIUM 5–9, HIGH 10–14, URGENT 15–20. Band accuracy, mean absolute error, **under-triage** (predicted band lower than target; the costly error), over-triage. Cost = 3 × under + over.
- **Labels:** written by C, **not independently reviewed** (single labeller; no agreement measured).
- **Data (typed text, not speech; no audio, no kit, no ASR errors):**
  - `scenarios_dev.jsonl`: 53 reports, English / Hinglish / Devanagari. Used while tuning.
  - `scenarios_holdout.jsonl` (holdout 1): 20 reports, written before the first run.
  - `scenarios_holdout2.jsonl` (holdout 2): 20 reports, written **after** holdout 1 was spent and **before** the fixes below, not run until after them.

## Results (n = number of reports)

| Run | Set | n | Strategy | Band accuracy | Under-triage | Over-triage | MAE |
|---|---|---|---|---|---|---|---|
| 1 | dev | 53 | risk-matrix | 0.87 (46/53) | 0.04 (2/53) | 0.09 (5/53) | 0.94 |
| 1 | holdout 1 | 20 | all four | **0.20 (4/20)** | **0.60 (12/20)** | 0.20 (4/20) | 4.3 to 5.0 |
| 2 (after fixes) | dev | 53 | risk-matrix | 0.96 (51/53) | 0.00 (0/53) | 0.04 (2/53) | 0.74 |
| 2 | dev | 53 | additive | 0.92 | 0.00 | 0.08 | 1.15 |
| 2 | dev | 53 | protocol-tree, authored-rules | 0.96 | 0.00 | 0.04 | 0.87, 0.89 |
| 2 | holdout 1 (contaminated) | 20 | risk-matrix | 0.40 (8/20) | 0.35 (7/20) | 0.25 (5/20) | 3.25 |
| 2 | **holdout 2 (unseen)** | 20 | all four | **0.55 (11/20)** | **0.25 (5/20)** | 0.20 (4/20) | 2.65 to 3.30 |

## What went wrong in run 1 (kept, not deleted)
- 12 of 20 unseen reports were under-triaged. Unrecognised text defaulted to impact 2 ("trivial"), which is the dangerous direction.
- Dev misses showed four rule defects: "ho gaya" read as "resolved"; plain "is leaking" escalated like "still leaking"; "someone"/"person" gave a second people boost; "since morning" and "burst pipe" not treated as continuing.

## Verdict
- **The four formulas tie** (differences within one report). The formula is not the bottleneck; **hazard recognition is**.
- **Generalisation estimate: holdout 2, 55 % band accuracy and 25 % under-triage on 20 reports.** The 0.96 on dev is optimistic (tuned on it). 20 reports is a small sample; the interval is wide.
- Treat urgency as **advice with visible reasons**, never as a decision.

## Not tested / limits
- No spoken audio, no ASR errors, no SenseVoice/YamNet (signals tested with fakes only), no kit.
- Single labeller; targets are judgement, not ground truth.
- Hindi lists are not reviewed by a native speaker.

## Next step
- Get a second person to label the three files independently and compute agreement.
- Decide whether to add an LLM `impact_hint` floor (open question Q6 in `docs/core-rules/C-design-and-questions.md`); it is the only fix I expect to move the 25 %.
- Write holdout 3 before any further tuning; re-measure on E's recorded clips once they exist.

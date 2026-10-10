# Qwen3-4B-Instruct-2507 on the kit: extraction on E's dev split (A, 2026-10-11)

Setup: GenieX 0.3.16, QAIRT bundle (2.45), NPU, near-greedy sampling, context reset before every call, `maxTokens=320`.
Pipeline: the app's real path (prompt, model, C's ToolCallParser/PayloadValidator, merge, planner) via the debug eval runner.
Data: E's `gold_v1.jsonl`, **dev split only (27 cases, 34 turns)**; the 13 held-out cases are excluded in code. Scored with E's `score.py`.
Raw outputs and predictions: on the kit under `files/eval/<run>/` (not in git). Scripts/prompts: `docs/experiments/llm-prompts/`.

| Run | Valid on 1st call | Valid after 1 retry | Overall precision | Overall recall | Median per turn |
|---|---|---|---|---|---|
| 1 (first try) | not a model result: my bug, "Context length exceeded" in 23/27 items (no context reset) | | | | |
| 2 plan prompt, reset fixed | 8/34 (23%) | 14/34 (41%) | 31/42 (74%) | 31/135 (23%) | 13.4 s |
| 3 compact prompt + reasoned repair | 29/34 (85%) | 32/34 (94%) | 49/66 (74%) | 49/135 (36%) | 4.25 s (max 9.4 s) |

Run 3 detail: mandatory-field precision 38/48 (79%), recall 38/86 (44%); hallucination 6/81 (7%); first token median 439 ms; decode median 22.1 tok/s; generated tokens mean 76; prompt tokens max 582; cold model load 9.5 s; all 27 items labelled NPU.

## Findings
- The model's failures are format and grounding, not understanding. Run 2's invalid calls were `[]`/`null` written into `evidence` (C's parser rejects them). Telling the model to omit empty fields cut output length by 40% and raised validity from 23% to 85%.
- Run 3 date/time precision 2/11: 8 of 9 wrong are **my interim "assume today/yesterday" rule** (an invented date is wrong by the gold). The replies helper removes it; not re-measured.
- Run 3 incident type found 11/27 (41%), but in 11 of 16 misses the model named the right type and C's grounding rule correctly demoted it to SUGGESTED: either no evidence was given (g01) or the label itself was given as evidence ("injury", never said) (g20). Prompt v4 (`prompt_v4_evidence.txt`) targets this and is **NOT measured yet**.
- One resolver miss for C: "at ten, no wait, half past ten this morning" resolved to "morning" and lost 10:30 (g35).

## Not measured / caveats
Dev split only (27 cases), one run per prompt, no confidence intervals; no held-out run; no Hindi; compute label NPU comes from the bundle's runtime id (`qairt`); Whisper and this model were not run together (coexistence A2b not done).

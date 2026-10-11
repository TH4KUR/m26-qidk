# E-20261011: baseline re-run on the fictional-name gold set

- **When:** 2026-10-11, 01:13 to 01:19 IST. **Who:** E (AI assistant under E's direction).
- **Why:** the user's 00:35 decision moved all committed data to D's fictional site list. Earlier runs (`E-20261011-llm-contest-r1.md`, `E-20261011-homophones-and-schema.md`) used real campus names and their run outputs were deleted from `tools/laptop_eval/runs/`. This re-run gives numbers on the data that is actually committed; **cite these, not the earlier ones**.

## Setup
- Qwen3-1.7B Q4_0, llama.cpp b11541 **CPU on a laptop (WSL, 3 GB RAM), not the kit**. Temperature 0, seed 42, thinking off, max 320 tokens. Prompt `tools/laptop_eval/prompts/chat_json_system.txt` (≤ 478 tokens). Grammar = `prompts/update_report.schema.json` v2 (evidence `minLength 1`, strings `maxLength 120`).
- Data: first 20 `dev` items of `gold_v1.jsonl`; all 8 items of `gold_homophones.jsonl` (clean text, then mis-heard text). Gold **text**, no audio. Config: **raw** (parsed payload written straight in; no grounding, no gazetteer). Runs in `tools/laptop_eval/runs/20261011-011*`.

## Results
| Run | Valid calls | Precision | Recall | Mandatory recall | Hallucination | Follow-up P / R |
|---|---|---|---|---|---|---|
| Dev 20, free decoding | 6/20 | 28/36 | 28/98 | 19/61 | 3/62 | 18/58 / 18/19 |
| Dev 20, grammar v2 | **20/20** | 76/102 | **76/98** | **56/61** | 17/62 | 11/11 / 11/19 |
| Homophones, clean, grammar v2 | 7/8 | 17/18 | 17/30 | 15/23 | 0/34 | 9/16 / 9/9 |
| Homophones, mis-heard, grammar v2 | 7/8 | 10/17 | 10/30 | 9/23 | 1/34 | 9/17 / 9/9 |

Location recall: dev grammar 16/19; homophones clean 7/8, mis-heard 2/8.

## Findings
- Same picture as before the rename: **grammar mode is required** for this model (valid calls 6/20 → 20/20, recall 28/98 → 76/98) and it **raises hallucination** (3/62 → 17/62): severity 6, persons 4, witnesses 3, equipment 2, location 1, date_time 1 (from the saved `summary.json`; corrected after A/033, the earlier text said "mostly persons and witnesses"). Grounding is meant to catch the persons and witnesses; severity is only filled when stated.
- On mis-heard text the model still copies sound-alikes; place recall falls from 7/8 to 2/8. The gazetteer has to repair places.
- **Not predictive of the app's primary model:** the lead's pick is Qwen3-4B-Instruct-2507 as a QAIRT bundle, which has **no grammar mode**; its output is checked by the validator after the fact. These are shortlist numbers for the 1.7B fallback only.

## Limits
Laptop CPU, typed gold text (no ASR errors except the hand-written mis-heard set), 20 + 8 items (wide intervals), one model, one seed.

## Next step
The 4B on the kit (app session); "+grounding" through A's pipeline once `a/stubs` is pushed; recordings at the 03:15 kit slot.

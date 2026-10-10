# E-20261011: LLM contest round 1 (Qwen3-1.7B, free vs grammar)

- **When:** 2026-10-10 23:58 to 2026-10-11 00:05 IST. **Who:** E (AI assistant under E's direction).
- **Goal:** first shortlist numbers for the LLM extraction stage, and check that the eval harness works end to end.

## Setup
- **Model:** `unsloth/Qwen3-1.7B-GGUF` `Qwen3-1.7B-Q4_0.gguf`, llama.cpp b11541 CPU build, `llama-server --ctx-size 4096 --threads 10 --jinja`, on a **laptop CPU under WSL (3 GB RAM), not the kit**. Temperature 0, seed 42, max 320 tokens, thinking disabled (`/no_think` + `enable_thinking=false`).
- **Prompt:** `tools/laptop_eval/prompts/chat_json_system.txt` (max prompt 479 tokens). **Grammar mode:** `response_format` json_schema from `prompts/update_report.schema.json`.
- **Data:** first 20 `dev` items of `gold_v1.jsonl` (gold **text**, no audio, no ASR errors), 20 model calls (all single-turn).
- **Pipeline config:** **raw** (parsed payload written straight in as REPORTED; no grounding, no merge rules, time scored on the spoken phrase).
- **Command:** `python3 run_eval.py --model qwen3-1.7b-q4_0-cpu --split dev --limit 20 --no-think [--grammar]`. Outputs in `tools/laptop_eval/runs/`.

## Results (n = slots or calls as shown)
| Metric | Free decoding | Grammar (json_schema) |
|---|---|---|
| Valid `update_report` calls (C's parser rules) | 7/20 | **19/20** |
| Overall precision | 33/42 (79%) | 70/95 (74%) |
| Overall recall | 33/98 (34%) | **70/98 (71%)** |
| Mandatory-field recall (type, location, time, severity) | — | 53/61 (87%) |
| Hallucination (gold empty, model filled) | 3/62 (5%) | **16/62 (26%)** |
| Follow-up precision / recall (derived from unanswered mandatory fields) | 18/54 / 18/19 | 11/15 / 11/19 |
| Mean output tokens | 106.9 | 112.7 |

## What went wrong / findings
- **Free decoding fails mostly on `evidence` typing:** the model writes `null` or `[]` inside `evidence`. C's parser decodes `evidence` as `Map<String,String>`, so the whole call is rejected (the Python port mirrors that; to be confirmed against the Kotlin parser). Grammar removes this failure class.
- **Grammar trades validity for hallucination:** with every key forced, the model fills persons/witnesses it was not told about (witness precision 0/4). This is the case the grounding layer (C's `PayloadValidator`) exists for: next run is the **+grounding** configuration on the same outputs.
- Severity is never stated in most items, so most of the mandatory misses are correct asks, not model errors.

## Verdict
Grammar mode is required for this model. Raw numbers are a lower bar for the ablation; the shortlist needs NuExtract-2.0-2B and Gemma on the same 20 items, then the kit.

## Next step
Replay these raw outputs through C's `PayloadValidator` (+grounding) in Kotlin; add the other candidates; move runs to a GPU (A's RTX 4060 Vulkan build) for speed.

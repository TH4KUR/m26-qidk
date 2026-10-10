From: E   To: all   Date: 2026-10-11 00:45 IST   Re: -
Type: done

Eval data and tools are pushed on `e/data-eval` (head `164c8aa`). Formats you can rely on:
- **Gold:** `app/src/main/assets/eval/gold_v1.jsonl` (40) and `gold_homophones.jsonl` (8). One object per line: `id, split (dev|holdout), source, turns[], gold{8 fields}, missing_mandatory[], tags[]`. Field status uses C's `ValueStatus` names. Rules: `docs/eval-guide.md`.
- **Held-out:** every third item (13 of 40) is `holdout`. Do not tune on it; I score it once, at the end.
- **Predictions (what any pipeline should write for scoring):** one line per gold item, `{"gold_id","captured_at","asked":[["SEVERITY"]],"report":<C's IncidentReport JSON>}`. Kotlin: `eval.Prediction.toJson()`.
- **Clip manifest:** `app/src/main/assets/eval/manifest.jsonl`, B's keys `clip_id, wav, reference` plus `gold_id, device (kit|phone), speaker S1–S5, mode, language, condition`.
- **Scoring:** filled = REPORTED / USER_CONFIRMED / USER_EDITED; a blank run scores recall 0, never 1.0; every number is printed as num/den.
First numbers (Qwen3-1.7B, laptop CPU, gold text, raw config) are in `docs/experiments/E-20261011-*.md`.
Note: the committed data still uses real campus names; it switches to the fictional list next (see 003 and the lead's 00:35 decision). Do not copy names from it yet.

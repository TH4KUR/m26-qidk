# Saved model outputs of the fine-tune experiments (dev set only, fictional gold)

Re-score without a GPU: replay each `raw.jsonl` through the real parser, validator and merge (`ReplayScoringHarness`), then
score the resulting `pred.jsonl` with E's `score.score(golds, preds)` (`tools/laptop_eval/score.py`) against the dev rows
(`split == "dev"`) of the committed `app/src/main/assets/eval/gold_v1.jsonl`. The saved `pred.jsonl` files were produced
exactly that way, so they can be scored directly.

```
REPLAY_JOBS="<dir>/raw.jsonl|<dir>/captured.jsonl|<dir>/pred.jsonl"   ./gradlew :core:cleanTest :core:test --tests '*ReplayScoringHarness*' --no-build-cache
```

`pred.jsonl` is the replayed report per item in the scorer's format; `captured.jsonl` fixes one capture instant for every arm.
All arms: Qwen3 bf16 on a GPU, greedy decoding, one run, 27 dev items (34 turns). "report so far" is rebuilt from the model's
own earlier answers (`tools/finetune/gen_eval.py`), an approximation of the app's state. `synthetic_test_*` are replies on B's 100
synthetic test rows (score with `tools/finetune/score_test.py`).

| dir | model | prompt | training |
|---|---|---|---|
| 1p7b_base_v8 / 1p7b_tuned_v1_v8 | Qwen3-1.7B | extract_v8 | v1 data (599 rows), 3 epochs |
| 4b_base_v8 / 4b_tuned_v1_v8 | Qwen3-4B-Instruct-2507 | extract_v8 | v1 data (599 rows), 3 epochs |
| 4b_base_v11 / 4b_base_v12 | Qwen3-4B-Instruct-2507 | extract_v11 / extract_v12 | none |
| 4b_tuned_v2_v12 | Qwen3-4B-Instruct-2507 | extract_v12 | v2 data (1,175 rows: v1 + partial-utterance and presence rows), 2 epochs, best-by-validation epoch 1 |

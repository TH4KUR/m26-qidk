# Qwen3-1.7B fine-tune: run order

Plan: A's `communication/A-lead-kit-app/messages/017-to-C-finetune-task-v2.md` (1.7B first) on B's synthetic gold
(`b/speech-engines`, `data/synthetic_gold/v1`, 800 rows: 600 train, 100 validation, 100 test). Training is gated by
the human user; the adoption gate is fixed in A/017 and is not tuned after results.

| Step | Where | Command |
|---|---|---|
| 0. Environment | GPU VM | `bash tools/finetune/setup_vm.sh` |
| 1a. Label audit | laptop (JDK) | `SFT_AUDIT="<v1 dir>/gold.jsonl|audit.json" ./gradlew :core:cleanTest :core:test --tests '*SftLabelAuditHarness*' --no-build-cache` (every label through the real parser and validator; `--no-build-cache` because the env var is not a Gradle input) |
| 1b. SFT files | anywhere | `python tools/finetune/build_sft.py --gold-dir <v1 dir> --prompt core/src/main/resources/prompts/extract_v8.txt --out sft --dev-gold app/src/main/assets/eval/gold_v1.jsonl --drop-ids audit.json --strip-location-prefix` |
| 2. Train + merge | GPU VM | `python tools/finetune/train_lora.py --data sft --out run1` |
| 3. Dev-27, base | GPU VM | `python tools/finetune/gen_eval.py --model Qwen/Qwen3-1.7B --gold <gold_v1.jsonl> --prompt <extract_v8.txt> --out eval_base` |
| 3. Dev-27, tuned | GPU VM | `python tools/finetune/gen_eval.py --model run1/merged --gold <gold_v1.jsonl> --prompt <extract_v8.txt> --out eval_tuned` |
| 4. Score | laptop | replay `raw.jsonl` with `ReplayScoringHarness` (`REPLAY_JOBS="raw.jsonl|captured.jsonl|pred.jsonl"`), then E's `score.py` |
| 5. Quantize | GPU VM | `python -m qai_hub_models.models.qwen3_1_7b.quantize --precision w4a16 --checkpoint run1/merged --output-dir run1/q` |
| 6. Export | GPU VM | `qai-hub-models export qwen3_1_7b --checkpoint run1/q --chipset qualcomm-snapdragon-8-elite --skip-profiling --output-dir run1/genie_bundle` |

Rules carried over from A/017 and B/014: the system prompt is the **shipping** `extract_v8.txt` (not B's), so inputs are
byte-identical to the app; E's dev 27 and held-out 13 are never trained on (the held-out are never read by these
scripts); `test.jsonl` is evaluation only; only a checkpoint that beats its own base on the dev 27 goes to step 5.

Step 6 uploads the weights to Qualcomm AI Hub for compilation (cloud, build time only): disclose it in the report.
A copy of the model config must be in the checkpoint folder passed to step 5 (the quantize CLI says so); `merged/`
already holds it.

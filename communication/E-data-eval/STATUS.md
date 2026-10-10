# Status: agent E (data, evaluation, pitch)

Owner: agent E only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 05:20 IST
Read up to: A=019 B=016 C=009 D=004

## Doing now
- Typing references for the 20 own-words phone clips, by ear, and checking their gold answers against what was said.
- Next (A/016): results tables from logs only, the one-shot held-out scorer, the voice-versus-typing protocol.

## Done and ready for others (with file/commit) — branch `e/data-eval`
- **Phone recordings round 1** (message 010): 34 clips in Drive (https://drive.google.com/drive/folders/1QPgCuU_l0B3vGcYCIeqcWyQDtU1Ods8a?usp=sharing). Manifests `app/src/main/assets/eval/manifest_phone_r1.jsonl` (34) and `manifest_phone_r1_scripted.jsonl` (14 with exact references, B's contest shape), both with `split` dev/holdout (message 011). The `_06` alarm clips are very noisy: report them separately. The `_08` clips are single takes. 8 held-out clips run once at the end.
- **Gold on fictional names:** `gold_v1.jsonl` (40: 27 dev, 13 held out), `gold_homophones.jsonl`, `manifest.jsonl` (plan), `docs/recording-sheets.md`.
- **Baseline to cite:** `docs/experiments/E-20261011-fictional-gold-baseline.md` (Qwen3-1.7B, laptop CPU, gold text; does not predict the 4B on the kit).
- Scorers (`score.py`, `wer.py`, Kotlin `eval/EvalScorer.kt`), `FakePipelineRunner.kt`, `run_eval.py`, parser port of C's `ToolCallParser`. Python 99 tests pass.

- Answers to A/018 (gold versions, holdout file) and an eval-side look at B's synthetic set: message 012.

## Blocked on / waiting for
- B: raw transcripts of the 34 clips and the phone accent table.
- A (app session): kit end-to-end run on the 26 dev clips.

## Needs from others
- Two blind labellers for the fresh urgency holdout (A/016 task for whoever recorded).

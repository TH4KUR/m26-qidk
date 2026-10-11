# Status: agent E (data, evaluation, pitch)

Owner: agent E only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 09:50 IST
Read up to: A=038 B=027 C=029 D=007

## Doing now
- Typing references for the 20 own-words phone clips, by ear, and checking their gold answers against what was said.
- Next (A/016): results tables from logs only, the one-shot held-out scorer, the voice-versus-typing protocol.

## Done and ready for others (with file/commit) — branch `e/data-eval`
- **Phone recordings round 1** (message 010): 34 clips in Drive (https://drive.google.com/drive/folders/1QPgCuU_l0B3vGcYCIeqcWyQDtU1Ods8a?usp=sharing). Manifests `app/src/main/assets/eval/manifest_phone_r1.jsonl` (34) and `manifest_phone_r1_scripted.jsonl` (14 with exact references, B's contest shape), both with `split` dev/holdout (message 011). The `_06` alarm clips are very noisy: report them separately. The `_08` clips are single takes. 8 held-out clips run once at the end.
- **Gold on fictional names:** `gold_v1.jsonl` (40: 27 dev, 13 held out), `gold_homophones.jsonl`, `manifest.jsonl` (plan), `docs/recording-sheets.md`.
- **Baseline to cite:** `docs/experiments/E-20261011-fictional-gold-baseline.md` (Qwen3-1.7B, laptop CPU, gold text; does not predict the 4B on the kit).
- Scorers (`score.py`, `wer.py`, Kotlin `eval/EvalScorer.kt`), `FakePipelineRunner.kt`, `run_eval.py`, parser port of C's `ToolCallParser`. Python 115 tests pass.

- Answers to A/018 (gold versions, holdout file) and an eval-side look at B's synthetic set: message 012.
- Optional-field recall: position test (field type, not sentence position, explains the misses) and a "may guess, worker confirms" prompt proposal for the four optional fields: message 013.
- **Equipment second pass** (message 014): focused 1.7B pass finds equipment 17/19 on dev (4B v11 3/19, D's chips 11/19, together 19/19), 106/115 on B's 134 independent rows, 5/5 on typed phone transcripts; over-offers when there is none, so chips only. Log `docs/experiments/E-20261011-optional-field-second-pass.md`, tool `tools/laptop_eval/optional_pass.py`.
- **Scorer:** `score.py` adds a labelled lenient free-text view (`lenient_free_text`); headline rule unchanged.
- **Message 015:** probe audit (2 of 3 false alarms are gold gaps), request for a 1.7B CPU arm and a comparison on B's 134 reports (`tools/laptop_eval/for_a/b_synthetic134_gold.jsonl`), A/033 fixes done (wer.py Indic + CER, held-out guards, doc corrections).
- **Results tables for the report:** `docs/results-tables.md`, every number with its source.
- **Message 016:** the user's word, keep the probe (item, action, people) and the presence chips ON by default.

## Blocked on / waiting for
- B: raw transcripts of the 34 clips and the phone accent table.
- A (app session): kit end-to-end run on the 26 dev clips.

## Needs from others
- Two blind labellers for the fresh urgency holdout (A/016 task for whoever recorded).

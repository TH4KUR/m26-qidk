# Status: agent E (data, evaluation, pitch)

Owner: agent E only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 01:25 IST
Read up to: A=005 B=007 C=004 D=003

## Doing now
- Phone recordings by the team (the kit is busy with development); then re-align 9 site names with D's map, M2 draft (due 03:00).

## Done and ready for others (with file/commit) — branch `e/data-eval` (push pending, see 007)
- **Gold on fictional names** (decision 00:35; 9 names still to re-align with D's final list 1d2aa74 via D's map): `app/src/main/assets/eval/gold_v1.jsonl` (40: 27 dev, 13 held out), `gold_homophones.jsonl` (8, clean + mis-heard), `manifest.jsonl` (50 clips), `docs/recording-sheets.md`. Old run outputs with real names deleted; a test scans every mirrored eval file against master's scan list (96 Python tests pass with both lists).
- **Baseline to cite:** `docs/experiments/E-20261011-fictional-gold-baseline.md` (Qwen3-1.7B, laptop CPU, gold text): grammar v2 valid calls 20/20, recall 76/98, mandatory recall 56/61, hallucination 17/62; free decoding valid 6/20, recall 28/98.
- Scorers (Python `score.py`, `wer.py`; Kotlin `eval/EvalScorer.kt`), `testing/FakePipelineRunner.kt`, runner `run_eval.py`, grammar schema v2, parser port matching C's `ToolCallParser`.

## Blocked on / waiting for
- App session: push of `a/stubs` (Judge Bench replays through its `Pipeline` + `ReplayLlmEngine`); a 16 kHz recording route (tinycap numbers or app recorder) and the demo mic.
- User: GPU logins (A 005 #3); teammates for phone recordings now, a second human labeller and a native Hindi reader for C.

## Needs from others
- D: `real-to-fictional.json` off-repo (D 003, item 2).

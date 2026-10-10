# Status: agent E (data, evaluation, pitch)

Owner: agent E only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 00:45 IST
Read up to: A=000 B=000 C=004 D=000 (also legacy `docs/messages/lead-to-E.md` incl. the 00:35 demo-only names note, `AtoB.md` on master)

## Doing now
- Paused for replies to messages 002–006. Next on `e/data-eval`, in this order: switch committed eval data to the fictional site list (lead 00:35 decision), recordings with A, model runs on a GPU, M2 draft by 03:00.

## Done and ready for others (with file/commit) — branch `e/data-eval`, head `164c8aa`
- Gold set `app/src/main/assets/eval/gold_v1.jsonl` (40: 27 dev, 13 held out) and `gold_homophones.jsonl` (8 sound-alike items, clean + mis-heard text). Source: `tools/laptop_eval/build_gold.py`. Rules: `docs/eval-guide.md`.
- Recording plan: `docs/recording-sheets.md` + `app/src/main/assets/eval/manifest.jsonl` (50 clips: S1–S5 x 8, plus 10 Hindi/Telugu; uses B's `clip_id/wav/reference` keys).
- Scorers: `tools/laptop_eval/score.py` (precision, recall, hallucination per field, Wilson CI, counts always shown), `wer.py` (WER/CER, B's normalisation); Kotlin `core/.../eval/EvalScorer.kt` (same 29 shared cases in `core/src/test/resources/fixtures/eval/scorer_cases.jsonl`).
- `core/.../testing/FakePipelineRunner.kt` + `PipelineRunner` interface for the Judge Bench screen (reads `predictions.jsonl`).
- LLM runner `tools/laptop_eval/run_eval.py` + `prompts/` (system prompt, grammar schema v2). Parser port `parse.py` matches C's `ToolCallParser` on C's fixtures.
- Experiment logs: `docs/experiments/E-20261011-llm-contest-r1.md`, `E-20261011-homophones-and-schema.md`.
- Tests: Python 95 passing; Kotlin 504 passing (run on an export of `b/speech-engines` + my files).

## Blocked on / waiting for
- A: merge of B + C into `master` (my Kotlin needs C's types and B's `testing.Compute`); push of `a/stubs` (Judge Bench should replay through A's `Pipeline` + `ReplayLlmEngine`); a kit slot for recordings; GPU access (002).
- A + C: one rule for undated past times (003).

## Needs from others
- A: 002. B: 004. C: 005. D: 006.
- The user: a second human labeller and a native Hindi reader for C (names stay out of the repo).

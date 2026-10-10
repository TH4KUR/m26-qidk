# Synthetic gold: B to C cross-verification

The user requests independent cross-verification before this dataset is used for fine-tuning. This handoff authorizes review and a written verdict, not a training run.

## Candidate revision

- Branch: `b/speech-engines`; the immutable commit is supplied in B's communication request.
- Dataset: `data/synthetic_gold/v1/`; 480 records, 144 scenario families, 360 train / 60 validation / 60 test.
- Gold SHA-256: `d57aa7e1bcb43b4ac02ae434c6017bf6d58f8a0e7b0cb6c9840cfd8bc53ba139`.
- Sources: `tools/synthetic_gold/`; app contract test: `core/src/test/kotlin/com/megathon/incident/llm/SyntheticGoldContractTest.kt`.
- Exact baseline app revision and source/data hashes: `data/synthetic_gold/v1/manifest.json`.

Read the dataset README, prompt, schema, provenance, and recorded validation results first. This is a small synthetic pilot for the user's Qwen3-4B-Instruct-2507 target, not the larger teacher-labelled corpus proposed in A/017. The user explicitly requires all mandatory answers in every raw input, including correction turns, and supported facts only. Do not add missing-mandatory examples or switch the target checkpoint without discussing the change with the human user.

## Independent review requested

1. Review every raw input against its mandatory answers and target. Confirm that incident type, location, date and clock time, and stated severity are actually supported. Severity must not be inferred from panic, injury, hazard, or urgency. Report exact IDs for unsupported or conflicting labels.
2. Check all optional fields for entailment and omission: affected people versus witnesses/helpers/reporters; affected asset versus landmark/response tool; completed or ongoing action versus denied/planned/hypothetical action; uncertainty; unnamed groups; heard name/code errors. Each emitted field needs an exact supporting quote. Follow-ups require correct sparse replacement/no-op targets; unchanged fields can be omitted although all mandatory answers remain in the raw input.
3. Independently recompute the temporal gold, rather than validating only with its own generator. Use a recorded capture instant in `Asia/Kolkata`, including midnight, month/year rollover, Friday-on-Friday, and an evaluator clock in another timezone. Preserve approximation. `date_time_text` is the spoken phrase; resolved timestamp gold is a separate downstream boundary.
4. Verify actual `PromptBuilder`, `ToolCallParser`, and `PayloadValidator` behavior. B checked the real parser and prompt builder, not every validator outcome. Distinguish intended suggested/explicit-none behavior from rejection or unsupported gold. Test against the manifest's source revision and the intended deployment revision; identify any contract drift introduced by C's recall patch or A's history/prompt changes. Do not silently rewrite the shipping contract to fit the data.
5. Check family-level separation, exact/near duplicates, provenance, and model-message formatting. E's original benchmark remains external. Its held-out narratives must stay unopened for authoring/tuning; a non-disclosing overlap audit may compare them in-process. B's audit proves zero exact overlaps only. The new 60 synthetic test rows share template patterns with training and do not prove real-world generalization.

The saved-state date/time examples use a fixed synthetic reference of 2026-10-11 11:00 IST. Live evaluation must rebase the seeded date/time consistently from its source phrase, or replay the original reference. Capture once per call; do not obtain a fresh "now" after model inference for scoring. The existing `EvalRunner` cannot consume this dataset directly: it expects `split=dev` and final-state `turns`, whereas this dataset supplies explicit messages, saved-state preconditions, and per-call patches.

Some relative phrases exceed the current resolver's coverage and its approximate-offset handling is imperfect. Separate resolver failures from extraction-label failures; synthetic SFT cannot fix downstream resolver code by itself. The supplied system prompt also differs from the checked-in default, which currently advertises nulls/empty lists. Freeze and compare the intended training/inference prompt before approving an experiment.

## Reproduce B's mechanical checks

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/synthetic_gold/build_dataset.py --check
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/synthetic_gold -p 'test_*.py' -v
./gradlew :core:test
python3 tools/synthetic_gold/temporal_gold.py --captured-at 2026-10-11T00:10:00+05:30 --output /tmp/incident-gold-midnight.jsonl
python3 tools/synthetic_gold/temporal_gold.py --captured-at 2027-01-01T08:00:00+05:30 --output /tmp/incident-gold-year-boundary.jsonl
```

B's recorded results: 18 Python tests and 793 core tests passed, 480/480 parser and prompt compatibility checks, and all complete records fit in 1,024 tokens with the actual Qwen tokenizer. Normal Gradle was blocked on this host by a missing JDK; the core result came from fresh cached Kotlin compiler/JUnit execution. These checks are not independent semantic adjudication. No Qwen inference, training, NPU conversion, or measured model improvement occurred.

## Required reply and next step

Reply in C's own communication outbox, addressed to B, referencing B's request. Include the exact reviewed commit and gold hash, methods and number of rows independently reviewed, PASS/BLOCKED verdict, failing IDs, remaining limitations, and any proposed correction commit. Partial sampling is a partial review, not clearance of all 480 rows. Findings must be resolved and the resulting exact revision rechecked before the gate can close. Agent review must not be described as human adjudication.

After C reports completion, B will tell C that the next step is fine-tuning. **Training still requires human intervention and explicit approval of the experiment, checkpoint, compute/export feasibility, and evaluation plan.** C should present the review result and wait for the human user; do not autonomously launch training, spend on compute, export/replace model weights, or deploy. If review finds blockers, repair and re-verification precede that next step. A/017's separate export feasibility and user GO gates remain applicable.

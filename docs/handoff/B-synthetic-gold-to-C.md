# Synthetic gold: B to C cross-verification

The user requests independent cross-verification before this dataset is used for fine-tuning. This handoff authorizes review and a written verdict, not a training run.

## Candidate revision

- Branch: `b/speech-engines`; the immutable commit is supplied in B's communication request.
- Dataset: `data/synthetic_gold/v1/`; 800 records, 224 scenario families, 600 train / 100 validation / 100 test; supersedes 959d671.
- Gold SHA-256: `9e00c787e1c6da572f4f95d28c96fa7091db4a60fbcf9dca366547a3c543f389`.
- Sources: `tools/synthetic_gold/`; app contract test: `core/src/test/kotlin/com/megathon/incident/llm/SyntheticGoldContractTest.kt`.
- Exact baseline app revision and source/data hashes: `data/synthetic_gold/v1/manifest.json`.

Read the dataset README, prompt, schema, provenance, recorded validation results, and runtime_update_audit.json first. The revised dataset adds 80 four-turn sequences: initial report, correction, another correction, and no-op confirmation. Saved-context rows rise from 24 to 264; all eight fields have repeated updates, including list additions/removals, role swaps, action corrections, and 16 sequences with multiple field changes. This is a small synthetic pilot for the user's Qwen3-4B-Instruct-2507 target, not the larger teacher-labelled corpus proposed in A/017. The user explicitly requires all mandatory answers in every raw input, including correction turns, and supported facts only. Do not add missing-mandatory examples or switch the target checkpoint without discussing the change with the human user.

## Independent review requested

1. Review every raw input against its mandatory answers and target. Confirm that incident type, location, date and clock time, and stated severity are actually supported. Severity must not be inferred from panic, injury, hazard, or urgency. Report exact IDs for unsupported or conflicting labels.
2. Check all optional fields for entailment and omission: affected people versus witnesses/helpers/reporters; affected asset versus landmark/response tool; completed or ongoing action versus denied/planned/hypothetical action; uncertainty; unnamed groups; heard name/code errors. Each emitted field needs an exact supporting quote. Follow-ups require correct sparse replacement/no-op targets; unchanged fields can be omitted although all mandatory answers remain in the raw input.
3. Independently recompute the temporal gold, rather than validating only with its own generator. Use a recorded capture instant in `Asia/Kolkata`, including midnight, month/year rollover, Friday-on-Friday, and an evaluator clock in another timezone. Preserve approximation. `date_time_text` is the spoken phrase; resolved timestamp gold is a separate downstream boundary.
4. Verify actual `PromptBuilder`, `ToolCallParser`, and `PayloadValidator` behavior. B checked the real parser and prompt builder, not every validator outcome. Distinguish intended suggested/explicit-none behavior from rejection or unsupported gold. Test against the manifest's source revision and the intended deployment revision; identify any contract drift introduced by C's recall patch or A's history/prompt changes. Do not silently rewrite the shipping contract to fit the data.
5. Check family-level separation, exact/near duplicates, provenance, and model-message formatting. E's original benchmark remains external. Its held-out narratives must stay unopened for authoring/tuning; a non-disclosing overlap audit may compare them in-process. B's audit proves zero exact overlaps only. The new 100 synthetic test rows share template patterns with training and do not prove real-world generalization.

The saved-state date/time examples use a fixed synthetic reference of 2026-10-11 11:00 IST. Live evaluation must rebase the seeded date/time consistently from its source phrase, or replay the original reference. Capture once per call; do not obtain a fresh "now" after model inference for scoring. The existing `EvalRunner` cannot consume this dataset directly: it expects `split=dev` and final-state `turns`, whereas this dataset supplies explicit messages, saved-state preconditions, and per-call patches.

Some relative phrases exceed the current resolver's coverage and its approximate-offset handling is imperfect. Separate resolver failures from extraction-label failures; synthetic SFT cannot fix downstream resolver code by itself. The supplied system prompt differs from the sparse shipping extract_v8.txt prompt. Inputs now match master 9bf0d36: saved time is the spoken phrase under date_time_text and context includes at most two prior worker utterances. Freeze and compare the intended training/inference prompt before approving an experiment.

## Reproduce B's mechanical checks

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/synthetic_gold/build_dataset.py --check
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/synthetic_gold -p 'test_*.py' -v
./gradlew :core:test
python3 tools/synthetic_gold/temporal_gold.py --captured-at 2026-10-11T00:10:00+05:30 --output /tmp/incident-gold-midnight.jsonl
python3 tools/synthetic_gold/temporal_gold.py --captured-at 2027-01-01T08:00:00+05:30 --output /tmp/incident-gold-year-boundary.jsonl
```

B's recorded results: 24 Python tests passed, 800/800 parser and prompt compatibility checks, and the longest record is 1080 tokens with the actual Qwen tokenizer; 32 exceed 1,024 and all fit the 4,096-token primary context. Normal Gradle was blocked on this host by a missing JDK; the core result came from fresh cached Kotlin compiler/JUnit execution. The full core run is **876/877 passing**, with one failing new regression. It replays all 320 new sequence calls from an empty report through the actual validator and merge, carrying actual output forward; time proposals are excluded and need your separate temporal review. **274 states match, 46 mismatch**, all tied to explicit severity none being rejected as negation, including downstream consequences. Gold remains correct; do not remove those cases to fit the bug. **Resolve/adjudicate this blocker and re-run the regression before clearance.** See runtime_update_audit.json for IDs. These checks are not independent semantic adjudication. No Qwen inference, training, NPU conversion, or measured model improvement occurred.

The dataset checks deliberately skip when data/synthetic_gold/v1 is absent from a submission mirror, as requested in A/018. Do not interpret that skip as dataset clearance. In the development checkout, the validator/merge regression remains enabled and failing for the documented app defect.

## Required reply and next step

Reply in C's own communication outbox, addressed to B, referencing B's request. Include the exact reviewed commit and gold hash, methods and number of rows independently reviewed, PASS/BLOCKED verdict, failing IDs, remaining limitations, and any proposed correction commit. Partial sampling is a partial review, not clearance of all 800 rows. Verify the transition links, changed-field masks, expected after-states, and no-op calls, including actual sequential replay rather than gold-state reseeding. Findings must be resolved and the resulting exact revision rechecked before the gate can close. Agent review must not be described as human adjudication.

After C reports completion, B will tell C that the next step is fine-tuning. **Training still requires human intervention and explicit approval of the experiment, checkpoint, compute/export feasibility, and evaluation plan.** C should present the review result and wait for the human user; do not autonomously launch training, spend on compute, export/replace model weights, or deploy. If review finds blockers, repair and re-verification precede that next step. A/017's separate export feasibility and user GO gates remain applicable.

# Incident extraction synthetic gold v1

800 English examples for **Qwen3-4B-Instruct-2507**: 600 train, 100 validation, 100 test. All **800 raw inputs contain supported answers to the four mandatory questions**: incident type, location, date/time phrase, and reporter-stated severity. Every time answer provides a resolvable **date and clock time**, possibly approximate. Optional fields vary in completeness. This reflects the user's final requirement; missing-mandatory training examples are excluded.

**Review gate: BLOCKED by the validator defect below and awaiting C's independent cross-verification. Do not use this revision for fine-tuning until C has cleared its labels and app compatibility.** After verification, the next step is a fine-tuning experiment involving the human user. C must not start training, provision compute, export weights, or deploy a model without the user's explicit go-ahead. The review scope and required verdict are in [B's handoff to C](../../../docs/handoff/B-synthetic-gold-to-C.md).

These are machine-checked synthetic gold **candidates**, not independently human-adjudicated labels. There are **224 scenario families**, not 800 independent incidents: 96 authored fact sets with four controlled variants each, 24 semantic-contrast pairs, 24 initial/follow-up pairs, and **80 four-turn update sequences**. The original 480 narratives, labels, saved states, and splits are preserved; 320 rows were added. Model-message formatting was refreshed for master 9bf0d36. Names, assets, and situations are fictional. No real incident records, recordings, or external teacher-model API were used. ASR-like noise is manufactured, not measured Whisper output.

## Files

| File | Purpose |
|---|---|
| `train.jsonl` | 600 SFT records; only `messages` |
| `validation.jsonl` | 100 development records; only `messages` |
| `test.jsonl` | 100 reserved records; do not train or tune on these |
| `gold.jsonl` | All records with IDs, family/split, raw text, seeded saved state, targets, mandatory-answer evidence, temporal oracle rules, tags, and notes |
| `system_prompt.txt` | Exact system instruction used by this dataset; not installed as the app default |
| `target.schema.json` | Sparse `update_report` target schema |
| `statistics.json` | Coverage counts and machine-validation results |
| `manifest.json` | Dataset, generator, and app-contract hashes |
| `tokenizer_check.json` | Actual Qwen tokenizer lengths and template hash |
| `existing_e_benchmark.json` | E's existing benchmark provenance, split sizes, and exact-overlap audit; no benchmark narratives copied |
| `preview.md` | Four original training examples for review |
| `update_preview.md` | Mandatory and optional corrections across four-turn training sequences |
| `runtime_update_audit.json` | Exhaustive new-sequence validator/merge replay; downstream failures remain blockers |
| `validation_results.json` | Recorded Python/core test results and app-compatibility checks |

Every training record has one system message, one user message, and one assistant message. Follow-ups are **independent calls with an explicit report snapshot**, matching master 9bf0d36's `PromptBuilder` and `ReportSession`. Up to two actual earlier worker utterances are included as context lines. Saved values are strings, including comma-separated names. `report_before` metadata retains the internal rendered `date_time`; the model sees its spoken phrase under `date_time_text`, as in the current app. The user message is exactly:

```text
Earlier the worker said: "previous worker utterance"
Report so far: {saved extraction fields in the app's field order}
Worker: "raw report"
```

The assistant content is a JSON **string**, containing exactly one `{"tool":"update_report","arguments":{...}}` object. Metadata, symbolic temporal rules, and resolved timestamps never enter model messages. The earlier-worker line is absent for initial reports and repeated at most twice for subsequent calls.

## Updating an already-reported answer

The added corpus contains **80 four-turn sequences**: initial report → correction → another correction → confirmation. There are eight sequences for each of the eight extraction fields, plus 16 sequences that change multiple fields. These add **160 actual update calls**, 80 initial reports, and 80 confirmations that should produce an empty patch. Saved-context rows increase from **24/480 (5%)** to **264/800 (33%)**; 178 rows replace at least one previously saved value.

| Field | Updates to a previously saved value, including original cases |
|---|---:|
| `incident_type` | 23 |
| `severity` | 28 |
| `location` | 29 |
| `date_time_text` | 36 |
| `persons_involved` | 30 |
| `equipment_asset` | 27 |
| `immediate_actions` | 26 |
| `witnesses` | 30 |

The cases cover severity upgrades and downgrades; incident reclassification; location/code corrections; relative-date and offset corrections; affected-person and witness spelling/replacement/addition/removal; role swaps; affected-asset replacement; action replacement/addition/removal; and several corrections in the same call. Updates retain unchanged mandatory answers in the current words, while the patch omits their already-saved values. Some confirmations repeat optional facts and mention a future inspection, which must not create a new action or witness.

Each new row's `transition` metadata records its turn index, previous row ID, operation, changed fields, frozen capture reference, and independently authored complete `expected_report_after`. The next turn's `report_before` must equal that state. Mechanical validation rejects a stale precondition or a patch that cannot produce the authored state. This state oracle is annotation metadata and is never supplied to the model as an answer. All four turns of a sequence stay in one split.

The sequences use controlled sentence patterns and a finite set of narrative values. They are targeted correction regressions, not independent natural conversations. The model still receives one app-format extraction call at a time, with the same two-utterance history window as `ReportSession`, rather than four chat-role messages.

## Label policy

- Mandatory answers must be supported in each raw input, including follow-ups. `mandatory_answers` stores a value and a literal supporting quote for each of the four fields. A follow-up patch can omit a mandatory field when that answer is unchanged in the saved report.
- All 12 app incident-type enums and all five severity levels occur. Severity always comes from an explicit rating or a stated synonym, such as “serious” → `severe`. Scale, urgency, injury, or panic never supplies severity. “No one was hurt” alone does not mean severity `none`.
- Vague variants keep all mandatory answers but omit optional details. They use relative/approximate time and severity synonyms. Hidden details from a fuller variant cannot be reconstructed.
- People affected, witnesses, helpers, and reporters have different roles. Only explicit named affected people and witnesses enter their respective lists. Unnamed groups do not create names. A person can have both roles if both are stated.
- Extract affected equipment/property, retaining the heard wording. Mere landmarks and response tools are excluded. Heard name/code mistakes are not silently corrected to gazetteer entries.
- Completed and ongoing actions are eligible; denied, future, conditional, and suggested actions are excluded. The examples chiefly cover completed actions, with planned-response distractors.
- Every emitted field has one exact contiguous evidence quote; evidence keys equal emitted field keys. For names lists, that quote covers every name and its role. An uncertain optional asset is proposed with its hedge retained so the app validator can mark it `SUGGESTED`.
- Missing optional fields are omitted. No nulls, empty strings/lists, invented names, or dummy `none` list members. No-op patches use empty arguments. Lists and action strings replace the saved field; addition examples restate the full desired value with current evidence.
- Description and recommended follow-up are derived by app code and are excluded from extraction targets. Urgency hints are excluded too.

The runtime parser is more permissive than the training schema. In particular, it accepts some null/empty values and unknown properties. The targets deliberately teach a consistent sparse format. The shipping `extract_v8.txt` and supplied SFT prompt both use sparse patches, but their full instructions differ. **Freeze the supplied system prompt for the training/inference comparison**, or measure any system-prompt mismatch explicitly. No shipping prompt was changed by this task.

## Relative time and dynamic gold

Qwen extracts `date_time_text` as the **spoken phrase**. The app's resolver uses device capture time to turn it into a calendar value. Evaluate these as two separate boundaries: extraction quality, then time-resolution quality. A reporting, isolation, or treatment time must not replace the incident time.

Every record has an independently authored `temporal_gold` rule. The evaluator can pull current date/time once or supply the exact app/evaluator capture instant to materialize expected timestamps:

```bash
python3 tools/synthetic_gold/temporal_gold.py \
  --captured-at now --output /tmp/incident-gold-resolved.jsonl

# Reproducible replay, including rollover across midnight:
python3 tools/synthetic_gold/temporal_gold.py \
  --captured-at 2026-10-11T00:10:00+05:30 \
  --output /tmp/incident-gold-resolved-midnight.jsonl
```

For an actual run, prefer **per-report capture timestamps** from the same clock the app used. Supply JSONL records like `{"id":"base-water_leak_flood-01-direct","captured_at":"2026-10-11T00:10:00+05:30"}` through `--captures`. Capture IDs must exactly match the selected gold IDs; pass the evaluated subset through `--gold`. Do not call “now” again after inference and use that later time as the reference.

```bash
python3 tools/synthetic_gold/temporal_gold.py \
  --gold /tmp/evaluated-gold-subset.jsonl \
  --captures /tmp/captures.jsonl --output /tmp/resolved-gold.jsonl
```

All resolved values use `Asia/Kolkata`, regardless of the evaluator machine's timezone. Numeric relative offsets use minute precision; approximation remains flagged. For example, at `00:10` on 11 October, “about twenty minutes ago” has the reference point `23:50` on 10 October, marked approximate.

All training inputs have enough information to resolve both date and clock. Broad expressions were re-authored with explicit missing details **in the input itself**: for example, “last Friday at 3 PM,” “Thursday of last week at about 1 PM,” and “two hours after the 9 AM class today.” Approximate phrases remain marked approximate; no arbitrary scoring tolerance is supplied. “Last Friday” means the preceding Friday strictly before the reference date. The oracle also retains broader expressions solely for unit tests that prove missing precision is not manufactured; those incomplete phrases are excluded from the final training records.

The synthetic SFT saved-state strings are authored snapshots, some using a fixed 11 AM IST reference on 11 October 2026. They teach extraction/comparison. For live seeded-context evaluation, materialize a matching saved date/time from its source phrase using the same reference, or replay with the original snapshot reference. Do not compare a newly resolved current date against an old saved snapshot and call that a model error.

## Existing E data

E already supplied `app/src/main/assets/eval/gold_v1.jsonl` on `origin/e/data-eval`: **40 English incidents, 27 dev / 13 holdout**, with 34 dev turns. E also supplied **8 homophone pairs, 6 dev / 2 holdout**. The exact inspected revision and file hashes are in `existing_e_benchmark.json`.

That dataset labels final report states and has its own scorer. This dataset labels **per-call extraction patches**. They are not interchangeable. E's records were not copied into training; their held-out narratives were not displayed or used for authoring. An in-process hash comparison found **zero exact utterance overlaps** across E's clean/misheard text and these new reports. This does not establish the absence of semantic overlap.

Keep E's held-out data as an external benchmark. The 100 synthetic test rows hold out complete families, but they share generation patterns with training. They are useful regression cases and cannot establish real-world generalization by themselves.

The current app evaluation reader expects E's `dev`/`holdout` final-state schema and the `EvalRunner` filters for `split == dev` and consumes `turns`, ignoring the saved-state preconditions and patch labels here. **Do not pass these files directly to that runner.** Use the explicit `messages` inputs and `target` outputs in a per-call evaluator, or write an adapter that preserves the seeds and per-call capture times.

## Reproduce and validate

No Python packages are required for generation, validation, or temporal materialization:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/synthetic_gold/build_dataset.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/synthetic_gold/build_dataset.py --check
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/synthetic_gold -p 'test_*.py' -v
```

The checks reject unsupported enums/fields, bad shapes, empty values, fabricated evidence, ungrounded literal values, repeated saved values, missing mandatory-answer annotations, duplicate inputs, split leakage, export mismatches, and manifest drift. Temporal regressions cover rollover, timezone conversion, dynamic dates, missing precision, and unknown phrases. Quote presence is a mechanical check, not proof of semantic entailment; independent human adjudication remains outstanding.

`SyntheticGoldContractTest` additionally checks all 800 tool targets against the actual Kotlin parser and all model inputs against the actual `PromptBuilder`. Normal invocation is `./gradlew :core:test` with the repository's required JDK 17.

For this revision, **24 Python tests passed** and the parser/prompt contract checks passed for all **800** records. The full core suite ran **877 tests: 876 passed and one failed**. The new exhaustive sequence validator/merge regression exposes a downstream severity bug; see `runtime_update_audit.json` and the blocker below. The host has a Java runtime but no JDK, so normal Gradle execution was blocked. The fallback freshly compiled all core/test Kotlin with cached K2 and serialization compiler plugins, targeted JVM 17 bytecode, and ran JUnit on the Java 21 runtime. No repository toolchain setting was changed. See `validation_results.json`.

## Known blocker from sequential replay

The new regression runs every update sequence from an empty report through the real parser, `PayloadValidator`, and `ReportMerge`. It carries actual prior output forward rather than reseeding from the expected gold state. Resolved `date_time` proposals are excluded from this audit because time resolution has its own oracle and documented resolver gaps. The per-call parser/prompt check still includes every time extraction target.

The validator treats the explicit severity rating **“none” as negation** and rejects a valid `severity: none` target. This can leave an old severity in the report when the reporter changes the rating to none, or leave severity missing in an initial report; later no-op calls cannot recover it. The correct labels are retained. This is a downstream app failure, not evidence that the SLM returned wrong JSON. C must resolve or explicitly adjudicate it and re-run the regression before clearing the training/integration gate. Of 320 replayed calls, **274 match and 46 mismatch**; the mismatches include downstream consequences of the rejected none rating. The exact affected IDs and cascading replay outcomes are saved in `runtime_update_audit.json`. This audit did not run the SLM and is not a model accuracy score.

The new validator/merge test currently fails for this documented reason. Do not describe the full core suite as green, and do not remove correct gold cases merely to make an app defect disappear.

## Training and measurement

Load only `train.jsonl` for training and `validation.jsonl` for model selection. Use Qwen's native chat template and train on assistant tokens. TRL supports these conversational `messages` records and assistant-only loss; generation-marker support depends on the installed template/version. Verify the loss mask rather than assuming it is correct. [TRL SFT documentation](https://huggingface.co/docs/trl/sft_trainer).

`tokenizer_check.json` records actual lengths from the Qwen3-4B-Instruct-2507 tokenizer, including this dataset's system prompt and chat template. With the current app history window, the longest complete record is **1080 tokens**; 32 records exceed 1,024 but all fit within the **4,096-token** primary deployment context. A 1,024-token bundle would need history trimming or a separate export and fresh length/contract checks. Changes to input, output, system prompt, or template require a new check.

Compare the unfine-tuned baseline and the fine-tuned candidate with identical prompts, tokenizer, decoding settings, capture references, and post-processing. Measure the update slice separately: changed-field precision/recall, stale saved values after a correction, preservation of unchanged fields, full-list replacement, repeated-correction success, final sequence state, and no-op accuracy. Report mandatory and optional update fields separately, and include the prior-state precondition rather than testing only the raw sentence.

Track first-call JSON validity; type/location/time/severity accuracy; each optional field's precision and recall; hallucinated fields/names; evidence validity; and relative-time resolution separately. For resolved time, compare the precision actually stated, not an invented point for an interval. Exact evidence wording may have multiple correct alternatives; do not score JSON key order or an equally valid supporting quote as semantic errors.

**No fine-tuning or Qwen inference was run in this task. Improvement is unmeasured.** The NPU bundle and its deployment were not modified.

Master 9bf0d36 expands the app resolver's offset number words, including fifteen, twenty, and fifty, but does not cover every annotated weekday, anchored offset, or spoken-clock phrase here. It also loses the approximate flag in its numeric-offset path. Use these cases to separate resolver defects from extraction errors; adding synthetic labels alone cannot repair downstream code.

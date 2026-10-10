# Incident extraction synthetic gold v1

480 English examples for **Qwen3-4B-Instruct-2507**: 360 train, 60 validation, 60 test. All **480 raw inputs contain supported answers to the four mandatory questions**: incident type, location, date/time phrase, and reporter-stated severity. Every time answer provides a resolvable **date and clock time**, possibly approximate. Optional fields vary in completeness. This reflects the user's final requirement; missing-mandatory training examples are excluded.

**Review gate: awaiting C's independent cross-verification. Do not use this revision for fine-tuning until C has cleared its labels and app compatibility.** After verification, the next step is a fine-tuning experiment involving the human user. C must not start training, provision compute, export weights, or deploy a model without the user's explicit go-ahead. The review scope and required verdict are in [B's handoff to C](../../../docs/handoff/B-synthetic-gold-to-C.md).

These are machine-checked synthetic gold **candidates**, not independently human-adjudicated labels. There are **144 scenario families**, not 480 independent incidents: 96 authored fact sets with four controlled variants each, 24 semantic-contrast pairs, and 24 initial/follow-up pairs. Names, assets, and situations are fictional. No real incident records, recordings, or external teacher-model API were used. ASR-like noise is manufactured, not measured Whisper output.

## Files

| File | Purpose |
|---|---|
| `train.jsonl` | 360 SFT records; only `messages` |
| `validation.jsonl` | 60 development records; only `messages` |
| `test.jsonl` | 60 reserved records; do not train or tune on these |
| `gold.jsonl` | All records with IDs, family/split, raw text, seeded saved state, targets, mandatory-answer evidence, temporal oracle rules, tags, and notes |
| `system_prompt.txt` | Exact system instruction used by this dataset; not installed as the app default |
| `target.schema.json` | Sparse `update_report` target schema |
| `statistics.json` | Coverage counts and machine-validation results |
| `manifest.json` | Dataset, generator, and app-contract hashes |
| `tokenizer_check.json` | Actual Qwen tokenizer lengths and template hash |
| `existing_e_benchmark.json` | E's existing benchmark provenance, split sizes, and exact-overlap audit; no benchmark narratives copied |
| `preview.md` | Four concrete training examples for review |
| `validation_results.json` | Recorded Python/core test results and app-compatibility checks |

Every training record has one system message, one user message, and one assistant message. Follow-ups are **independent calls with an explicit report snapshot**, matching this checkout's `PromptBuilder`; they are not chat history that the current app never sends. Saved values are strings, including comma-separated names and the rendered `date_time`. The user message is exactly:

```text
Report so far: {saved fields in the app's field order}
Worker: "raw report"
```

The assistant content is a JSON **string**, containing exactly one `{"tool":"update_report","arguments":{...}}` object. Metadata, symbolic temporal rules, and resolved timestamps never enter model messages.

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

The runtime parser is more permissive than the training schema. In particular, it accepts some null/empty values and unknown properties. The targets deliberately teach a consistent sparse format. This checkout's default system prompt still shows nulls/empty lists: **use the supplied system prompt for the training/inference comparison**, or measure that mismatch explicitly. No shipping prompt was changed by this task.

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

Keep E's held-out data as an external benchmark. The 60 new synthetic test rows hold out complete families, but they share generation patterns with training. They are useful regression cases and cannot establish real-world generalization by themselves.

The current `EvalRunner` filters for `split == dev` and consumes `turns`, ignoring the saved-state preconditions and patch labels here. **Do not pass these files directly to that runner.** Use the explicit `messages` inputs and `target` outputs in a per-call evaluator, or write an adapter that preserves the seeds and per-call capture times.

## Reproduce and validate

No Python packages are required for generation, validation, or temporal materialization:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/synthetic_gold/build_dataset.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/synthetic_gold/build_dataset.py --check
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/synthetic_gold -p 'test_*.py' -v
```

The checks reject unsupported enums/fields, bad shapes, empty values, fabricated evidence, ungrounded literal values, repeated saved values, missing mandatory-answer annotations, duplicate inputs, split leakage, export mismatches, and manifest drift. Temporal regressions cover rollover, timezone conversion, dynamic dates, missing precision, and unknown phrases. Quote presence is a mechanical check, not proof of semantic entailment; independent human adjudication remains outstanding.

`SyntheticGoldContractTest` additionally checks all 480 tool targets against the actual Kotlin parser and all model inputs against the actual `PromptBuilder`. Normal invocation is `./gradlew :core:test` with the repository's required JDK 17.

For this task, **18 Python tests and 793 core tests passed**, including all 480 parser/prompt compatibility checks. The host has a Java runtime but no JDK, so normal Gradle execution was blocked. The fallback freshly compiled all core/test Kotlin with cached K2 and serialization compiler plugins, targeted JVM 17 bytecode, and ran JUnit on the Java 21 runtime. No repository toolchain setting was changed. The temporary runner initially included an abstract test-contract base; that harness error was corrected before the successful full run. See `validation_results.json`.

## Training and measurement

Load only `train.jsonl` for training and `validation.jsonl` for model selection. Use Qwen's native chat template and train on assistant tokens. TRL supports these conversational `messages` records and assistant-only loss; generation-marker support depends on the installed template/version. Verify the loss mask rather than assuming it is correct. [TRL SFT documentation](https://huggingface.co/docs/trl/sft_trainer).

`tokenizer_check.json` records actual lengths from the Qwen3-4B-Instruct-2507 tokenizer, including this dataset's system prompt and chat template. Every complete record fits within 1,024 tokens and hence within the 4,096-token deployment context recorded in the app. Changes to input, output, system prompt, or template require a new check.

Compare the unfine-tuned baseline and the fine-tuned candidate with identical prompts, tokenizer, decoding settings, capture references, and post-processing. Track first-call JSON validity; type/location/time/severity accuracy; each optional field's precision and recall; hallucinated fields/names; evidence validity; and relative-time resolution separately. For resolved time, compare the precision actually stated, not an invented point for an interval. Exact evidence wording may have multiple correct alternatives; do not score JSON key order or an equally valid supporting quote as semantic errors.

**No fine-tuning or Qwen inference was run in this task. Improvement is unmeasured.** The NPU bundle and its deployment were not modified.

The current app resolver's English offset lexicon includes number words only through twelve and does not cover every annotated relative or spoken-clock phrase here. It also loses the approximate flag in its numeric-offset path. Use these cases to separate resolver defects from extraction errors; adding synthetic labels alone cannot repair downstream code.

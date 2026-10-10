# B extraction prompt candidates

These are **unmeasured experimental prompts**, prepared for A's requests 012 and 013. Local checks validate examples and schema; they do not measure model accuracy. The shipping default is unchanged.

## Files and run order

1. A's `extract_v8.txt` baseline, from the app session's branch (not present in this checkout). Record its hash and the exact app commit.
2. `place_context.txt`: explicit location spans, fields beside evidence, time/equipment/actions and context recovery.
3. `compact_context.txt`: the same contract with fewer rules/examples and shorter quotes. Actual deployment token counts and runtime remain to be measured.

`candidate_a.txt` through `candidate_f.txt` preserve the six strategies from root `prompts.md`, correcting people to nonempty JSON lists. They are reserve alternatives, not six mandatory kit runs. The root draft also has its people examples corrected; its old approximate token counts were removed.

The new site-based probes use names from the committed fictional `core/src/main/resources/site/site.json`. `Block B-2` is the fictional code in A's diagnosis, not a new gazetteer entry. Reserve prompts retain fictional examples such as Room 12 and Block C that need not appear in the directory. Codes are preserved as spoken; D's post-processing owns any suggested expansion. Keep `near/outside` if dropping it would move the incident into a site.

## Input prerequisite

A/013 says the app session will provide the last two transcript turns as `Earlier the worker said: ...` and schema-aligned saved-report keys with spoken time phrases. **Verify that app change is present before testing context recovery.** This checkout's `PromptBuilder` still sends only `Report so far` and the newest sentence, and renders `date_time` from an internal report value. The validator already sees the combined transcript history; B changes neither builder nor validator.

Example input:

```text
Earlier the worker said: "There was a huge fire in Block B-2 today morning."
Report so far: {"incident_type":"fire_smoke","date_time_text":"today morning"}
Worker: "Sam is trapped there; a lot of people saw it."
```

The missing location may be recovered from that one unambiguous antecedent; evidence quotes the earlier location. If location is already saved, the patch omits it. With no unique antecedent, it stays absent.

## Kit operator commands (A only)

Use a debug APK with the input prerequisite above. The existing `MainActivity` supports `eval`, `prompt` and `run` intent extras; `EvalRunner` filters to `split == dev`. Start from A/E's current gold, not the diagnostics below. These shell variables intentionally require paths from the operator; B has not verified the kit's current file layout.

```bash
candidate=docs/experiments/B-20261011-prompt-candidates/place_context.txt
kit_prompt_tmp=/data/local/tmp/b-place-context.txt
adb push "$candidate" "$kit_prompt_tmp"
adb shell run-as com.megathon.incident cp "$kit_prompt_tmp" files/b-place-context.txt

# Set kit_app_files to the absolute app files directory and kit_gold to the
# absolute readable path to A/E's existing gold file before this command.
: "${kit_app_files:?set the app files directory}"
: "${kit_gold:?set the existing gold file path}"
adb shell am force-stop com.megathon.incident
adb shell am start -n com.megathon.incident/.MainActivity \
  --es eval "$kit_gold" --es prompt "$kit_app_files/b-place-context.txt" \
  --es run b-place-context
```

Repeat with a distinct filename/run name for `compact_context.txt` and v8. Collect results **after the completion log** (`Incident`, `eval <run> done`):

```bash
mkdir -p /tmp/b-place-context-results
adb exec-out run-as com.megathon.incident cat files/eval/b-place-context/raw.jsonl \
  > /tmp/b-place-context-results/raw.jsonl
adb exec-out run-as com.megathon.incident cat files/eval/b-place-context/predictions.jsonl \
  > /tmp/b-place-context-results/predictions.jsonl
adb exec-out run-as com.megathon.incident cat files/eval/b-place-context/meta.json \
  > /tmp/b-place-context-results/meta.json
```

A's estimate is ~3.5 minutes per prompt; B has not measured it. Check path readability and scratch-file naming before running. Use the team's existing scorer and unchanged gold. `docs/experiments/llm-prompts/score_run.py` contains paths to A's scratch environment, so it is not a portable local scoring command; A/E must use their known scorer rather than B rewriting it.

## Diagnostic cases

`hard_cases.jsonl` is **five authored synthetic cases**, not E's gold or real Whisper measurements. Every row has `turns`, one `expected_updates` tool envelope per turn, and human-readable `checks`. `reports_before`, where present, deliberately seeds the saved report for each turn; earlier turns supply context. It makes missing-field recovery reproducible even if the previous turn succeeds. Do **not** pass these rows directly to EvalRunner: it can read `id`, `split` and `turns`, but ignores `reports_before` and `expected_updates`, so it silently loses the seeded preconditions. A must use a manual/model-test route that explicitly supplies each seeded saved report and up to two earlier worker turns. B has not verified that such an operator route exists. If it is unavailable, run only the unseeded scenarios and mark the seeded diagnostics pending; do not edit E's gold or merge them into its score denominator.

Five scenarios cover article/code plus `right now`, a meaningful landmark/proximity, two places, A's three-turn failure, and missing/ambiguous antecedents. Expected outputs show supported updates; a different exact quote or action span can still be correct. Check schema validity and meaning, not byte-for-byte JSON ordering. Omit inferred severity and unnamed group witnesses.

## Comparison contract

Run v8 and the two focused candidates on the same 27 dev cases, app/input format, model bundle and generation settings; reserve the 13 held-out cases. Preserve raw outputs and all hashes. Report valid first call / after one repair as num/den, mandatory precision/recall, overall recall and hallucinations as num/den, location/equipment/action breakdowns, elapsed median/p90, generated tokens and prompt tokens. Count evidence-only and unknown-key failures separately. Inspect diagnostic failures independently from dev score.

A/012's reported v8 baseline: 88% first-call validity, 97% after retry, 90% mandatory precision, 65% mandatory recall, 64/135 overall recall, 4% hallucinations. Counts other than 64/135 were not supplied, so obtain raw results rather than inventing denominators. This is a quoted team measurement, not a fresh B run. The committed older A experiment documents a different prompt/run. One run per prompt; differences under about five percentage points are inconclusive. Choose/promote a candidate only after measurement; reject speed gains that sacrifice validity or grounding.

A/016 subsequently reports a recall-parser patch moving dev recall from 47% to 65%. Rerun v8 and candidates on that same patched build/input format; do not attribute a parser change to prompt improvement. B message 010 requests this comparison.

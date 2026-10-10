# B Prompt Tuning Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan in the current B checkout, task by task. Steps use checkbox syntax for tracking.

**Goal:** Deliver schema-correct extraction prompts and diagnostic cases for A to evaluate on Qwen3-4B, then continue the accent contest when usable recordings arrive.

**Architecture:** B supplies plain-text system prompts and a separate diagnostic JSONL file. A supplies the app's input history and runs the existing parser, validator and scorer on the kit; D owns place snapping. Candidates remain experiments until measured.

**Tech Stack:** UTF-8 text, JSONL, existing Kotlin ToolCallParser/PayloadValidator, existing Android EvalRunner and Python accent contest.

**Spec:** `origin/communication:communication/A-lead-kit-app/messages/012-to-B-prompt-tuning-for-place-names.md` and `013-to-B-kit-diagnosis-for-prompt-side.md`; schema in `core/src/main/kotlin/com/megathon/incident/domain/Model.kt`.

## Global Constraints

- B works on `b/speech-engines`, merges `origin/master` before any code push; only A merges master and operates the kit.
- `persons_involved` and `witnesses` are JSON lists; every evidence value is one string; every evidence key must also be a field with a value; no null, `[]` or `""`; each key once; no grammar on QAIRT.
- Use fictional site names from `core/src/main/resources/site/site.json`; keep spoken codes instead of inventing a canonical site name.
- Do not tune on the held-out 13, edit the scorer or the gold, or lower grounding thresholds.
- A implements input history and schema-aligned saved-report keys. D implements place matching. B edits neither module.
- Keep public/proxy, synthetic, phone and kit evidence separate; keep WER normalization unchanged.

## Review Focus

- Article before a code: `a SH2` must yield `SH2`, with unchanged spoken evidence (Task 2, hard-01).
- Two places: identify the incident location rather than the reporter's route (Task 2, hard-03).
- Pronoun without a unique antecedent: omit the location rather than guess (Task 2, hard-05).
- Evidence-only output or invented `date_time`: emit supported arguments with exact schema keys (Task 2, hard-04).
- A landmark changes meaning: preserve `near/outside` when the incident is not inside the named site (Task 2, hard-02).

---

### Task 1: Repair the six existing candidates

**Files:** Modify `prompts.md`; create `docs/experiments/B-20261011-prompt-candidates/candidate_[a-f].txt`.

**Interfaces:** Consume the six fenced system prompts in the user's document. Produce six directly loadable UTF-8 system prompt files.

- [x] Convert people fields to nonempty lists in rules and examples; retain single-string evidence.
- [x] Preserve candidate strategies, scenario text and field semantics; remove outdated token estimates.
- [x] Verify every JSON example against the existing wire schema, duplicate keys, evidence pairing and enum values; inspect the diff. Text edits need artifact validation, not a new production test framework.

### Task 2: Add focused candidates and diagnostic cases

**Files:** Create `docs/experiments/B-20261011-prompt-candidates/place_context.txt`, `compact_context.txt`, `hard_cases.jsonl`, and `README.md`.

**Interfaces:** Input is `Report so far`, optional `Earlier the worker said` lines, and newest `Worker` words. Output is one `update_report` envelope. Each diagnostic row has `id`, `split: dev`, `source: synthetic_diagnostic`, `turns`, `expected_updates`, and `checks`; it is separate from E's gold and is not a Judge Bench scoring manifest.

- [x] Author a detailed candidate covering location spans/codes, completed actions, equipment, `right now`, named-person lists, context and field/evidence pairing.
- [x] Author a shorter candidate with the same contract and shorter supporting quotes; preserve enough context for negation/action grounding.
- [x] Supply five diagnostic scenarios: article/code, landmark, two places, three-turn failure reproduction, and absent/ambiguous antecedent.
- [x] Validate all example/expected JSON, exact evidence provenance, and no empty or unknown fields. Confirm each candidate includes every contract rule.
- [x] Write operator commands using existing debug intent extras. Make A's history input prerequisite and unavailable local v8 resource explicit.

### Task 3: Prepare reviewable handoffs

**Files:** Create `docs/experiments/B-20261011-prompt-candidates/handoff-to-A.md` and `handoff-to-D.md`.

**Interfaces:** A receives candidate paths, run order and requested metrics. D receives exact fictional phrases and expected spans, not a replacement matching implementation.

- [x] Draft a request for a v8 baseline and `place_context`, then `compact_context` on the same 27 dev cases, build/input format and decode settings; hold six alternatives in reserve.
- [x] Draft the request for first-call/retry-valid num/den, mandatory precision/recall, overall recall, hallucinations, equipment/action/location counts and timing/token summaries. Do not invent baseline counts missing from A's message.
- [x] Explain that article/code diagnosis is prompt extraction; canonical expansion stays a suggestion under D's rules. Include no invented kit timings.
- [x] Commit B-owned experimental files and the plan after validation. Leave the user's root draft untracked unless explicitly adding it is desired. Prepare communication locally; publishing messages requires authorization.

### Task 4: Measure and choose (depends on A)

- [ ] A runs the same 27 dev cases: v8 first, then the two focused candidates on the same build after the recall-parser patch (A/016 reports 65% recall). Estimate ~3.5 minutes per prompt; not measured by B.
- [ ] Compare counts, not rounded percentages. Treat differences under about five points as noise from one run. Do not promote a shorter variant at the cost of validity or grounding.
- [ ] Examine the five diagnostics separately, preserving raw outputs and the input-history format. Document a measured winner or leave the default unchanged if inconclusive.

### Task 5: Resume speech evaluation (depends on data)

- [x] Prepare `tools/accent_contest/contest.py` with optional `--raw-transcripts` and repeatable exact `--select KEY=VALUE` metadata filters. Keep legacy predictions and WER normalization unchanged. Verify typed raw text, device/split exclusion, missing-metadata errors and CLI file outputs with `python3 -m unittest -v` from the tool directory (12 tests).
- [x] Audit existing public proxy metadata first: the 80-clip run already exists; its broad South-Asia accent label and unverified mirror licence/release prevent treating it as a newly approved strict Indian-only benchmark.
- [x] Decide whether another set is material: A/016 now says about 35 real clips exist. Prioritize locating and scoring them; a second public corpus is unnecessary for this task. Retain existing proxy limitations and avoid another download/run.
- [ ] When E sends labeled 16 kHz WAVs, run unchanged WER and export per-clip `{clip_id, model, text}` rows; report phone/kit subsets separately and forward raw error pairs to C/D/E once authorized.

## Execution decisions

- Implement the prompt deliverables inline: one tightly coupled artifact batch does not benefit from parallel implementers.
- Reuse the existing B checkout; it is on B's branch and has only the user-owned untracked draft. Preserve that draft and merge the latest master without rebasing.
- No shipping prompt change or claim of accuracy improvement is authorized by local JSON validation. Kit measurement remains pending.

## Implementation record

- 2026-10-11: merged master d3d1cc3 into B (merge 6d13586). Eight prompts and five diagnostics written; root draft fixed but remains untracked.
- Artifact verification: 44 JSON example/expected envelopes passed strict keys, people-list types, evidence pairing, enums and exact diagnostic evidence provenance. Compact/full lengths: 2,354/3,611 characters; tokenizer and kit runtime unmeasured.
- Existing off-repo proxy metadata/manifest confirmed: revision 536a87197f1ebe18f844352c0601fc4c740cfe96, seed 20261011, 80 clips, 19 speakers. No download/inference repeated.
- Initial artifact batch ended at prepared handoffs; user's follow-up authorizes completing delivery. Measurement depends on A; genuine accent results depend on E.
- Review fixes: explicitly typed evidence as an object in both focused prompts; corrected diagnostic-run instructions because EvalRunner ignores seeded reports rather than rejecting this file shape. A needs an explicit seeded-input route, otherwise those probes stay pending.
- Review follow-through: clarified that the pronoun rule does not suppress explicit corrected locations, and distinguished site-list probe names from the retained fictional reserve examples. No outstanding review findings.
- Final artifact check passed again after review: 44 envelopes, eight prompts, five diagnostics; six reserve exports equal the corrected root draft. Staged diff check passed. Task 3 delivery is committed locally; handoffs are drafts.
- Follow-up: prompt commit 9625063 pushed. Communication commit 5ce0cf6 delivered B/010 to A (same-build baseline/results), B/011 to D (span probes), B/012 to E (real audio/manifest), and updated B's read boundary through A=016. The handoff notes in the artifact directory preserve their original draft wording; the sent messages are the delivery record.
- Follow-up implementation: four new contest tests failed before the raw-export/selection implementation, then all 11 tool tests passed. No model inference or real-audio score was produced. User was asked for the recording folder/manifest immediately; A/E were asked through communication.
- Tool review fix: a null/blank/non-string selected metadata value could have silently removed an unlabeled clip. Regression cases failed, then passed after explicit metadata validation; 12 tests pass. Output collision test confirms file preservation before input reading/inference. Independent reviewer confirmed no unresolved code findings.
- Raw export exercised on 320 cached public-proxy predictions (four models); exact text and unique clip/model rows preserved, output kept off-repo. No new inference or accuracy run. Drive discovery failed because the connector is disconnected; recording paths remain a required input.

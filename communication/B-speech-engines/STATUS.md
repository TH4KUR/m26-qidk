# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Snapshot.
Last updated: 2026-10-11 04:25 IST
Read up to: A=017 C=009 D=000 E=010

## Doing now
- Synthetic extraction pilot pushed; awaiting C's independent cross-verification (B/014). Dataset remains gated before fine-tuning; human approval is required for the next experiment.
- Ready to compare A's prompt results. E/010 now provides the phone-recording folder and manifests; its requested audio contest/export is queued separately from the human's current dataset push/review task.

## Done and ready for others (with file/commit)
- b/speech-engines 959d671: 480 synthetic gold candidates (360/60/60, 144 families), generator/dynamic temporal oracle, app parser/prompt contract test, data card, and C review checklist. All raw inputs answer all mandatory questions. 18 Python tests and 793 core tests passed via the documented Kotlin/JUnit fallback; no Qwen inference or training, no independent semantic/human adjudication yet.
- b/speech-engines pushed at 6ea0e5b, including prompt commit 9625063 and master d3d1cc3 merge 6d13586.
- Raw export and exact subset filters in tools/accent_contest/contest.py: 12 tests pass, independent review findings fixed. Commands in tool README.
- Exported 320 cached public-proxy rows off-repo, no new inference; format validation only.
- Eight experimental prompts, five synthetic diagnostics and operator commands: docs/experiments/B-20261011-prompt-candidates/. Forty-four example/expected envelopes checked; independent review issues fixed. Candidates unmeasured; default unchanged.
- Plan: docs/superpowers/plans/2026-10-11-b-prompt-tuning.md. Six reserves corrected, two focused full/compact candidates added.
- Existing off-repo proxy confirmed: 80 clips/19 speakers; no repeat inference/download. Broad accent and mirror provenance limits retained.
- Speech contracts/signals/engine code already on master. No WhisperKit/JNI work; A operates kit.

## Blocked on / waiting for
- C: full dataset review and exact-revision verdict on B/014. After verification, fine-tuning is next only with human intervention and explicit approval; do not run it autonomously.
- A: deployed v8/candidate results on same build/settings and input-history patch (B/010).
- E/010: 14 scripted references are supplied; 20 own-words references remain unchecked. Audio access and contest/export execution are not verified by this dataset handoff.

## Needs from others
- C: acknowledge B/014, independently cross-verify labels and temporal/validator/deployment compatibility, return PASS/BLOCKED with reviewed revision and failing IDs.
- A: relay B/010; return raw/prediction/meta files and hashes.
- D: probes in B/011; tell A results/commit.
- E: human-checked own-words references; carry E/010's speaker/noise/final-state/holdout limits into any future phone table.

## Current limitation
- E/010 has now supplied a Drive link and phone manifests, but this dataset task has not verified access or run speech inference. Prompt efficacy and real-speech WER remain unmeasured by B. Handoffs 010-014 are sent; C's dataset review is pending.

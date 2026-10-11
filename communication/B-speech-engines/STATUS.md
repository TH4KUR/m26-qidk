# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Snapshot.
Last updated: 2026-10-11 07:41 IST
Read up to: A=018 C=009 D=004 E=010

## Doing now
- Expanded update-heavy dataset pushed at 51c1124; C replacement review requested (B/016). Training blocked by the stated-none validator bug and pending independent review; human approval is required before any fine-tuning.
- Ready to compare A's prompt results. E/010 now provides the phone-recording folder and manifests; its requested audio contest/export is queued separately from the human's current dataset push/review task.

## Done and ready for others (with file/commit)
- 51c1124, including master 9bf0d36: 800 synthetic candidates (600/100/100), 80 new four-turn sequences, 264 saved-context calls, all eight fields updated. Current app history/time-key input contract checked on all 800; 24 Python tests pass. Core fallback 876/877 passing, one explicit validator regression fails. Runtime replay audit: 46/320 non-temporal after-states mismatch due to none-rating rejection; no inference/training. Dataset tests skip only when the data folder is absent from a submission mirror; mirror dry-run blind scan clean (306 files).
- Original, superseded pilot at 959d671: 480 synthetic gold candidates (360/60/60, 144 families), generator/dynamic temporal oracle, app parser/prompt contract test, data card, and C review checklist. All raw inputs answer all mandatory questions. 18 Python tests and 793 core tests passed via the documented Kotlin/JUnit fallback; no Qwen inference or training, no independent semantic/human adjudication yet.
- b/speech-engines pushed at 6ea0e5b, including prompt commit 9625063 and master d3d1cc3 merge 6d13586.
- Raw export and exact subset filters in tools/accent_contest/contest.py: 12 tests pass, independent review findings fixed. Commands in tool README.
- Exported 320 cached public-proxy rows off-repo, no new inference; format validation only.
- Eight experimental prompts, five synthetic diagnostics and operator commands: docs/experiments/B-20261011-prompt-candidates/. Forty-four example/expected envelopes checked; independent review issues fixed. Candidates unmeasured; default unchanged.
- Plan: docs/superpowers/plans/2026-10-11-b-prompt-tuning.md. Six reserves corrected, two focused full/compact candidates added.
- Existing off-repo proxy confirmed: 80 clips/19 speakers; no repeat inference/download. Broad accent and mirror provenance limits retained.
- Speech contracts/signals/engine code already on master. No WhisperKit/JNI work; A operates kit.

## Live inference (separate task, 2026-10-11): pushed, unbuilt on Android, unmeasured
- b/speech-engines c901d89: resident speech worker source, protocol client, incremental transcript + live coordinator, provisional preview in ReportSession, shared accelerator lane, launch warm-up, latency log. Details and requests in B/017.
- Verified on the JVM only: :core:test 943 tests, 1 failure (SyntheticGoldContractTest, validator defect fixed on c/core-rules 35a96dc). Not built (no Android SDK/NDK here); worker.cc only syntax-checked against sherpa-onnx 9db1af1.
- Waiting on A: SDK build, worker build, kit slot for batch vs live measurements. Not for master until measured.

## Blocked on / waiting for
- C: exact-revision review on B/016, including resolution/recheck of stated severity none being rejected as negation. After verification, fine-tuning is next only with human intervention and explicit approval; do not run it autonomously.
- A: deployed v8/candidate results on same build/settings and input-history patch (B/010).
- E/010: 14 scripted references are supplied; 20 own-words references remain unchecked. Audio access and contest/export execution are not verified by this dataset handoff.

## Needs from others
- C: acknowledge B/016, independently cross-verify all 800 labels, temporal and update transitions, fix/adjudicate the validator blocker, return exact-revision PASS/BLOCKED.
- A: relay B/010; return raw/prediction/meta files and hashes.
- D: probes in B/011; tell A results/commit.
- E: human-checked own-words references; carry E/010's speaker/noise/final-state/holdout limits into any future phone table.

## Current limitation
- E/010 has now supplied a Drive link and phone manifests, but this dataset task has not verified access or run speech inference. Prompt efficacy and real-speech WER remain unmeasured by B. Handoffs 010-016 are sent; C's replacement dataset review is pending and training remains gated.

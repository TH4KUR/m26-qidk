# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Snapshot.
Last updated: 2026-10-11 08:16 IST
Read up to: A=018 C=009 D=004 E=010

## Doing now
- **800-row synthetic dataset ABANDONED for fine-tuning (user decision, B/019).** Overlap evidence: docs/experiments/B-20261011-dataset-overlap/ on b/speech-engines. No training, export or requantization on it. Kept only as a parser/validator/merge regression fixture.
- Presence-sentence gold for A/032 (16 items, authored after failures, not dev/held-out) and a wording review of C's extract_v12; results message follows.
- Paced replay harness (core) for the batch vs live kit comparison; app hook pending.

## Done and ready for others (with file/commit)
- Live-inference work: see the section below and B/017.
- b/speech-engines holds, besides the above: prompt candidates and raw-export/subset tools (6ea0e5b) as before; the superseded 51c1124 dataset is marked abandoned.
- Eight experimental prompts and synthetic diagnostics: docs/experiments/B-20261011-prompt-candidates/ (unchanged).

## Live inference (separate task, 2026-10-11): pushed, unbuilt on Android, unmeasured
- b/speech-engines c901d89: resident speech worker source, protocol client, incremental transcript + live coordinator, provisional preview in ReportSession, shared accelerator lane, launch warm-up, latency log. Details and requests in B/017.
- Verified on the JVM only: :core:test 943 tests, 1 failure (SyntheticGoldContractTest, validator defect fixed on c/core-rules 35a96dc). Not built (no Android SDK/NDK here); worker.cc only syntax-checked against sherpa-onnx 9db1af1.
- Waiting on A: SDK build, worker build, kit slot for batch vs live measurements. Not for master until measured.

## Blocked on / waiting for
- A: SDK build, worker build and a kit slot for the live-inference measurements (B/017).
- The user: any new training set, FLEURS download, or Drive download of the phone clips is not started and needs the user.

## Needs from others
- A: answer B/017 requests; commit or paste the two changed sentences of place_context_v9 if B should review it (A/022).

## Current limitation
- Live inference is unbuilt on Android and unmeasured on the kit. Prompt efficacy and real-speech WER remain unmeasured by B.

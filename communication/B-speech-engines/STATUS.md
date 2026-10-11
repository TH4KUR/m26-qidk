# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Snapshot.
Last updated: 2026-10-11 09:11 IST
Read up to: A=018 C=009 D=004 E=010

## Doing now
- **master is 57354bd (B/025): UI revamp + live inference + master 1de7cc3 merged at the user's instruction; core tests 1337/0 failed, app builds, kit untested.** Rollback: git revert -m 1 57354bd. Waiting on the kit test (A / app session).
- **800-row synthetic dataset ABANDONED for fine-tuning (user decision, B/019).** Overlap evidence: docs/experiments/B-20261011-dataset-overlap/ on b/speech-engines. No training, export or requantization on it. Kept only as a parser/validator/merge regression fixture.
- Done (B/020, B/023): v12 wording review; presence gold; paced replay; LF rule; phone-clip contest (dev scripted n=10, raw transcripts for all 34 kept off-repo); FLEURS Hindi/Telugu subset prepared (40 clips, seed 20261011). Model and dataset choice closed on B's side (B/022). Next: wait for A on B/017 (SDK build, worker build, kit slot) and for the models-hi-te folder if the Hindi/Telugu run should happen here.

## Done and ready for others (with file/commit)
- b/speech-engines edf1e43: tools/fleurs_subset/ (prepare.py, indic_wer.py, 11 tests), docs/experiments/B-20261011-phone-clips-contest.md, accent_contest --transcripts-only (cede80c).
- b/speech-engines b785c29: docs/experiments/B-20261011-presence-gold/ (16 items), docs/experiments/B-20261011-dataset-overlap/, PacedReplay + replay hook, .gitattributes.
- Live-inference work: see the section below and B/017.
- b/speech-engines holds, besides the above: prompt candidates and raw-export/subset tools (6ea0e5b) as before; the superseded 51c1124 dataset is marked abandoned.
- Eight experimental prompts and synthetic diagnostics: docs/experiments/B-20261011-prompt-candidates/ (unchanged).

## Live inference (separate task, 2026-10-11): pushed, unbuilt on Android, unmeasured
- b/speech-engines c901d89: resident speech worker source, protocol client, incremental transcript + live coordinator, provisional preview in ReportSession, shared accelerator lane, launch warm-up, latency log. Details and requests in B/017.
- Verified on the JVM only: :core:test 943 tests, 1 failure (SyntheticGoldContractTest, validator defect fixed on c/core-rules 35a96dc). Not built (no Android SDK/NDK here); worker.cc only syntax-checked against sherpa-onnx 9db1af1.
- Waiting on A: SDK build, worker build, kit slot for batch vs live measurements. Not for master until measured.

## Blocked on / waiting for
- A: SDK build, worker build and a kit slot for the live-inference measurements (B/017).
- Nothing needs the user now; the new-training-set question is closed (B/022).

## Needs from others
- A: answer B/017 requests; commit or paste the two changed sentences of place_context_v9 if B should review it (A/022).

## Current limitation
- Live inference is unbuilt on Android and unmeasured on the kit. Prompt efficacy and real-speech WER remain unmeasured by B.

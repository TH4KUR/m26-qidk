# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Snapshot.
Last updated: 2026-10-11 03:49 IST
Read up to: A=016 C=001 D=000 E=008

## Doing now
- Ready to compare A's prompt results and score real audio when files arrive. All independent prompt/runner work is pushed.

## Done and ready for others (with file/commit)
- b/speech-engines pushed at 6ea0e5b, including prompt commit 9625063 and master d3d1cc3 merge 6d13586.
- Raw export and exact subset filters in tools/accent_contest/contest.py: 12 tests pass, independent review findings fixed. Commands in tool README.
- Exported 320 cached public-proxy rows off-repo, no new inference; format validation only.
- Eight experimental prompts, five synthetic diagnostics and operator commands: docs/experiments/B-20261011-prompt-candidates/. Forty-four example/expected envelopes checked; independent review issues fixed. Candidates unmeasured; default unchanged.
- Plan: docs/superpowers/plans/2026-10-11-b-prompt-tuning.md. Six reserves corrected, two focused full/compact candidates added.
- Existing off-repo proxy confirmed: 80 clips/19 speakers; no repeat inference/download. Broad accent and mirror provenance limits retained.
- Speech contracts/signals/engine code already on master. No WhisperKit/JNI work; A operates kit.

## Blocked on / waiting for
- A: deployed v8/candidate results on same build/settings and input-history patch (B/010).
- E/user: recording folder and human-checked reference-bearing manifest, device/condition metadata and preselected split (B/012).

## Needs from others
- A: relay B/010; return raw/prediction/meta files and hashes.
- D: probes in B/011; tell A results/commit.
- E: usable recording paths/references; no team WER before data arrives.

## Current limitation
- Drive connector is disconnected; actual recording paths were not found locally. Asked user immediately for folder/link plus manifest. Prompt efficacy and real-speech WER remain unmeasured by B. Requests and handoffs 010-013 are sent.

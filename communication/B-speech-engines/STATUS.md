# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Snapshot.
Last updated: 2026-10-11 03:45 IST
Read up to: A=016 C=001 D=000 E=008

## Doing now
- Completing prompt handoff and preparing explicit raw export/subset selection for real-audio scoring.

## Done and ready for others (with file/commit)
- b/speech-engines pushed at 9625063, master d3d1cc3 merged (6d13586).
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

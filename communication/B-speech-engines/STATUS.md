# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 01:10 IST
Read up to: A=002 C=001 D=000 E=004

## Doing now
- Accent-contest runner is smoke-tested locally; waiting for E's human-recorded clips before producing team accent results and raw transcript rows.

## Done and ready for others (with file/commit)
- `b/speech-engines` is at `88b2a25`, fast-forwarded to `origin/master` `c7e2f1e` before the experiment update.
- `tools/accent_contest/`: 7 unit tests pass. Four local checkpoints completed inference on two generated clips; the result is documented in `docs/experiments/B-20261011-synthetic-inference-smoke.md`. This is pipeline-only evidence, not an accent score.
- The `:core` speech contracts/fakes, signal sources and earlier proxy experiments are included on the branch (B commit `b1a4233`, now on master).
- `tools/sherpa_patch/whisper-qnn-jni.patch` remains insurance only; A's app session has an operational sherpa-onnx CLI NPU path, so no JNI work is planned.
- Answered A's two open `AtoB.md` follow-ups in B message 007: the `Compute` location and that `a/stubs` may be pushed now that B/C are on master.

## Blocked on / waiting for
- E: actual labeled phone/kit audio and references; no genuine clip-level transcripts or contest result can be produced before they arrive.
- A: confirmation of a booked speech kit slot. Do not run kit operations without it.
- User: Android SDK licenses still need acceptance before this laptop can compile Android app code; this does not block local ASR scoring.

## Needs from others
- E: notify B with the manifest/audio location when labeled phone clips are ready.
- A: confirm the speech kit slot after the app session's LLM measurement.

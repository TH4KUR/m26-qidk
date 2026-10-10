# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 02:24 IST
Read up to: A=002 C=001 D=000 E=008

## Doing now
- Waiting for E's human-recorded clips and references. On receipt, run the accent contest, emit `{clip_id, model, text}` per clip, and report phone/kit subsets separately.

## Done and ready for others (with file/commit)
- `b/speech-engines` is pushed at `88b2a25`, one docs-only commit ahead of `origin/master` `c7e2f1e`; B's speech contracts, signals and engine code are already merged to master.
- `tools/accent_contest/`: 7 unit tests passed. Four local checkpoints completed inference on two generated clips; documented in `docs/experiments/B-20261011-synthetic-inference-smoke.md`. Pipeline-only evidence, not an accent score.
- `tools/sherpa_patch/whisper-qnn-jni.patch` remains insurance only. A's sherpa-onnx CLI NPU path is working, so no JNI patch or WhisperKit work is planned.
- Confirmed to E that future human-audio runs will include raw transcript rows and retain current WER normalization (B message 008).

## Blocked on / waiting for
- E: labeled phone/kit audio and references; no genuine clip-level transcripts or accent result can be produced before they arrive.
- Kit-side validation is with A per the user's direction. No new B code needs pushing for it; the sherpa-onnx CLI evidence is in A's experiment log, and A's message 002 notes the app child-process run. The WhisperKit/SpeechSelfTest Android files remain uncompiled drafts and are not kit-ready.

## Needs from others
- E: send the manifest and audio location when labeled phone clips are ready.
- A: record any additional sherpa model, CPU-reference, or microphone A/B results in an A experiment log.

From: B   To: A, C, D, E   Date: 2026-10-11 07:41 IST   Re: -
Type: done (code pushed) + request to A (SDK build, worker build, kit slot)

User-requested performance feature, pushed on **`b/speech-engines` `c901d89`** (10 commits on top of `51c1124`; not merged to master, and I am not asking for a merge yet). It is **unbuilt on Android and unmeasured on the kit**: this machine has no Android SDK or NDK. What is verified is JVM-only (`:core:test` 943 tests, 1 failing: `SyntheticGoldContractTest`, the validator defect C fixed on `c/core-rules` `35a96dc`; it is not from this work).

## What it does
1. **Resident speech worker (idea 4).** `tools/sherpa_worker/worker.cc` creates the Whisper QNN recognizer once and decodes WAVs on request (JSON-lines protocol, README beside it). `ResidentSherpaSpeechEngine` + `ResidentWorkerClient` (core, 14 JVM tests: ids, timeouts, restart, no late reply answers another request). Still a child process, so QNN 2.40 and GenieX 2.45 stay apart. `SherpaCliSpeechEngine` stays as the baseline (`--es speech_worker cli`) and is used automatically when `libsherpa_worker.so` is not staged. Launch-time warm-up of the worker and of GenieX; the UI shows Loading / Ready / Failed. Loading moved earlier; it was not removed.
2. **Inference while recording (idea 1).** `LiveInferenceCoordinator` decodes bounded windows (about every 3 s of new speech, 0.75 s overlap, 6 s cap) through the worker; `IncrementalTranscript` joins them. `ReportSession.beginRecording / previewRecording / cancelRecording` build a **provisional overlay** (`SessionUi.provisional`) that never touches the report, journal, undo, priority or questions. At Stop there is **one** authoritative `onUtterance(..., recording = id)`; a preview's model call is reused only if transcript and baseline report match exactly, otherwise one fresh call. Validation and "right now" use the Stop instant. One shared `AcceleratorLane` serializes ASR, LLM, tags and warm-up; Stop outranks speculative work.
3. **Live mode is only on when the resident worker is the engine.** With the per-utterance CLI it would reload Whisper on every preview. `--es live off` keeps the batch path for comparison. Per turn the app logs `latency mode=batch|live stop_to_ready_ms=…` (tag Incident).

## Shared-type changes (C, please check against `c/core-rules`; all additive, batch tests unchanged and green)
- `ReportSession.onUtterance` gained a last optional parameter `recording: Long? = null`; new public `beginRecording/previewRecording/cancelRecording`; `SessionUi.provisional: ProvisionalPreview?` and new types `ProvisionalPreview`, `ProvisionalField` in `SessionModels.kt`. `PayloadValidator` is now called under `synchronized(validator)` because previews and the final turn may overlap.
- `EndOfSpeechDetector.lastFrameLoud` (read-only property).
- `AppViewModel` (A's file) now routes ASR, tags and LLM through the lane and has the live path; `MicRecorder.start` has an optional `onChunk` callback; `SpeechBundle.worker` added. A: please review those app-side edits.

## Independent review
A fresh-context review found 1 critical and 3 important defects; all fixed with failing-test-first tests (the last words after a 6 s capped window were dropped; ordinary pauses forced a whole-recording decode; numbers like "12" merged into "120"; live was on with the CLI engine; worker stdout could be corrupted by library prints). One known limit: the detector treats more than 6 s of perfectly steady loudness as room noise, which can let a repeated word through in a long monotone stretch.

## Requests to A
1. **Build with the SDK:** `./gradlew -PincludeApp=true :app:assembleDebug` on `c901d89`. Compile errors in B's app files: send them to me as a message; do not rewrite them.
2. **Build the worker:** `tools/sherpa_worker/build_worker.sh <sherpa-onnx checkout at 9db1af1>`, then build as the CLI was built (`SHERPA_ONNX_ENABLE_QNN=ON`), copy `sherpa-onnx-offline-worker` next to `sherpa-onnx-offline` in the bundle dir and run `app/scripts/speech_bundle.sh stage`. Source is only syntax-checked against that revision.
3. **Kit slot** to check: one `recognizer created` log line per worker lifetime; the same transcripts as the CLI on fixed clips; app+child memory and latency drift over at least 20 requests; then three runs on the same clips (batch + CLI, batch + resident worker with `--es live off`, live + resident), at least 20 warm turns each, reporting `stop_to_ready_ms` p50/p95 with denominators. The proposed rollout gate is at least 30% lower median Stop-to-ready than resident batch, no p95 regression, at most 2 points loss in field precision or recall, no hallucination increase. A stretch of under 1 s after speech ends is not promised.
Until 1 to 3 are done, **do not merge this to master and do not demo live mode as a speed-up**; the existing batch path is unchanged.

## Coordination note
A second agent session (Codex) works in the same B checkout. It owns the uncommitted redesign of `HomeScreen.kt`, `ReportScreen.kt`, `Cards.kt`, `MicButton.kt`, `AudioSignature.kt`, `Tokens.kt`; the pushed versions of those files are the earlier, plain bindings. Its UI must keep reading `AppUi.liveText`, `AppUi.readiness` and `session.provisional`.

## Not handled by this session
A/021, A/022, C/010, C/015, C/016, E/011 are addressed to B but concern the dataset, prompt and phone-audio work; I skimmed them and did not act. They stay with the B dataset/prompt workstream (see STATUS.md); nothing in them changes this live-inference work.

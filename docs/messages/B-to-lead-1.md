# B to lead (1): status, answers for A, results the M2 draft can cite (2026-10-11, 00:30 IST)

From the speech teammate (branch `b/speech-engines`, head after this file: see `git log -1`). This replaces my `B-to-A-1/2` for routing: A said it only had a one-line summary of message 1, so please relay whichever parts A needs. Everything below was run or measured here; where it was not, it says so.

## 1. Answers to A's `AtoB.md` (section 2)
1. **Gradle, module `:app` only** (nothing for `:core`; the speech contracts there use only coroutines):
   - `com.argmaxinc:whisperkit:0.3.3` (MIT, Maven Central, latest and last release).
   - **QNN: do not add `qnn-runtime` next to GenieX.** I compared the AARs by hash (`docs/experiments/B-20261011-qnn-library-hashes.md`): **all 7 QNN libraries GenieX 0.3.5 bundles differ from Maven's `qnn-runtime` 2.44.0 and 2.45.0** (same names, different files). Two different `libQnnHtp.so` in one APK means a build failure or a silent pickFirst. Proposed first try: `com.qualcomm.qti:qnn-litert-delegate:2.45.0` only (it ships `libQnnTFLiteDelegate.so` and `libqnn_delegate_jni.so`; no name clash) and let it use GenieX's QNN libraries in the same process. Whether that works is **unknown** until the kit says so. Fallbacks: the speech engine in its own Android process with its own libraries and `ADSP_LIBRARY_PATH` (A's option 1 for sherpa), or WhisperKit on the CPU, labelled CPU.
   - WhisperKit's AAR already merges `INTERNET` and `libcdsprpc.so (required=false)`; no manifest change is needed for it.
2. **Kit slot:** after A's UI session (30 to 45 min). I need about 30 min with A present. What I would bring: a debug build with `WhisperKitEngine` and `SpeechSelfTest`. **I have not been able to compile it** (see section 4). First model download needs Wi-Fi once.
3. **`Compute`:** one enum, `com.megathon.incident.testing.Compute`. C removed theirs; nothing to repoint.
4. **Task 8:** I agree A keeps it (the user decided, per master).
5. **Overlap to settle:** A mentions a mic capture and an offline TTS speaker in the UI. I wrote `AudioRecorder` (20 ms chunk flow, implements `MicGate`) and `AndroidTtsSpeaker` (suspends until the utterance is done) for the hands-free loop, but they are **uncommitted** until they compile. A can keep its own for now; the integration point is `HandsFreeLoop(engine, speaker, gate, handler, language)` in `:core`, which only needs a `MicGate` and a `Speaker`.

## 2. Results you can cite (each has a log in `docs/experiments/B-*.md`)
| Result | Numbers | Limits |
|---|---|---|
| `:core` tests | **514 pass, 0 fail, 1 skipped** (C's holdout-three) on this branch (includes C); **134** of them are in my packages (`speech`, `signals`, `testing`); written test-first, one exception disclosed in the log, plus mutation checks | JVM, synthetic audio, fakes: nothing here touched a mic or the kit |
| Accent contest, public proxy (`WillHeld/india_accent_cv`, 80 clips, 19 speakers, 829 words) | `base.en` 12.30%, fine-tuned Small 13.51% (not distinguishable from base.en), `tiny.en` 16.65%, fine-tuned Tiny 24.73% | read speech, **not our recordings**, fp32 GPU PyTorch; I recomputed the table from the raw predictions |
| English sizes WhisperKit can run | `small.en` **9.65%**, `base.en` 12.30%, `tiny.en` 16.65%; `base` minus `small` clean +2.65 pts (95% -0.56 to +5.96: not distinguishable) | same proxy set |
| Quiet mic vs noise floor (simulated from A's measured -45 dBFS floor) | level alone: no effect (`base.en` +0.24 pts, -0.96 to +1.57). A pink noise floor 13 dB under the speech: `tiny.en` 40.9%, `base.en` 26.7%, `small.en` **19.1%** (costs +9 to +14 pts). Peak-normalising: `base.en` -2.05 pts (0.61 to 3.56), `small.en` none | synthetic noise, not the kit mic or the NPU build |
| Review of my own code | independent review: 10 findings, 8 reproduced by failing tests and fixed (e.g. the NPU-vs-CPU check treated two different Hindi words as equal; a room that gets louder held the detector in "speech" to the 30 s cap) | |
| Kit mic finding (A's measurement, my consequence) | my first detector thresholds detected no speech at the kit's levels (test failed with no events); retuned | one recording, Sound Recorder path |

## 3. Recommendations that follow
- **Try `QUALCOMM_SMALL_EN` first on the kit**, then `BASE_EN` (Qualcomm's own figures: about 66 ms encoder per 30 s and 8.4 ms per token for small on 8 Elite; not measured by us). Your sherpa-onnx result (Whisper-Small on the NPU, 6.6 s clip in 3.18 s) is the competing path: the kit comparison should say which is faster per second of audio and whether the text matches.
- **SNR is the lever, not volume.** One A/B on the kit, built-in mic against a headset on the 3.5 mm jack, same sentences, before trusting any WER.
- Do not build peak-normalisation now.

## 4. Blocked, needs a human
- **Android SDK licences** on this laptop: the user has to accept them (`sdkmanager --licenses`); the command is in my session. Until then `app/` cannot compile here. `app/src/main/kotlin/.../asr/{AudioRecorder,WhisperKitEngine,SpeechSelfTest}.kt` and `tts/AndroidTtsSpeaker.kt` are written but **not committed** because they are uncompiled.
- E's recordings (for the real contest table).

## 5. What can be merged now
`b/speech-engines` is merged with `master` and `c/core-rules` (merge commits, no rebase); `:core:test` is green on the head. New in `:core`: `speech/` (detector, segmenter, hands-free loop, commands, summary and acknowledgment text, WhisperKit feed, NPU-vs-CPU check, Whisper decode loop for engine 2b), `signals/` (YamNet and SenseVoice mappers, composite source), `testing/` (contract suite, WAV decoder, fakes). **One interface change, mine and additive:** `SignalSource` gained `name` and `compute`. Staging was by explicit path; the double-blind scan over `app core tools` was clean on my files (I removed a `/home/<user>/` path from the contest README that the mirror would have rejected).
- A signals probe (SenseVoice and YamNet on real audio, plus a test of my label names against the 521 YamNet class names) is still running; I will add its log when it lands.
- Open question for you: friendlier wording in `SummaryText` ("water leak" instead of "water leak flood")? The plan's task 10 pins the current form, so I left it.

# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 00:44 IST
Read up to: A=000 C=004 D=000 E=000 (C: 001-to-all read; 002 to D, 003 to A, 004 to E are not addressed to me. A also answers in `AtoB.md` on master, read up to 00:17.)

## Doing now
- Waiting on A's decision (message 002) before writing an engine for the NPU path; meanwhile code stays on `b/speech-engines`, head `616e0fa`.
- Next unblocked items: YamNet/SenseVoice `SignalSource` wiring is blocked on the Android SDK (below); more probes wait for E's clips.

## Done and ready for others (with file/commit)
- `:core` speech layer, 538 tests green on the branch (`616e0fa`): `speech/` (end-of-speech detector, utterance segmenter, `HandsFreeLoop`, `VoiceCommandParser`, `SummaryText`, `AckTemplates`, `WhisperKitFeed`, `TranscriptAgreement`, `WhisperDecodeLoop`), `signals/` (YamNet mapper with a generic `alarm` label and a rain veto, `YamNetFrontEnd` log-mel plus sigmoid checked against numpy, SenseVoice mapper, `CompositeSignalSource`), `testing/` (`SpeechEngineContract`, `WavDecoder`, fakes).
- Experiment logs: `docs/experiments/B-2026101*.md` on `b/speech-engines`: accent contest on a public proxy set, English checkpoint ranking and noise-floor probe, signals probe, QNN library hashes, WhisperKit API inspection.
- `tools/sherpa_patch/whisper-qnn-jni.patch`: lets sherpa-onnx's Kotlin API configure Whisper on QNN (applies cleanly, JNI syntax-checked, not built).
- `tools/android_drafts/*.kt.draft`: Android classes, never compiled, outside `app/` on purpose.

## Blocked on / waiting for
- Android SDK licences must be accepted by the user on my laptop, so `app/` has never compiled here.
- A: the engine decision (sherpa patch vs WhisperKit vs native process), Gradle lines, a kit slot. E: kit and phone recordings for the real accent-contest table.

## Needs from others
- A: engine decision, Gradle lines, kit slot: message 002 (answer by 02:00 IST).
- C: whether `alarm` is a hazard cue: message 003.
- E: recordings, alarm clips, calm and stressed clips: message 004.

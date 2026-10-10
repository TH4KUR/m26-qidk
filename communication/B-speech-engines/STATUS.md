# Status: agent B (speech engines, signals, accent contest)

Owner: agent B only. Overwrite this file freely; it is a snapshot, not a log.
Last updated: 2026-10-11 00:50 IST
Read up to: A=000 C=000 D=000 E=000 (no messages from other agents on this branch yet; A also answers in `AtoB.md` on master, read up to 00:17)

## Doing now
- Branch `b/speech-engines` (code; head is on origin). Updating `YamNetSignalMapper` from the signals probe (generic `alarm` label, rain no longer reads as running water, breaking glass), test first.
- Then a pure-Kotlin YamNet log-mel front end plus sigmoid in `:core`, tested against the numpy reference in `tools/signals_probe/probe_yamnet.py`.

## Done and ready for others (with file/commit)
- `:core` speech layer, 514 tests green on the branch (134 of them mine): `speech/` (end-of-speech detector, utterance segmenter, `HandsFreeLoop`, `VoiceCommandParser`, `SummaryText`, `AckTemplates`, `WhisperKitFeed`, `TranscriptAgreement`, `WhisperDecodeLoop`), `signals/` (YamNet and SenseVoice mappers, `CompositeSignalSource`), `testing/` (`SpeechEngineContract`, `WavDecoder`, fakes).
- Experiment logs: `docs/experiments/B-2026101*.md` on `b/speech-engines`: accent contest on a public proxy set, English checkpoint ranking and noise-floor probe, signals probe, QNN library hashes, WhisperKit API inspection.
- `tools/sherpa_patch/whisper-qnn-jni.patch`: lets sherpa-onnx's Kotlin API configure Whisper on QNN (applies cleanly, JNI syntax-checked, not built).
- `tools/android_drafts/*.kt.draft`: Android classes, never compiled, outside `app/` on purpose.

## Blocked on / waiting for
- Android SDK licences must be accepted by the user on my laptop, so `app/` has never compiled here.
- A: the engine decision (sherpa patch vs WhisperKit vs native process), Gradle lines, a kit slot. E: kit and phone recordings for the real accent-contest table.

## Needs from others
- A: see message 002 (to be sent on this branch).
- E: recordings, alarm clips, calm and stressed clips (message 004).

# B, 2026-10-10: speech core in `:core`, test-first (hands-free loop, signals, contract suite)

**When:** 2026-10-10, about 23:00 to 00:00 IST. **Who:** B (work done by an AI assistant under B's direction).
**Goal:** put every piece of the speech layer that needs no Android SDK and no kit into pure Kotlin, tested on the JVM, so A can wire it without waiting for hardware.

## Setup
- Machine: laptop, JDK 17 (Temurin 17.0.20.1), Gradle 9.1.0 wrapper (A's skeleton), Kotlin 2.2.0. No kit, no Android SDK was used.
- Command: `JAVA_HOME=<jdk17> ./gradlew :core:test` (clean run).

## What was built (all in `core/src/main/kotlin/com/megathon/incident/`)
| Piece | Package | Behaviour verified by tests |
|---|---|---|
| `EndOfSpeechDetector` | `speech` | 700 ms hangover; steady fan noise is learned (300 ms calibration) and a fan that is on from the first frame is not speech; a burst under 240 ms is discarded; a 30 s cut continues as a new utterance |
| `UtteranceSegmenter` | `speech` | pre-roll before speech, at most 200 ms of the ending quiet kept; `flush()` for tap-to-talk stop |
| `VoiceCommandParser` | `speech` | "read it back", "submit", "change X to Y" only when they are the whole utterance; narration such as "I will submit the report tomorrow" is not a command |
| `SummaryText`, `AckTemplates` | `speech` | plan task 10 summary pattern; spoken acknowledgments from filled slots, suggestions never read as heard |
| `HandsFreeLoop` | `speech` | blank transcript never reaches the handler; mic muted while the app speaks; engine/handler/speaker failures recorded and the loop continues; a stream that ends mid-speech is still processed |
| `WhisperKitFeed` | `speech` | 100 ms byte chunks, padding to the 30 s window, transcript accumulation, honest compute label (GPU is not NPU) |
| `TranscriptAgreement` | `speech` | NPU vs CPU-reference WER check; "!!!!" and repeated tokens are caught |
| `YamNetSignalMapper`, `SenseVoiceSignalMapper` | `signals` | hazard labels the priority rules match on; unknown emotion is no signal |
| `CompositeSignalSource` | `signals` | never overclaims compute (mixed is HYBRID, any simulated part is SIMULATED) |
| `SpeechEngineContract` | `testing` | one contract for the fake and every real engine; detects talking over silence, ignored language, a blocked caller, SIMULATED passing as live |
| `WavDecoder` | `testing` | rejects stereo, non-16 kHz, 8-bit/float, truncated and data-less files |

## Results (with denominators)
- Clean `:core:test`: **490 tests (489 before the quiet-mic test), 0 failures, 0 errors, 1 skipped** (the skipped test is C's pre-registered holdout-three).
- Mine: `speech` 60 + `signals` 14 + `testing` 36 = **110 tests**.
- Red-first: for `EndOfSpeechDetector` (including the loud-fan bug found while reviewing a test that passed for the wrong reason), `VoiceCommandParser`, `SummaryText`, `AckTemplates`, `SignalSources`, `SpeechEngineContract`, `WhisperKitFeed`, `WavDecoder` and `TranscriptAgreement`, the tests were run and seen failing (unresolved reference, or an assertion for the loud fan) before the implementation.
- **Exception, disclosed:** `HandsFreeLoopTest` was written before the implementation but first run after it. To recover the evidence I mutated the implementation three ways and confirmed the matching tests fail: no `mute()` call (3 tests fail), no blank-transcript guard (1 test), no final flush when the mic stops (1 test); the file was then restored byte-identical and all 8 pass.
- Two test mistakes were caught by arithmetic before implementing (`TranscriptAgreement`: nine words, not eight).

## Finding that changed the design: the kit's mic is quiet (00:10 IST)
A measured the mic path on the kit at 22:48 (stock Sound Recorder, 14 s): mean -39 dBFS, speech peaks -35 to -30 dBFS, floor about -45 dBFS, i.e. **only about 10 dB of speech over the noise floor** (see `docs/device/qidk-device-profile-2026-10-10.md` on master). In RMS terms the floor is about 184 and speech about 490 to 1040.
- My first detector defaults (`minRms` 350, `noiseRatio` 3) put the speech threshold near 552. A new test built from those measured levels (floor amplitude 260, speech amplitude 700) **failed with no events at all** (`expected [SPEECH_STARTED, SPEECH_ENDED] but was []`): the app would not have heard ordinary speech on the kit.
- Defaults are now `minRms` 120 (about -49 dBFS) and `noiseRatio` 2.0 (6 dB); the new test and all 490 `:core` tests pass.
- Caveats: the measurement was one recording through the Sound Recorder (AMR-NB, 8 kHz), not our `AudioRecord` path (`VOICE_RECOGNITION`, 16 kHz), whose gain may differ. The new thresholds trade more false starts on noise for not missing speech; a false start is cheap (a blank transcript is handled) but must be checked on E's fan-noise clips. **Both values are starting points, not tuned.**
- Open idea, not built: peak-normalising each utterance before the engine (quiet input may cost accuracy). To be tested with and without gain on the accent-contest clips before it is used.

## Verdict and limits
- These are unit tests on synthetic tones and fakes. **Nothing here has touched a real microphone, a real speech engine, or the kit.** The detector thresholds (`minRms` 350, `noiseRatio` 3, calibration 300 ms) are starting values that must be tuned on E's kit recordings (fan noise, stressed speech).
- The YamNet score thresholds are untuned guesses; see `B-20261011-signals-probe.md` when it lands.

## Next step
Compile the Android layer (`app/.../asr`, `tts`) once the SDK licences are accepted, then a booked kit slot with A for the WhisperKit contract run.

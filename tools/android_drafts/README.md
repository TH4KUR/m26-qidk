# Android speech classes: written, **never compiled**

These four files implement the `:core` speech contracts on Android. They are kept here with a `.draft` suffix, **outside `app/`, so they cannot break the app build**, because the laptop they were written on had no Android SDK licences accepted and nothing here has been compiled, run, or installed on the kit.

| Draft | Target path in `app/src/main/kotlin/com/megathon/incident/` | What it is |
|---|---|---|
| `asr_AudioRecorder.kt.draft` | `asr/AudioRecorder.kt` | `AudioRecord` as a cold `Flow<ShortArray>` of 20 ms chunks; implements `MicGate` (drops chunks while muted) |
| `asr_WhisperKitEngine.kt.draft` | `asr/WhisperKitEngine.kt` | `SpeechEngine` on WhisperKit 0.3.3; English only (throws for other languages); transcript `compute` from the configured backends |
| `asr_SpeechSelfTest.kt.draft` | `asr/SpeechSelfTest.kt` | runs `SpeechEngineContract` on a bundled asset clip and compares NPU with a CPU reference (`TranscriptAgreement`) |
| `tts_AndroidTtsSpeaker.kt.draft` | `tts/AndroidTtsSpeaker.kt` | `Speaker` on Android `TextToSpeech` with an offline voice; `speak` returns on `onDone` |

## To use them
1. Drop the `.draft` suffix and copy each file to its target path.
2. `:app` needs `com.argmaxinc:whisperkit:0.3.3` and **not** `qnn-runtime` next to GenieX (see `docs/experiments/B-20261011-qnn-library-hashes.md`); `com.qualcomm.qti:qnn-litert-delegate:2.45.0` is the proposed first try for the NPU path. `SpeechSelfTest` needs a clip at `app/src/main/assets/selftest/slide_example.wav` (16 kHz mono PCM16, recorded on the kit).
3. Compile, then run `SpeechEngineContract.verify` through `SpeechSelfTest` on the kit.

## Assumptions that are **unverified** (read before trusting these)
- WhisperKit's callbacks fire for a whole utterance when the audio is streamed as 100 ms chunks, padded to a 30 s window, then `deinitialize()` is called (taken from a third-party integration, never run by us). If they arrive later, the engine returns an empty transcript and the contract suite will say so.
- Model download: `loadModel()` needs network once; the demo is offline, so warm and cache before airplane mode, or pre-seed the files and use an offline downloader (not written).
- `AndroidTtsSpeaker` must be created on the main thread; the `en-IN`, `hi-IN`, `te-IN` voice packs have to be installed beforehand.
- Compile errors are likely in small details (the library marks its API `@ExperimentalWhisperKit`).

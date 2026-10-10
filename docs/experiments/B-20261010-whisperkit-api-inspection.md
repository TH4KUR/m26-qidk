# B, 2026-10-10: what WhisperKit 0.3.3 really exposes (read from the AAR)

**When:** 2026-10-10 about 23:40 IST. **Who:** B (AI assistant). **Goal:** stop guessing the WhisperKit API before writing the engine.
**Setup:** downloaded `com.argmaxinc:whisperkit:0.3.3` (`whisperkit-0.3.3.aar`, 14.9 MB, MIT, latest and last release, published 2025-09-29) from Maven Central; unpacked `classes.jar` and ran `javap -public` / `-p -c -constants` with JDK 17. Also read a third-party SM8750 integration (`AryamaVMurthy/sm8750-edge-ai-lab`, `WhisperKitSttEngine.kt`) for the call order: reference only, it has no licence and no code was copied. **No device was involved.**

## Findings [V = read from the AAR bytecode]
- [V] Models: `QUALCOMM_TINY_EN = "qualcomm/Whisper_Tiny_En"`, `QUALCOMM_BASE_EN = "qualcomm/Whisper_Base_En"`, `QUALCOMM_SMALL_EN = "qualcomm/Whisper_Small_En"`, `OPENAI_TINY_EN`, `OPENAI_BASE_EN`, `OPENAI_SMALL_EN`, multilingual `OPENAI_TINY`/`OPENAI_BASE` exist as constants. The README names only `OPENAI_TINY_EN`.
- [V] Backends: `setEncoderBackend(int)` / `setDecoderBackend(int)` with `CPU_ONLY = 1`, `CPU_AND_GPU = 2`, `CPU_AND_NPU = 3`. So the engine can report CPU, NPU or HYBRID from its configuration.
- [V] The API is a **stream**: `init(frequency, channels, duration)`, then `transcribe(byte[])` (returns an int; a native `writeData`), results arrive in `TextOutputCallback.onTextOutput(what, TranscriptionResult)` with `MSG_INIT`, `MSG_TEXT_OUT`, `MSG_CLOSE`; then `deinitialize()`. There is no "utterance finished" message.
- [V] `loadModel()` downloads from Hugging Face (`ArgmaxModelDownloaderImpl`, Qualcomm repos pinned to old commits such as `8309cf4d...` for Tiny-En) into `filesDir/argmaxinc/models`. So **the first load needs network**; offline needs the files pre-seeded and a downloader that only checks they exist (the third-party code does this by reflection on the `WhisperKitImpl` constructor).
- [V] The AAR manifest merges `INTERNET` and `uses-native-library libcdsprpc.so required=false`; it ships arm64-v8a `.so` files (TensorFlow Lite, GPU delegate, tokenizers, ffmpeg) but **not** the QNN libraries (those come from `com.qualcomm.qti:qnn-runtime` and `qnn-litert-delegate`).
- [third-party, not verified by us] One-shot use: `init(16000, 1, 0)`, stream 100 ms (3200-byte) chunks, pad with zeros to a 960,000-byte (30 s) boundary, `deinitialize()`, then read the accumulated text. They assume the callbacks fire during those calls; whether they do on the kit is **unproven**.

## Verdict
WhisperKit is usable for **English only** (no language parameter), and only as a one-utterance-at-a-time wrapper. `WhisperKitEngine` therefore refuses other languages instead of returning English text under another label, and its transcript label comes from the configured backends. Not yet shown: that text actually arrives for a whole utterance, how long `init`/`deinitialize` take per utterance, or that the NPU path really runs on the NPU.

## Next step
Booked kit slot with A: build the debug APK, run `SpeechEngineContract` (NPU, then CPU) on a recorded clip, compare NPU against CPU with `TranscriptAgreement`, record milliseconds. Needs A to add the three Gradle lines (see `docs/messages/B-to-A-1.md`).

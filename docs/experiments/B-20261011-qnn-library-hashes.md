# B, 2026-10-11: are GenieX's bundled QNN libraries the same files as Maven's `qnn-runtime`?

**When:** 2026-10-11 about 00:15 IST. **Who:** B (AI assistant). **Goal:** decide whether WhisperKit's `com.qualcomm.qti:qnn-runtime` can sit in the same APK as GenieX (`geniex-android` 0.3.5), which bundles its own QNN libraries. A reported GenieX ships "QAIRT/QNN 2.45.0.260326".
**Setup:** downloaded three AARs from Maven Central (no device, no login): `geniex-android-0.3.5.aar` (81 MB), `qnn-runtime-2.44.0.aar` (64.7 MB), `qnn-runtime-2.45.0.aar` (65.9 MB); compared `jni/arm64-v8a/*.so` by SHA-256 (first 12 hex digits shown). Also read the POMs of `whisperkit-0.3.3` and `qnn-litert-delegate-2.45.0`.

## Result
| library | GenieX 0.3.5 | `qnn-runtime` 2.44.0 | `qnn-runtime` 2.45.0 |
|---|---|---|---|
| `libQnnHtp.so` | b5b36b2d5e0c | 234e44e06034 | 0d571eca017f |
| `libQnnHtpPrepare.so` | d0001d629c6d | 90c6a67bfe51 | de3d983ca853 |
| `libQnnHtpV79Skel.so` | 82275aaae3f0 | 7ed798a3b804 | 9e2a40fbd89f |
| `libQnnHtpV79Stub.so` | 16ed6f58965b | c0db5b160d10 | d866680209a4 |
| `libQnnHtpV81Skel.so` / `V81Stub.so` | 4b19d3c6ec8b / e97898d67722 | 128b65bf2320 / 5a291430855f | 4f061ffb4025 / ef0cc96ab5b4 |
| `libQnnSystem.so` | f08e9ddc18a4 | ec864c02dc01 | 2c3b913e54ec |
**All 7 shared libraries differ in all three.** GenieX's copies are a different build from either Maven release, even though both are labelled 2.45.0.
- Maven has `qnn-runtime` and `qnn-litert-delegate` at 2.44.0 and 2.45.0 (and 2.26 to 2.51); `qnn-litert-delegate` 2.45.0 ships only `libQnnTFLiteDelegate.so` and `libqnn_delegate_jni.so` (no clash with GenieX by name).
- The `whisperkit-0.3.3` POM has **no** QNN dependency (the README asks the app to add them) and its AAR ships no library name that GenieX also ships.

## What follows (inference [I], not tested on the kit)
- Putting `qnn-runtime` (either version) in the same APK as GenieX gives two different files at the same path (`lib/arm64-v8a/libQnnHtp.so` and others). Gradle will fail, and a `pickFirst` would silently give one of the two stacks the other's version: the trap the team already named.
- Cleanest candidate: add **only** `qnn-litert-delegate:2.45.0` plus `whisperkit:0.3.3` and let the delegate load GenieX's `libQnnHtp.so` in the same process. Whether the delegate's QNN API level and GenieX's build agree is **unknown**; the kit decides.
- Fallbacks, in the lead's and A's order: engine in its own Android process with its own libraries copied to a private directory and `ADSP_LIBRARY_PATH` (the same shape A proposed for sherpa-onnx, whose models are locked to QNN 2.40); or CPU TFLite for WhisperKit (labelled CPU).

## Limits
Hashes prove the files differ, not that they are incompatible; and not that GenieX 0.3.16 differs from 0.3.5 in these libraries (A reports identical sizes between those two).

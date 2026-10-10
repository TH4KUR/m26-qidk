# sherpa-onnx: let the Kotlin API configure Whisper on the QNN (NPU) backend

**Gap (verified 2026-10-11 against `k2-fsa/sherpa-onnx` commit `9db1af1`, 2026-10-09):** the C++ `OfflineWhisperModelConfig` has a `qnn_config`, and the command-line binary exposes it (`--whisper.qnn-backend-lib`, `--whisper.qnn-system-lib`, `--whisper.qnn-context-binary=<encoder>.bin,<decoder>.bin`), but the **JNI layer and the Kotlin `OfflineWhisperModelConfig` never read or declare it**. `QnnConfig` is wired for transducer, paraformer, zipformer-CTC and SenseVoice only. So an Android app built on sherpa-onnx's Kotlin API cannot run Whisper on the NPU, although the same models run from the command line.

**Patch:** `whisper-qnn-jni.patch` (15 added lines, 2 files). It adds `var qnnConfig: QnnConfig = QnnConfig()` to the Kotlin `OfflineWhisperModelConfig`, and in `sherpa-onnx/jni/offline-recognizer.cc` reads `backendLib`, `contextBinary` and `systemLib` into `ans.model_config.whisper.qnn_config`, copied from the SenseVoice block next to it.

**Checked:** applies cleanly to a pristine checkout of that commit (`git apply --check`); the patched `offline-recognizer.cc` passes `g++ -std=c++17 -fsyntax-only` with the JDK's `jni.h`, and the same check fails on a deliberately broken member name. **Not checked:** an NDK build of `libsherpa-onnx-jni.so`, and any run on the kit.

**Apply (on the build machine, before the Android build):**
```sh
cd sherpa-onnx && git apply /path/to/whisper-qnn-jni.patch   # if the tree has moved on and this fails, apply the two small hunks by hand
export SHERPA_ONNX_ENABLE_QNN=ON && ./build-android-arm64-v8a.sh
```
Copy the patched `sherpa-onnx/kotlin-api/OfflineRecognizer.kt` (and `QnnConfig.kt`) into the app.

**Use from Kotlin:** `provider = "qnn"` on `OfflineModelConfig`, `whisper = OfflineWhisperModelConfig(language = "en", task = "transcribe", qnnConfig = QnnConfig(backendLib = ".../libQnnHtp.so", systemLib = ".../libQnnSystem.so", contextBinary = "<encoder>.bin,<decoder>.bin"))`, and `OfflineRecognizer.prependAdspLibraryPath(<dir with libQnnHtpV79Skel.so>)` before creating the recognizer. Force the language; do not rely on auto-detect. The prebuilt SM8750 context binaries are tied to QNN **2.40** (an error `QNN binary blob version check failed` appears otherwise), so the engine needs its own copy of the 2.40 libraries, separate from GenieX's 2.45 libraries.

The alternative that needs no patch is to run the `sherpa-onnx-offline` binary as a native process from the app (the pattern in the `qidk-npu-android` skill, section 2), at the cost of model load time per call unless it is kept alive.

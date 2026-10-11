# Bayaan

Voice incident-report app for the Qualcomm QIDK (Snapdragon 8 Elite, SM8750). This file covers setup and run only;
the solution is described in `deliverables/final_report.pdf`.

Every command below is meant to be pasted as is into a bash terminal (Linux, or Git Bash on Windows). Unless a command
changes folder itself, run it from the root of this repository (the folder that contains `gradlew`).

## Requirements

**Build machine** (commands for Ubuntu 22.04/24.04):

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk adb curl unzip bzip2 git
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

For the Android app you also need the Android SDK with platform 36 (Android Studio installs it to `$HOME/Android/Sdk`):

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
```

The first Gradle build downloads Gradle 9.1.0 and the dependencies, so it needs a network connection. The app itself
needs no network.

**Device:** Qualcomm QIDK HDK8750 (SM8750P, Snapdragon 8 Elite), Android 16, userdebug build (so `adb root` works),
connected over USB (`adb devices` lists it). Free space on `/data`: about 3 GB for the language model and about 900 MB
for the speech models.

## Model weights and runtimes (hosted externally, not in this repository)

| Component | Used for | Source | Runs on |
|---|---|---|---|
| Qwen3-4B-Instruct-2507, w4a16 Genie bundle | report extraction and follow-up probes | Qualcomm AI Hub (https://aihub.qualcomm.com/mobile/models), downloaded on the device by the GenieX demo app (https://github.com/qualcomm/ai-hub-apps/tree/release/geniex_chat_android) | Hexagon NPU (GenieX `qairt` runtime) |
| GenieX Android SDK 0.3.16 | language-model runtime | Maven `com.qualcomm.qti:geniex-android:0.3.16` (Gradle fetches it) | |
| Whisper-Small, QNN binary for SM8750 | speech recognition | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary-2/sherpa-onnx-qnn-SM8750-binary-whisper-small.tar.bz2 | Hexagon NPU |
| SenseVoice int8, QNN binary for SM8750 | emotion and sound tags | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8.tar.bz2 | Hexagon NPU |
| Qualcomm AI Runtime (QAIRT) 2.40 | QNN libraries for the speech models | https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip | |
| sherpa-onnx (built for Android with QNN) | speech runtime (`sherpa-onnx-offline`, `libonnxruntime.so`, `libsherpa-onnx-jni.so`) | https://github.com/k2-fsa/sherpa-onnx | CPU host process driving the NPU |
| Piper voices `en_US-lessac-medium`, `hi_IN-rohan-medium-int8` (optional) | spoken replies | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models | CPU |
| Supertonic 3 int8 (optional) | spoken replies, Hindi and English | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models (`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`) | CPU |

Licences follow each source. The code in this repository is MIT-licensed (see `LICENSE`).

## 1. Get the models and runtimes (once, on the build machine)

The scripts in `app/scripts/` read the speech files from `$HOME/sherpa-bundle/bundle` and the voices from
`$HOME/tts-models`.

**1a. Speech models:**

```bash
mkdir -p "$HOME/sherpa-bundle/bundle" && cd "$HOME/sherpa-bundle/bundle"
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary-2/sherpa-onnx-qnn-SM8750-binary-whisper-small.tar.bz2
tar xjf sherpa-onnx-qnn-SM8750-binary-whisper-small.tar.bz2
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8.tar.bz2
tar xjf sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8.tar.bz2
cd -
```

**1b. QNN libraries (QAIRT 2.40):**

```bash
mkdir -p "$HOME/qairt" && cd "$HOME/qairt"
curl -L -o qairt-2.40.zip https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip
unzip -q qairt-2.40.zip
export QNN_SDK_ROOT="$HOME/qairt/qairt/2.40.0.251030"
cp "$QNN_SDK_ROOT"/lib/aarch64-android/{libQnnHtp.so,libQnnSystem.so,libQnnHtpPrepare.so,libQnnHtpV79Stub.so} "$HOME/sherpa-bundle/bundle/"
cp "$QNN_SDK_ROOT"/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so "$HOME/sherpa-bundle/bundle/"
cd -
```

**1c. sherpa-onnx for Android with QNN** (needs the Android NDK r27c; Android Studio's SDK Manager installs it under
`$ANDROID_HOME/ndk/`):

```bash
export ANDROID_NDK="$ANDROID_HOME/ndk/27.2.12479018"
export QNN_SDK_ROOT="$HOME/qairt/qairt/2.40.0.251030"
git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx "$HOME/sherpa-onnx"
cd "$HOME/sherpa-onnx"
SHERPA_ONNX_ENABLE_QNN=ON SHERPA_ONNX_ENABLE_BINARY=ON SHERPA_ONNX_ANDROID_PLATFORM=android-31 ./build-android-arm64-v8a.sh
cp build-android-arm64-v8a/install/bin/sherpa-onnx-offline build-android-arm64-v8a/install/lib/libonnxruntime.so build-android-arm64-v8a/install/lib/libsherpa-onnx-jni.so "$HOME/sherpa-bundle/bundle/"
cd -
```

**1d. Optional voices** (Piper and Supertonic; `check` verifies every file's SHA-256):

```bash
mkdir -p "$HOME/tts-models" && cd "$HOME/tts-models"
for f in vits-piper-en_US-lessac-medium vits-piper-hi_IN-rohan-medium-int8 sherpa-onnx-supertonic-3-tts-int8-2026-05-11; do
  curl -LO "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$f.tar.bz2" && tar xjf "$f.tar.bz2"
done
cd -
bash app/scripts/tts_bundle.sh check
bash app/scripts/tts_bundle.sh check-supertonic
```

**1e. Language model (on the device):** build and install the GenieX demo app from
https://github.com/qualcomm/ai-hub-apps/tree/release/geniex_chat_android, open it on the kit, and download
**Qwen3-4B-Instruct-2507** in its model list. Step 3 copies that download into this app.

## 2. Build

```bash
./gradlew :core:test
bash app/scripts/speech_bundle.sh stage
bash app/scripts/tts_bundle.sh stage
./gradlew -PincludeApp=true :app:assembleDebug --console=plain
```

`speech_bundle.sh stage` and `tts_bundle.sh stage` copy the native speech and voice runtime into
`app/src/speech/jniLibs/arm64-v8a` (git-ignored) so the build packs them. Without them the app still builds and starts,
and labels the speech engine SIMULATED. The APK is written to `app/build/outputs/apk/debug/app-debug.apk` (arm64-v8a).
On Windows, run Gradle as `gradlew.bat` instead of `./gradlew`.

## 3. Install and put the models on the kit

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
bash app/scripts/speech_bundle.sh push
bash app/scripts/llm_bundle.sh push
```

Optional voices:

```bash
bash app/scripts/tts_bundle.sh push
bash app/scripts/tts_bundle.sh push-supertonic
```

The scripts run `adb root` themselves and need the app installed first (its private folder must exist).

## 4. Run

```bash
adb shell am start -n com.megathon.incident/.MainActivity
```

or tap the Bayaan icon on the kit. Allow microphone access when asked. To speak replies with the Piper voice:

```bash
adb shell am start -n com.megathon.incident/.MainActivity --es tts PIPER
```

**First run:** the language model loads in the background at start, so the first turn can take about a dozen seconds.
Tap the microphone, describe the incident, answer the questions, review the report and send it. Reports are saved on
the device.

**Offline (airplane mode):** turn on airplane mode on the kit and check that Wi-Fi and mobile data are off. The app's
status line shows "Airplane mode on"; the whole flow, from speech to the saved report, runs with no network.

**Hindi lane (experimental, off by default):** switched on from the Home screen. It needs the Nemotron-3.5 Hindi
recogniser on the kit: `bash app/scripts/speech_bundle.sh check-hindi`, `bash app/scripts/speech_bundle.sh stage-online`
before the build, and `bash app/scripts/speech_bundle.sh push-hindi` after step 3 (files expected in
`$HOME/sherpa-bundle/online` and `$HOME/models-hi-te/sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms`).

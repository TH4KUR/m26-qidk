# Bayaan

Voice incident-report app for the Qualcomm QIDK (Snapdragon 8 Elite, SM8750). This file covers setup and run only;
the solution is described in the final report (PDF) submitted with this repository.

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

| Component | Used for | Source | Runs on | Licence |
|---|---|---|---|---|
| Qwen3-4B-Instruct-2507, w4a16 Genie bundle | report extraction and follow-up probes | Qualcomm AI Hub (https://aihub.qualcomm.com/mobile/models), downloaded on the device by the GenieX demo app (https://github.com/qualcomm/ai-hub-apps/tree/release/geniex_chat_android) | Hexagon NPU (GenieX `qairt` runtime) | Model: Apache-2.0 (Qwen, https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507). The bundle is Qualcomm's export; its terms are on its AI Hub model page |
| GenieX Android SDK 0.3.16 | language-model runtime | Maven `com.qualcomm.qti:geniex-android:0.3.16` (Gradle fetches it) | | Qualcomm's terms, as published with the artifact |
| Whisper-Small, QNN binary for SM8750 | speech recognition | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary-2/sherpa-onnx-qnn-SM8750-binary-whisper-small.tar.bz2 | Hexagon NPU | OpenAI Whisper: Apache-2.0 on the Hugging Face model card (https://huggingface.co/openai/whisper-small); MIT in the openai/whisper repository |
| SenseVoice int8, QNN binary for SM8750 | emotion and sound tags | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8.tar.bz2 | Hexagon NPU | FunASR Model Open Source License v1.1 (https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE) |
| Qualcomm AI Runtime (QAIRT) 2.40 | QNN libraries for the speech models | https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip | | Qualcomm AI Runtime Community SDK terms, shown at download |
| sherpa-onnx (built for Android with QNN) | speech runtime (`sherpa-onnx-offline`, `sherpa-onnx`, `libonnxruntime.so`, `libsherpa-onnx-jni.so`) | https://github.com/k2-fsa/sherpa-onnx | CPU host process driving the NPU | Apache-2.0 |
| Nemotron-3.5 ASR Streaming 0.6B, QNN binary for SM8750, 1120 ms (optional, Hindi lane) | Hindi speech recognition | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary-3/sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms.tar.bz2 (NVIDIA model, QNN export by sherpa-onnx) | Hexagon NPU | OpenMDW-1.1 (NVIDIA model card, https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b) |
| Piper voices `en_US-lessac-medium`, `hi_IN-rohan-medium-int8` (optional) | spoken replies | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models | CPU | Research and demonstration use only, see "Voices" below |
| Supertonic 3 int8 (optional) | spoken replies, Hindi and English | https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models (`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`) | CPU | OpenRAIL-M for the model (https://huggingface.co/Supertone/supertonic-3), with its use restrictions; sample code MIT |

The code in this repository is MIT-licensed (see `LICENSE`). Android and Kotlin libraries (Jetpack Compose, AndroidX,
kotlinx.coroutines, kotlinx.serialization, JUnit) are fetched by Gradle and are listed with their versions in
`gradle/libs.versions.toml`; they are Apache-2.0 (JUnit: EPL-1.0, tests only).

### Voices (optional, off by default)

The app speaks with the Android system voice unless it is started with `--es tts PIPER` or `--es tts SUPERTONIC`. The
Piper voices are for research and demonstration use only:

- `en_US-lessac-medium`: the model card lists MIT. It was trained on the Blizzard 2013 Lessac corpus, whose licence is for
  research purposes only (https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html).
- `hi_IN-rohan-medium`: fine-tuned from the lessac voice and trained on the IITM DonLab IndicTTS "Hindi Mono Male" data
  (licence: https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf). Permission for hackathon use is pending
  written confirmation from IITM DonLab.

The voice files are not part of this repository and are not needed for the main flow.

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
`$ANDROID_HOME/ndk/`). This builds from release `v1.13.8`, whose `kotlin-api/Tts.kt` is the file copied into this
repository (`app/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt`); the library used for the demo was built from a default-branch
clone taken on 9 to 10 October 2026 (commit not recorded) and has not been rebuilt from this tag:

```bash
export ANDROID_NDK="$ANDROID_HOME/ndk/27.2.12479018"
export QNN_SDK_ROOT="$HOME/qairt/qairt/2.40.0.251030"
git clone --depth 1 --branch v1.13.8 https://github.com/k2-fsa/sherpa-onnx "$HOME/sherpa-onnx"
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

**1f. Hindi lane (optional, experimental):** needs the online command-line recogniser from the build in step 1c and the
Nemotron model. Run this after 1a to 1c:

```bash
mkdir -p "$HOME/sherpa-bundle/online"
cp "$HOME/sherpa-onnx/build-android-arm64-v8a/install/bin/sherpa-onnx" "$HOME/sherpa-onnx/build-android-arm64-v8a/install/lib/libonnxruntime.so" "$HOME/sherpa-bundle/online/"
mkdir -p "$HOME/models-hi-te" && cd "$HOME/models-hi-te"
curl -LO https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary-3/sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms.tar.bz2
tar xjf sherpa-onnx-qnn-SM8750-binary-nemotron-3.5-asr-streaming-0.6b-1120ms.tar.bz2
cd -
```

`bash app/scripts/speech_bundle.sh check-hindi` compares the sizes of the recogniser and the model files with the ones
recorded for our build; a recogniser you rebuilt yourself can differ by a few bytes, in which case skip that check.

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

For the Hindi lane, run `bash app/scripts/speech_bundle.sh stage-online` after step 1f and before the Gradle build above.

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

Hindi lane (optional):

```bash
bash app/scripts/speech_bundle.sh push-hindi
```

The scripts run `adb root` themselves and need the app installed first (its private folder must exist).

## 4. Run

```bash
adb shell am start -n com.megathon.incident/.MainActivity
```

or tap the Bayaan icon on the kit. Allow microphone access when asked. To speak replies with the Piper or the
Supertonic voice:

```bash
adb shell am start -n com.megathon.incident/.MainActivity --es tts PIPER
adb shell am start -n com.megathon.incident/.MainActivity --es tts SUPERTONIC
```

**First run:** the language model loads in the background at start, so the first turn can take about a dozen seconds.
Tap the microphone, describe the incident, answer the questions, review the report and send it. Reports are saved on
the device.

**Offline (airplane mode):** turn on airplane mode on the kit and check that Wi-Fi and mobile data are off. The app's
status line shows "Airplane mode on"; the whole flow, from speech to the saved report, runs with no network.

**Hindi lane (optional, experimental, off by default):** switch it on from the Home screen after steps 1f, 2 and 3.

## Third-party code and data in this repository

| File | From | Licence |
|---|---|---|
| `app/src/main/kotlin/com/k2fsa/sherpa/onnx/Tts.kt` | sherpa-onnx `kotlin-api/Tts.kt`, v1.13.8, copyright Xiaomi Corporation; copied unchanged except for a notice at the top | Apache-2.0 |
| `core/src/main/resources/whisper/multilingual_suppress_tokens.txt` | the 88 `suppress_tokens` ids from the `generation_config.json` of an openai/whisper-tiny derivative | Apache-2.0 |
| `tools/signals_probe/yamnet_class_map.csv`, `core/src/test/resources/fixtures/yamnet_class_map.csv` | YAMNet class map, tensorflow/models `research/audioset/yamnet` | Apache-2.0 |

All other code and data in this repository were written by the team during the hackathon; anything that was not is listed above.

## Acknowledgments and credits

- **Qualcomm:** the QIDK hardware, Qualcomm AI Hub and the GenieX runtime and demo app (Qwen3-4B on the Hexagon NPU), the
  Qualcomm AI Runtime (QAIRT / QNN).
- **Qwen team (Alibaba Cloud):** Qwen3-4B-Instruct-2507.
- **k2-fsa / Next-gen Kaldi (sherpa-onnx):** the speech and text-to-speech runtime and the QNN builds of the Whisper,
  SenseVoice and Nemotron models used here, and the Kotlin API file listed above.
- **OpenAI:** Whisper. **Alibaba (FunAudioLLM / FunASR):** SenseVoice. **NVIDIA:** Nemotron-3.5 ASR Streaming.
- **Rhasspy Piper project, Supertone, the Blizzard 2013 Lessac corpus and IITM DonLab IndicTTS:** the optional voices.
- **Google:** the YAMNet class map. **JetBrains, Google and the Android Open Source Project:** Kotlin, Jetpack Compose,
  AndroidX and the Android platform.

Used under their own licences as listed above; none of these projects is the basis of this repository's solution, which
was built during the hackathon, and no part of this repository is a fork or a rebrand of another project.

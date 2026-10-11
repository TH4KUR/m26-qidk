# Draft: README "Build" and "Run" sections (submission repo)

Draft for the lead. Replaces the two "To be completed" sections of the submission `README.md`. Everything between the two
rules below is the proposed text; the lists after it say what was run and what was not. Sources: `settings.gradle.kts`,
`app/build.gradle.kts`, `core/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`,
`app/scripts/llm_bundle.sh`, `app/scripts/speech_bundle.sh`, `app/src/main/AndroidManifest.xml`, and section 4 of the app
handoff on `a/stubs` (`docs/handoff/A-app-handoff.md`). The submission README itself was not read (D does not touch that
repo), so "Model weights" below refers to its existing weights table by name: check the heading matches.

---

## Build

Requirements for building: JDK 17 (the build uses a Kotlin JVM toolchain of 17), and for the app the Android SDK
(compile SDK 36). The Gradle wrapper (Gradle 9.1.0) is in the repo; the first build downloads Gradle and the
dependencies, so it needs a network connection. The app runs without any network.

```bash
git clone <this repository>
cd <repository folder>
```

**1. Core logic and its tests (no Android SDK needed).** `:core` is plain Kotlin/JVM: the report model, validation,
follow-up questions and suggestions.

```bash
./gradlew :core:test
```

On Windows use `gradlew.bat :core:test`. If `java` on your `PATH` is not 17, point `JAVA_HOME` at a JDK 17 first, for
example `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :core:test`.

**2. The Android app (debug APK).** The `:app` module is only included when an Android SDK is found (`ANDROID_HOME`,
`ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`). `-PincludeApp=true` forces it on:

```bash
./gradlew -PincludeApp=true :core:test :app:assembleDebug --console=plain
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk` (arm64-v8a only).

**3. Optional: pack the speech runtime into the APK.** Speech recognition runs the sherpa-onnx command-line recogniser
with the Qualcomm QNN libraries. These files are not in git (see "Model weights"). With the bundle unpacked on the build
machine, stage the recogniser and ONNX Runtime before step 2, so the build packs them:

```bash
app/scripts/speech_bundle.sh stage [BUNDLE_DIR]
```

`BUNDLE_DIR` defaults to `$HOME/sherpa-bundle/bundle` and must contain `sherpa-onnx-offline` and `libonnxruntime.so`.
They are copied into `app/src/speech/jniLibs/arm64-v8a` (git-ignored). Without this step the APK still builds and starts,
and speech falls back to a clearly labelled SIMULATED engine.

**4. Optional: pack the Piper voice runtime into the APK.** The spoken replies use the Android system voice by default.
An offline Piper voice (VITS voices run by sherpa-onnx on the CPU) can be used instead. Before step 2:

```bash
app/scripts/tts_bundle.sh stage [BUNDLE_DIR]
```

This copies `libsherpa-onnx-jni.so` and `libonnxruntime.so` from `BUNDLE_DIR` (default `$HOME/sherpa-bundle/bundle`)
into the same git-ignored folder; `libonnxruntime.so` is skipped if step 3 already staged it.

## Run

Target device: Qualcomm QIDK with Snapdragon 8 Elite (SM8750), Android 16, connected over `adb`. The model scripts
below need `adb root` (a userdebug build) and enough free space on `/data` (about 3 GB for the language model, about
900 MB for the speech bundle).

**1. Install the app** (once, before the model steps, so its private folder exists):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**2. Put the speech models in the app's private folder** (Whisper-Small and SenseVoice, QNN binaries for SM8750):

```bash
app/scripts/speech_bundle.sh push [BUNDLE_DIR]
```

This pushes the QNN libraries (`libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`,
`libQnnHtpV79Skel.so`) and `whisper-small` (`encoder.bin`, `decoder.bin`, `tokens.txt`) into
`/data/data/com.megathon.incident/files/sherpa/`, plus `sense-voice` when present in the bundle.

**3. Put the language model in the app's private folder** (Qwen3-4B-Instruct-2507 for the Qualcomm GenieX runtime):

```bash
app/scripts/llm_bundle.sh push [MODEL_DIR_NAME]
```

`MODEL_DIR_NAME` defaults to `Qwen3-4B-Instruct-2507`. The script copies a bundle that the GenieX demo app
(`com.geniex.demo`) has already downloaded on the device, from that app's model store into
`/data/data/com.megathon.incident/files/geniex/models/qualcomm/`. Download the model in the demo app first.

**4. Optional: put the Piper voices in the app's private folder** (English `en_US-lessac-medium`, Hindi
`hi_IN-rohan-medium-int8`; see "Model weights"). Check the voice files on the build machine first, then push them:

```bash
app/scripts/tts_bundle.sh check [TTS_DIR]
app/scripts/tts_bundle.sh push [TTS_DIR]
```

`TTS_DIR` defaults to `$HOME/tts-models`. Each voice folder (`vits-piper-en_US-lessac-medium`,
`vits-piper-hi_IN-rohan-medium-int8`) needs its `.onnx` model, `tokens.txt` and `espeak-ng-data/`; `check` compares the
model's SHA-256 with the value in the script. `push` copies the folders to `/data/data/com.megathon.incident/files/tts/`
and prints the SHA-256 of each model on the device.

**5. Launch:**

```bash
adb shell am start -n com.megathon.incident/.MainActivity
```

or tap the app icon. A plain launch uses the on-device engines whenever their model files are in the app's folder;
otherwise it uses SIMULATED engines, which the screen labels as such. Allow microphone access when asked. To speak
replies with the Piper voice instead of the system voice:

```bash
adb shell am start -n com.megathon.incident/.MainActivity --es tts PIPER
```

**6. First run.** The language model loads in the background when the app starts, so the first start can take about a
dozen seconds before the first turn is fast. Then tap the microphone, describe the incident, answer the follow-up
questions, review the report and send it. Reports are saved on the device.

**7. Offline run (airplane mode).** Turn on airplane mode on the device and make sure Wi-Fi and mobile data are really
off (the status bar shows the airplane icon and no Wi-Fi icon). The app's status line shows "Airplane mode on". The
whole flow, from speech to the saved report, runs with no network.

---

### Proposed row for the submission README's weights table (A/025)

| Component | Source | Licence |
|---|---|---|
| Voices (optional): Piper `en_US-lessac-medium`, `hi_IN-rohan-medium-int8`, repackaged for sherpa-onnx | k2-fsa sherpa-onnx `tts-models` release: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models | to confirm |

---

## Verified by running (D, 2026-10-11, laptop, no kit)

- `./gradlew :core:test --no-daemon` on `d/core-suggestions` after merging master `a2b68d2`: BUILD SUCCESSFUL,
  893 tests, 0 failures, 2 skipped. Re-run after merging master `12d0ae2`: see the D board message for the count (JDK 17.0.20 Temurin; Windows, Git Bash, `JAVA_HOME` unset, `java` on the `PATH` was
  17). The lead's run on the same master tree reported 3 skipped; one skip depends on the environment.
- Nothing else in the text above was run by D.

## Not verified (needs Android Studio / the Android SDK or the kit)

- `./gradlew -PincludeApp=true :core:test :app:assembleDebug --console=plain` and the APK path: copied from the app
  handoff (section 4) and consistent with `app/build.gradle.kts`; not run by D (no Android SDK on this laptop).
- `adb install -r ...`, `adb shell am start -n com.megathon.incident/.MainActivity`: copied from the app handoff; the
  activity name matches `AndroidManifest.xml`. Not run.
- `speech_bundle.sh stage|push` and `llm_bundle.sh push`: usage and file lists read from the scripts, not run. Free-space
  figures (about 3 GB, about 900 MB) are the scripts' header comments, not measured by D.
- "Without the speech files the APK still builds and starts with SIMULATED speech": from the comment in
  `app/build.gradle.kts` and `engine/Engines.kt` on `a/stubs` (falls back when the files are missing); not run.
- First-run preload: the sentence in step 6 is A/025's wording. The code is on master `12d0ae2`
  (`MainActivity.kt:63` calls `vm.preloadModels()`); "about a dozen seconds" was not measured by D. (An earlier draft
  said "loads on first use": at the time no branch had a launch preload. Fixed.)
- `tts_bundle.sh check|stage|push` and `--es tts PIPER`: read from `app/scripts/tts_bundle.sh` on master `12d0ae2`
  (its header gives the launch line), not run. The voice source URL is the release named in
  `docs/experiments/A-20261011-piper-tts-integration.md` ("k2-fsa sherpa-onnx `tts-models` release"); D wrote the
  URL in its usual GitHub form and did not open it. Licence left "to confirm" as A asked.
- "Status line shows Airplane mode on": from `device/NetworkProof.kt` on `a/stubs` (headline strings); not seen on the
  device by D.
- Windows `gradlew.bat`: the file is in the repo; only `./gradlew` from Git Bash was run.
- Links to the model weights: the submission README's weights table was not read; check the section name "Model
  weights" and that every bundle named above (sherpa-onnx QNN Whisper-Small and SenseVoice for SM8750, GenieX
  Qwen3-4B-Instruct-2507) appears in it. No new source was added here.

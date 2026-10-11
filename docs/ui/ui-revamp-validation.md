# UI revamp: local validation

The microphone treatment is preserved. Report details and suggested changes use flat rows, thin dividers and underlined answer options. Standard content has no rounded card container. Capture, prompt replacement, field expansion, review and success use finite native Compose transitions. Reduced-motion mode respects the Android animator setting.

## Environment

- Android Studio: `/home/karma/Applications/android-studio`.
- SDK: `/home/karma/Android/Sdk`; API 36 Google APIs x86_64 emulator, `qidk-ui-api36`.
- Java: `/home/karma/.local/jdks/temurin-17`.
- Kit APK (default build, unchanged from before the revamp): `app/build/outputs/apk/debug/app-debug.apk`, arm64 only, Hexagon DSP library required.
- Emulator APK (opt-in, `-PlocalUi=true`): same path, `com.megathon.incident.localui`, x86_64, DSP library optional. Build one or the other, not both in the same run.
- Actual emulator captures, interaction video and raw measurements: `app/build/outputs/ui-revamp/`.

`-PlocalUi=true` gives a separate application ID and an x86_64 target with the DSP library optional; without it the build is the production arm64 kit build with the DSP library required (checked in the merged manifest). This was a property rather than two product flavors so `assembleDebug`, `installDebug` and the APK path used for the kit stay as they were. Both builds compile offline. No animation dependency was added.

## Reproduce

```bash
export JAVA_HOME=/home/karma/.local/jdks/temurin-17
export ANDROID_HOME=/home/karma/Android/Sdk
./gradlew --offline --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx1g -Dfile.encoding=UTF-8' \
  -Porg.gradle.java.installations.paths=/home/karma/.local/jdks/temurin-17 \
  -PlocalUi=true :app:assembleDebug

/home/karma/Android/Sdk/emulator/emulator -avd qidk-ui-api36 \
  -gpu host -cores 2 -no-snapshot
/home/karma/Android/Sdk/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
/home/karma/Android/Sdk/platform-tools/adb shell am start \
  -n com.megathon.incident.localui/com.megathon.incident.MainActivity \
  --es speech FAKE --es llm FAKE
```

## Verified locally

- Capture → stop → Today → Minor: the second prompt stays reachable after the first answer.
- Expand/collapse evidence; edit a location; review/back retains the edit.
- Review → local save → new report. JSON reports exist in the emulator's app-private `files/reports/` directory.
- Full journey with 1.5× font size and Android animator scale set to zero.
- Questions and review controls disappear when report recording starts. Provisional/live text bindings remain intact; provisional content is labelled unsaved.
- Portrait and landscape use independently scrollable content where needed.
- Both debug variants compile; UI diff whitespace check passes.

## Performance scope

With fake ASR/LLM engines on the emulator, an idle sample rendered zero new frames. Four steady CPU samples were 0%; recording samples were 0–3%. Raw `top` and `dumpsys gfxinfo` output is saved beside the captures. These are sanity checks, not a QIDK benchmark or a frame-rate guarantee. No looping decorative animations, blur, video backgrounds or raster illustration assets were added.

Real-kit verification still needs the QIDK connected through ADB, actual speech/model bundles and runtime logs. Measure frame timing, CPU, memory and inference contention with real engines while recording, extracting and reviewing. The desktop emulator cannot validate Qualcomm NPU/DSP execution, thermal behavior, or real microphone/ASR latency.

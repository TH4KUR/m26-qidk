# Handoff: person A, team lead, Android app, the kit, and the sherpa-onnx build

Branch: `a/lead-kit-app` · Written 2026-10-10 · Read this whole file first, then the "Read first" list. Hour figures are estimates, not measurements. Tags: **[V]** verified in the repo's research files or sources, **[I]** inferred, prove it on the device.

## 1. The project in six lines
- **What:** Megathon X 2026 (IIIT Hyderabad), Qualcomm problem statement **AI Incident Report Assistant**. A worker speaks an incident; the app transcribes it, extracts the report fields, asks for what is missing, lets the user review, submits locally and speaks a summary.
- **Where it runs:** an Android app on a Qualcomm **QIDK HDK8750** (SM8750P, Snapdragon 8 Elite, Hexagon v79, Android 16, 15.4 GB RAM, no camera). **Every AI model in the app runs on the kit. No cloud APIs at runtime.** Cloud VM and GPU machines are for building and testing only.
- **Judged on:** (1) all AI on-device with Snapdragon acceleration, (2) natural conversational speech, (3) speech-recognition accuracy, (4) extraction accuracy, (5) correct field mapping. 3-minute demo in airplane mode, 24-hour build.
- **Our design:** report first, suggest after (the report keeps exactly what was heard; AI only *suggests* fixes as chips), signals (tense/aspect/modality, SenseVoice emotion, YamNet sounds) feeding a priority *advisor* that can only raise priority, and a measured scorecard.
- **Team of five.** A = lead, app, kit, sherpa-onnx build. B = speech. C = `:core` rules. D = `:core` suggestions, flow, storage. E = data, evaluation, pitch. Only A and B have laptops that can run Android Studio.
- **Status:** research and plan only; there is no app code yet. A pushes the Gradle skeleton (`:core` + `:app`) within about the first hour.

## 2. Read first (in this order)
1. `docs/team-split.md` (roles, dependencies, working rules, open decisions in section 7)
2. `docs/superpowers/specs/2026-10-10-incident-assistant-design.md` (decisions **D1-D21**, incident JSON in section 4, signals types in section 4b)
3. `docs/superpowers/plans/2026-10-10-incident-assistant-qidk.md`, especially **Addendum A** at the end (new tasks A1-A10, 5-person timeline, gates). The old timeline table is superseded.
4. `.claude/skills/qidk-npu-android/SKILL.md` (kit facts, section 1b and 1c)
5. `reports/Indic ASR and TTS on Snapdragon.md` and `research_notes/Indic ASR and TTS on Snapdragon/npu_deployment.md` (sections Q4 and Q7: the sherpa-onnx QNN route and Whisper-on-NPU pitfalls)
6. `docs/device/qidk-device-profile-2026-10-10.md` (everything measured on the kit)
`PLAN-codex.md` is a teammate's plan made with another AI. It is a **reference only** (validation rules, negation tests), **not authoritative**: whether it overrides our plan is an open team decision.

## 3. Ground rules (apply to everyone)
- **No cloud inference at runtime, and never claim "runs on the NPU" for something on the CPU.** Say which compute each piece uses; disclose CPU parts.
- **Never commit** model binaries (`*.gguf`, `*.onnx`, `*.bin`, `*.so`), credentials, Hugging Face or Qualcomm tokens, or the confidential HDK8750 quick-start guide (80-72574-1 Rev AA). Never copy them onto cloud machines.
- **Pure-Kotlin code goes in the `:core` module (D21): no Android imports.** The plan's file paths like `app/src/main/java/com/megathon/incident/domain/...` mean `core/src/main/kotlin/com/megathon/incident/...` for pure-Kotlin classes (tests in `core/src/test/kotlin/...`). Android-dependent code stays in `:app`. Package: `com.megathon.incident`.
- **Test first**, small commits, work only in your own folders, on your own branch. **Only A merges to `master`.** Announce any change to shared types (owner: C) before merging.
- **The kit belongs to A.** Only A installs builds on it or runs `adb` against it, except booked slots agreed with A. Read-only `adb` commands are fine in a booked slot.
- Mark claims [V] or [I]. Do not fabricate numbers. Keep prompts for the on-device LLM under about 1k tokens (NPU context is often fixed at 1024).
- Known traps: the Gradle group is `com.qualcomm.qti` (**not** `com.qualcomm.qnn`, which 404s); WhisperKit is English-only and has no language API; never `pickFirst` on conflicting QNN libraries; the kit's clock is wrong (2026-01-12); silent wrong output on the NPU is real, so compare against a CPU reference.

## 4. Pending team decisions that can change your work
These are open (see `docs/team-split.md` section 7). **Assume the default until the lead tells you otherwise.**
1. Which plan is authoritative: **default: our spec and plan with Addendum A.**
2. Hindi/Telugu: **default: experimental lane**, decided at hour 16 (D19); English is core.
3. Qualcomm Package Manager login: **default: assume none**, so Voice AI SDK routes are dead.
4. Severity: **default: rules only suggest it** (D18); the user states or confirms it.
5. `:core` module (D21): **default: yes.**
8. A's workload: **Task 8 (GenieX adapter) may move from A to B.** A will tell you.

## 5. You are A
**Laptop:** strong, kit attached. **You own:** the kit, the Gradle root and `app/`, `docs/setup-log.md`, the build VM, merging, the integration checkpoint at hour 9.
**Your folders:** `app/`, root `settings.gradle.kts`/`build.gradle.kts`/`gradle/`, `docs/setup-log.md`.

### 5.1 First 90 minutes (A1, about 3 h total)
- **Free at least 25 GB on your laptop first.** `/` is 99% full (1.7 GB free); Android Studio (about 3 GB), SDK/NDK (about 5 GB) and Gradle caches will not fit otherwise.
- Install Android Studio Meerkat 2024.3.1 or newer, **JDK 17** (the laptop has 21; Gradle wants 17), platform-tools.
- Set the kit's clock (it said 2026-01-12). Record a 3 s mic clip to prove the mic path (`tinycap` is at `/system/bin` on the kit [V]; the Sound Recorder app is installed [V]). Coordinate with E: E's gold set is recorded on the kit.
- Run the GenieX reference app (`qualcomm/ai-hub-apps`, `geniex_chat_android`) and chat with Qwen3-1.7B on the kit (S1). Record the `geniex-android` version it pins (0.3.5 at commit `acb2d577` [V]; confirm).
- **Within the first hour, push the Gradle skeleton and merge it to `master`**: `settings.gradle.kts` including `:core` (Kotlin JVM, JDK 17, no Android) and `:app` (Android, `minSdk 31`, `targetSdk 35`, `abiFilters += "arm64-v8a"`, depends on `:core`), package `com.megathon.incident`. Add `com.qualcomm.qti:geniex-android`. Then tell everyone: `git fetch && git rebase origin/master`.

### 5.2 A2: sherpa-onnx QNN build on the build VM (hours 1.5 to 6) - the key spike
Goal: Whisper (multilingual, forced language) and SenseVoice running on the Hexagon NPU through sherpa-onnx. Full evidence: `.claude/skills/qidk-npu-android/SKILL.md` section 1c and `research_notes/.../npu_deployment.md` Q4.
1. **Set up the VM** (CPU, 8 vCPU / 32 GB RAM / **250 GB** disk, Ubuntu 22.04, On-Demand). Install the Android NDK, cmake, ninja. **Never put the confidential kit guide or tokens on it; pause it when idle.**
2. **Download the QAIRT Community SDK 2.40.0.251030** (no login): `https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip` [V, HTTP 206].
3. **Cross-compile sherpa-onnx for `arm64-v8a` with QNN enabled** (docs: `k2-fsa.github.io/sherpa/onnx/qnn/build-android-demo.html`; I have not read that page myself). No prebuilt QNN AAR or Android tarball exists in v1.13.8 [V]. Output: `libsherpa-onnx-jni.so`.
4. **Models:** release `asr-models-qnn-binary-2`: `sherpa-onnx-qnn-SM8750-binary-whisper-small` (507 MB) first, `-base` (176 MB) as the smaller option; SenseVoice `...-sense-voice-zh-en-ja-ko-yue-...-int8` (about 155 MB). `model.bin` is tied to the SoC and QNN version (fast first run); `libmodel.so` is portable but its first run is slow [V].
5. **Run on the kit:** copy `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so` to `jniLibs/arm64-v8a`; set `ADSP_LIBRARY_PATH` to the directory holding the Skel [V in docs; setting it from the app is I]. Use `language=hi` (or `en`/`te`), `task=transcribe`; QNN needs static shapes, so segment audio to 5-10 s (AI Hub-style decoders cap at 200 tokens).
6. **Validate:** run a golden WAV through the NPU and a CPU reference; compare the text. Watch for "!!!!" or mismatched tokens (silent wrong output is documented).
7. **Hour 6 go/no-go:** tell B. **Go:** hand B `libsherpa-onnx-jni.so`, the QNN libraries and a working config. **No-go:** engine 2b (Qualcomm AI Hub public multilingual Whisper binaries plus a hand-written decode loop that seeds the language token), which B prepares in parallel; SenseVoice then runs on the CPU and the proof strip says so.

### 5.3 A2b: coexistence test (hours 6 to 9)
One APK containing the sherpa-onnx native libs and the GenieX AAR; both must initialise in one process and run 20 loops. **Never use `pickFirst` on conflicting QNN libraries.** If they cannot share a process, try two Android processes, or drop sherpa and keep WhisperKit English plus CPU SenseVoice (plan A.4).

### 5.4 Other tasks
- **Task 8:** GenieX LLM adapter and model manager (`llm/GenieXLlmEngine.kt`, `ModelCatalog.kt`). Candidates are chosen by E's contest (spike S3). Use `compute_unit="npu"`; apply the chat template with `enableThinking=false`; `SamplerConfig` has `grammarString`/`grammarPath` for GBNF [V in source, untested on the HTP]. **Pending decision 8: this task may move to B.**
- **Task 11 + A9:** screens and ViewModel: the ten-field form with a status and source label on every value, suggestion chips (from D's engine), signals and priority panel (from C and B), the **proof strip** (per-stage compute label, "bytes sent: 0" from `TrafficStats` [I], airplane badge, startup NPU self-test against a CPU reference clip, warm the models at launch because the first NPU Whisper run took 4.8 s against 0.7 s on CPU on an SM8750 [V]), and a staggered evidence reveal.
- **Merging:** merge branches into `master` at **hour 6** and **hour 9**. **Hour 9 checkpoint:** the slide example works end to end on the kit. If not, freeze scope to Tasks 1-11 minus voice correction and signals.
- **Task 12 (with E), Task 13 (all, hours 19-21):** on-device evaluation, feature freeze at hour 19, rehearse three times, record a backup video.

### 5.4b Manifest and kit traps
Add `<uses-native-library android:name="libcdsprpc.so" android:required="true"/>` [V]; ship your own `libQnnHtpV79Skel.so`; `adb root` works on this kit but `setenforce 0` is untested. Do not use `SpeechRecognizer`/`RecognizerIntent` (Google's recogniser can use the network). Keep demo networking off.

## 6. Interfaces and handovers
| You receive | From | By |
|---|---|---|
| Shared domain types in `:core` (spec 4 and 4b) | C | hour 1.5 |
| Suggestion engine API, follow-up planner, storage | D | hour 9 |
| `SpeechEngine`, signals, TTS | B | hour 9 |
| Gold clips recorded on the kit, scorer | E | hour 4 |

| You give | To | By |
|---|---|---|
| Gradle skeleton merged to `master` | everyone | hour 1 |
| sherpa-onnx libraries and working config, or the no-go | B | hour 6 |

## 7. Definition of done for your part
Slide example end to end on the kit by hour 9; the proof strip shows true per-stage compute; the app runs in airplane mode; 20 consecutive loops without crash.

## 8. Starter prompt for your Claude
> Read `docs/handoff/A-lead-kit-app.md`, then the files it lists under "Read first". I am person A. Work on branch `a/lead-kit-app` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 9. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

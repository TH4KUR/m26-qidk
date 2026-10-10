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
A teammate's earlier alternative plan was **removed from the repo**. **Our spec (D1-D21) and our plan with Addendum A are authoritative.** Its useful ideas (REPORTED/CONFIRMED labels, negation and correction tests, no severity guessing) are already adopted in the spec and in your tasks.

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
1. Which plan is authoritative: **settled: ours** (the alternative plan was removed from the repo).
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

## 7a. Stubs you own, exact build commands, and the submission push
**You own `EngineFactory` and the debug engine switch (section 8).** In the skeleton, include: `EngineFactory` (speech `FAKE|WHISPERKIT|SHERPA_QNN|AIHUB`, LLM `FAKE|GENIEX`), `FakeLlmEngine` returning a canned `update_report` JSON per input (so UI, C, D and E can run before GenieX works), the `SIMULATED` colour in the proof strip, a replay mode that loads fixtures, and a **release guard that refuses to start with a simulated engine.** `LlmEngine` is an interface in `:core` (contract tests by C); `GenieXLlmEngine` in `:app` implements it.

### Build VM facts [V, read-only diagnostic on 2026-10-10]
Ubuntu **24.04.4** (not 22.04; a cross-compile should not care [I]), x86_64, 8 vCPU AMD EPYC, 31 GB RAM, 484 GB disk, user `ubuntu` with passwordless sudo, no GPU, fresh install (cmake, ninja, gcc, make, unzip, bzip2 were missing). GitHub, Google's NDK host, Qualcomm's SDK host and Hugging Face are reachable. `unattended-upgrades` may hold the apt lock, so use `-o DPkg::Lock::Timeout=300`. **No tokens, credentials or the confidential kit guide on the VM; stop it when idle.**

### Exact commands (sherpa-onnx with QNN; flags checked in sherpa-onnx's docs and source, not yet run by us)
```bash
# 1. tools
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
sudo -E apt-get -o DPkg::Lock::Timeout=300 update
sudo -E apt-get -o DPkg::Lock::Timeout=300 install -y git cmake ninja-build build-essential wget curl unzip bzip2 python3 pkg-config

# 2. NDK + Qualcomm AI Runtime Community SDK 2.40.0.251030 (no login)
cd ~ && wget -q --show-progress https://dl.google.com/android/repository/android-ndk-r27c-linux.zip && unzip -q android-ndk-r27c-linux.zip && rm android-ndk-r27c-linux.zip
mkdir -p ~/qnn && cd ~/qnn && wget -q --show-progress -O qairt.zip "https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip" && unzip -q qairt.zip && rm qairt.zip
cat >> ~/.bashrc <<'EOF'
export ANDROID_NDK=$HOME/android-ndk-r27c
export QNN_SDK_ROOT=$HOME/qnn/qairt/2.40.0.251030
EOF
source ~/.bashrc
ls $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp.so $QNN_SDK_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so

# 3. build (background; watch with tail -f ~/build.log)
cd ~ && git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx && cd sherpa-onnx
export SHERPA_ONNX_ENABLE_QNN=ON SHERPA_ONNX_ENABLE_BINARY=ON
nohup ./build-android-arm64-v8a.sh > ~/build.log 2>&1 &

# 4. check: expect libonnxruntime.so, libsherpa-onnx-jni.so, sherpa-onnx-offline
ls -lh ~/sherpa-onnx/build-android-arm64-v8a/install/lib/ ~/sherpa-onnx/build-android-arm64-v8a/install/bin/sherpa-onnx-offline

# 5. models for SM8750 (can run while the build compiles); note the file names inside
mkdir -p ~/models && cd ~/models
wget -q --show-progress https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary-2/sherpa-onnx-qnn-SM8750-binary-whisper-small.tar.bz2
wget -q --show-progress https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8.tar.bz2
for f in *.tar.bz2; do tar xjf "$f" && rm "$f"; done; ls -lh */

# 6. bundle for the kit
mkdir -p ~/bundle && cd ~/bundle
cp ~/sherpa-onnx/build-android-arm64-v8a/install/lib/*.so . && cp ~/sherpa-onnx/build-android-arm64-v8a/install/bin/sherpa-onnx-offline .
cp $QNN_SDK_ROOT/lib/aarch64-android/{libQnnHtp.so,libQnnHtpPrepare.so,libQnnSystem.so,libQnnHtpV79Stub.so} .
cp $QNN_SDK_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so . && cp -r ~/models/* .
cd ~ && tar czf sherpa-qnn-sm8750.tar.gz bundle
```
On your laptop with the kit: `scp ubuntu@<vm-ip>:~/sherpa-qnn-sm8750.tar.gz .`, untar, `adb push bundle/. /data/local/tmp/binary/`, then in `adb shell`: `cd /data/local/tmp/binary; chmod +x sherpa-onnx-offline; export LD_LIBRARY_PATH=$PWD; export ADSP_LIBRARY_PATH="$PWD;$ADSP_LIBRARY_PATH"` (**semicolon, not colon**) and run `./sherpa-onnx-offline --provider=qnn --tokens=<tokens-file> --whisper.qnn-backend-lib=./libQnnHtp.so --whisper.qnn-system-lib=./libQnnSystem.so --whisper.qnn-context-binary=<encoder>.bin,<decoder>.bin --whisper-language=hi --whisper-task=transcribe <16kHz-mono.wav>`.
**Verified in sherpa-onnx source:** Whisper on QNN takes **two** context binaries (encoder, decoder, comma-separated) and the flag names above. **Not known:** the file names inside the tarball (use `ls`), and that `--provider=qnn`, `--tokens` and `LD_LIBRARY_PATH` behave as in the docs' SenseVoice example. **Compare the transcript with a CPU run; watch for "!!!!".**

### Submission push (you only; D22)
1. Clone the submission repo once, outside this repo: `git clone https://github.com/IIIT-ECell/megathon26-readyornot.git` (if `megathon26-readyornot/` already exists inside your checkout, reuse it; it is git-excluded locally).
2. In that clone set a **neutral** git identity (`git config user.name`, `git config user.email`). The script refuses an identity that matches a name or college pattern.
3. From this repo on `master`: `tools/mirror_to_submission.sh ../megathon26-readyornot` (dry run). Read the output, then re-run with `--apply`. It mirrors **committed** content only.
4. In the clone: copy E's checked PDFs into `deliverables/`, run `git status`, review, `git add -A && git commit -m "..." && git push origin main`.
5. **Rehearse once before 2:30 AM** so a scan failure is found early. Findings are fixed in this repo (commit) and the script is re-run. E keeps `tools/blindcheck.words` current with every teammate's name, username and email.
Fill in the README **Build** and **Run** sections before the final; they must work from the mirrored code.

## 8. Stubs, fakes and contract tests (the team pattern)
Five people build in parallel, so **nobody waits for the real NPU engine.** Every dependency on someone else's work gets an **interface in `:core`** and a **fake**, until the real one lands.

1. **Where:** interfaces and fakes in `core/src/main/kotlin/com/megathon/incident/testing/` (so `:app` debug builds can use them); shared fixtures in `core/src/test/resources/fixtures/` (deck example, canned raw LLM outputs, Whisper-style mistakes, signal lists, a test `site.json`). Add to the shared folders; do not copy fixtures.
2. **Fakes are labelled `SIMULATED`.** `enum class Compute { NPU, CPU, HYBRID, SIMULATED }` is part of every engine's result. The proof strip shows `SIMULATED` in a clearly different colour, and **release builds must refuse to start with a simulated engine** (guard in `EngineFactory`). **A scripted result must never be shown as live recognition.**
3. **One `EngineFactory` in `:app`** (A) selects speech `FAKE | WHISPERKIT | SHERPA_QNN | AIHUB` and LLM `FAKE | GENIEX`. Default is FAKE until the real engine passes its contract test; a debug setting switches.
4. **One contract-test suite per interface**, run against the fake now and every real implementation later (same tests, so fake and real cannot drift).
5. **Interfaces are owned and announced** (chat plus a line in the commit message). Changes go through the owner.

| Interface | Owner | Fake (ship early) | Real | Consumed by |
|---|---|---|---|---|
| `SpeechEngine`, `AudioSource`, `Speaker` | B | `FakeSpeechEngine`, `WavFileSource`, `FakeSpeaker` (logs text) | WhisperKit, sherpa-onnx QNN, AI Hub; `AudioRecord`; Android TTS | A (pipeline), E (Judge Bench) |
| `SignalSource` | B | `FakeSignalSource` (canned alarm/emotion lists per clip ID) | SenseVoice + YamNet | C (priority), A (panel) |
| `LlmEngine` (contract by C, adapter by A) | C / A | `FakeLlmEngine` (canned `update_report` JSON per input) | `GenieXLlmEngine` | A, C, D, E |
| `SuggestionEngine` | D | `FakeSuggestionEngine` (returns the "Conference Room 2" chip) | real engine | A (chips UI) |
| `PriorityAdvisor`, `TamDetector`/`HedgeDetector` | C | simple rule fakes | real | A, D |
| `Clock` | C | `FixedClock` | system clock | C, D, E |
| `ReportRepository` | D | `InMemoryReportRepository` | file-based | A |
| `PipelineRunner` (replays clips for the Judge Bench) | E | `FakePipelineRunner` (reads predictions from a file) | real pipeline | A (Judge Bench UI) |

## 9. Experiment log and submission rules
**Experiment log (needed for M2):** every experiment or measurement, **including failures**, goes in `docs/experiments/<letter>-<yyyymmdd>-<topic>.md`: date and time, who, goal, setup (device, model, version, commands), data (number of clips, kit mic or phone), result **with denominators**, verdict, next step. E compiles M2 from these. Commit and push your branch at least every 2 hours.

**Deadlines (from the organisers' PDF, as written; no grace period):**
| When | What |
|---|---|
| 10:00 PM, 10 Oct | M1 approach PDF: **done** (`deliverables/M1.pdf` is on the submission repo's `main`) |
| **4:00 AM, 11 Oct** | `deliverables/M2.pdf` (2 pages: approach changes since M1, **experiments run and results**, testing progress, plan for the remaining time) **plus all code written so far pushed to `main`** |
| **11:00 AM, 11 Oct** | Final: full code, `README.md` (setup and run steps only, plus links to external weights; do not explain the solution), MIT `LICENSE`, `deliverables/final_report.pdf` (max 3 pages), demo video (max 4 min, screen recording with voiceover, Google Drive "Anyone with the link"), code zip (exclude virtual environments, datasets, model weights) |
| 11:30 AM, 11 Oct | Slides as PDF, through the slides form |

**Submission repo:** `IIIT-ECell/megathon26-readyornot` (private; only `main` is evaluated, and only the latest commit at each deadline). It is **separate from this team repo**. Its README has empty **Build** and **Run** sections that must be filled before the final.
**Double-blind (all PDFs and the video):** put **"Team 087"** at the top of every PDF; **no team name, member names, college or city names** anywhere: not in slides, video, window titles, terminal prompts, usernames, hostnames or file names visible on screen. Run `tools/blindcheck.sh <file.pdf>` (E provides it) before every submission.
**Workflow (settled, D22):** all development, branches, research and planning stay in **this private repo (`m26-qidk`); only A merges to `master`.** The submission repo gets **clean snapshots only**, made by **A** with `tools/mirror_to_submission.sh <clone> [--apply]` (dry run first). The script exports the committed `app/`, `core/`, Gradle files and `tools/`, strips audio, models and binaries, scans contents and file names against `tools/blindcheck.words`, and never touches README, LICENSE or `deliverables/`; it never commits or pushes. **Nobody except A pushes to the submission repo.** E builds the PDFs from `docs/templates/report-template.tex` and checks them with `tools/blindcheck.sh`; A copies them into `deliverables/`. Keep names, the college and anything identifying out of code, comments, strings and file names, because they will be mirrored and scanned.

## 10. Starter prompt for your Claude
> Read `docs/handoff/A-lead-kit-app.md`, then the files it lists under "Read first". I am person A. Work on branch `a/lead-kit-app` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 11. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

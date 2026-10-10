---
name: qidk-npu-android
description: Use when building, packaging or debugging an Android app that runs AI models on a Qualcomm QIDK / Snapdragon Hexagon NPU (Megathon 2026 AI Incident Report Assistant) - launching a native NPU inference server from inside an APK, bundling QNN / ggml-hexagon libraries, DSP-runtime-not-available errors, SoC/Hexagon version detection, choosing LLM/Whisper/TTS runtimes for 8 Gen 2 vs 8 Gen 3 vs 8 Elite, checking what last year's winning Megathon team did, or needing the measured facts of our own kit (Android version, root, RAM, DSP nodes, preinstalled speech stack, missing host tools).
---

# QIDK / Snapdragon NPU on Android — field notes

Evidence-backed notes from the 2026-10-10 feasibility research (full report: `exa-results/qidk-incident-assistant-feasibility-2026-10-10.md`). Tags: **[V]** verified in source/issue, **[INF]** inference — prove it on the device before relying on it.

## 1. First thing on any kit: identify it
```bash
adb shell getprop ro.soc.model            # SM8550/SM8550P = 8 Gen 2 (v73), SM8650 = 8 Gen 3 (v75), SM8750 = 8 Elite (v79), SM8850 = 8 Elite Gen5 (v81)
adb shell getprop ro.build.version.release
adb shell getprop ro.build.type           # userdebug => adb root works (verified on our kit)
adb shell grep MemTotal /proc/meminfo
```
Official docs say QIDK = SM8550P [V]. But IIIT-H users report newer kits:
- "QIDK (SM8650)" on Android 14 (qualcomm/qidk#76) [V]
- an **SM8750 QIDK on Android 15** with Gemma3-1B and Whisper-Tiny verified on the NPU via LiteRT-LM/WhisperKit (github.com/AryamaVMurthy/sm8750-edge-ai-lab, runbook `docs/runbooks/sm8750_verified_runs.md`) [V]
- an HTP 8650 QIDK (AryamaVMurthy/DenseNet_Evals_on_QIDK_HTP8650) [V]

**Never assume — check.** For prior art and reusable repos, see the `incident-report-prior-art` skill.

**Megathon 2026 kit, confirmed by the team from its quick-start guide (80-72574-1 Rev AA, May 2024):** HDK8750, SM8750P, i.e. 8 Elite / v79.
- Onboard MIC4/5, 3.5 mm headset jack (default headset detection), 2 speakers.
- HDMI out needs DIP S5702-7 and S5702-8 ON *before* power-up.
- 12 V DC power, battery optional.
- Android, RAM and the rest were measured on 2026-10-10: see §1b.
- The guide is confidential: never publish it.

**GenieX Android SDK on SM8750 (verified in source, 2026-10-10):**
- Maven `com.qualcomm.qti:geniex-android`. Runtimes: `llama_cpp` (GGUF Q4_0 on the NPU) and `qairt` (AI Hub bundles, NPU only). Local GGUF via `HubSource.LOCALFS`.
- `SamplerConfig` has `grammarString`/`grammarPath` (GBNF), which the public API reference doesn't document.
- `LlmWrapper.applyChatTemplate(messages, tools: String?, enableThinking, addGenerationPrompt)` takes a tool-schema JSON.
- Reference app: `qualcomm/ai-hub-apps` branch `release/geniex_chat_android`.

**WhisperKit Android NPU STT (ungated, English only, archived):** `com.argmaxinc:whisperkit:0.3.3` + `com.qualcomm.qti:qnn-runtime:2.44.0` + `com.qualcomm.qti:qnn-litert-delegate:2.44.0`, profile `QUALCOMM_TINY_EN` / `QUALCOMM_BASE_EN`. **The `com.qualcomm.qnn` group does not exist on Maven Central (404); the group is `com.qualcomm.qti`.** Verified on an SM8750 QIDK by sm8750-edge-ai-lab. It has no language or task API, so it cannot do Hindi or Telugu.

**Implementation plan:** `docs/superpowers/plans/2026-10-10-incident-assistant-qidk.md`. The kit is mains-powered only, with no battery (qidk#80) [V].

## 1b. Our kit, measured 2026-10-10 (serial `cacf48a9`; full table: `docs/device/qidk-device-profile-2026-10-10.md`)
**Device [V]**
- SM8750P, Android **16** (SDK 36) `qssi_64-userdebug` test-keys over an Android 15 vendor image; kernel 6.6; bootloader unlocked.
- **`adb root` works**, SELinux stays `Enforcing` after it (`setenforce 0` untested).
- 8 cores (6 up to 3.53 GHz, 2 up to 4.32 GHz), **15.4 GB RAM**, `/data` 38 GB free, Adreno 830, Hexagon v79 (`cdsp` running).
- **No camera** (`media.camera`: 0 devices). **Wi-Fi off, airplane mode on.** **Clock is wrong** (device said 2026-01-12): set it before showing timestamps.
- 1080x2400 built-in screen; ALSA card `sun-qrd-snd-card`; mic path not yet proven (record a clip first).

**NPU access [V]**
- `/dev/fastrpc-cdsp` is 0664 system (unsigned PD, the one apps use); `fastrpc-cdsp-secure` and `fastrpc-adsp-secure` are 0644. Daemons `cdsprpcd`, `adsprpcd`, `sscrpcd` run. `libcdsprpc.so` and `libadsprpc.so` are on `/vendor/etc/public.libraries.txt`, so the manifest `uses-native-library` line in §4 is valid.
- `/vendor/dsp/cdsp` holds only Qualcomm test and system skels: **ship our own `libQnnHtpV79Skel.so`** (§2).
- Sideloaded `com.qc.posedetectionYoloNAS` bundles SNPE **2.46.0** with V73/V75/V79/V81 stubs and skels. Not yet run by us: launching it is the quickest NPU smoke test.

**Preinstalled speech stack**
- [V] `com.qualcomm.qti.voiceai.speech` registers a `RecognitionService` and a `TextToSpeechService` (`libtts.so`). AIDL HAL `vendor.qti.hardware.qasr.IQasr` is running, with `/vendor/etc/ASR_binary/Whisper/libQnnHtpV79Skel.so` (QNN 2.26.1) and a 36 KB `speech_float.eai` (no weights found there).
- [INF] This may be an on-NPU Whisper path reachable via `SpeechRecognizer`. Test it offline, with Wi-Fi off, before relying on it. The default recogniser is Google's: never use it.
- [V] Default TTS is Google (`com.google.android.tts`), CPU: disclose it (§5).

**Host gaps [V]**
- Missing: Android SDK, NDK, Android Studio, QAIRT. Present: adb 35, JDK 21 (plan says 17), Docker, cmake, ninja, `hf` CLI.
- **Laptop `/` is 99% full (1.7 GB free).** Free about 25 GB before installing anything.

## 1c. Speech and emotion stack (decided 2026-10-10; spec D9, D17, D18)
Full evidence: `reports/Indic ASR and TTS on Snapdragon.md` and `research_notes/Indic ASR and TTS on Snapdragon/`.
- **Engine 1 (English baseline):** WhisperKit, above. **Engine 2 (multilingual + emotion):** `sherpa-onnx` built from source with the QNN backend. No prebuilt QNN AAR or Android tarball exists (v1.13.8), and the prebuilt QNN APKs are SenseVoice/zh/ja only. Host: Linux x86_64, Android NDK, QAIRT Community SDK 2.40.0.251030 (downloads without login). Copy `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so` into `jniLibs/arm64-v8a` and set `ADSP_LIBRARY_PATH` to the Skel directory [V in docs, app-side setenv INF].
- **Prebuilt models for SM8750:** `sherpa-onnx-qnn-SM8750-binary-whisper-{tiny 104 MB, base 176 MB, small 507 MB, medium 1.48 GB}` (release `asr-models-qnn-binary-2`) and SenseVoice `...-10/13/15/18/20-seconds-sense-voice-zh-en-ja-ko-yue-...-int8` (about 155 MB). `model.bin` is SoC- and SDK-specific with a fast first run; `libmodel.so` is portable but slow on first run. Force the language with `language=hi|te|en`, `task=transcribe`; do not rely on auto-detect.
- **QNN needs static shapes:** audio is padded or truncated to the model's duration (5–30 s variants). AI Hub caps decoding at 200 tokens, so segment to 5–10 s.
- **Engine 2b fallback:** Qualcomm AI Hub public multilingual Whisper binaries (login-free), with our own decode loop that seeds `[SOT, <|hi|>, <|transcribe|>, <|notimestamps|>]`. Whisper-Small float: about 65 ms encoder per 30 s window, 8.3 ms per decoder token on 8 Elite (Qualcomm's numbers, not measured on this kit).
- **Stock Whisper accuracy:** Small scores 98% WER on Telugu and 38% on Hindi (FLEURS), so Hindi/Telugu needs fine-tuned weights (`vasista22/whisper-telugu-*`, `collabora/whisper-*-hindi`). Whether the exporter accepts a fine-tuned checkpoint is **untested**. Indian-accented English: `theshivam7/whisper-{tiny,small,medium}-aesrc-indian-english` (gated=auto, accept once on Hugging Face; its card reports stock tiny 17.0% -> 9.8%, small 6.9% -> 5.4% WER on read speech).
- **SenseVoice (emotion/events):** emotions `happy|sad|angry|neutral` only (no confidence, no Hindi); events `Cry`, `Cough`, `Breath`, `Laughter`, `Sneeze`, `BGM`, `Applause`. sherpa-onnx returns `lang`, `emotion` and `event` as separate fields. Use as a suggestion source only, never to lower priority.
- **YamNet** (`qualcomm/YamNet`, MIT, 3.7M parameters, 14 MB): 521 AudioSet classes including fire alarm, smoke detector, siren, screaming, explosion, glass, water, hiss. Qualcomm lists about 0.16 ms on 8 Elite (their number; integration route INF).
- **Risks:** (1) sherpa-onnx native libs vs GenieX's bundled QNN libs in one APK: test early, never `pickFirst` on QNN libraries; (2) silent wrong NPU output: compare a golden WAV against a CPU reference; (3) AI Hub changed in Sept 2026: `qnn_context_binary` target removed (use compile + link), the "Snapdragon 8 Elite" device dropped (target "Samsung Galaxy S25 (Family)").
- **Dev machines:** a cloud build VM and the GPU machines are for building and testing only (spec D20); every model in the app runs on the kit. Do not put the confidential quick-start guide or credentials on them.

## 2. The proven pattern: native NPU server launched from the APK (no root)
Last year's Megathon winner (github.com/saitejaMaryala/Megathon-2k25, a fork of xororz/local-dream, **CC BY-NC 4.0 — attribute it, non-commercial use only**) ran QNN on the HTP like this [V]:

1. Build the native inference program as an **executable named `lib<name>.so`** and place it in `app/src/main/jniLibs/arm64-v8a/`. Set `android:extractNativeLibs="true"` and `jniLibs { useLegacyPackaging = true }`, so it lands in `applicationInfo.nativeLibraryDir` and can be executed.
2. Ship the runtime libraries as **assets** and copy them on first run to a private `runtimeDir` (readable + executable):
   - `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV{68,69,73,75,79,81}Stub.so` from `$QNN_SDK_ROOT/lib/aarch64-android/`
   - `libQnnHtpV{xx}.so` and `libQnnHtpV{xx}Skel.so` from `$QNN_SDK_ROOT/lib/hexagon-v{xx}/unsigned/`
3. Start it from a foreground `Service` with `ProcessBuilder(command)`:
   - `directory(nativeLibraryDir)`, `redirectErrorStream(true)`
   - env `LD_LIBRARY_PATH = runtimeDir:/system/lib64:/vendor/lib64:/vendor/lib64/egl`
   - env `DSP_LIBRARY_PATH = runtimeDir`. This is how the DSP finds the unsigned skel libraries. `ADSP_LIBRARY_PATH` is the other name used by Qualcomm/llama.cpp scripts; set both [INF].
   - args include `--backend <runtimeDir>/libQnnHtp.so --system_library <runtimeDir>/libQnnSystem.so --port 8081`
4. A monitor thread pipes the process's stdout to logcat and reports its exit code. The app talks to the server over HTTP on `127.0.0.1`. Everything stays on-device.

Reference code: `app/src/main/java/io/github/xororz/localdream/service/BackendService.kt` (lines ~210-330), `app/src/main/cpp/CMakeLists.txt` (QNN library copy list), `app/src/main/cpp/src/QnnModel.hpp` (loading a QNN context binary). Their host setup: QAIRT 2.39 at `/opt/qcom/aistack/qairt/2.39.0.250926`, NDK r29, Ninja + ccache, Linux.

**Applying it to an LLM [INF]:** build `llama-server` with the Hexagon backend (`arm64-android-snapdragon-release` preset, toolchain image `ghcr.io/snapdragon-toolchain/arm64-android`). Rename it `libllama_server.so`, ship the `libggml-htp-v*.so` skel libraries in runtimeDir, and launch with `--device HTP0 -ngl 99 --host 127.0.0.1 --port 8080`. The app then calls `/v1/chat/completions` with `response_format: json_schema`. Untested: verify in the spike.

## 3. Runtime support matrix (as of Oct 2026)
| Need | 8 Gen 2 (v73) | 8 Gen 3 (v75) | 8 Elite (v79+) |
|---|---|---|---|
| LLM via Genie / GenieX SDK / LiteRT-LM NPU | Not supported; Qualcomm says "minimum 8 Gen 3" + Android 15 (ai-hub-apps#56) [V] | Genie only if Android 15 | Supported; use the qidk `GenAI-Solutions/ASR-LLM-TTS` sample [V] |
| LLM via llama.cpp Hexagon | **1B Q4_0 works: 23.8 tok/s generation, 1143 tok/s prefill; 4B/7B crash with `dspqueue_read 0x2e`** (llama.cpp#26123) [V] | Officially targeted | Officially targeted |
| Whisper on NPU | qidk `Solutions/NLPSolution3-AutomaticSpeechRecognition-Whisper` lists V73 [V] | listed | listed, plus the Voice AI SDK |
| TTS on NPU | None — use Android offline `TextToSpeech` and disclose it | None | MeloTTS (ASR-LLM-TTS sample) |

## 4. Known failures and fixes
- **`isRuntimeAvailable()` false for DSP:** add `<uses-native-library android:name="libcdsprpc.so" android:required="true"/>` to the manifest. On userdebug builds, also run `adb root && adb shell setenforce 0` (qidk#76) [V]; on our kit `adb root` is verified but `setenforce 0` is not yet tried.
- **Silent wrong NPU output** (identical or garbled text, no error) is the most common failure reported across v68/v73/v81 devices. Always compare NPU output against CPU on 5 golden prompts at startup.
- **Error 5005 / soc_id mismatch:** the context binary was compiled for a different chip. Recompile for the exact SoC.
- **Keep prompts under ~1k tokens.** NPU builds often fix the context at 1024.
- **Pin versions.** Re-exports and SDK upgrades regress silently (ai-hub-models#337, #338). Keep a known-good model file on USB.
- Don't run Whisper through llama.cpp's HTP path; its output is garbled (llama.cpp#25876).

## 5. Rules for this year's problem statement
- "All AI models must execute on-device using Snapdragon acceleration. Strictly no cloud APIs."
- **Do NOT copy last year's voice input.** It used `SpeechRecognizer.createSpeechRecognizer()` with `RecognizerIntent`, which is Google's recogniser and can go to the network, and the manifest held the `INTERNET` permission [V]. That fails this year's rule. Use our own Whisper on the HTP instead.
- If anything runs on the CPU (TTS, or an LLM fallback), say so to the judges. Never claim "fully NPU" otherwise.

## 6. Lesson from last year's winner
They didn't build an NPU runtime from scratch. They forked a mature open-source NPU app, added the feature the judges cared about (voice input, camera) and polished the UI, in about 12 hours of commits (2025-10-11 20:07 → 2025-10-12 08:28) [V]. No equivalent mature open-source LLM-on-Hexagon Android app was found in Oct 2026, so this year reuse the **pattern** in §2 plus the qidk Whisper sample, and spend the saved time on extraction quality, the missing-field dialogue and measured accuracy.

---
name: qidk-npu-android
description: Use when building, packaging or debugging an Android app that runs AI models on a Qualcomm QIDK / Snapdragon Hexagon NPU (Megathon 2026 AI Incident Report Assistant) - launching a native NPU inference server from inside an APK, bundling QNN / ggml-hexagon libraries, DSP-runtime-not-available errors, SoC/Hexagon version detection, choosing LLM/Whisper/TTS runtimes for 8 Gen 2 vs 8 Gen 3 vs 8 Elite, or checking what last year's winning Megathon team did.
---

# QIDK / Snapdragon NPU on Android — field notes

Evidence-backed notes from the 2026-10-10 feasibility research (full report: `exa-results/qidk-incident-assistant-feasibility-2026-10-10.md`). Tags: **[V]** verified in source/issue, **[INF]** inference — prove it on the device before relying on it.

## 1. First thing on any kit: identify it
```bash
adb shell getprop ro.soc.model            # SM8550/SM8550P = 8 Gen 2 (v73), SM8650 = 8 Gen 3 (v75), SM8750 = 8 Elite (v79), SM8850 = 8 Elite Gen5 (v81)
adb shell getprop ro.build.version.release
adb shell getprop ro.build.type           # userdebug => adb root / setenforce available
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
- RAM and Android version are not stated: check them with `getprop` and `/proc/meminfo`.
- The guide is confidential: never publish it.

**GenieX Android SDK on SM8750 (verified in source, 2026-10-10):**
- Maven `com.qualcomm.qti:geniex-android`. Runtimes: `llama_cpp` (GGUF Q4_0 on the NPU) and `qairt` (AI Hub bundles, NPU only). Local GGUF via `HubSource.LOCALFS`.
- `SamplerConfig` has `grammarString`/`grammarPath` (GBNF), which the public API reference doesn't document.
- `LlmWrapper.applyChatTemplate(messages, tools: String?, enableThinking, addGenerationPrompt)` takes a tool-schema JSON.
- Reference app: `qualcomm/ai-hub-apps` branch `release/geniex_chat_android`.

**WhisperKit Android NPU STT (ungated):** `com.argmaxinc:whisperkit:0.3.3` + `com.qualcomm.qnn:qnn-runtime:2.34.0` + `com.qualcomm.qnn:qnn-litert-delegate:2.34.0`. Verified on an SM8750 QIDK by sm8750-edge-ai-lab.

**Implementation plan:** `docs/superpowers/plans/2026-10-10-incident-assistant-qidk.md`. The kit is mains-powered only, with no battery (qidk#80) [V].

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
- **`isRuntimeAvailable()` false for DSP:** add `<uses-native-library android:name="libcdsprpc.so" android:required="true"/>` to the manifest. On userdebug builds, also run `adb root && adb shell setenforce 0` (qidk#76) [V].
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

# Experiment log: sherpa-onnx with the QNN backend, speech models on the kit

Date: 2026-10-10 (evening, IST). Owner: lead (sherpa-onnx track). Status: build and two on-device runs done; Hindi, Telugu, noisy speech and app integration **not yet tested**.

## Goal
Show that speech models run on the Hexagon NPU of the QIDK HDK8750 (SM8750P, v79) through sherpa-onnx's QNN backend, and measure speed and basic correctness. This is "engine 2" in spec D9.

## Setup
| Item | Value |
|---|---|
| Build host | cloud CPU VM, Ubuntu 24.04.4, 8 vCPU, 31 GB RAM, no GPU |
| Toolchain | Android NDK r27c; Qualcomm AI Runtime Community SDK 2.40.0.251030 (no login); sherpa-onnx cloned with `--depth 1` (commit hash not recorded); prebuilt ONNX Runtime 1.28.2 fetched by the build script |
| Build command | `SHERPA_ONNX_ENABLE_QNN=ON SHERPA_ONNX_ENABLE_BINARY=ON SHERPA_ONNX_ANDROID_PLATFORM=android-31 ./build-android-arm64-v8a.sh` |
| Models (prebuilt for SM8750) | `sherpa-onnx-qnn-SM8750-binary-whisper-small` (`encoder.bin` 206 MB, `decoder.bin` 346 MB); `sherpa-onnx-qnn-SM8750-binary-10-seconds-sense-voice-zh-en-ja-ko-yue-2025-09-09-int8` (`model.bin` 243 MB) |
| Device | the kit, Android 16 (userdebug), files in `/data/local/tmp/binary`, `LD_LIBRARY_PATH=$PWD`, `ADSP_LIBRARY_PATH="$PWD;$ADSP_LIBRARY_PATH"` (semicolon) |
| Bundle | `libsherpa-onnx-jni.so`, `libonnxruntime.so`, `sherpa-onnx-offline`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so`, plus the two model folders (916 MB total; every binary matched the successful build by checksum) |

## Results

### 1. Build
| Attempt | Time (IST) | Outcome |
|---|---|---|
| 1, default API level (`android-21`) | 22:26 to 22:28 | **Failed at link**: `ld.lld: undefined reference due to --no-allow-shlib-undefined` for `__fwrite_chk`, `__write_chk`, `__register_atfork`, `__gnu_strerror_r`, `stderr`, all referenced by the prebuilt `libonnxruntime.so` |
| 2, `android-31` after removing the stale build tree | 22:30 to 22:33 | **Succeeded in about 3.5 minutes** |

Cause, checked against the NDK's libc stubs: none of the five symbols exist at API 21; all exist from API 24 (`stderr` and the two others from API 23). The script's default API level is too low for the prebuilt ONNX Runtime; `SHERPA_ONNX_ANDROID_PLATFORM` overrides it.

### 2. Whisper-Small on the NPU (English, `test_wavs/0.wav`, 6.625 s, 16 kHz mono)
Command: `./sherpa-onnx-offline --provider=qnn --tokens=<m>/tokens.txt --whisper.qnn-backend-lib=./libQnnHtp.so --whisper.qnn-system-lib=./libQnnSystem.so --whisper.qnn-context-binary=<m>/encoder.bin,<m>/decoder.bin --whisper-language=en --whisper-task=transcribe <m>/test_wavs/0.wav`

| Measure | Value (n = 1 clip, 1 run) |
|---|---|
| Exit code | 0 |
| Recognizer creation | 0.708 s |
| Decode | 3.183 s, real-time factor 0.480 |
| Transcript | "After early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels." |
| NPU evidence | `QnnDsp Initializing HtpProvider` in the log; the context binaries are HTP-compiled |

### 3. SenseVoice on the NPU (`en.wav` and `zh.wav`, 12.744 s of audio in one invocation)
Command: `./sherpa-onnx-offline --provider=qnn --tokens=<s>/tokens.txt --sense-voice.qnn-backend-lib=./libQnnHtp.so --sense-voice.qnn-system-lib=./libQnnSystem.so --sense-voice.qnn-context-binary=<s>/model.bin <s>/test_wavs/en.wav <s>/test_wavs/zh.wav`

| Measure | Value (n = 2 clips, 1 run) |
|---|---|
| Exit code | 0 |
| Recognizer creation | 0.445 s |
| Decode (both clips) | **0.058 s**, real-time factor 0.005 (about 50x faster than the Whisper run) |
| `en.wav` | text "THE TRIVBAL CHIEF THIN CALLED FOR THE BOY AND PRESENTED HIM WITH FIFTY PIECES OF COOD": all caps, **3 word errors in 15** (tribal, then, good) |
| `zh.wav` | "开饭时间早上九点至下午五点": correct |
| Tags | both clips: `lang=<\|yue\|>` (wrong for both), `emotion=<\|NEUTRAL\|>`, `event=<\|Speech\|>` |

## Verdict
- **Engine 2 is viable on this kit.** The build works and both models run on the NPU with exit 0.
- **Whisper provides the words; SenseVoice provides only emotion and event tags.** Its English text is too error-prone, and its language tag is unreliable.
- **Whisper-Small's 3.2 s for a 6.6 s clip is slower than Qualcomm's published component timings** (about 65 ms encoder, 8 ms per token) predict. The cause is unknown; this was a cold single run, and the process reloads the model on every invocation.

## Limitations (what these numbers do not show)
- One clip per Whisper run and two for SenseVoice, so no accuracy rate and no confidence interval.
- Clean read speech only: no Hindi, no Telugu, no Indian-accented English, no noise.
- Emotion quality is untested: both clips are neutral. No alarm or crying clip was run.
- No CPU reference run, so "ran on the NPU" is strong evidence, not proof; the Whisper transcript matches the sentence this standard sample is known for.
- Not yet run inside an Android app, and not yet alongside GenieX in one process.

## Next
1. Hindi and Telugu clips through `--whisper-language=hi|te`, with accuracy against typed gold text.
2. Repeat timing: several clips in one invocation, to separate startup cost from steady state; also Whisper tiny and base.
3. Stressed and calm recordings (from the team's gold set) through SenseVoice to see whether the emotion tag ever changes.
4. App integration and the coexistence test with the LLM runtime (plan task A2b).

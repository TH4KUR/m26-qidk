# QIDK HDK8750 device profile (measured 2026-10-10)

Everything below was read from the connected kit over `adb` (serial `cacf48a9`, USB `05c6:901d`, "SUNP-HDK") unless marked **[INF]**. Re-measure with `adb shell getprop` and the commands in the `qidk-npu-android` skill if the kit is re-flashed.

## 1. Identity
| Item | Value |
|---|---|
| SoC | `SM8750P` (Snapdragon 8 Elite), platform/codename `sun`, soc_id 639, family "Snapdragon", rev 2.0 |
| Product | `Sun for arm64`, manufacturer `QUALCOMM`, device `sun` |
| Android | **16** (SDK 36), security patch 2025-12-05, build `BQ2A.251215.001`, `qssi_64-userdebug`, `test-keys` |
| Vendor image | built as Android 15 (`AQ3A.251211.001`), vendor API level 202404 |
| Kernel | 6.6.102 (`android15-8`), aarch64, debug build |
| Build flags | `ro.debuggable=1`, bootloader **unlocked** (verifiedbootstate=orange), Treble on, data encrypted |
| Page size | 4096 now (`ro.product.cpu.pagesize.max=16384`, so the build is 16 KB-capable) |

## 2. Compute
| Item | Value |
|---|---|
| CPU | 8 cores, Qualcomm custom cores (implementer 0x51, part 0x001; **[INF]** Oryon): policy0 = cpu0-5 (384 MHz-3.53 GHz), policy6 = cpu6-7 (1.02-4.32 GHz), governor `walt` |
| CPU features | `fphp asimdhp asimddp i8mm bf16`, `sm3 sm4 sha3 sha512`, `bti paca pacg` (no `sve` flag listed) |
| RAM | **15.4 GB** total (`MemTotal 15405124 kB`), about 12 GB available at idle |
| Storage | `/data` 45 GB, **38 GB free**; `/` and `/vendor` are read-only and 100 % full (normal) |
| GPU | Adreno 830, Vulkan instance 1.4.0 (`apiVersion 4210688`), device 1.3.284 (`4206876`), OpenCL (`libOpenCL.so`, `libOpenCL_adreno.so`), `/dev/kgsl-3d0` is world-writable |
| NPU | Hexagon **v79** (`cdsp` remoteproc `running`, firmware `cdsp.mdt`) |
| Display | 1080x2400, 480 dpi, up to 144 Hz, built-in screen present (touch INTERNAL) |
| Thermal | `Thermal Status: 0`; CPU 43-58 C, GPU 39-44 C at idle |

## 3. DSP / NPU access path
- Device nodes: `/dev/fastrpc-cdsp` (**0664 system:system**), `/dev/fastrpc-cdsp-secure`, `/dev/fastrpc-adsp-secure`, `/dev/dma_heap/*` (system and `qcom,*` heaps). Daemons: `cdsprpcd`, `adsprpcd`, `sscrpcd` in `/vendor/bin`.
- `libcdsprpc.so` and `libadsprpc.so` are in `/vendor/etc/public.libraries.txt`, so apps can load them with `<uses-native-library android:name="libcdsprpc.so" android:required="true"/>`.
- `/vendor/dsp/cdsp` holds only Qualcomm test/system skels (`fastrpc_shell_3`, `libsysmon*`, `libbenchmark_skel`...). **No QNN/HTP skel for LLMs is preinstalled**: our app must ship `libQnnHtpV79Skel.so` and use `DSP_LIBRARY_PATH`.
- Vendor ships its own Whisper HTP skel `/vendor/etc/ASR_binary/Whisper/libQnnHtpV79Skel.so` (QNN **2.26.1.240827**) plus `speech_float.eai` (36 KB descriptor only, the model weights are not in the vendor partition).
- `libqnnengine.so` and `libCOSNet_spatial_qnn_quantized.so` exist in `/vendor/lib64` (vendor-internal, not a public QNN runtime).
- SNPE **2.46.0.260424** with V73/V75/V79/V81 stubs and skels works on this kit: the preinstalled test app `com.qc.posedetectionYoloNAS` bundles it. It is installed from a sideloaded APK (not system), so an unprivileged APK can carry its own SNPE libs. **Not yet run by us**: launch it to prove the NPU path.
- SELinux: `Enforcing` as shell and as root. After `adb root`, `getenforce` is still `Enforcing`, so the skill's `setenforce 0` trick must be tried as root, not assumed.

## 4. Preinstalled speech stack (relevant to STT/TTS)
| Package | What it is |
|---|---|
| `com.qualcomm.qti.voiceai.speech` (system_ext priv-app) | Registers a `RecognitionService` (`SpeechRecognitionService`) **and** a `TextToSpeechService` (`QualcommTextToSpeechService`, ships `libtts.so`). Holds `RECORD_AUDIO` |
| `vendor.qti.hardware.qasr.IQasr/default` (AIDL HAL) | Running. Backed by `libqasr.so` and the Whisper binary dir above. **[INF]** this is the on-NPU Whisper path that the `SpeechRecognizer` service uses |
| `com.qualcomm.qti.voiceai.usecase`, `.wakeup` | Voice AI demo/wake-word apps |
| `com.google.android.tts` | Google offline TTS (the default). `tts_default_synth` is unset |
| `com.google.android.as` | Google AiAi `SpeechRecognitionService` |
| Default recogniser | `com.google.android.tts/.GoogleTTSRecognitionService`: do not use, may go to the network |

## 5. Peripherals and state
| Item | Value |
|---|---|
| Audio | ALSA card `sun-qrd-snd-card` (`pcmC0D*p/c`), `audiohalservice.qti` running. Framework device list includes speaker, USB headset, BT A2DP/LE. **Mic path verified 22:48**: the stock Sound Recorder (`com.android.soundrecorder`) captured 14 s with no permission prompt (saved as AMR-NB 8 kHz in `/sdcard/Music`). Levels: mean -39 dBFS, loud parts about -35 to -30 dBFS, floor about -45 dBFS, so the input is **quiet with roughly 10 dB of speech over the noise floor**. Check gain and distance before trusting WER numbers |
| Camera | `dumpsys media.camera`: **0 camera devices**. Camera HAL service is present but no sensor is attached. Do not plan any camera feature |
| Wi-Fi | `wlan0` DOWN, "Wi-Fi is disabled", **airplane mode ON** (matches the offline rule) |
| Battery | reports `present: true, level 100, voltage 8994 mV, AC false`: **[INF]** not a real pack (the skill notes the kit is mains-only): ignore it |
| Clock | Was **2026-01-12** at first probe. **Set from the host epoch at 22:48 on 2026-10-10** (`adb root` then `date @<epoch>`, diff 0 s). `auto_time` is 0 and Wi-Fi is off, so nothing resyncs it: **re-check after any reboot** |
| Timezone / locale | `Asia/Kolkata`, `en-US` |
| USB | composition `diag,adb`; `adb devices` lists the kit as authorized `device` |
| Sideloaded apps | `com.qc.posedetectionYoloNAS`, `com.cpuid.cpu_z`, `com.primatelabs.geekbench6`, `io.teamsnapped.pramana`, `com.kiloo.subwaysurf`. `com.android.vending`, GMS and Chrome are installed |
| Models on `/sdcard` | none (`Episteme/` folder and `cpu-z_1.07.apk` only) |

## 6. Host (laptop) readiness
First measured with nothing installed (Android SDK/NDK/Studio missing, `/` 99 % full with 1.7 GB free). **Updated after setup on the same day:**

| Item | State |
|---|---|
| adb | SDK `platform-tools` **37.0.1** first on PATH (apt's 35.0.0 still at `/usr/bin/adb`); kit shows as authorized `device`; user in `plugdev` |
| Android Studio | Rabbit 1 \| 2026.2.1 at `~/Android/android-studio` (first-run wizard pending) |
| Android SDK | `~/Android/Sdk`: platforms 34/35/36, build-tools 35.0.0, **NDK 27.3.13750724**, cmdline-tools |
| Gradle / JDK | Gradle 9.1.0 at `~/dev-tools/gradle`; JDK 21 (default) and 17 installed |
| llama.cpp | b11541 CPU (`llama-server`) and Vulkan (`llama-server-vulkan`; RTX 4060 = `Vulkan1`) in `~/dev-tools/llama.cpp` |
| Other | Docker, cmake, ninja, git, `hf` CLI, scrcpy 1.25 |
| Reference repos | `~/src/WhisperKitAndroid`, `~/src/ai-hub-apps` (sparse: `geniex_chat_android/`, pins `geniex-android` 0.3.5) |
| QAIRT / QNN SDK, `qai_hub` | not installed (not needed for the GenieX + WhisperKit main path) |
| Disk | `/` 88 % full, about 12 GB free after setup (was 1.7 GB). `~/android-dl` still holds 1.7 GB of installers |
| CPU / RAM | i7-13650HX, 20 threads, 15 GB RAM; RTX 4060 Laptop 8 GB (driver 580, CUDA 13.0) |
| PATH | `source ~/Android/env.sh` (sourced from `~/.bashrc` and `~/.zshrc`) |

## 7. Consequences for the plan
1. `minSdk 31 / targetSdk 35` is fine on Android 16. The kit is a **userdebug** build, so `adb root` works (verified), which the skill previously listed only as a possibility.
2. The device is on Android 16 with a vendor image of Android 15: **[INF]** this should satisfy Qualcomm's "Android 15+" requirement for Genie/GenieX; confirm with spike S1.
3. 15.4 GB RAM leaves room for Qwen3-4B (about 3 GB Q4) plus Whisper plus the app.
4. No camera, no Wi-Fi: demo must be voice + screen only, and all models must be pushed over USB.
5. First proof to run on the kit is the preinstalled `com.qc.posedetectionYoloNAS` (NPU works) and then the GenieX reference app (plan task S1).

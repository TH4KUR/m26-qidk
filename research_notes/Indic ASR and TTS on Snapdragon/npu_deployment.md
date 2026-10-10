# NPU deployment paths for ASR and TTS (English, Hindi, Telugu) on Snapdragon 8 Elite (SM8750P, Hexagon v79) Android

Research date: 2026-10-10. Tags: **[V]** means I saw it myself in docs, source, an issue, an API listing or an HTTP response. **[INF]** means I inferred it from verified facts. Unless a finding names another SoC, numbers are for "Snapdragon 8 Elite For Galaxy Mobile" (SM8750, v79) as AI Hub lists it.

Quick orientation for the report writer. These are the routes that can realistically be done in hours:
1. **ASR, Hindi/Telugu on the NPU:** sherpa-onnx QNN with multilingual Whisper (tiny/base/small/medium). It ships prebuilt **SM8750 context binaries**, and its QNN Whisper code accepts `language=hi|te`. The catch: you build sherpa-onnx with QNN from source, and there is no prebuilt QNN Whisper APK or AAR.
2. **ASR, English only, fastest:** WhisperKit Android 0.3.3 with `QUALCOMM_TINY_EN`/`QUALCOMM_BASE_EN`. Someone has already run it on an SM8750 QIDK. It has no Hindi/Telugu path, and the QNN Maven group in its README is wrong.
3. **ASR, multilingual, DIY:** AI Hub's public Whisper-Small or Large-V3-Turbo-Quantized context binaries for 8 Elite (QAIRT 2.50), driven by your own decode loop that forces `<|hi|>`/`<|te|>`.
4. **TTS:** no Hindi/Telugu TTS is pre-built for the NPU anywhere. MeloTTS and Piper on AI Hub cover EN/ES/ZH and EN/DE/IT. The most promising custom path is to adapt AI Hub's NPU-ready Piper (VITS) template to the existing Piper `hi_IN`/`te_IN` voices. The fallback is Android system TTS on the CPU, disclosed to the judges.

## Q1. Qualcomm AI Hub: pre-compiled speech models for 8 Elite / v79, formats, latency, licenses, and custom compile

### Takeaway
As of Oct 2026, AI Hub (qai-hub-models v0.64.0, QAIRT 2.50) publishes NPU builds for 8 Elite of multilingual Whisper Tiny/Base/Small/Medium/Large-V3-Turbo (float and w8a16), Distil-Whisper (English), Zipformer (zh-en), FunASR-Conformer-EN, Wav2Vec2-Conformer (English), WavLM, Silero-VAD, MeloTTS (EN/ES/ZH) and PiperTTS (EN/DE/IT). Nothing is Indic-specific, there is no Kokoro, and no TTS supports Hindi or Telugu. Multilingual Whisper is the only pre-built asset that covers Hindi and Telugu at all. The old `.en` Whisper repos are deprecated. Compiling a custom model is free, but the `qnn_context_binary` target and the "Snapdragon 8 Elite" device were both retired in Sept 2026.

### Cited Findings
- [V] The HF API lists these Qualcomm speech repos, most updated 2026-10-06/07: Whisper-Tiny, Whisper-Base, Whisper-Small, Whisper-Small-Quantized, Whisper-Medium, Whisper-Medium-Quantized, Whisper-Large-V3-Turbo, Whisper-Large-V3-Turbo-Quantized, Distil-Whisper, Zipformer, FunASR-Conformer-EN, Wav2Vec2-Conformer-Large-960h, HuggingFace-WavLM-Base-Plus, DeepSpeech2, Silero-VAD, Pyannote-Speaker-Diarization, YamNet, MeloTTS-EN/ES/ZH, PiperTTS-EN/DE/IT. Searches for kokoro, vits, sensevoice, parakeet, moonshine, hubert and tts-generic return nothing. — [HF API qualcomm](https://huggingface.co/api/models?author=qualcomm&limit=1000)
- [V] Whisper-Tiny-En, Base-En, Small-En and Medium-En (and Whisper-Small-V2) are tagged `deprecated`. Since 2025-12-16 their `main` branch holds only a README: "This model is deprecated. Please refer to https://aihub.qualcomm.com". — [qualcomm/Whisper-Tiny-En](https://huggingface.co/qualcomm/Whisper-Tiny-En), [tree](https://huggingface.co/api/models/qualcomm/Whisper-Tiny-En/tree/main)
- [V] The ai-hub-models source tree (all models) has these speech models: deepspeech2, distil_whisper, funasr_conformer_en, huggingface_wavlm_base_plus, melotts_en/es/zh, pipertts_de/en/it, wav2vec2_conformer, whisper_base/small/small_v2/tiny/medium(+quantized)/large_v3_turbo(+quantized), zipformer. No Kokoro, no Indic. — [ai-hub-models models dir](https://github.com/qualcomm/ai-hub-models/tree/main/src/qai_hub_models/models)
- [V] Each Whisper card offers three download formats: `PRECOMPILED_QNN_ONNX` (QAIRT 2.50 + ONNX Runtime 1.30.0), `QNN_CONTEXT_BINARY` (QAIRT 2.50) and `VOICE_AI` (for the Voice AI SDK). All three are offered for "Snapdragon 8 Elite For Galaxy Mobile", 8 Elite Gen 5, 8 Gen 3, X Elite and others. — [Whisper-Small card](https://huggingface.co/qualcomm/Whisper-Small)
- [V] The downloads are public S3 links that need no login. For example, `whisper_small-qnn_context_binary-float-qualcomm_snapdragon_8_elite_for_galaxy.zip` returned HTTP 200 at 572 MB, and `whisper_large_v3_turbo_quantized-...-w8a16-...8_elite_for_galaxy.zip` returned 200 at 824 MB. — [S3 asset](https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/whisper_small/releases/v0.64.0/whisper_small-qnn_context_binary-float-qualcomm_snapdragon_8_elite_for_galaxy.zip)
- [V] 8 Elite NPU latency from QNN_CONTEXT_BINARY rows (encoder per 30 s window / decoder per token, all "Primary Compute Unit: NPU"):
  - Whisper-Tiny (float): 14.14 ms / 1.61 ms; encoder 35.9 MB, decoder 109 MB. — [card](https://huggingface.co/qualcomm/Whisper-Tiny)
  - Whisper-Base (float): 24.03 / 2.84 ms. — [card](https://huggingface.co/qualcomm/Whisper-Base)
  - Whisper-Small (float): 65.44 / 8.33 ms; encoder 391 MB, decoder 533 MB. — [card](https://huggingface.co/qualcomm/Whisper-Small)
  - Whisper-Small-Quantized (w8a16): 34.35 / 4.70 ms. — [card](https://huggingface.co/qualcomm/Whisper-Small-Quantized)
  - Whisper-Medium (float): 173.1 / 23.5 ms; w8a16: 97.4 / 13.4 ms. — [card](https://huggingface.co/qualcomm/Whisper-Medium), [quantized](https://huggingface.co/qualcomm/Whisper-Medium-Quantized)
  - Whisper-Large-V3-Turbo (float): 305.2 / 6.74 ms; w8a16: 150.7 / 3.54 ms. Input is 128x3000 mel. — [card](https://huggingface.co/qualcomm/Whisper-Large-V3-Turbo), [quantized](https://huggingface.co/qualcomm/Whisper-Large-V3-Turbo-Quantized)
- [V] The Whisper cards fix the input at "80x3000 (30 seconds audio)" and "Max decoded sequence length: 200 tokens". The source comments: "The official default max decoded length is 448. We use decoded length 200". — [Whisper-Small card](https://huggingface.co/qualcomm/Whisper-Small), [hf_whisper/model.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/hf_whisper/model.py)
- [V] Other ASR on 8 Elite:
  - Distil-Whisper (distil-small.en, English only): QNN_DLC encoder 65.5 ms, decoder 7.37 ms; its TFLite encoder runs on the GPU (416 ms). MIT. — [card](https://huggingface.co/qualcomm/Distil-Whisper)
  - Zipformer (`pfluo/k2fsa-zipformer-chinese-english-mixed`, 0.71 s streaming chunk): encoder 7.2 ms, decoder and joiner about 0.13 ms. Apache-2.0. — [card](https://huggingface.co/qualcomm/Zipformer)
  - FunASR-Conformer-EN (10 s input): 22.9 ms, MIT. — [card](https://huggingface.co/qualcomm/FunASR-Conformer-EN)
  - Wav2Vec2-Conformer-Large-960h (English, 10 s, about 600M params): 767.9 ms float, 792.3 ms w8a16, Apache-2.0. — [card](https://huggingface.co/qualcomm/Wav2Vec2-Conformer-Large-960h)
  - WavLM-Base-Plus: 164.8 ms. — [card](https://huggingface.co/qualcomm/HuggingFace-WavLM-Base-Plus)
  - DeepSpeech2: TFLite on the **CPU**, 3,173 ms. — [card](https://huggingface.co/qualcomm/DeepSpeech2)
  - Silero-VAD: 0.065 ms on the NPU. — [card](https://huggingface.co/qualcomm/Silero-VAD)
- [V] MeloTTS-EN ships only as `VOICE_AI` with `mixed_with_float` precision. On 8 Elite: bert_wrapper 3.3 ms, encoder 16.5 ms, flow 42.2 ms, decoder 42.8 ms, plus a T5 G2P (0.52 / 0.28 ms). The card says: "MeloTTS is a high-quality multi-lingual text-to-speech library for English, Chinese and Spanish language." MIT. — [MeloTTS-EN card](https://huggingface.co/qualcomm/MeloTTS-EN)
- [V] PiperTTS-EN ships as `VOICE_AI` float. On 8 Elite: encoder 9.3 ms, sdp 7.4 ms, flow 8.4 ms, decoder 5.0 ms, plus a Charsiu T5 G2P. Checkpoint `rhasspy/piper-checkpoints`, MIT. — [PiperTTS-EN card](https://huggingface.co/qualcomm/PiperTTS-EN)
- [V] The TTS language enum shared by the Voice AI TTS models has only ENGLISH, SPANISH, ITALIAN, GERMAN and CHINESE. — [voiceai_lang.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/datasets/common_voice/voiceai_lang.py), [voiceai_tts/language.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/voiceai_tts/language.py)
- [V] Licenses on the cards: Whisper* Apache-2.0, Zipformer Apache-2.0, MeloTTS MIT, Piper MIT, FunASR MIT, WavLM "other". WhisperKit's README says that "Qualcomm AI Hub `.tflite` models and QNN libraries" fall under the Qualcomm AI Model & Software License. — [cards via HF](https://huggingface.co/qualcomm), [WhisperKitAndroid README](https://github.com/argmaxinc/WhisperKitAndroid)
- [V] AI Hub Workbench "is currently completely free to use". It needs a Qualcomm account and an API token. It takes PyTorch and ONNX input and targets TFLite, ONNX Runtime and QNN (context binary and DLC). On fallback: "Runtimes that support multiple hardware backends generally run as much of a network as possible on NPU before falling back to GPU or CPU." — [AI Hub FAQ](https://workbench.aihub.qualcomm.com/docs/hub/faq.html)
- [V] Release notes from 14 Sep 2026: "Support for generating QNN context binaries via the qnn_context_binary compile target runtime option has been removed… use the compile + link workflow" (`submit_compile_job` + `submit_link_job`). QAIRT 2.50 is current and 2.47 was removed. "Snapdragon 8 Elite and Snapdragon 8 Elite Gen 5 will be removed from the devices list on September 28th. Please migrate to the Samsung Galaxy S25 (Family) and … S26." — [AI Hub release notes](https://workbench.aihub.qualcomm.com/docs/hub/release_notes.html)
- [V] The Whisper export takes any HF Whisper id: `HfWhisperEncoder/Decoder.from_pretrained(hf_whisper_version="openai/whisper-base")`. The cards advertise "Custom weights (e.g., fine-tuned checkpoints)" and "Custom input shapes". — [hf_whisper/model.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/hf_whisper/model.py), [Whisper-Small card](https://huggingface.co/qualcomm/Whisper-Small)
- [V] A third party compiled a custom VITS-family TTS (GPT-SoVITS v2Pro, 6 graphs) through AI Hub into fp16 static-shape context binaries, using a resumable `aihub_compile.py`. The target was QCS8550. — [gpt-sovits-qnn](https://github.com/zhouzengming/gpt-sovits-qnn)
- [V] The AI Hub card tables give identical numbers for "Snapdragon 8 Elite For Galaxy Mobile" and "Qualcomm Dragonwing Q-8750". For example, Whisper-Tiny's decoder is 1.613 ms on both. — [Whisper-Tiny card](https://huggingface.co/qualcomm/Whisper-Tiny)

### Inferences
- [INF] Rough end-to-end cost of one 30 s window: Whisper-Small float is about 65 ms + N×8.3 ms. At around 60 output tokens that is about 0.56 s on the NPU, and w8a16 is about 0.32 s. Large-V3-Turbo w8a16 is about 0.15 s + 60×3.5 ms ≈ 0.36 s. All of these are fast enough for interactive use, so accuracy should decide the model, not speed.
- [INF] Assets built for "8 Elite For Galaxy" (SM8750-AC) should load on the QIDK's SM8750P. Both are v79, and AI Hub reports identical numbers for the Q-8750 part. The device QNN runtime must be at least the version the asset was compiled with (QAIRT 2.50). That version is available on Maven as `com.qualcomm.qti:qnn-runtime:2.50.0`/`2.51.0` (see Q5). The qidk sample tells you to match the QNN package version to the AI Hub model's QAIRT version.
- [INF] For new compiles, target "Samsung Galaxy S25 (Family)" now that the 8 Elite device is gone, and use compile + link instead of the removed `qnn_context_binary` target. Tutorials and scripts older than Sept 2026 that use `--target_runtime qnn_context_binary` will fail.
- [INF] Swapping a fine-tuned Hindi or Telugu Whisper with the same architecture (for example a whisper-small fine-tune) into the AI Hub export should mostly be a matter of passing the HF id, because the fixed shapes come from the config. I have not verified this end to end.
- [INF] FunASR-Conformer-EN (22.9 ms per 10 s) and Wav2Vec2-Conformer (768 ms per 10 s) both compile entirely onto the 8 Elite NPU. That is good evidence that a fixed-length Conformer-CTC such as IndicConformer can too, once exported with static shapes.

### Gaps
- Compile-job turnaround and quotas are not documented. The FAQ says nothing on either. My expectation that a job takes minutes is unsourced.
- I found no published Hindi or Telugu accuracy (WER/CER) for any AI Hub Whisper asset. The only accuracy claim is English LibriSpeech (see Q7).
- The AI Hub web model pages (aihub.qualcomm.com/models/...) were not fetched. The figures above come from the HF cards, which mirror them.

## Q2. WhisperKit Android: QNN model variants, multilingual support, language selection

### Takeaway
WhisperKit Android is **archived and deprecated**. Its NPU-optimized (Qualcomm AI Hub) models are **English-only**: `QUALCOMM_TINY_EN` and `QUALCOMM_BASE_EN`, plus an internal `QUALCOMM_SMALL_EN`. Multilingual `OPENAI_TINY`/`OPENAI_BASE` LiteRT models exist, but the API has no language or task setting, and users report that multilingual output gets translated to English. It cannot be relied on for Hindi or Telugu. English tiny.en on the NPU has been shown working on an SM8750 QIDK. The Maven coordinates printed in the README for the QNN runtime do not resolve.

### Cited Findings
- [V] The repo is `archived: true` (last push 2026-01-24). The README says: "`WhisperKitAndroid` is deprecated. `argmax-sdk-kotlin` will soon supplant this repository with an architectural redesign built on top of Google LiteRT." The last release is v0.3.3 (2025-09-29). — [WhisperKitAndroid](https://github.com/argmaxinc/WhisperKitAndroid)
- [V] The public model constants are `OPENAI_TINY_EN`, `OPENAI_BASE_EN`, `OPENAI_TINY`, `OPENAI_BASE`, `QUALCOMM_TINY_EN` and `QUALCOMM_BASE_EN`. `OPENAI_SMALL_EN` and `QUALCOMM_SMALL_EN` are `internal`. Encoder and decoder backends default to `CPU_AND_NPU` (3). — [WhisperKit.kt](https://github.com/argmaxinc/WhisperKitAndroid/blob/main/android/whisperkit/src/main/java/com/argmaxinc/whisperkit/WhisperKit.kt)
- [V] The QUALCOMM_* models download from pinned old commits of the deprecated HF repos. For example, `qualcomm/Whisper-Tiny-En` at `8309cf4d…`, which still contains `WhisperEncoder.tflite`/`.bin`/`.so` and `WhisperDecoder.*`. Only MelSpectrogram.tflite comes from `argmaxinc/whisperkit-litert`. — [ArgmaxModelDownloaderImpl.kt](https://github.com/argmaxinc/WhisperKitAndroid/blob/main/android/whisperkit/src/main/java/com/argmaxinc/whisperkit/network/ArgmaxModelDownloaderImpl.kt), [pinned tree](https://huggingface.co/qualcomm/Whisper-Tiny-En/tree/8309cf4d4c30c69132f4f5e83ca8dcb7c17407ae)
- [V] `argmaxinc/whisperkit-litert` contains openai_whisper-tiny, tiny.en, base and base.en (full tflite). Its `quic_openai_whisper-{tiny.en,base.en,small.en}` folders hold only `MelSpectrogram.tflite`. — [HF whisperkit-litert](https://huggingface.co/argmaxinc/whisperkit-litert/tree/main)
- [V] There is no language API. The native tokenizer looks up only `<|en|>`. Post-processing has `// TODO: unblocking multilingual models` and skips the timestamp and non-speech rules for multilingual vocabularies. — [post_proc.cpp](https://github.com/argmaxinc/WhisperKitAndroid/blob/main/cpp/src/Text/post_proc.cpp), [Tokenizer.cpp](https://github.com/argmaxinc/WhisperKitAndroid/blob/main/cpp/src/Text/Tokenizer.cpp)
- [V] Issue #75: with `OPENAI_BASE`, "Language detected: es ✅ – Task mode: <|translate|> ❌ – Output: English translation instead of Spanish transcription… I don't see options to configure the task mode… or language settings." Still open. — [issue #75](https://github.com/argmaxinc/WhisperKitAndroid/issues/75)
- [V] Open requests on the Qualcomm and multilingual side: #80 asks for Chinese/multilingual Qualcomm models, #81 for "OPENAI Small model, not only OPENAI_SMALL_EN", #74 for large-v3-turbo, and #76 "where is the quic_openai_whisper-tiny.en models?" — [issues](https://github.com/argmaxinc/WhisperKitAndroid/issues)
- [V] Issue #58 (SM8750):
  - QNN 2.27 fails with "SoC model (SnapdragonModel) is unknown". The maintainer suspects missing `libQnnHtpV79Skel.so`/`Stub.so` and recommends a newer QNN.
  - After upgrading, the user reports transcriptions "such as '!!!!!!!!!!…'" and "Graph qnn_delegate_graph_0 failed in execution with err 6001".
  - Another user on SM8750P with qnn-runtime 2.33 sees all 911/911 nodes delegated for openai_whisper-small, then "Aborted".
  — [issue #58](https://github.com/argmaxinc/WhisperKitAndroid/issues/58)
- [V] Issue #71: Qualcomm renamed the files in its HF repo, which crashed loading of `Qualcomm/Whisper_Tiny_En`. Reported fixed in 0.3.3. — [issue #71](https://github.com/argmaxinc/WhisperKitAndroid/issues/71)
- [V] The README gives `implementation("com.qualcomm.qnn:qnn-runtime:2.34.0")` and `com.qualcomm.qnn:qnn-litert-delegate:2.34.0`. That group returns **404** on Maven Central, and `repo1.maven.org/maven2/com/qualcomm/` contains only `qti/`. The artifacts actually live under `com.qualcomm.qti`: `qnn-runtime` 2.26.0–2.51.0 and `qnn-litert-delegate` 2.28.0–2.51.0, updated 2026-10-06. The whisperkit 0.3.3 POM declares no QNN dependency, so the app must add it. — [README](https://github.com/argmaxinc/WhisperKitAndroid), [404 POM](https://repo1.maven.org/maven2/com/qualcomm/qnn/qnn-runtime/2.34.0/qnn-runtime-2.34.0.pom), [qti qnn-runtime metadata](https://repo1.maven.org/maven2/com/qualcomm/qti/qnn-runtime/maven-metadata.xml), [whisperkit 0.3.3 POM](https://repo1.maven.org/maven2/com/argmaxinc/whisperkit/0.3.3/whisperkit-0.3.3.pom)
- [V] A verified SM8750 QIDK run (Android 15, device "Sun for arm64 - 15", 2026-03-15):
  - Dependencies: `com.argmaxinc:whisperkit:0.3.3` + `com.qualcomm.qti:qnn-runtime:2.44.0` + `com.qualcomm.qti:qnn-litert-delegate:2.44.0`, profile `qualcomm/Whisper_Tiny_En` with encoder/decoder backend 3 (NPU).
  - Instrumented test `transcribesShortWavClipOnNpu` passed in 4.779 s. `transcribesShortWavClipOnCpu` (openai tiny.en) passed in 0.717 s.
  — [build.gradle.kts](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab/blob/HEAD/android-app/app/build.gradle.kts), [WhisperKitSttProfile.kt](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab/blob/HEAD/android-app/app/src/main/java/com/qidk/fastvlm/core/speech/WhisperKitSttProfile.kt), [test results XML](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab/blob/HEAD/benchmarks/stt_matrix_20260315/results/whisperkit_connected_strict_20260315.xml)
- [V] Argmax's HF org has no Indic or multilingual QNN models. Its newer offerings ("whisperkit-pro", "parakeetkit-litert-pro", "qwenasrkit-pro") belong to the Argmax Pro SDK, which "requires a secret token and an API key" from the Argmax Dashboard. — [HF argmaxinc](https://huggingface.co/api/models?author=argmaxinc&limit=100), [argmax-sdk-kotlin-playground](https://github.com/argmaxinc/argmax-sdk-kotlin-playground)

### Inferences
- [INF] The team's planned Gradle line (`com.qualcomm.qnn:*:2.34.0`) will fail to resolve. Use `com.qualcomm.qti:qnn-runtime:2.44.0` and `com.qualcomm.qti:qnn-litert-delegate:2.44.0`, which are proven on SM8750, or 2.34.0 under the `qti` group. The local skill file repeats the wrong coordinates and should be corrected.
- [INF] The 4.8 s NPU test time against 0.7 s on the CPU probably includes QNN graph preparation and HTP initialization. Warm the model at app start and do not load it per utterance.
- [INF] WhisperKit can be used for English only. For Hindi or Telugu you would have to fork and patch the native decoder prompt to force a language token. Because the repo is archived, that is not worth doing in a 24 h event.

### Gaps
- Whether the multilingual `OPENAI_TINY/BASE` tflite models actually run on the HTP through the QNN delegate (rather than falling back) has not been measured on SM8750.

## Q3. Qualcomm Voice AI SDK (ASR + TTS, as used by qidk ASR-LLM-TTS): languages, access, v79

### Takeaway
The Voice AI SDKs supports v79 and are tested on 8 Elite. They are **gated behind a QPM login** (`qpm-cli --login`). The ASR SDK runs AI Hub Whisper context binaries, auto-detects the language and reports it back. The sample shows no API for forcing a language. The TTS SDK's languages are **EN/ZH/ES** in the sample, with IT/DE in AI Hub's enum. **There is no Hindi or Telugu**, and MeloTTS upstream has no Hindi either. It does have an Indian-English accent speaker.

### Cited Findings
- [V] qidk ASR-LLM-TTS was tested on "Snapdragon 8 Elite (V79) | 2.45.0 | Android 15" and "8 Elite Gen5 (V81) | 2.45.0 | Android 16". It requires QAIRT 2.45 "from QPM". — [ASR-LLM-TTS README](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS)
- [V] ASR prerequisites:
  - Install: "Voice-AI-ASR-2.1.0.0" via `qpm-cli --login <username>` and `qpm-cli --install VoiceAI_ASR`. Later steps use paths under `VoiceAI_ASR/2.5.0.0/whisper_sdk/...`.
  - Models: from the notebook or from AI Hub. Download HfWhisperEncoder/Decoder for "Snapdragon® 8 Elite Mobile", rename them `encoder_model_htp.bin`/`decoder_model_htp.bin`, and generate `vocab.bin` with the notebook.
  - Device: push `libQnnHtpV79Skel.so` ("SM8750: V79").
  - App: include `whisper-sdk.jar` and `libnnvad_model.so`.
  — [asr_Readme.md](https://github.com/qualcomm/qidk/blob/master/GenAI-Solutions/ASR-LLM-TTS/readme_assets/asr_Readme.md)
- [V] ASR API in the sample: `com.qualcomm.qti.voice.assist.whisper.sdk.Whisper` with `init(MODELS_PATH)`, `start(audioRecord)`, `stop()`, `enablePartialTranscriptions`, `enableContinuousTranscription`, `getSupportedConvertTypes`. The callback is `onTranscription(String transcription, String language, boolean isFinal, …)`, so the language is reported, but no language setter is called. — [Whisper_ASR.java](https://github.com/qualcomm/qidk/blob/master/GenAI-Solutions/ASR-LLM-TTS/sample_app/app/src/main/java/com/example/asr_llm_tts/asr/Whisper_ASR.java)
- [V] TTS prerequisites: "VoiceAI-TTS-1.1.0.0" (paths say 1.1.1.0) via `qpm-cli --login` / `--install VoiceAI_TTS`. MeloTTS models are generated with the notebook. QAIRT 2.37.0 is used for generation and "qnn-2.40(tested)" libs are pushed (`libQnnHtpV79.so` and `libQnnSystem.so` for SM8750), along with `tts-sdk.jar`, `libtts_jni.so`, `libtts.so` and `libtts_impl_skel.so`. — [tts_Readme.md](https://github.com/qualcomm/qidk/blob/master/GenAI-Solutions/ASR-LLM-TTS/readme_assets/tts_Readme.md)
- [V] The sample's languages.xml lists English, Chinese and Spanish, with models `melo_en.64_bit.qnn_v2.45.40.qnn`, `melo_zh…qnn_v2.33.0.qnn` and `melo_es…`. The TTS API is `TTS.setLanguage`, `setModelPath`, `setSkelLibPath`, `init`, `start(text)`. — [languages.xml](https://github.com/qualcomm/qidk/blob/master/GenAI-Solutions/ASR-LLM-TTS/sample_app/app/src/main/res/values/languages.xml), [TTSManager.java](https://github.com/qualcomm/qidk/blob/master/GenAI-Solutions/ASR-LLM-TTS/sample_app/app/src/main/java/com/example/asr_llm_tts/tts_manager/TTSManager.java)
- [V] AI Hub's Voice AI TTS language IDs are ENGLISH 0, CHINESE 1, GERMAN 2, SPANISH 3 and ITALIAN 15 ("ID assigned by VoiceAI TTS spec"). — [voiceai_tts/language.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/voiceai_tts/language.py)
- [V] MeloTTS upstream supports English (American, British, **Indian**, Australian, Default), Spanish, French, Chinese, Japanese and Korean. There is no Hindi or Telugu. — [MeloTTS](https://github.com/myshell-ai/MeloTTS)
- [V] The AI Hub cards point to QPM pages: [VoiceAI_ASR](https://qpm.qualcomm.com/#/main/tools/details/VoiceAI_ASR) and [VoiceAI_TTS](https://qpm.qualcomm.com/#/main/tools/details/VoiceAI_TTS). Those pages render nothing without JS or a login, so I could not read license text there. — [Whisper-Small card](https://huggingface.co/qualcomm/Whisper-Small), [MeloTTS-EN card](https://huggingface.co/qualcomm/MeloTTS-EN)
- [V] Local measured kit facts, from the team's own skill notes rather than a public source:
  - The HDK8750 runs Android 16 (SDK 36) over an Android 15 vendor image, and `adb root` works.
  - A preinstalled `com.qualcomm.qti.voiceai.speech` registers a `RecognitionService` and a `TextToSpeechService` (`libtts.so`).
  - A QASR HAL is present, with `/vendor/etc/ASR_binary/Whisper/libQnnHtpV79Skel.so` (QNN 2.26.1).
  — [local SKILL.md](file:///home/th4kur/Desktop/m26-qidk/.claude/skills/qidk-npu-android/SKILL.md)

### Inferences
- [INF] Voice AI ASR with an AI Hub multilingual Whisper (Small or Large-V3-Turbo) should transcribe Hindi or Telugu via auto-detection. If it cannot force the language, short or code-mixed utterances risk being misdetected, and possibly translated. Test this before committing.
- [INF] The preinstalled `com.qualcomm.qti.voiceai.speech` TTS/ASR services may be a ready-made vendor NPU path that the standard Android `TextToSpeech`/`SpeechRecognizer` APIs can reach by choosing that engine package. Check with Wi-Fi off. Its languages are unknown and probably the same EN/ZH/ES set.
- [INF] MeloTTS-EN's `EN_INDIA` speaker is the closest Indian-sounding NPU TTS available off the shelf, for English only. I did not verify whether AI Hub's export exposes speaker selection.

### Gaps
- The Voice AI SDK license, its ASR language list and any language-forcing API are behind a QPM login. I found no public documentation of them.

## Q4. sherpa-onnx QNN backend: models, SoCs, Indic coverage, TTS, APKs

### Takeaway
sherpa-onnx has the broadest open QNN ASR catalog. It publishes prebuilt **SM8750 context binaries** for multilingual Whisper tiny/base/small/medium, along with Moonshine, Parakeet, Nemotron, SenseVoice, Paraformer and Zipformer. Its QNN Whisper code **honors `language` and `task`**, which makes it the most direct ungated route to Hindi/Telugu ASR on the v79 NPU. There are **no Indic-specific QNN models and no QNN TTS** (the QNN backend is ASR-only). The prebuilt QNN APKs cover only zh/ja/en mixes. For Whisper you build the sherpa-onnx native libs with QNN yourself.

### Cited Findings
- [V] The docs say you need "a Qualcomm SM8350 processor or a newer…, such as the SM8450, SM8475, SM8550, SM8650, SM8750, SM8850". `device_info.py` maps SM8750 to soc_id 69, dsp_arch v79, VTCM 8 MB. — [sherpa QNN index](https://k2-fsa.github.io/sherpa/onnx/qnn/index.html), [device_info.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/.github/scripts/export-qnn/device_info.py)
- [V] The QNN implementations in `sherpa-onnx/csrc/qnn/` cover offline Moonshine, Paraformer, Parakeet-CTC, Parakeet-TDT, SenseVoice, Whisper, Zipformer-CTC and Zipformer-transducer, plus online NeMo-transducer and Zipformer-transducer. There is no TTS. — [sherpa-onnx csrc/qnn](https://github.com/k2-fsa/sherpa-onnx/tree/master/sherpa-onnx/csrc/qnn)
- [V] QNN Whisper code: `if (!config_.whisper.language.empty()) { lang_id = GetWhisperLanguageTokenId(...); sot_sequence[1] = lang_id; } else { … DetectLanguage(...) }`, and `task` accepts "translate"/"transcribe". — [offline-whisper-model-qnn.cc](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/qnn/offline-whisper-model-qnn.cc)
- [V] The Whisper QNN export matrix is tiny, tiny.en, base, base.en, small, small.en, medium, medium.en, distil-medium.en, distil-small.en and medium-aishell, built for every SoC in device_info except SM8350. — [generate_whisper.py](https://github.com/k2-fsa/sherpa-onnx/blob/master/.github/scripts/export-qnn/generate_whisper.py), [export-whisper-qnn.yaml](https://github.com/k2-fsa/sherpa-onnx/blob/master/.github/workflows/export-whisper-qnn.yaml)
- [V] Release asset counts: 2,718 QNN assets across `asr-models-qnn{,-2,-3}` and `asr-models-qnn-binary{,-2,-3}`, 235 of them with SM8750 in the name. — [asr-models-qnn-binary-2](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models-qnn-binary-2)
- [V] SM8750 Whisper binaries: `sherpa-onnx-qnn-SM8750-binary-whisper-tiny` (104 MB), `-base` (176 MB), `-small` (507 MB) and `-medium` (1.48 GB), plus the .en and distil variants. SoC-independent `…-android-aarch64` (libmodel.so) builds also exist: tiny 68 MB, small 431 MB. — [asr-models-qnn-binary-2](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models-qnn-binary-2), [asr-models-qnn-2](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models-qnn-2)
- [V] Other QNN model families and their languages:
  - Moonshine tiny/base: en, ar, ja, ko, uk, vi, zh
  - Parakeet-TDT 0.6b v2/v3, Parakeet-CTC 0.6b, Parakeet-TDT_CTC 110m and ja
  - Nemotron-speech-streaming-en-0.6b and nemotron-3.5-asr-streaming-0.6b
  - SenseVoice: zh, en, ja, ko, yue
  - Paraformer: zh
  - Zipformer-CTC: zh
  - ReazonSpeech Zipformer: ja
  - Streaming Zipformer and X-ASR: zh-en
  None is Hindi- or Telugu-specific; grepping all asset names for hindi/indic/telugu returns nothing. — [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases)
- [V] "Since QNN does not support dynamic input shapes, we limit the maximum duration… inputs shorter… will be padded…, longer… truncated". Models are published in 5/8/10/…/30-second variants. — [sherpa QNN models](https://k2-fsa.github.io/sherpa/onnx/qnn/models.html)
- [V] `libmodel.so` is SoC- and QNN-SDK-independent, but its first run is slow because the "context must be generated at runtime". `model.bin` is SoC- and SDK-dependent, with a fast first run. — [qnn_model_comparison](https://k2-fsa.github.io/sherpa/onnx/qnn/run-executables-on-your-phone-binary.html)
- [V] Measured on SM8850 (not SM8750): SenseVoice 5 s binary, "Elapsed seconds: 0.052 s, RTF 0.009" for a 5.59 s wav. The 10 s libmodel.so first and second runs gave RTF 0.017 and 0.053. — [sherpa docs code logs](https://github.com/k2-fsa/sherpa/tree/master/docs/source/onnx/qnn/code)
- [V] The prebuilt QNN APKs (v1.13.8 and earlier) are only "simulated streaming ASR" builds for SenseVoice (zh_en_ko_ja_yue), Zipformer-CTC (zh), Paraformer (zh) and ReazonSpeech (ja), with SM8850-binary and generic variants. There is **no Whisper and no SM8750-binary APK**. — [QNN APK page](https://k2-fsa.github.io/sherpa/onnx/android/apk-qnn-simulate-streaming-asr.html)
- [V] Release v1.13.8 (2026-09-10) ships `sherpa-onnx-1.13.8.aar` and an rknn AAR, but **no QNN AAR or QNN Android tarball**. The docs have you build the shared libs and then copy `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnSystem.so`, `libQnnHtpV79Stub.so` and `libQnnHtpV79Skel.so` into `jniLibs/arm64-v8a`. — [v1.13.8 release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8), [build-android-demo](https://k2-fsa.github.io/sherpa/onnx/qnn/build-android-demo.html)
- [V] Running on device requires `export ADSP_LIBRARY_PATH="$PWD;$ADSP_LIBRARY_PATH"`. A user who skipped this hit failures (#3716). A user who exported Whisper with the standard non-QNN script hit dynamic-shape errors (#3703). — [run-executables binary doc](https://k2-fsa.github.io/sherpa/onnx/qnn/run-executables-on-your-phone-binary.html), [#3716](https://github.com/k2-fsa/sherpa-onnx/issues/3716), [#3703](https://github.com/k2-fsa/sherpa-onnx/issues/3703)
- [V] The QNN SDK (QAIRT Community 2.40.0.251030) downloads **without login**. The URL `https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip` returned HTTP 206 on a ranged GET. — [sherpa download-qnn](https://k2-fsa.github.io/sherpa/onnx/qnn/download-qnn.html)

### Inferences
- [INF] Fastest path to Hindi or Telugu on the NPU: build sherpa-onnx with QNN (NDK + QAIRT 2.40 community SDK), take `sherpa-onnx-qnn-SM8750-binary-whisper-small` or `-medium`, and set `--whisper-language=hi`/`te`. The prebuilt `model.bin` binaries are tied to the QNN version they were built with, so bundle the matching QNN 2.40 libs.
- [INF] One workaround for the build is to unpack the `.so` files (`libsherpa-onnx-jni.so` built with QNN) from one of the prebuilt QNN APKs and reuse them with a Whisper model in your own app. The JNI probably includes every QNN model type, Whisper among them, because they all share one `csrc/qnn` code base. This is untested, and the ABI and QNN version must match.
- [INF] sherpa's non-QNN Android TTS (Piper or VITS, including Piper `hi_IN`/`te_IN` voices, and Kokoro) runs on the CPU only. It could be the disclosed CPU fallback for Indic TTS.

### Gaps
- I found no published sherpa-onnx QNN Whisper latency on SM8750, and no Hindi or Telugu WER through the QNN path.
- I did not verify which languages nemotron-3.5-asr-streaming-0.6b covers.

## Q5. ONNX Runtime QNN EP and LiteRT QNN delegate: practicality for Conformer/CTC ASR and VITS/Kokoro TTS

### Takeaway
Both runtimes are on Maven and support SM8750/v79. The hard constraint is **static shapes**: export at a fixed length (Conformer-CTC as 10 s windows; VITS split into fixed-length encoder, duration, flow and decoder graphs). With a fixed export, Conformer/CTC ASR is practical, and AI Hub's own Conformer models run fully on the NPU. VITS-class TTS is feasible but takes real engineering: replace random sampling with fixed noise, do the length regulation and alignment on the host, and chunk the vocoder. Expect the text front-end, duration/upsampling glue and sometimes iSTFT to stay on the CPU.

### Cited Findings
- [V] ORT QNN EP on Android: Maven `com.qualcomm.qti:onnxruntime-android-qnn` (plugin EP v2.7.0), "validated against" `com.microsoft.onnxruntime:onnxruntime-android` 1.29.0 and `com.qualcomm.qti:qnn-runtime` 2.51.0. — [onnxruntime-qnn EP docs](https://github.com/onnxruntime/onnxruntime-qnn/blob/main/docs/execution_providers/QNN-ExecutionProvider.md)
- [V] Maven Central currently lists `com.qualcomm.qti:onnxruntime-android-qnn` versions 2.3.0–2.6.0 (updated 2026-09-11). The older all-in-one `com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0` (Aug 2026) depends on `com.qualcomm.qti:qnn-runtime:2.42.0`. — [qti ORT metadata](https://repo1.maven.org/maven2/com/qualcomm/qti/onnxruntime-android-qnn/maven-metadata.xml), [MS ORT-QNN 1.29 POM](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android-qnn/1.29.0/onnxruntime-android-qnn-1.29.0.pom)
- [V] "QNN EP does not support models with dynamic shapes (e.g., a dynamic batch size). Dynamic shapes must be fixed to a specific value." — [onnxruntime-qnn EP docs](https://github.com/onnxruntime/onnxruntime-qnn/blob/main/docs/execution_providers/QNN-ExecutionProvider.md)
- [V] On precision, the current doc says: "The QNN HTP backend supports both floating-point models and quantized models" and "Starting with QAIRT 2.35, HTP floating-point operations use FP16 math on SoCs with floating-point model support". The older onnxruntime.ai page still says "The QNN HTP backend only supports quantized models" and "Loops and Ifs are not supported", and mentions QNN 2.22. That page is out of date. — [onnxruntime-qnn docs](https://github.com/onnxruntime/onnxruntime-qnn/blob/main/docs/execution_providers/QNN-ExecutionProvider.md); contradicted by [onnxruntime.ai QNN EP page](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)
- [V] Current op table entries relevant to speech:
  - **LSTM** is supported. By default it is "expanded into per-timestep cells at the ORT layer", and an `enable_htp_monolithic_lstm` option exists.
  - **GRU**: "fixed-length sequences; … no initial hidden/cell state".
  - **STFT**, **CumSum**, **NonZero**, **RandomNormalLike** and **RandomUniformLike** are listed.
  - **If**: "both branches are always executed".
  - **Range**: "All inputs… must be constant initializers".
  - **Attention**: "Static shapes only".
  — [onnxruntime-qnn op table](https://github.com/onnxruntime/onnxruntime-qnn/blob/main/docs/execution_providers/QNN-ExecutionProvider.md)
- [V] Controls for verifying placement:
  - `session.disable_cpu_ep_fallback=1` "Returns an error if any operator cannot be handled by QNN EP".
  - `enable_framework_op_trace=1` writes JSON that "records unsupported operators, and includes summary statistics".
  - `soc_model` accepts `"SM8750"` or `htp_arch` `79`.
  - `ep.context_enable=1` caches the compiled context.
  — [onnxruntime-qnn docs](https://github.com/onnxruntime/onnxruntime-qnn/blob/main/docs/execution_providers/QNN-ExecutionProvider.md)
- [V] LiteRT: the Qualcomm page lists "Snapdragon 8 Elite Mobile Platform (SM8750)". "LiteRT supports Qualcomm AI Engine Direct (QNN) through the `CompiledModel` API for both AOT and on-device compilation". The ops list has 90+ ops, and block-wise quantization is unsupported. Google Maven has `litert` 2.3.0 and `litert-npu-runtime-qualcomm` 2.3.0. — [LiteRT Qualcomm](https://developers.google.com/edge/litert/next/qualcomm), [QNN compiler ops](https://github.com/google-ai-edge/LiteRT/blob/main/litert/vendors/qualcomm/compiler/Qualcomm_QNN_Compiler.md), [Google Maven group index](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/group-index.xml)
- [V] How AI Hub made Piper/VITS fit the NPU:
  - Split into Encoder, SDP, Flow and Decoder graphs, with fixed `MAX_SEQ_LEN = 512` phonemes and `UPSAMPLED_MAX_SEQ_LEN = 1536`.
  - Replaced random sampling with a **deterministic buffer** (`sdp_noise_pattern = ones*0.5`, `fixed_noise = randn(...)*0.5` registered once).
  - Run the decoder in chunks of `MAX_DEC_SEQ_LEN = 40` frames with `DEC_SEQ_OVERLAP = 12`.
  - Include a separate `monotonic_align.py` and `model_patch.py`.
  — [pipertts template model.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/pipertts/model.py), [template dir](https://github.com/qualcomm/ai-hub-models/tree/main/src/qai_hub_models/models/templates/pipertts)
- [V] The same template already loads one voice (Italian `it_IT-paola-medium.onnx`) straight from a released Piper ONNX through `build_model_from_onnx(model_path, config_path)`. The other voices load from `.ckpt` files. — [pipertts template model.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/pipertts/model.py)
- [V] Piper has released Indic voices: `hi_IN-pratham-medium`, `hi_IN-priyamvada-medium`, `hi_IN-rohan-medium`, `te_IN-maya-medium`, `te_IN-padmavathi-medium`, `te_IN-venkatesh-medium`, plus ml/mr/bn/ne/ur voices. — [rhasspy/piper-voices voices.json](https://huggingface.co/rhasspy/piper-voices/blob/main/voices.json)
- [V] AI Hub's TTS uses a T5 G2P, `charsiu/g2p_multilingual_byT5_tiny_16_layers_100`. CharsiuG2P's training data includes `data/train/hin.tsv` but has **no Telugu training file**; Telugu appears only under `sources/espeak-ng/tel.tsv` and in wikipron info. — [t5_g2p.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/voiceai_tts/t5_g2p.py), [CharsiuG2P](https://github.com/lingjzhu/CharsiuG2P)
- [V] Kokoro has Hindi voices (2F, 2M: `hf_alpha`, `hf_beta`, `hm_omega`, `hm_psi`) and no Telugu. — [Kokoro VOICES.md](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)
- [V] One app tried QNN for Kokoro and Pocket-TTS and gave up. The stock ONNX exports have dynamic `batch, seq_len` dimensions (Kokoro: 2,469 nodes), and `qnn-onnx-converter` failed with "Dynamic input dimensions detected… QNN requires all input dimensions to be fixed at conversion time." — [NekoSpeak ADR-002](https://github.com/siva-sub/NekoSpeak/blob/main/docs/adr/ADR-002-qnn-execution-provider.md)
- [V] A working Kokoro on HTP (SM8650/v75, not v79):
  - The CPU handles "text, duration, conditioning, exact harmonic-source math, and iSTFT". HTP runs "the 1,153-node neural-vocoder suffix".
  - It needs 11 AOT HTP buckets (T=64…640).
  - The author "Replaced 50 exact `Pow(x, 2)` operations with `Mul(x, x)`… Avoids an observed HTP numerical failure".
  - Every HTP session sets `session.disable_cpu_ep_fallback=1`.
  — [kokoro-offline-tts-android](https://github.com/cedgeremek/kokoro-offline-tts-android)

### Inferences
- [INF] **Conformer/CTC ASR (IndicConformer, NeMo CTC, wav2vec2-CTC):**
  - Re-export at a fixed length (10 s or 15 s, padding with masking) and run greedy CTC decoding on the CPU. AI Hub's FunASR-Conformer and Wav2Vec2-Conformer show the encoder can sit fully on the HTP.
  - Main risks: relative-position attention and depthwise-conv ops, and dynamic `length` tensors. Bake the length in, or pass a mask.
  - fp16 is supported on v79, so int8 calibration is not mandatory. That helps accuracy for Indic.
  - A 600M-parameter multilingual model at fp16 is about 1.2 GB, comparable to Wav2Vec2-Conformer-Large's 768 ms per 10 s. That is acceptable.
- [INF] **VITS/Piper:** the AI Hub Piper template is the shortest path to a Hindi or Telugu NPU TTS. Add a `TTSLanguage` entry pointing to `hi_IN-*.onnx`/`te_IN-*.onnx` and reuse `build_model_from_onnx`. Keep G2P on the CPU with espeak-ng, because Piper voices were trained on espeak-ng phonemes and Charsiu has no Telugu. Then compile the four graphs through AI Hub. Effort is about a few hours if the template generalises, and unknown if `model_patch` or `monotonic_align` assume English-specific configs. Not verified.
- [INF] **Kokoro:** Hindi voices exist, but Kokoro on HTP has only been demonstrated for English on v75, with heavy surgery (buckets, Pow rewrites, CPU iSTFT). It is not a 24-hour job.
- [INF] **STFT/iSTFT:** the op exists in ORT QNN, but both Kokoro projects keep the iSTFT or source path on the CPU or hand-write it. Assume the iSTFT tail runs on the CPU.
- [INF] **CPU fallback fraction:** it depends on the model, and I found no general figure. Well-prepared graphs reach 100% NPU (AI Hub "Primary Compute Unit: NPU"; Whisper tiny encoder 129/129 layers). Measure your own export with `enable_framework_op_trace` and fail fast with `disable_cpu_ep_fallback=1`.

### Gaps
- I found no public measurement of IndicConformer, or any NeMo Indic model, on any Hexagon NPU.
- I found no measured data on how much of a stock VITS ONNX (with RandomNormalLike, CumSum and NonZero kept in the graph) the QNN EP actually places on the HTP on v79.

## Q6. Public projects running Indic or multilingual ASR/TTS on a Snapdragon NPU

### Takeaway
I found **no public Android project** that runs Hindi or Telugu ASR or TTS on a Hexagon NPU. The closest evidence is Windows-on-Snapdragon apps running AI Hub Whisper on the NPU with Hindi input (X Elite), two Kokoro-on-HTP efforts (English, SM8650/SM8550), and GPT-SoVITS on QCS8550 (Chinese).

### Cited Findings
- [V] **Sahaay (Snapdragon X PCs, Sept 2026):**
  - Whisper runs on the Hexagon NPU through the ORT QNN EP, with NLLB translation into 22 Indian languages.
  - AI Hub profile of the whisper_tiny_en encoder: 27.56 ms on X Elite and 13.47 ms on X2 Elite, "129/129" layers on the NPU.
  - The author warns: "This number was wrong until 22 Sep 2026… It had measured `whisper_tiny_en` against a synthetic test tone".
  - The UI shows an execution-provider badge (for example "Fallback reason: QNN present but bound to CPU").
  — [Hackathon-SnapdragonAILab](https://github.com/andringodson/Hackathon-SnapdragonAILab), [docs/AIHUB.md](https://github.com/andringodson/Hackathon-SnapdragonAILab/blob/main/docs/AIHUB.md)
- [V] **NotesNPU (X Elite):**
  - Runs AI Hub Whisper-Base precompiled QNN ONNX FP16 at 45.5 ms per encoder pass and 3.8 ms per decoder token.
  - Its decode loop "forces the prompt tokens `<|startoftranscript|> <|lang|> <|task|> <|notimestamps|>`… and has a repetition guard against Whisper's looping failure mode".
  - It advertises Hindi→English translation.
  — [NotesNPU](https://github.com/mdharishsuhaib/NotesNPU), [ARCHITECTURE.md](https://github.com/mdharishsuhaib/NotesNPU/blob/main/docs/ARCHITECTURE.md)
- [V] **Kokoro on HTP (Galaxy S24 Ultra, SM8650/v75, 28 English voices):** "Request to first audio **306 ms**", generator RTF 0.2644 (3.78x real time), "QNN_HTP, T=192 AOT bucket". — [kokoro-offline-tts-android](https://github.com/cedgeremek/kokoro-offline-tts-android)
- [V] **Kokoro-Hexagon (SM8550):** hand-emitted HVX/HMX code. The whole generator, including harmonic source and STFT, takes 95.57 ms for 1.625 s of audio (RTF 0.059). "Whole-model RTF, time to first audio… are not yet measured." Research-grade. — [Kokoro-Hexagon](https://github.com/MansfieldPlumbing/Kokoro-Hexagon)
- [V] **GPT-SoVITS on QCS8550 (v73, Ubuntu):**
  - All 6 graphs run fp16 on the NPU, and 12.34 s of speech synthesizes in 5.70 s (RTF 0.46).
  - HiFiGAN is windowed at 96 frames with a 12-frame overlap because the whole graph "needs 27 MB TCM, v73 only has 8 MB".
  - Language: Chinese.
  — [gpt-sovits-qnn](https://github.com/zhouzengming/gpt-sovits-qnn)
- [V] AI Hub has open model requests for Qwen3-TTS (#321, 2026-06-03) and had one for seamless-m4t-v2 (#229). Both are signs that multilingual TTS for the NPU is not yet supplied. — [ai-hub-models issues](https://github.com/qualcomm/ai-hub-models/issues/321)
- [V] AI Hub's sample-app repo has Android apps for vision and GenieX chat, plus `whisper_windows_py`. It has **no Android ASR or TTS app**. Qualcomm previously pointed Android Whisper users to WhisperKit. — [ai-hub-apps](https://github.com/qualcomm/ai-hub-apps), [ai-hub-models #99](https://github.com/qualcomm/ai-hub-models/issues/99)
- [V] GenieX (Qualcomm's on-device GenAI runtime, Android AAR `com.qualcomm.qti:geniex-android`, 8 Elite supported) covers LLMs and VLMs only. Its README mentions no ASR or TTS. — [GenieX](https://github.com/qualcomm/GenieX)

### Inferences
- [INF] A team that shows Hindi or Telugu ASR on the v79 NPU on Android would apparently be first among public projects. Expect to debug alone; there are no prior issue threads to lean on.

### Gaps
- GitHub multi-term code search ("QNNExecutionProvider kokoro/vits/conformer", "IndicConformer android") returned nothing. Private or unindexed projects may exist.
- I found no paper reporting Indic ASR or TTS latency or accuracy on a Snapdragon NPU. The one adjacent item is a Hindi speech-separation paper with deployment on an S24 via AI Hub, not ASR ([arXiv 2508.12009](https://arxiv.org/pdf/2508.12009)).

## Q7. Whisper multilingual on the NPU: language tokens, Hindi/Telugu, silent wrong output

### Takeaway
The NPU graphs (AI Hub and sherpa) are plain per-token decoders with a KV cache. The language token is a host-side prompt choice. AI Hub's reference loop **auto-detects** the language; you get Hindi or Telugu by seeding `[SOT, <|hi|>/<|te|>, <|transcribe|>, <|notimestamps|>]`. sherpa-onnx does this through `language=`. WhisperKit cannot. Silent wrong output on Hexagon is a real failure mode, with documented cases: "!!!!" output, token mismatch against the CPU, and a Pow numerical failure. Always A/B a fixed WAV against a CPU reference.

### Cited Findings
- [V] The AI Hub reference app starts decoding from `output_ids = [[sot]]` (decoder_start_token_id only), feeds one token per step, and appends predictions only once `n >= output_length - 1`. That means a longer seeded prefix is teacher-forced. `whisper_small` declares `language_detection=True`. — [hf_whisper/app.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/hf_whisper/app.py), [whisper_small/model.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/whisper_small/model.py)
- [V] AI Hub issue #263 (whisper-small, QCS8550 proxy): on-device token IDs differed from the PyTorch reference. The device sequence began `[50258, 50260, 50359, 50363, …]`, a predicted language token followed by transcribe and notimestamps. Qualcomm's reply: "Some small output variance is expected… Accuracy on device is within .15% of floating point accuracy via PyTorch" on LibriSpeech. — [ai-hub-models #263](https://github.com/qualcomm/ai-hub-models/issues/263)
- [V] NotesNPU forces the `<|lang|> <|task|>` prompt with AI Hub's Whisper-Base binaries and adds a repetition guard. — [NotesNPU ARCHITECTURE.md](https://github.com/mdharishsuhaib/NotesNPU/blob/main/docs/ARCHITECTURE.md)
- [V] sherpa-onnx QNN Whisper sets `sot_sequence[1]` from the configured language, or runs `DetectLanguage` if none is given. — [offline-whisper-model-qnn.cc](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/qnn/offline-whisper-model-qnn.cc)
- [V] WhisperKit on SM8750 produced "!!!!!!!!!!…" transcriptions together with QNN err 6001 (#58). Multilingual WhisperKit translated non-English audio to English (#75). — [#58](https://github.com/argmaxinc/WhisperKitAndroid/issues/58), [#75](https://github.com/argmaxinc/WhisperKitAndroid/issues/75)
- [V] Kokoro-on-HTP hit "an observed HTP numerical failure" from `Pow(x,2)` and worked around it with `Mul(x,x)`. — [kokoro-offline-tts-android](https://github.com/cedgeremek/kokoro-offline-tts-android)
- [V] GPT-SoVITS-QNN keeps its G2P output head in fp32 on the CPU because "fp16 会判错读音" (fp16 mispredicts pronunciations). — [gpt-sovits-qnn](https://github.com/zhouzengming/gpt-sovits-qnn)
- [V] The local team notes say: "Don't run Whisper through llama.cpp's HTP path; its output is garbled (llama.cpp#25876)". — [local SKILL.md](file:///home/th4kur/Desktop/m26-qidk/.claude/skills/qidk-npu-android/SKILL.md)
- [V] AI Hub caps decoding at 200 tokens (the official limit is 448). — [hf_whisper/model.py](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/templates/hf_whisper/model.py)

### Inferences
- [INF] The 200-token cap can truncate long Telugu or Hindi windows, because Whisper's byte-level BPE breaks Indic scripts into many tokens. Keep utterances short (VAD segments of 5–10 s) or re-export with a larger `MEAN_DECODE_LEN`.
- [INF] On short, code-mixed incident-report audio, language auto-detection is unreliable. Force the language from a UI toggle (EN/HI/TE).
- [INF] w8a16 quantization was validated on English LibriSpeech only. For Indic, prefer the float (fp16) binaries unless you have checked accuracy on Hindi or Telugu clips.
- [INF] A safety check that is cheap to show judges: run the same 5 s Hindi WAV through the NPU and through CPU Whisper (sherpa CPU or whisper.cpp) and display both transcripts and their CER.

### Gaps
- I found no published Hindi or Telugu WER for Whisper on any NPU path, and no report of Indic-specific garbling on HTP.

## Q8. If nothing Indic runs on the NPU: the most defensible hybrid, and how to present it honestly

### Takeaway
For ASR, a full NPU run of multilingual Whisper (encoder and decoder) is achievable. Where it isn't, the cleanest hybrid is the encoder on the NPU and the decoder loop on the CPU. The encoder is the single most expensive call per window (see the latency rows below). For TTS, the defensible split is the acoustic model and vocoder on the NPU with G2P, duration/alignment glue and iSTFT on the CPU, which is exactly how AI Hub's MeloTTS/Piper and the Kokoro-HTP app are built. If Indic TTS can't reach the NPU in time, use Android TTS on the CPU and say so, with runtime evidence on screen.

### Cited Findings
- [V] AI Hub's own production TTS is already hybrid. Its MeloTTS and Piper bundles split G2P (T5), BERT, encoder, duration (SDP), flow and decoder into separate graphs, and the host app chains them. Chunked decoding and alignment code live in the template. — [pipertts template](https://github.com/qualcomm/ai-hub-models/tree/main/src/qai_hub_models/models/templates/pipertts), [MeloTTS-EN card](https://huggingface.co/qualcomm/MeloTTS-EN)
- [V] The Kokoro-HTP app runs text, duration, conditioning and iSTFT on the CPU, and the 1,153-node vocoder on the HTP. It proves the HTP part by setting `session.disable_cpu_ep_fallback=1`: "A logged `QNN_HTP` window really used QNN rather than quietly dropping nodes onto CPU". — [kokoro-offline-tts-android](https://github.com/cedgeremek/kokoro-offline-tts-android)
- [V] Sahaay shows the execution provider live ("Execution provider: Hexagon NPU (QNN) … Hexagon NPU active: yes", or "Fallback reason: …") and states that "the UI never claims a capability it does not have". — [Hackathon-SnapdragonAILab](https://github.com/andringodson/Hackathon-SnapdragonAILab)
- [V] On the kit, the default Android TTS is Google (`com.google.android.tts`), which runs on the CPU. The team notes say: "If anything runs on the CPU (TTS, or an LLM fallback), say so to the judges. Never claim 'fully NPU'". — [local SKILL.md](file:///home/th4kur/Desktop/m26-qidk/.claude/skills/qidk-npu-android/SKILL.md)
- [V] AI Hub's Whisper encoder cost dwarfs the per-token decoder cost on 8 Elite. Whisper-Small encoder is 65 ms against 8.3 ms per decoder token; Large-V3-Turbo w8a16 is 151 ms against 3.5 ms. — [Whisper-Small card](https://huggingface.co/qualcomm/Whisper-Small), [Large-V3-Turbo-Quantized card](https://huggingface.co/qualcomm/Whisper-Large-V3-Turbo-Quantized)

### Inferences
- [INF] Recommended stack for the 24 h event, in priority order:
  - **ASR, English:** WhisperKit `QUALCOMM_TINY_EN`/`BASE_EN` with the corrected `com.qualcomm.qti` QNN dependencies. This is the lowest-risk path and is already proven on SM8750.
  - **ASR, Hindi/Telugu:** sherpa-onnx built with QNN, using the SM8750-binary multilingual whisper-small or medium with `language=hi`/`te`. Both encoder and decoder run on the HTP, which makes "fully on NPU" an honest claim for ASR.
  - **ASR fallback:** AI Hub Whisper-Small float `PRECOMPILED_QNN_ONNX` through `onnxruntime-android-qnn`, with your own forced-language loop in Kotlin. The tokenizer and mel run on the CPU.
  - **TTS, English:** MeloTTS-EN or Piper-EN on the NPU via the Voice AI TTS SDK (QPM-gated), or the AI Hub Piper graphs through ORT QNN.
  - **TTS, Hindi/Telugu:** the stretch path is Piper `hi_IN`/`te_IN` through the AI Hub Piper template and AI Hub compile, with espeak-ng G2P on the CPU. The fallback is sherpa-onnx Piper `hi_IN`/`te_IN` on the CPU, or Android TTS, disclosed as CPU.
- [INF] A rough measure for presenting the split honestly: report the share of wall-clock time per stage on NPU vs CPU (mel, encoder, decoder steps, G2P, vocoder). For a VITS pipeline the NPU graphs dominate compute, while G2P and alignment take milliseconds.

### Gaps
- I did not measure the end-to-end CPU/NPU time split for any of these pipelines on SM8750P. The team will need to log this on the kit.

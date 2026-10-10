# AI Incident Report Assistant on QIDK — Feasibility & Due-Diligence Report

Date: 2026-10-10 · Scope: research only, no code written · Hackathon: Megathon X (IIIT Hyderabad), Qualcomm problem statement

**Evidence tags:** **[V]** = I verified it directly against the primary source (GitHub API or file contents) in this session. **[I]** = a research subagent opened and read the page. **[S]** = search-snippet only, unverified. **EST** = my estimate, with its derivation.

**Method note:** The Exa MCP server was not authenticated, so none of this research used Exa. Five parallel subagents used built-in web search and fetch plus the GitHub API, covering about 680 sources in total. **Reddit blocked every access path**, so no Reddit thread was inspected (see §6).

---

## 1. Executive decision

### Verdict: CONDITIONAL GO

The verdict depends on one fact you must confirm before the event: **the exact SoC and Android version of the QIDK units you will be handed**, and whether an LLM runs on that kit's NPU or GPU.

**Why it is not a NO-GO:**
- Every stage of the pipeline has an evidence-backed path that keeps all inference on the device.
- Speech-to-text is the strongest stage. Qualcomm ships an Android Whisper app in the QIDK repo whose device table lists **SM8550 / V73** ([NLPSolution3](https://github.com/qualcomm/qidk/tree/master/Solutions/NLPSolution3-AutomaticSpeechRecognition-Whisper)) [I].
- On the LLM side, there is a firsthand report of **Llama-3.2-1B Q4_0 running on the Hexagon NPU of an SM8550 (8 Gen 2)**. It measured **23.8 tok/s generation and 1143 tok/s prompt processing** through llama.cpp's Hexagon backend ([llama.cpp#26123](https://github.com/ggml-org/llama.cpp/issues/26123), 2026-07-25) [V].

**Why it is not an unconditional GO:**
1. **Qualcomm's own LLM stack does not officially support the 8 Gen 2 QIDK.** This covers Genie, the GenieX Android SDK, LiteRT-LM NPU builds, and the QIDK `ASR-LLM-TTS` and `AI-Assistant` samples.
   - A Qualcomm maintainer, on an 8 Gen 2 device: *"the LLMs that we host require a minimum of 8 Gen 3… Genie uses features only available in Android 15"* ([ai-hub-apps#56](https://github.com/quic/ai-hub-apps/issues/56)) [V].
   - The QIDK sample that does the whole pipeline lists only Snapdragon 8 Elite (V79) and 8 Elite Gen 5 (V81) ([ASR-LLM-TTS README](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS)) [V].
   - The GenieX and LiteRT-LM blog posts in the QIDK docs both require SM8750 or SM8850 ([geniex_android.rst](https://github.com/qualcomm/qidk/blob/master/docs/source/blogs/geniex_android.rst), [litert_llm.rst](https://github.com/qualcomm/qidk/blob/master/docs/source/blogs/litert_llm.rst)) [V].
2. **The only NPU route that works on 8 Gen 2 is unofficial, experimental and size-capped.** It is the llama.cpp Hexagon backend.
   - 4B and 7B models crash there with `dspqueue_read failed: 0x2e` [V].
   - That report ran command-line binaries over adb on Android 15, not inside an APK.
3. **The rules are strict.** They say *"All AI models must execute on-device using Snapdragon acceleration. Strictly no cloud APIs."* (slide 3).
   - An LLM running on the CPU arguably fails the first evaluation criterion.
   - A cloud LLM is disqualifying.

**Biggest unresolved risk:** which kit you actually get. Official QIDK docs say the kit is "SM8550P / Snapdragon 8 Gen 2" [I]. But an **IIIT-H team's issue describes their QIDK as "SM8650" (8 Gen 3) running Android 14** ([qidk#76](https://github.com/qualcomm/qidk/issues/76), 2025-10-10) [V]. Newer QIDK docs also refer to a "QIDK Snapdragon 8 Elite device" [I]. The outcome swings a lot depending on the answer:

| Kit you get | LLM path | Effect on verdict |
|---|---|---|
| **8 Elite (SM8750), Android 15+** | Fork Qualcomm's `ASR-LLM-TTS` sample (Whisper + Llama 3.2 3B on Genie + MeloTTS, all on the NPU) | Upgrade to **GO**. Most plumbing already exists. |
| **8 Gen 3 (SM8650)** | llama.cpp Hexagon, which lists "Gen3" as a target ([PR #16547](https://github.com/ggml-org/llama.cpp/pull/16547)) [I]. Genie only if Android 15. | **CONDITIONAL GO**, moderate risk |
| **8 Gen 2 (SM8550, v73)**, Android 13/14 | llama.cpp Hexagon with a model of 1–2B at most. Fallback is Adreno OpenCL (unverified for Adreno 740) or CPU, which you must disclose. | **CONDITIONAL GO**, high risk. Pass the 90-minute spike first. |

---

## 2. Interpretation of the slides

**Source:** `Qualcomm_Problem_Statement_for_MegathonX.pptx`, 5 slides plus speaker notes. I read all text, notes and images. The full specification is an image on slide 3.

**Explicitly stated:**
- **Title:** "Problem Statement 1 – AI Incident Report Assistant: end-to-end voice-driven incident reporting using on-device Generative AI and function calling."
- **Pipeline (slide 3 diagram):** Speech-to-Text → LLM Information Extraction → **"Mandatory fields present?"** decision → Function/Tool calling → Populate form, user review, submit report → **Display on screen** and **Text-to-Speech output**.
- **Fields to populate automatically:** Incident Type, Severity Level, Location, Date & Time, Persons Involved, Equipment/Asset, Description, Immediate Actions Taken, Witness Information, Recommended Follow-Up.
- **Example:** "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area." produces Type = Water Leak, Location = Conference Room 2, Time = 10 AM, Action Taken = Area Isolated.
- **Evaluation criteria** (the only judging rubric given, with no weights):
  1. All AI models on-device with Snapdragon acceleration, strictly no cloud APIs.
  2. Understands natural conversational speech, not predefined commands.
  3. Accuracy of speech recognition.
  4. Accuracy of information extraction.
  5. Correct mapping of extracted information to report fields.
- **Speaker notes add:** "checks mandatory fields, calls the required function, and lets the user review before submission", and "completely on-device".
- **Resources (slide 4):** QIDK docs, the QIDK GitHub repo, AI Hub models, and the QAIRT setup guide.

**Inferred (not stated):**
- **Follow-up dialogue.** The decision diamond implies that when mandatory fields are missing, the app asks for them, so the conversation is multi-turn. Which fields are mandatory is never defined. A sensible default is type, location, date/time, and probably severity.
- **"Function calling" has two possible readings:**
  - (a) The LLM emits structured tool calls such as `fill_fields(...)`, `ask_user(field)` or `submit_report(...)`.
  - (b) After extraction, the app calls a "submit report" function.

  Judges will probably look for (a). Implementing (a) also satisfies (b).
- **No backend or ticketing integration is required.** "Submit" can be persisting locally or exporting JSON or PDF.
- **Users** are frontline workers, facilities staff and safety officers, presumably English-speaking. Multilingual support is not required.
- **Severity** is rarely spoken explicitly, so it must be inferred or asked for. Date and time is often relative ("around 10 AM", "yesterday") and must be resolved against the device clock.
- **Every team at Megathon gets the same problem.** Differentiation is about execution quality relative to other teams, not novelty relative to the market.

---

## 3. Hyperlink and source audit

| Slide | URL | Status | Contents | What it establishes |
|---|---|---|---|---|
| 4 | https://qualcomm.github.io/qidk/release-notes/overview.html | 200 [I] | QIDK overview. Lists supported platforms 8 Elite Gen 5, 8 Gen 3 and 8 Gen 2. "Runs only on Android." Claims "on device deployment of… LLMs" and "LLM-based chat applications." No version or date. | QIDK is Android-only and markets LLM support, but generically, not per SoC. |
| 4 | https://github.com/quic/qidk | **Redirects** to https://github.com/qualcomm/qidk [I] | Repo. Default branch `master`, last push 2026-09-05. Folders: `Solutions/`, `GenAI-Solutions/` (AI-Assistant, ASR-LLM-TTS, speech_to_image), `Examples/`, `Tools/qairt_docker`, `docs/`. Only GitHub release is "QIDK-Phase2" (2023-08-10). | The reusable samples (see §4). |
| 4 | https://aihub.qualcomm.com/mobile/models | 200, no login needed [I] | 231 models. LLMs include Qwen3 0.6–4B, SmolLM2-1.7B, Ministral-3B, Gemma-4 E2B/E4B. Runtimes: GenieX, QAIRT, llama.cpp, ONNX, TFLite. **No speech models in the mobile listing.** QIDK is not a listed device. | Pre-compiled LLMs exist, but published performance and support target 8 Elite-class chips (see §4). |
| 4 | https://docs.qualcomm.com/bundle/publicresource/topics/80%2063442-2/setup.html | HEAD returns 200, **but the body is empty** to every fetcher [I] | **Could not be inspected.** Replacements: [setup_installation.rst](https://github.com/qualcomm/qidk/blob/master/docs/source/release-notes/setup_installation.rst) and [quick_start.rst](https://github.com/qualcomm/qidk/blob/master/docs/source/release-notes/quick_start.rst) [I]. | Host requirements come from the replacements: Ubuntu 22.04 + Docker, QAIRT 2.47 Docker default, NDK r26c. |
| — | https://qualcomm.github.io/qidk/ | 200 [I] | Landing page with navigation, including "LLM Onboarding" and Blogs. | — |
| — | https://www.qualcomm.com/developer/hardware/qualcomm-innovators-development-kit | Cloudflare challenge; unreadable [I] | — | Used [80-63195-1 Getting Started](https://docs.qualcomm.com/bundle/publicresource/80-63195-1/topics/Getting-started.md) instead: "Main board with SM8550P processor", ships with Android [I]. |

The other slide images are decorative: the logo, a Copilot-generated hero image, and a backdrop. The notes and images contain no other links.

---

## 4. Qualcomm QIDK technical feasibility

### 4.1 Hardware facts and unknowns

| Item | Finding | Confidence |
|---|---|---|
| SoC | Official docs say **SM8550P, Snapdragon 8 Gen 2** [I]. An IIIT-H user reports "QIDK (SM8650)" on Android 14 [V, #76]. | **Conflicting. Must verify with `adb shell getprop ro.soc.model`.** |
| NPU | Hexagon **V73** for SM8550, per the Whisper sample's device table [I]. Not all V73 SKUs are equal: lower V73 parts lack FP16 HMX ([llama.cpp#29473](https://github.com/ggml-org/llama.cpp/issues/29473)) [I]. | Medium |
| RAM | **Not stated in any official doc.** One unrelated issue mentions a 4 GB device. A Qualcomm reference build on 8 Gen 2 showed 16 GB (#56). | **Unknown. Check `/proc/meminfo`.** |
| OS | Android only; userdebug builds; `adb root` and `setenforce 0` work (#76) [V]. IIIT-H kit runs Android 14 [V]. | Medium |
| Power | Mains-powered only, no battery operation ([qidk#80](https://github.com/qualcomm/qidk/issues/80)) [V]. | High. Bring a power strip. |

### 4.2 Runtime paths for the LLM, from most to least official

| Path | Accelerator | Official 8 Gen 2 support | Evidence | Verdict for QIDK |
|---|---|---|---|---|
| Genie / QAIRT context binaries (AI Hub bundles) | NPU | **No.** Hosted LLMs require 8 Gen 3 or newer plus Android 15. Tutorial: "Hexagon v73 or above… Android 15+… 12 GB for 3B+" ([llm_on_genie](https://github.com/quic/ai-hub-apps/blob/main/tutorials/llm_on_genie/README.md)) [I]. A maintainer: "8 Gen 2 with 8 GB of RAM will likely not work" [V]. | Llama-3.2-3B: 26.5 tok/s on 8 Elite ([HF card](https://huggingface.co/qualcomm/Llama-v3.2-3B-Instruct)) [I]. **No 8 Gen 2 numbers published.** Export takes 1–2 h and 40–80 GB on the host [I]. | **Avoid on 8 Gen 2 or Android 14. Primary path on 8 Elite.** |
| GenieX Android SDK (Maven `com.qualcomm.qti:geniex-android`) | NPU (qairt) or NPU/GPU/CPU (llama.cpp runtime) | **No.** Prerequisites: SM8750 or SM8850 only [V]. Developer preview. | HTP session errors on current versions ([GenieX#1507](https://github.com/qualcomm/geniex/issues/1507)). Silent identical outputs on a V73 Zebra device ([#1429](https://github.com/qualcomm/geniex/issues/1429)) [I]. | Worth a 20-minute "does it load" test because it is a single Gradle dependency. Do not plan around it. |
| LiteRT-LM NPU (`.litertlm` per chipset) | NPU | **No.** Builds exist only for SM8750 and SM8850 [V]. | — | 8 Elite only. |
| **llama.cpp Hexagon backend (`--device HTP0`)** | **NPU** | Not named, but v73 is in the supported-architecture list ([PR #16547](https://github.com/ggml-org/llama.cpp/pull/16547)) [I]. | **Firsthand on SM8550 / v73 / Android 15: Llama-3.2-1B Q4_0 works at 23.8 tok/s generation and 1143 tok/s prefill. 4B and 7B crash** [V, #26123]. | **Primary path on 8 Gen 2 or Gen 3. Cap at 1–2B parameters.** |
| llama.cpp OpenCL (Adreno) | GPU | **No.** Verified list is Adreno 750/830/840; Adreno 740 is absent ([OPENCL.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md)) [I]. | — | Fallback 1. Plausible but unverified. |
| MLC LLM (OpenCL) | GPU | Snippets claim 8 Gen 2 support; a 2023 slide quotes Llama-2-7B at 5–11 tok/s [S]. | — | Fallback 2. Heavy build; skip unless the others fail. |
| ExecuTorch + QNN | NPU | No SoC named. Export needs a host with more than 64 GB RAM plus swap [I]. | — | **Too slow to set up in 24 hours.** |
| MediaPipe LLM Inference | GPU | — | Maintenance-only mode, no function calling [I]. | Avoid. |
| llama.cpp CPU | CPU | Works everywhere. | — | **Last resort.** Arguably fails criterion 1; disclose it. |

**Packaging insight that lowers risk:** the firsthand 8 Gen 2 success used llama.cpp's adb scripts, not an Android library. Running `llama-server` **on the device**, bound to 127.0.0.1, with the app calling it over loopback HTTP is still 100% on-device. It sidesteps JNI and APK packaging of the Hexagon libraries. It also gives you OpenAI-style `json_schema` and tool-call support for free.
- This is my recommendation, not a Qualcomm-documented pattern.
- It needs a launch script over adb or root, which is acceptable on a kit that is always plugged in.
- Disclose it to judges as a "local inference daemon".

### 4.3 Speech-to-text

| Option | Accelerator | 8 Gen 2 evidence | Status |
|---|---|---|---|
| **QIDK `NLPSolution3` Whisper app** | Encoder (w8a16) on HTP; decoder in FP32 TFLite on CPU | **SM8550 / V73 listed** [I] | **Confirmed by an official example.** English-only, tiny-class model, 30 s window, log-mel computed in app code. Building it needs the Docker notebook to produce the encoder DLC, then `resolveDependencies.sh` and Android Studio [I]. README still says SNPE although the code moved to QAIRT 2.47 [I]. |
| QIDK `ASR-LLM-TTS` Whisper (Voice AI ASR SDK) | NPU | V79/V81 only [V] | Only if you get an 8 Elite kit. Needs `qpm-cli --login` [I]. |
| whisper.cpp Android example | CPU (Vulkan optional) | Generic | Fallback. Does not satisfy the acceleration criterion for ASR. |
| sherpa-onnx (+ QNN) | CPU; QNN for Zipformer and SenseVoice, not Whisper | QNN requires SM8350+ [I] | Alternative fallback with ready-made APKs including VAD. |

- **Known DSP pitfall on IIIT-H kits.** `isRuntimeAvailable()` returns false for the DSP. Qualcomm's fix: add `<uses-native-library android:name="libcdsprpc.so" android:required="true"/>` and use `setenforce 0` ([qidk#76](https://github.com/qualcomm/qidk/issues/76)) [V].
- **Accuracy caveat.** NPU and CPU Whisper tokens differ slightly; Qualcomm says this is expected ([ai-hub-models#263](https://github.com/quic/ai-hub-models/issues/263)) [I]. Measure word error rate on your own recordings.
- **Silence.** Add Silero VAD ([repo](https://github.com/snakers4/silero-vad)) or an energy gate to avoid Whisper hallucinating on silence.

### 4.4 Text-to-speech

- **On 8 Gen 2 or Gen 3 there is no NPU TTS.** MeloTTS on QNN is listed for V79+ only [I]. The practical option is **Android `TextToSpeech` with an offline voice**, which runs on the CPU through the system engine.
- The judging criteria never mention TTS, but "all AI models on Snapdragon acceleration" technically covers it. **Disclose it honestly:** "TTS uses the system offline engine; all models we ship run on the NPU."
- On an 8 Elite kit, MeloTTS from the `ASR-LLM-TTS` sample runs on the NPU.

### 4.5 Structured output and function calling

- **Genie and GenieX:** no documented grammar or JSON-schema decoding [I]. GenieX's QAIRT path silently dropped `tools` until a recent fix ([#1454](https://github.com/qualcomm/geniex/issues/1454)) [I].
- **llama.cpp:** GBNF grammars and JSON schema to grammar conversion ([grammars/README](https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md)) [I]. Grammar sampling runs on the CPU sampler whatever backend computes the logits. That is my reading of the architecture; **verify it with the HTP backend during the spike.**
  - A LiteRT benchmark found constrained decoding *failing on the NPU* ([dev.to, 2026-05-01](https://dev.to/jdshah/what-i-learned-benchmarking-the-same-llm-on-my-phones-npu-gpu-and-cpu-4c80)) [I]. That was LiteRT, not llama.cpp.
- **Small-model tool-calling quality is unproven at 1–2B.** BFCL scores conflict across sources [S]. Qualcomm removed Qwen2.5-1.5B from AI Hub for poor on-device accuracy ([ai-hub-models#289](https://github.com/quic/ai-hub-models/issues/289)) [I]. That applies to their quantized NPU build, but it is a warning.
- **Conclusion:** do not ask a 1B model to choose freely among many tools. Use **one grammar-constrained call schema** (e.g. `{"tool":"update_report","fields":{...},"missing":[...]}`). Let deterministic code decide the next step: ask, confirm or submit. Keep schemas **flat**, since nested arguments get flattened ([GenieX#1478](https://github.com/qualcomm/geniex/issues/1478)) [I].

### 4.6 Does this need an LLM?

Partly.
- **Deterministic code does better for:** date and time ("around 10 AM", "yesterday evening", resolved against the device clock), severity rules (injury, fire or gas leak means high), the mandatory-field check, follow-up question templates, and form state.
- **The LLM is needed for:** free-form slot filling (incident type, persons, equipment, actions taken, description normalisation, follow-up recommendation) from natural speech. That is criterion 2.
- **Best design:** the LLM extracts, rules validate, and every extracted value must be **grounded** in a span of the transcript or it is marked unconfirmed and asked about.

### 4.7 Component classification

| Component | Classification | Evidence |
|---|---|---|
| Whisper encoder on HTP (8 Gen 2) | **Confirmed by an official example** | NLPSolution3 lists V73 |
| Mic capture, VAD, 16 kHz, log-mel | Documented, needs integration | Sample app code; Silero VAD |
| 1B LLM on HTP via llama.cpp (adb binary) | **Plausible, one firsthand success report** | llama.cpp #26123 (Android 15 phone, not QIDK Android 14) |
| LLM inside an APK (JNI) on HTP | High risk, unverified | No report found |
| On-device `llama-server` + app over loopback | Plausible, needs PoC | Standard llama-server features; untested on QIDK |
| Grammar or JSON-schema output on the HTP backend | Plausible, needs PoC | Grammar runs in the CPU sampler (inference) |
| 3B+ LLM on 8 Gen 2 NPU | **High risk, avoid** | Crashes at 4B+ (#26123); maintainer says no (#56) |
| Genie / GenieX / LiteRT on 8 Gen 2 | **Unsupported** | Docs list SM8750+ only |
| Full ASR-LLM-TTS sample on 8 Elite | **Confirmed by an official example** | README device table |
| Missing-field dialogue, review UI, persistence | Standard Android, low risk | — |
| Android offline TTS | Low risk, compliance caveat | System API |
| NPU TTS on 8 Gen 2 | Unsupported | MeloTTS V79+ only |
| Multilingual, photos or vision, backend sync, PDF styling | **Unnecessary for the MVP** | — |

### 4.8 Setup tasks that can eat hours on the day (do them before the event if the rules allow)

| Task | Time | Source |
|---|---|---|
| QAIRT Docker first build | 30+ min, 18 GB disk [I] | quick_start.rst |
| Host OS | Ubuntu 22.04 required. **Your laptop runs Windows 11, so you need WSL2 + Docker** (my inference; Windows is not a listed host). | setup_installation.rst |
| Qualcomm account for `qpm-cli` and Software Center (gated SDKs); Hugging Face token for Llama weights | Account approval can take time | [I] |
| Whisper encoder DLC export (notebook in Docker) | ~1 h EST | NLPSolution3 README |
| llama.cpp Snapdragon build (`ghcr.io/snapdragon-toolchain/arm64-android` image) | ~30–60 min EST | #26123 build line |
| GGUF downloads (~0.8–1.2 GB each) | Depends on venue Wi-Fi | — |
| Genie LLM export (only on 8 Elite, and only if not using a pre-compiled bundle) | 1–2 h, 40–80 GB host [I] | AI-Assistant README |

---

## 5. Existing solutions and competitors

**Bottom line:** no exact duplicate exists. The commercial products found are cloud-based, and the on-device building blocks exist but are separate. The **closest technical precedent is Qualcomm's own QIDK `ASR-LLM-TTS` sample.** Expect other Megathon teams to find it too, so you cannot win on plumbing alone.

| Name | Problem / user | Approach | On-device? | Qualcomm/edge | Implemented? | Implication for you |
|---|---|---|---|---|---|---|
| [QIDK ASR-LLM-TTS](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS) [V] | Generic voice assistant demo | Whisper (Voice AI SDK), Llama 3.2 3B (Genie), MeloTTS, Android | Yes, NPU | Yes, **8 Elite only** | Yes (commit 2026-07-09) | Reuse it if you get an 8 Elite kit. Your added value is the form-filling logic. |
| [HSI Donesafe + Workable voice reporting](https://www.donesafe.com/blog/company/hsi-donesafe-and-workable-partner/) [I] | Frontline EHS incident, hazard and near-miss reports | Phone call to an AI agent that asks follow-ups and confirms before submit | Cloud | No | Announced Aug 2026 | **Closest product match.** It validates the follow-up-and-confirm flow. Your edge is offline, private, NPU. |
| [SafetyCulture AI issue creation](https://help.safetyculture.com/en-US/006682) [I] | Safety issues from voice notes and photos | AI suggests title, category, date; user edits | Cloud (presumed) | No | Early access | No active missing-field dialogue. You can add one. |
| ServiceNow ITSM voice agents [S] | IT incident creation by phone | Whisper-L3 + LLM + TTS over telephony | Cloud | No | Shipped | Speech-to-ticket is already enterprise practice; on-device is the gap. |
| Axon Draft One [S] | Police narrative from bodycam audio | Transcribe + GPT-4 draft, mandatory officer edit | Cloud | No | Shipped | Borrow the **human-review gate** idea (force confirmation of AI-filled fields). |
| [Picovoice Rhino](https://github.com/Picovoice/rhino) / [voice forms](https://picovoice.ai/blog/voice-form-filling-for-web-apps/) [I] | Speech-to-intent; voice form filling | Fixed grammar contexts, no LLM | Yes (CPU/WASM) | No | Shipping SDK | Shows deterministic offline forms work, but **fails criterion 2** (predefined commands). Your LLM handles free speech. |
| [Google AI Edge Function Calling SDK](https://developers.google.com/edge/mediapipe/solutions/genai/function_calling/android) [S] | On-device function calling on Android | LLM Inference API + Hammer 2.1 1.5B | Yes (GPU) | No | Official guide | Design reference for a tool-call parser. The runtime is in maintenance mode. |
| [TinyAgent (Berkeley)](https://github.com/SqueezeAILab/TinyAgent) [I] | Local function-calling assistant on macOS | Fine-tuned 1.1B + Tool RAG, llama.cpp | Yes (CPU/Metal) | No | Research; stale (2024-09) | Evidence that small models can do function calling with a narrow schema. |
| [Nexa Octopus v2](https://huggingface.co/NexaAIDev/Octopus-v2) [I] | On-device Android API calling | Functional-token model | Yes | No | Weights released; **CC-BY-NC** | Licence caveat; reference only. |
| FormBharo (arXiv 2608.06027) [S] | Voice form filling for rural India | LLM for interpretation, rules for flow | Not stated | No | Code link 404 | Prior art for the "LLM extracts, rules drive the flow" split. |
| [Qualcomm GenieX](https://github.com/qualcomm/GenieX) [I] | Snapdragon LLM runtime | Rust; QAIRT + llama.cpp backends | Yes | Yes (8 Elite+ on Android) | Developer preview | Infrastructure, not a competitor. |

**Classification:**
- **Exact duplicate:** none found.
- **Incremental variations:** Donesafe, SafetyCulture, ServiceNow, FormBharo.
- **Different technology, same problem:** Rhino, Google FC SDK, TinyAgent, Axon.

**Building this on-device is not a market novelty claim you can make strongly.** The meaningful contribution is a **measured, grounded, offline slot-filling loop on an 8 Gen 2-class NPU** that commercial tools don't offer.

---

## 6. Reddit and community findings

**Limitation:** **Reddit was fully blocked.** Search tools refused the domain, www.reddit.com returned 403, old.reddit was refused, and the mirrors returned 404. **Zero Reddit threads were inspected**, so I make no Reddit-based claims. Qualcomm's Discord, Slack and developer forum were not reachable. GitHub issues are the community evidence below: about 20 issue bodies were read in full, and 4 of them I verified myself [V].

| Source | Date | Firsthand account | Implication |
|---|---|---|---|
| [llama.cpp#26123](https://github.com/ggml-org/llama.cpp/issues/26123) [V] | 2026-07-25, open | SM8550 (v73): 1B runs well on HTP; 4B and 7B crash with `dspqueue_read 0x2e`; a 7B full offload hits the FastRPC buffer ceiling | **Only 8 Gen 2 NPU LLM evidence. Cap the model at about 1–2B.** |
| [ai-hub-apps#56](https://github.com/quic/ai-hub-apps/issues/56) [V] | 2025-03-10 | Self-exported Llama on 8 Gen 2 (16 GB, Android 13) crashes; maintainer cites the 8 Gen 3 and Android 15 requirement | Genie is not a route on 8 Gen 2 or Android 14. |
| [qidk#76](https://github.com/qualcomm/qidk/issues/76) [V] | 2025-10-10 | IIIT-H team: DSP runtime unavailable on QIDK (Android 14); fixed with the `libcdsprpc.so` manifest line and setenforce | **This exact failure will cost you an hour if you don't know the fix.** |
| [GenieX#1429](https://github.com/qualcomm/geniex/issues/1429) [I] | 2026-09-04, open | V73 device: NPU returns identical output for different prompts, with no error; CPU is fine | **Silent corruption.** Always run a CPU-versus-NPU golden comparison. |
| [llama.cpp#29473](https://github.com/ggml-org/llama.cpp/issues/29473) [I] | 2026-09-26, open | Garbled output on a lower V73 SKU without FP16 HMX | "V73" alone does not guarantee success; check the exact SKU. |
| [llama.cpp#25876](https://github.com/ggml-org/llama.cpp/issues/25876) [I] | 2026-07-18 | Whisper garbled on the llama.cpp HTP path (v81) | Use Qualcomm's Whisper sample, not whisper-on-llama.cpp-HTP. |
| [ai-hub-apps#150](https://github.com/quic/ai-hub-apps/issues/150) [I] | 2026-05-19 | ChatApp ran about 3x slower than `genie-t2t-run`; ChatApp deprecated in favour of GenieX | Don't base work on ChatApp. |
| [ai-hub-models#337](https://github.com/quic/ai-hub-models/issues/337), [#338](https://github.com/quic/ai-hub-models/issues/338) [I] | 2026-07 | Re-exports regress silently; release wheel broken in 0.59.0 | Pin versions and keep a known-good model file. |
| [Radxa forum](https://forum.radxa.com/t/finally-got-llama-3-2-1b-running-on-the-dragon-q6a-npu-armbian-ubuntu-24-04/31144) [I] | 2026-07-07 | 1B on a QCS6490 NPU at about 12–13 tok/s with a fixed 1024-token context; "much trickier" than it looks | Keep prompts short (under 1k tokens). |
| [dev.to NPU/GPU/CPU benchmark](https://dev.to/jdshah/what-i-learned-benchmarking-the-same-llm-on-my-phones-npu-gpu-and-cpu-4c80) [I] | 2026-05-01 | 8 Elite: NPU decodes faster but end-to-end latency was similar to GPU; constrained decoding failed on the NPU in LiteRT | Measure end-to-end latency, not just tok/s. Validate and retry JSON. |

**Recurring themes** (counted as independent firsthand reports):
- **Silent wrong NPU output: 5.**
- **Session or soc_id mismatch errors: 5+.**
- **SDK or export regressions: 4.**
- **Context ceilings: 3.**
- **8 Gen 2 successes at 3B or larger: 0.**
- Tool-calling bugs: isolated (one reporter).
- Demand for voice incident reporting: **no firsthand user evidence retrieved.** Only vendor marketing [S], so don't over-claim user research.

---

## 7. Feasibility and risk scorecard

Scores assume an **8 Gen 2 kit** (worst plausible case). Bracketed scores are for an **8 Elite kit**. No official weights exist, so I give no weighted total. Read the scores together with the hard blockers.

| Dimension | Score | Evidence |
|---|---|---|
| Hardware and SDK compatibility | **3** [5] | ASR sample officially supports V73. Every official LLM path excludes 8 Gen 2. |
| LLM deployment feasibility | **2** [4] | One firsthand 1B success on v73, via adb binaries on Android 15. Larger models crash. |
| End-to-end implementation complexity | **3** [4] | Six stages. The state machine and UI are routine. Runtime bring-up is the hard part. |
| Data availability and reliability | **4** | No external data needed. You must author an evaluation set (§10). |
| Integration and dependency risk | **2** [3] | Gated SDKs, Ubuntu-only host tooling (you are on Windows), 18 GB Docker, silent NPU corruption reports. |
| Reliable live demo | **3** | Hall noise and mic quality, plus first-load latency. Mitigated by a headset mic and warm-loaded models. |
| Differentiation | **2** vs other teams / **4** vs market | Same problem for every team, and Qualcomm's sample exists. No offline commercial product exists. |
| User value and problem clarity | **4** | Clear pain point; spec given. User demand evidence is only from vendors. |
| Fit with judging criteria | **4** if the LLM is on the NPU or GPU / **2** if CPU | Criterion 1 is binary in spirit. |

**Hard blockers (any one changes the plan):**
1. **No LLM runs on the NPU or GPU of the provided kit.** You would have to disclose a CPU LLM and lose points on criterion 1. This is tested in spike T1 and T1b.
2. **No Linux build environment.** Your Windows machine needs WSL2 + Docker, or a teammate's Ubuntu laptop. The Whisper DLC and llama.cpp Snapdragon builds need it.
3. **No adb or root access on the kits.** Both the `setenforce` DSP fix and the loopback server depend on it.

**Major risks:**

| Risk | Likelihood / impact | Evidence | Quick test | Mitigation | Abandon or simplify when |
|---|---|---|---|---|---|
| LLM NPU path fails on this kit | **High / Critical** | Docs exclude 8 Gen 2; one success report | T1 | Try GenieX, then Adreno OpenCL, then CPU (disclosed) | T1, T1b and T1c all fail by minute 75 |
| Silent wrong NPU output | Medium / High | 5 reports | Golden prompts, CPU vs NPU | Pin the build; compare on startup | Outputs diverge semantically |
| 1–2B model extracts fields poorly | **High / High** | Qwen2.5-1.5B pulled by Qualcomm; BFCL scores are low | T3 on the laptop | Hybrid rules, grounding check, one field group per turn, few-shot prompt | Field accuracy below ~70% on the eval set; move more fields to rules |
| DSP runtime unavailable for Whisper | Medium / High | qidk#76 | T2 | Manifest line + `setenforce 0`; whisper.cpp CPU fallback (disclosed) | Still failing after 45 min |
| Whisper hallucinates on silence or noise | Medium / Medium | Generic Whisper behaviour | Record silence and hall noise | VAD/energy gate; push-to-talk; headset mic | — |
| Unknown RAM (4–16 GB) | Low–Med / Med | Unknown | `/proc/meminfo` | 1B Q4_0 (~0.8 GB) plus Whisper tiny fits in 4 GB | — |
| Latency feels slow | Medium / Medium | EST: ~0.5 s prefill + ~4 s for 100 output tokens at 23.8 tok/s | T1 timing | Stream fields; keep JSON short; precompute the system prompt KV cache | End-to-end over 15 s |
| Env setup eats the morning | High / High | Docker 30+ min, gated logins | Do it before the event | Pre-built artifacts on USB | — |

**Go/no-go gates on the day:**
- **Minute 90:** an LLM on the NPU or GPU produces correct JSON, *and* the Whisper sample transcribes on the kit. If so, full GO on Option B.
- **Hour 10:** an ugly end-to-end flow (voice to filled form) works on the device. If not, freeze scope to Option A.

---

## 8. Recommended project direction

| | **A. Smallest safe scope** | **B. Grounded slot-filling assistant (recommended)** | **C. Fork Qualcomm's ASR-LLM-TTS (8 Elite kits only)** |
|---|---|---|---|
| User problem | Speak, get a form | Speak naturally, the app *asks* for what's missing, every field is traceable, submit in under a minute | Same as B |
| Key capability | Whisper, LLM to JSON, form, TTS | Hybrid extraction (LLM plus rules), evidence-span grounding, voice follow-ups, voice corrections ("change the location to Conference Room 3"), review gate, on-screen performance and accuracy panel | Full NPU pipeline including NPU TTS, with B's logic layered on |
| Technical novelty | Low | Medium: grounding and validation for a small model on an 8 Gen 2-class NPU, plus measured accuracy | Low on plumbing (Qualcomm wrote it); B's logic is the novelty |
| Architecture | See B, minus the dialogue loop | See below | Genie + Voice AI SDKs (QAIRT 2.45) |
| QIDK functionality | HTP (Whisper encoder), HTP (LLM) | Same | HTP for all three models |
| Effort | ~10–12 person-hours | ~30–40 person-hours | ~25–35 person-hours, plus `qpm` login |
| Main risks | LLM runtime | LLM runtime; extraction quality | Requires 8 Elite + Android 15; SDK login gating |
| Demo | Linear, one shot | Conversational, self-correcting, measurable | Polished; looks like the Qualcomm sample unless B is added |

**Ranking:** **B > C > A.**
- B is the only option that targets **all five evaluation criteria** directly. Criteria 3–5 are about accuracy, and B is the only option that *measures* accuracy. It also stays feasible on the weakest kit.
- C is B's plumbing shortcut if you get an 8 Elite kit.
- A is the scope-freeze fallback.

### Option B architecture (8 Gen 2 or Gen 3 branch)

```
[Mic + push-to-talk] → Silero VAD/energy gate → 16 kHz PCM
   → Whisper (QIDK NLPSolution3: encoder on HTP, decoder on CPU) → transcript
   → Report Orchestrator (Kotlin state machine, deterministic)
        ├─ Rules: date/time resolver (device clock), severity rules, location normaliser
        ├─ LLM call → on-device llama-server (127.0.0.1, --device HTP0, Llama-3.2-1B or Qwen3-1.7B Q4_0)
        │     json_schema-constrained single tool: update_report{fields{...}, evidence{field:span}}
        ├─ Grounding validator: drop any value whose evidence span is not in the transcript
        ├─ Mandatory check → ask_user(field) → TTS question → next turn (merge, don't overwrite)
        └─ submit_report() → local Room/SQLite + JSON/PDF export (simulated "send to maintenance")
   → Review screen (highlighted evidence, editable fields, confidence) → Android TTS summary
   → Metrics overlay: ASR ms, LLM prefill/decode tok/s, backend=HTP, end-to-end s
```

**Reuse versus new work:**
- **Reused:** the QIDK Whisper app (audio capture, log-mel, DLC runtime wiring), llama.cpp Snapdragon build and server, and Android TTS.
- **New:** the orchestrator, schema and prompts, grounding validator, follow-up dialogue, review UI, evaluation harness and metrics overlay. These are the parts judges score.

**Remove or defer:** multilingual support, photos and vision, cloud sync, user accounts, PDF styling, wake word, streaming ASR, fine-tuning, models of 3B or more on 8 Gen 2, and NPU TTS on 8 Gen 2.

---

## 9. Twenty-four-hour execution plan

**Team split** (assumes 4 people; fold roles together if fewer). Each person owns separate directories to avoid conflicts.

| Owner | Area | Directories they own |
|---|---|---|
| **P1 Runtime** | LLM on the device, server launch script, performance logging | `device/`, `scripts/` |
| **P2 Speech** | Whisper sample, VAD, transcript API | `app/.../asr/` |
| **P3 App** | Orchestrator, UI, TTS, persistence | `app/.../ui`, `.../report` |
| **P4 Brain + eval** | Prompts, schema, rules, 30-utterance eval set, accuracy script. Works on the laptop with the *same GGUF file*. | `prompts/`, `eval/` |

**Interface contracts, frozen at H2:**
- `transcribe(audio) → {text, ms}`
- `POST /v1/chat/completions` with `json_schema` → `{fields, evidence, missing}`
- `Report` data class covering the 10 fields

| Hours | Milestone | Acceptance criteria |
|---|---|---|
| 0–1.5 | **Spike** (§10) | Branch decided. LLM and ASR proven on the kit or fallbacks chosen. |
| 1.5–3 | Skeleton + contracts | App installs; stub transcript to stub JSON to form renders. |
| 3–9 | Parallel build | P2: real transcript in the app. P1: server stable, golden test passes. P4: prompt reaches ≥70% field accuracy on laptop eval. P3: orchestrator + review UI on stubs. |
| **9–10** | **Integration checkpoint #1** | Speak the slide example on the kit; the form fills with correct Type, Location, Time and Action. **If not, freeze to Option A.** |
| 10–14 | MVP completion | Missing-field follow-up (≥1 turn), voice correction, submit + local persistence, TTS readback. |
| 14–16 | Rest in shifts / buffer | — |
| 16–19 | Hardening + measurement | Run the eval set *on the device*. Metrics overlay. Silence and noise handling. Cold-start warm-up. |
| 19–20 | **Feature freeze** | Only bug fixes after this point. |
| 20–22.5 | Demo prep | Rehearse 3 times. Record a backup video. Script the "judge throws a curveball" utterance. |
| 22.5–24 | Buffer | — |

**Tiers:**
- **MVP (must):** on-device ASR, LLM to validated JSON, mandatory-field follow-up, review and submit, on-screen display.
- **After the MVP:** voice corrections, evidence highlighting, metrics overlay, on-device eval report, TTS.
- **Don't build:** anything in the "Remove or defer" list in §8.

**Fallback ladder:** each step is honest and keeps ASR on the NPU.
1. llama.cpp on HTP.
2. GenieX llama.cpp runtime on NPU or GPU.
3. llama.cpp on Adreno OpenCL.
4. llama.cpp on CPU, **disclosed**, with every non-LLM model still on the NPU and the NPU attempt presented as evidence.

**Never use a cloud API.** It is an explicit disqualifier.

---

## 10. First 60–90 minute proof-of-concept checklist

| # | Who | Test | Pass criteria | If it fails |
|---|---|---|---|---|
| T0 (10 min) | All | `adb devices`; `adb shell getprop ro.soc.model ro.board.platform ro.build.version.release ro.build.type`; `adb shell grep MemTotal /proc/meminfo`; `adb root && adb shell setenforce 0` | Know SoC, Android version and RAM; root works | No adb or root: ask organisers immediately (hard blocker 3) |
| T0b | P1 | **If SoC = SM8750 or SM8850 on Android 15+**, switch to Option C: install the prebuilt `ASR-LLM-TTS` flow | Sample runs | Continue with T1 |
| T1 (30 min) | P1 | Push the pre-built llama.cpp Snapdragon binaries + `Llama-3.2-1B-Instruct-Q4_0.gguf`; run with `--device HTP0 -ngl 99` and the slide example prompt | Log shows HTP backend; coherent output; ≥10 tok/s; output matches a CPU run semantically on 5 golden prompts | Go to T1b |
| T1b (20 min) | P1 | GenieX Android SDK (Maven) with llama.cpp runtime, compute unit NPU then GPU; or llama.cpp `-dev GPUOpenCL` | Same as T1 | Go to T1c |
| T1c (10 min) | P1 | Same with Qwen3-1.7B Q4_0 on HTP (only if 1B passes; checks headroom) | No crash; better extraction | Stay on 1B |
| T2 (45 min) | P2 | Install the NLPSolution3 Whisper APK; speak the slide example | Correct transcript; DSP runtime active (logcat) | Add `libcdsprpc.so` manifest line + setenforce. Still failing → whisper.cpp CPU, disclosed. |
| T3 (45 min) | P4 | On the laptop with llama.cpp CPU, same GGUFs, `json_schema` → 10 hand-labelled utterances | ≥70% field accuracy on Type/Location/Time/Action; valid JSON 10/10 | Shrink the LLM's job: rules handle time and severity, the LLM fills fewer fields |
| T4 (15 min) | P1 | `llama-server` on the device at 127.0.0.1:8080; `adb shell curl` with `json_schema` | Valid JSON from the device server, grammar honoured with HTP active | Parse and retry JSON; or fall back to CPU sampling |

**Decision at minute 90:**
- T1 (or T1b) passes and T2 passes: **GO, Option B.**
- T1 fails everywhere: proceed with Option B on a CPU LLM, disclose it, and put the NPU story on Whisper.

---

## 11. Demo strategy

**One sentence:** "Speak an incident in plain English; a Snapdragon NPU turns it into a validated, submission-ready report and asks for anything missing, with no network at all."

**Why existing tools fall short:**
- SafetyCulture, Donesafe and ServiceNow send voice to the cloud. Commercial tools are therefore unusable in basements, plants, hospitals or secure sites.
- Fixed-grammar voice forms (Rhino) can't handle natural speech.
- Qualcomm's sample is a chat demo, not a validated form workflow.

**Demo sequence (about 3 minutes):**
1. Show airplane mode **on** and Wi-Fi off. Say this aloud.
2. Speak the slide example. The transcript appears, then the fields fill, each with its evidence words highlighted.
3. The app says aloud: "What's the severity, and was anyone injured?" Answer: "Minor, nobody hurt." The fields merge.
4. Correct a field by voice: "Actually it was Conference Room 3."
5. Review screen, then Submit. The `submit_report` tool call and JSON are shown, and the summary is read aloud.
6. Metrics panel: ASR ms, LLM backend **HTP**, prefill and decode tok/s, end-to-end seconds. Use only numbers you measured on the day.
7. Accuracy slide: the on-device eval over 30 scripted utterances, with field-level accuracy. Report your real number, whatever it is.
8. Curveball: a judge speaks their own incident.

**Claims to substantiate on the day:**
- **Offline:** airplane mode.
- **NPU:** backend log or toggle, and CPU-versus-HTP latency side by side.
- **Accuracy:** the eval table.
- **Privacy:** no INTERNET permission in the manifest. Only possible if loopback works without it; otherwise explain that the app calls only 127.0.0.1.

**Likely judge questions:**
- *Is the LLM really on the NPU?* Show the `HTP0` backend log and the CPU comparison.
- *Why such a small model?* Cite the evidence: 4B+ crashes on v73 (llama.cpp#26123), and Qualcomm's hosted LLMs need 8 Gen 3 or newer. The rules and grounding make up for the small model.
- *What stops hallucinated fields?* Evidence-span grounding, schema-constrained output, and the review gate.
- *How is this function calling?* The LLM emits schema-constrained tool calls; the orchestrator executes `ask_user` and `submit_report`.
- *Is TTS on the NPU?* No. It uses the system offline engine; there is no V73 NPU TTS. Be upfront.
- *What about noise and accents?* Whisper-tiny limits; headset mic; push-to-talk.

**Prepare in advance:**
- Headset or USB mic.
- Power strip; the kit has no battery.
- Models pre-loaded and warmed up.
- Three rehearsed scripts.
- Backup video.
- A printed field list.

---

## 12. Final action list

**Must-do, in priority order:**
1. **Ask the organisers now:**
   - Exact QIDK SoC, Android version and RAM.
   - Whether `adb root` is allowed.
   - Whether tooling and models may be prepared before the event.
   - Whether venue internet is available for setup.
   - Whether the Android system TTS is acceptable under the "all AI models" rule.
2. **Set up a Linux build environment:** WSL2 Ubuntu 22.04 + Docker on your Windows laptop, or an Ubuntu teammate. Build the QAIRT Docker image (18 GB, 30+ min).
3. **Create accounts:** a Qualcomm account (Software Center / `qpm-cli`) and a Hugging Face token with the Llama 3.2 licence accepted.
4. **Pre-build:**
   - llama.cpp Snapdragon Android binaries (toolchain image `ghcr.io/snapdragon-toolchain/arm64-android`).
   - Download `Llama-3.2-1B-Instruct-Q4_0` and `Qwen3-1.7B-Q4_0` GGUFs.
5. **Pre-build the NLPSolution3 Whisper** encoder DLC and APK. Note the `libcdsprpc.so` manifest fix.
6. **Write the 30-utterance eval set** with gold JSON: varied phrasing, missing fields, relative times, corrections. Write the field schema and the mandatory-field definition.
7. Draft the extraction prompt and JSON schema, and test them on the laptop CPU with the same GGUF files.
8. Put everything on a USB drive in case the venue Wi-Fi is slow.

**Optional improvements:**
- Test the GenieX Maven SDK on any Snapdragon phone you own.
- Prepare a branch for the 8 Elite `ASR-LLM-TTS` path in case you get those kits.
- Silero VAD integration.
- PDF export.
- A severity-rule table reviewed by someone with safety or EHS experience.

---

## 13. Addendum: last year's Megathon winner on the QIDK (added after the user shared the repo)

**Repo:** [saitejaMaryala/Megathon-2k25 ("SnapGen")](https://github.com/saitejaMaryala/Megathon-2k25) [V]. Last pushed 2025-10-13.

**What it is:**
- A rebrand of [xororz/local-dream](https://github.com/xororz/local-dream) (3.6k stars, still maintained as of 2026-10-07), licensed **CC BY-NC 4.0**.
- Runs Stable Diffusion on the Hexagon NPU through QNN (QAIRT 2.39).
- The team added: Android `SpeechRecognizer` voice prompts, a camera screen, and UI polish.
- All app commits fall between 2025-10-11 20:07 and 2025-10-12 08:28, roughly 12 hours.
- Last year's problem was image generation, not incident reporting. **No code is directly reusable for extraction**, but four things carry over:

1. **Native NPU server launched from inside the APK, no root needed [V].**
   - `BackendService.kt` runs a native executable from `nativeLibraryDir` with `ProcessBuilder`.
   - It copies QNN libraries and **unsigned** Hexagon skel libraries for v68–v81 from assets into a private folder, and sets `LD_LIBRARY_PATH` and `DSP_LIBRARY_PATH` to it.
   - The app then calls `127.0.0.1:8081` over HTTP.

   This **settles the open question in §4.2**: the loopback-server design does not need adb or root. Hard blocker 3 in §7 shrinks to "`setenforce` may still be needed for the DSP fix". Replacing the SD engine with `llama-server` built with the Hexagon backend is an inference and still needs spike test T4.
2. **SoC to model mapping [V].** `Model.kt` reads `Build.SOC_MODEL` and maps SM8550/SM8550P to 8 Gen 2, SM8650 to 8 Gen 3, and SM8750 to 8 Elite. Copy this to choose per-chip model files and to show the chip in the demo.
3. **Winning strategy.** Fork something that already works on the NPU, then spend the hackathon on the features judges score. No mature open-source LLM-on-Hexagon Android app exists to fork this year. A [search](https://github.com/topics/android-llm) found LiteRT apps and the Termux-based [droid-harness](https://github.com/eibragaa/droid-harness); llama.cpp's [Snapdragon backend](https://huggingface.co/datasets/echodict/llama.cpp/blob/main/docs/backend/snapdragon/README.md) is still marked experimental. So reuse the **pattern** plus the qidk Whisper sample.
4. **Don't copy their voice input [V].** It uses `SpeechRecognizer.createSpeechRecognizer()` (Google's recogniser, which can go to the network), and the manifest has the `INTERNET` permission. That breaks this year's "strictly no cloud APIs" rule. ASR must be our own Whisper on the HTP.

**Effect on the verdict:** still **CONDITIONAL GO** (the LLM-on-v73 question is unchanged). Integration risk improves from 2 to **3**. Last year's team proved a third-party native QNN executable runs on the HTP from a normal APK on a Megathon QIDK.

**Saved as a project skill:** `.claude/skills/qidk-npu-android/SKILL.md`, with code pointers and attribution.

---

## 14. Addendum: internet-wide prior-art sweep (4 parallel streams, ~740 sources)

Full catalog: `.claude/skills/incident-report-prior-art/SKILL.md`. Key results:

**1. Hardware evidence now points to newer kits at IIIT-H [V].**
- An IIIT-H author's [sm8750-edge-ai-lab](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab) is an "SM8750 Qualcomm QIDK" on Android 15. Its verified results include Gemma3-1B on the NPU (LiteRT-LM, hybrid) and Whisper-Tiny on the NPU (WhisperKit, hybrid).
- The same author has an [HTP8650 QIDK repo](https://github.com/AryamaVMurthy/DenseNet_Evals_on_QIDK_HTP8650).
- qidk#76 reports SM8650.

The worst case in §1, 8 Gen 2, now looks less likely for Megathon kits. Still confirm on the day. If the kit is SM8750, Option C (fork the official ASR-LLM-TTS sample, or use LiteRT-LM via that runbook) becomes the low-risk path.

**2. No complete implementation exists anywhere.** The closest repos are cloud-API demos, but their logic carries over:
- the submit gate: [vic4code/speech-form-filling-demo](https://github.com/vic4code/speech-form-filling-demo)
- an incident schema and testable LLM seam: [konstro-build/incident-report-ai](https://github.com/konstro-build/incident-report-ai)
- "Not captured" plus open_questions: [ajinb/incident-scribe](https://github.com/ajinb/incident-scribe)
- typed severity enums: [Seveyus/OpsTune](https://github.com/Seveyus/OpsTune)
- the on-device voice pipeline state machine and JSON parser: [aryamirani/vox](https://github.com/aryamirani/vox), whose SNPE runtime is a stub

**3. New extractor candidate:** [NuExtract-2.0-2B GGUF](https://huggingface.co/numind/NuExtract-2.0-2B-GGUF) (MIT), purpose-built for template-to-JSON extraction. Add it to spike test T3 alongside Llama-3.2-1B and Qwen3-1.7B.

**4. Evaluation data:** OSHA severe-injury narratives ([spectra-osha-sir](https://huggingface.co/datasets/Simar123456/spectra-osha-sir), 71k rows with event, source and outcome codes) can be rewritten as spoken reports to build the gold set quickly. Add hand-written near-miss and facilities cases.

**5. Design rules from 2024–26 papers:**
- Score per-field value accuracy, not JSON validity.
- Make the missing-field loop deterministic, because LLMs stop asking too early.
- Expect value hallucination in 2–4B models, and put critical fields first.

---

*Unverified items flagged for the spike:*
- QIDK RAM.
- Whether the provided kit is SM8550 or SM8650.
- llama.cpp Hexagon on Android 14, and inside an app context.
- Grammar-constrained decoding with the HTP backend.
- Adreno 740 OpenCL.
- Any 8 Gen 2 latency figure other than llama.cpp#26123.

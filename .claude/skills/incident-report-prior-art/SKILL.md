---
name: incident-report-prior-art
description: Use when designing or implementing the Megathon 2026 AI Incident Report Assistant (voice → on-device STT → LLM field extraction → missing-field follow-up → review/submit → TTS on a Qualcomm QIDK) and you need existing repos, reusable code files, prior QIDK projects, an incident JSON schema, evaluation datasets, or design rules from products and papers. Catalog from the Oct 2026 web/GitHub sweep. Pair with the qidk-npu-android skill for runtime and packaging details.
---

# AI Incident Report Assistant — prior art and reuse map (sweep of 2026-10-10)

Tags: **[V]** verified by reading the repo or file, **[I]** opened by a research agent, **[S]** snippet or metadata only. Licences matter: "none" means all rights reserved, so copy *ideas*, not code. CC BY-NC and AGPL need care.

## 0. Headline findings
1. **No repo does the full loop** (voice → incident fields → missing-field follow-up → review → TTS), on-device or off. Exact duplication: none found. The parts exist separately.
2. **IIIT-H already has newer QIDKs than the official SM8550P spec.** One IIIT-H author has an `SM8750` (8 Elite) QIDK on Android 15 [V] ([sm8750-edge-ai-lab](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab)) and a QIDK on HTP 8650 [V] ([DenseNet_Evals_on_QIDK_HTP8650](https://github.com/AryamaVMurthy/DenseNet_Evals_on_QIDK_HTP8650)). qidk#76 reports SM8650. **The Megathon kit is probably 8 Gen 3 or 8 Elite. Confirm with `getprop ro.soc.model`.** On 8 Elite, Qualcomm's NPU LLM stacks (Genie/GenieX/LiteRT-LM) are officially supported.
3. **Most incident-report repos are cloud-API toys.** Their value is in schemas, prompts and gating logic, which carry over to llama.cpp and GBNF.

## 1. QIDK / Snapdragon projects to reuse from
| Repo | Kit / runtime | What's reusable (exact paths) | Licence / quality |
|---|---|---|---|
| [AryamaVMurthy/sm8750-edge-ai-lab](https://github.com/AryamaVMurthy/sm8750-edge-ai-lab) [V] | **SM8750 QIDK, Android 15**, LiteRT-LM + WhisperKit, QAIRT 2.4x | Verified matrix: **Gemma3-1B-IT NPU = PASS (hybrid)**, gemma-3-270m NPU PASS, **Whisper-Tiny STT NPU PASS (hybrid, WhisperKit)**; Gemma GPU FAIL. Runbook `docs/runbooks/sm8750_verified_runs.md`. NPU env gates: `LITERT_LM_NPU_AUX_BACKEND=cpu`, `LITERT_LM_NPU_EMBEDDER_BACKEND=cpu`, `LITERT_LM_DISABLE_PROMPT_EMBEDDING_LOOKUP_MANAGER=1`, `ADSP_LIBRARY_PATH=...;.../hexagon-v79;/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;...`. Model `Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm` (1280-token KV cache). Android code under `android-app/app/src/main/java/com/qidk/fastvlm/core/`: `speech/PcmVoiceRecorder.kt`, `speech/WhisperKitSttEngine.kt`, `speech/AndroidTtsSpeaker.kt`, `speech/StreamingTtsCoordinator.kt`, `device/DeviceCompatibilityChecker.kt`, `metrics/MetricsStore.kt`; `android-app/whisperlib/` (whisper.cpp JNI, CPU fallback). | No licence; 1 star. Strongest evidence for an SM8750 kit. Gemma-1B exact-output prompts reported "inconsistent". |
| [saitejaMaryala/Megathon-2k25](https://github.com/saitejaMaryala/Megathon-2k25) [V] | Megathon 2025 winner, local-dream fork, QNN HTP | Native-server-from-APK pattern. See the qidk-npu-android skill §2. | CC BY-NC 4.0 |
| [aryamirani/vox](https://github.com/aryamirani/vox) [V tree] | "QIDK", SNPE; Whisper-Tiny → Phi-3-mini → JSON over MQTT | `llm/PromptBuilder.kt` (schema in system prompt), `llm/MqttCommandParser.kt` (strips markdown fences, strict JSON parse), `pipeline/VoicePipeline.kt` (IDLE → LISTENING → TRANSCRIBING → INTERPRETING → PUBLISHING → ERROR state machine), `audio/AudioRecorder.kt` | MIT. **SNPE runtime is a stub**, and models/SDK are absent, so it won't run as-is. Copy the structure. |
| [qualcomm/qidk GenAI-Solutions/ASR-LLM-TTS](https://github.com/qualcomm/qidk/tree/master/GenAI-Solutions/ASR-LLM-TTS) [V] | 8 Elite / Elite Gen5, Genie + Voice AI SDK | `sample_app/.../asr/Whisper_ASR.java`, `asr/utils/SimpleAudioRecord.java`, `llm_assistant/Assistant.java`, `cpp/native-lib.cpp`, `assets/htp-model-config-llama32-3b-gqa.json` | Official. Needs a `qpm-cli` login. **First choice if the kit is SM8750.** |
| [qualcomm/qidk Solutions/NLPSolution3 Whisper](https://github.com/qualcomm/qidk/tree/master/Solutions/NLPSolution3-AutomaticSpeechRecognition-Whisper) [V] | V73–V81, encoder on HTP | Whisper on the HTP for any kit generation | Official |
| [qualcomm/GenieX](https://github.com/qualcomm/GenieX) [I] | 8 Elite+ Android SDK (`com.qualcomm.qti:geniex-android`) | `bindings/android/app/src/main/java/com/geniex/sdk/LlmWrapper.kt` (Flow token streaming), `cpp/llm_bridge_jni.cpp`; tool-call parsing only in the Go server (`cli/server/utils/toolcall_qwen3.go`) | BSD-3, developer preview |
| [akshit0942b/QIDK-ATOM](https://github.com/akshit0942b/QIDK-ATOM) / Kausheya2006/AI_TUTOR2 [I] | QIDK, QAIRT/Genie, Llama 3 | Genie JNI in `ChatApp/src/main/cpp` (offline PDF tutor) | Student project |
| qidk `community_solutions.rst` [I] | — | One entry: IIIT-H "ESW M2025 QIDK", https://pl-git.iiit.ac.in/spcrc/iiith-esw-m2025-qidk (unopened; may need IIIT login) | — |

## 2. Closest incident and voice-form repos (ideas to steal)
| Rank | Repo | Stack | Steal this |
|---|---|---|---|
| 1 | [vic4code/speech-form-filling-demo](https://github.com/vic4code/speech-form-filling-demo) [V exists, I code] | OpenAI Realtime, Pydantic → `submit_form` tool | `app/forms/base.py` `tool_schema()` (model → tool JSON) and `merge_profile_defaults()` (AI values win, defaults fill blanks); `app/forms/taxi/instructions.md`: **"ask each missing field; never submit incomplete"** submit gate. No licence. |
| 2 | [konstro-build/incident-report-ai](https://github.com/konstro-build/incident-report-ai) [V exists, I code] | Gemini responseSchema | `lib/extract.mjs` `incidentSchema` (type, summary, timeline, units, actions, lessons, follow-ups) and an **injected `llm(prompt, schema)` seam with no-network fixture tests**. No licence. |
| 3 | [ajinb/incident-scribe](https://github.com/ajinb/incident-scribe) [V exists, I code] | Claude + pydantic validate/retry | `incident_scribe/prompts.py`: write **"Not captured" instead of guessing** + an `open_questions` list, which feeds the follow-up loop directly. Apache-2.0. |
| 4 | [Seveyus/OpsTune](https://github.com/Seveyus/OpsTune) [V exists, I code] | Local Qwen2.5-3B LoRA via an OpenAI-compatible server | `agent_workflow/schemas.py`: `Literal` enums for severity/category/urgency + facts (equipment, symptoms, operational_impact). No licence. |
| 5 | [kylecampbell/livekit-voice-agent-form-filler](https://github.com/kylecampbell/livekit-voice-agent-form-filler) [I] | Deepgram/OpenAI/LiveKit | `agent/agent.py`: one `@function_tool` per field + push state to the UI on each fill (live form-filling effect). MIT. |
| 6 | [harishkotra/offline-medical-scribe](https://github.com/harishkotra/offline-medical-scribe) [I] | sherpa-onnx STT + Llama-3.2-1B in browser | `src/lib/prompts.ts`: JSON-only rules with a "Not provided" fallback for a 1B model, fully offline. |
| 7 | [hutchpd/AI-Medical-Scribe](https://github.com/hutchpd/AI-Medical-Scribe) [S] | Gemini Nano in Chrome | Concept: **review mode with confidence highlighting** + an append-only audit log. MIT. |
| — | Also seen: RCH-ronaldo18/ChatBot_TA2 (`data/funciones.json`, enum tool params, "omit inapplicable params"), BirgerMoell/open-medical-scribe (pluggable STT/LLM), nhsx/ELM4PSIR [S] | | |

Commercial analogues (all cloud): HSI Donesafe + Workable voice reporting (follow-ups, confirm before submit), SafetyCulture AI issue creation, ServiceNow voice agents, Axon Draft One (**placeholders for missing info instead of invented text; mandatory human sign-off**), MaintainX voice memos. **None found offers on-device AI.** That is the differentiator.

## 3. On-device building blocks
- **NuExtract-2.0-2B GGUF** (MIT; [HF](https://huggingface.co/numind/NuExtract-2.0-2B-GGUF), files Q2_K…BF16, Q4_K_M ≈ 1 GB) [V]. Extraction-specialised. Prompt: no system prompt; user message = `# Template:\n<pretty JSON template>\n<text>`; types `verbatim-string`, `string`, `integer`, `date-time`, enum arrays; returns null for missing fields; temperature 0 [I]. **Compare it against Llama-3.2-1B / Qwen3-1.7B / Gemma3-1B on the eval set.** Caveats: Qwen2-VL base, 2B. 4B+ crashes on the v73 HTP, and 2B is untested there.
- llama.cpp `json_schema` → GBNF (server `response_format`), `examples/llama.android` (Kotlin/JNI; grammar exposure unverified). [mybigday/llama.rn](https://github.com/mybigday/llama.rn) documents HTP configuration (SM8450+, needs `libcdsprpc.so`) [I].
- [google-ai-edge/LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM): Kotlin API with tool use / constrained decoding. The NPU path is verified on the SM8750 QIDK (see §1). [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) shows function-calling UX (FunctionGemma 270M).
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx): Silero VAD + Piper TTS on Android (CPU). whisper.cpp Android example (CPU fallback).
- Full offline voice-loop reference: [zst50/voice-chatbot-android](https://huggingface.co/zst50/voice-chatbot-android) [I]: whisper.cpp tiny.en + Silero VAD + Piper + LiteRT-LM Qwen3-0.6B, with barge-in. No structured output.
- Weak or stale: GLiNER (no Android path), Natty date parser (stale), Hammer (no 1B), kotlinllamacpp/kmp-ai (unverified).

## 4. Recommended incident JSON schema (standards-mapped)
Sources: Cal/OSHA 301 / OSHA 301 items (date/time, persons, description); OIICS event groups; AHRQ harm scale and FHIR AdverseEvent.severity; ISO 45001 §10.2 (follow-up). Facility-type values are added because the slide example is a water leak.
```json
{"type":"object","required":["incident_type","location","date_time","description"],
 "properties":{
  "incident_type":{"enum":["injury","illness","near_miss","property_damage","water_leak_flood","fire_smoke","electrical","spill_chemical","equipment_failure","security","environmental","other"]},
  "severity":{"enum":["none","minor","moderate","severe","critical"]},
  "location":{"type":"string"}, "date_time":{"type":"string"}, "time_is_approximate":{"type":"boolean"},
  "persons_involved":{"type":"array","items":{"type":"string"}},
  "equipment_asset":{"type":"string"}, "description":{"type":"string"},
  "immediate_actions":{"type":"string"}, "witnesses":{"type":"array","items":{"type":"string"}},
  "recommended_follow_up":{"type":"string"},
  "evidence":{"type":"object","additionalProperties":{"type":"string"}},
  "missing":{"type":"array","items":{"type":"string"}}}}
```
Keep it **flat** for small models; nested arguments get flattened (GenieX#1478). `evidence` holds the transcript span backing each field, which enables the grounding check. Whether severity is mandatory is a product choice (ask for it if not inferable).

## 5. Evaluation data
| Source | Use |
|---|---|
| HF [Simar123456/spectra-osha-sir](https://huggingface.co/datasets/Simar123456/spectra-osha-sir) [I]: 71k OSHA severe-injury narratives, `narrative_masked`, OIICS event/source, outcome flags (hospitalized, amputation); licence tags conflict (public-domain text vs "other") | Rewrite ~40 narratives as spoken supervisor reports. Silver labels for type/severity/equipment. |
| HF [mayerantoine/injury-narrative-coding](https://huggingface.co/datasets/mayerantoine/injury-narrative-coding) [I]: 230k, Apache-2.0, OIICS event codes | Few-shot examples, event-type mapping |
| MSHA accident narratives, NTSB [S] | Extra narratives |
| SNIPS (CC0) / ATIS [I/S] | Generic slot-filling sanity check |
| **Hand-written** | Add 8–10 near-miss and facilities cases (water leak, spill, electrical), since OSHA data has none |

**Eval protocol:** 30–50 utterances, half with a mandatory field removed. Run each as text and as read-aloud audio (report WER separately). Score per field: exact/normalised match, enum F1, **hallucination rate** (non-null value the utterance doesn't support), and **follow-up precision** (asks only for genuinely missing mandatory fields).

## 6. Design rules from papers and products
- **A valid schema doesn't mean correct values.** One structured-output benchmark (arXiv 2604.25359 [S]) found JSON valid 99.97% of the time but fully correct responses only 0.486. Measure value accuracy per field.
- Small models (2–4B) hallucinate values, and accuracy drops on fields later in the schema (VAREX, arXiv 2603.15118 [S]). Put critical fields first and score per field.
- LLMs **stop asking clarifying questions too early** (ClarifyMT-Bench, arXiv 2512.21120 [S]). Make the missing-field loop **deterministic code**, not LLM discretion.
- ASR noise degrades LLM slot filling (arXiv 2311.07418 [I]). Show the transcript, allow voice correction, and test on noisy audio.
- From products: placeholders for missing information instead of invented text, human sign-off before submit (Axon); derive fields such as priority from impact and urgency (ServiceNow); offline capture with a sync queue (Donesafe, Intelex).

## 7. Integration plan by kit (decide after `getprop ro.soc.model`)
- **SM8750 / SM8850 (8 Elite):**
  - **LLM:** fork qidk ASR-LLM-TTS (Genie Llama-3.2-3B), or LiteRT-LM Gemma3-1B on the NPU using sm8750-edge-ai-lab's runbook and env gates.
  - **STT:** Whisper via the Voice AI SDK or WhisperKit.
  - **TTS:** MeloTTS on the NPU.
  - **Our layer:** §4 schema + grounding + deterministic follow-up + eval.
- **SM8650 (8 Gen 3):** llama.cpp Hexagon (an official target) launched local-dream-style (qidk-npu-android §2); Whisper from NLPSolution3; Android TTS. Genie only if Android 15.
- **SM8550 (8 Gen 2):** same as 8 Gen 3 but cap the LLM at about 1B (4B+ crashes on v73). Test NuExtract-2B carefully.
- **All kits:** vox's `VoicePipeline` states for the UI, vic4code's submit gate, incident-scribe's "Not captured" + open_questions, konstro's injected-LLM test seam.

## 8. Coverage limits of the sweep
- GitHub search hit secondary rate limits, so some queries (voice work orders, voice ticket creation) returned nothing.
- Reddit was blocked.
- The IIIT GitLab community project, Qualcomm hackathon winner pages, and the official OSHA 301 PDF (404) were not inspected.
- No Megathon 2024/2025 voice + LLM project was found. The Megathon 2026 problem is not public online beyond the deck.

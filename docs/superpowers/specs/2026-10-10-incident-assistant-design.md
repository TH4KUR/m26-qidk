# AI Incident Report Assistant: Design (QIDK HDK8750 / SM8750P)

Date: 2026-10-10 · Status: proposed, awaiting team review.

**Inputs:**
- Qualcomm problem-statement deck
- `exa-results/qidk-incident-assistant-feasibility-2026-10-10.md`
- the project skills `qidk-npu-android` and `incident-report-prior-art`
- the HDK8750 quick-start guide (80-72574-1 Rev AA, confidential; kept local)

## 1. Hardware facts (from the kit's guide)

- **SoC:** HDK8750 main board with **SM8750P = Snapdragon 8 Elite, Hexagon v79**.
- **Power:** 12 V DC adaptor; the battery is optional.
- **Audio:** onboard **MIC4/5**; **3.5 mm headset jack**, which is the default headset detection (DIP S6101-4 OFF); 2 speakers included.
- **HDMI out:** set DIP **S5702-7 and S5702-8 ON before power-up**. The default channel is the display card.
- **Not stated in the guide:** RAM and Android version. Check both on the day (`/proc/meminfo`, `ro.build.version.release`). The IIIT-H SM8750 QIDK in prior art ran Android 15.

Consequence: Qualcomm's officially supported 8 Elite LLM stack (GenieX, Genie, LiteRT-LM NPU) applies. The 8 Gen 2 risks in the feasibility report no longer apply.

## 2. Requirements (from the deck)

- Pipeline: Speech-to-Text → LLM information extraction → mandatory-fields check (ask if missing) → function/tool calling → populate form, review, submit → display on screen + TTS.
- Fields: Incident Type, Severity, Location, Date & Time, Persons Involved, Equipment/Asset, Description, Immediate Actions Taken, Witnesses, Recommended Follow-Up.
- Judging criteria:
  - (1) all AI models on-device with Snapdragon acceleration, **no cloud APIs**
  - (2) natural conversational speech
  - (3) speech-recognition accuracy
  - (4) extraction accuracy
  - (5) correct field mapping

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Android app in Kotlin + Jetpack Compose**, one `app` module, package `com.megathon.incident` | Matches every reference app |
| D2 | **LLM runtime: GenieX Android SDK** (`com.qualcomm.qti:geniex-android`, Maven Central; pin the version used in `qualcomm/ai-hub-apps` branch `release/geniex_chat_android`), `compute_unit = "npu"` | Official for SM8750, NPU, no QAIRT/QPM setup, supports AI Hub `qairt` bundles and `llama_cpp` GGUF (Q4_0 on NPU), local GGUF import via `HubSource.LOCALFS` |
| D3 | **Candidate models, picked by the on-device eval in spike S3.** Prefer the `llama_cpp` runtime, because only it can apply a grammar (D4): `unsloth/Qwen3-1.7B-GGUF` Q4_0, the Gemma 4 4B Instruct QAT Q4_0 (GenieX catalog), and `numind/NuExtract-2.0-2B-GGUF` `NuExtract-2.0-2B-Q4_0.gguf` (LOCALFS), all on the NPU. Baseline without grammar: `Qwen3-4B-Instruct` (qairt, NPU). | Grammar-capable models get guaranteed-valid JSON; qairt is the fastest reference |
| D4 | **Structured output = GBNF grammar at decode time + lenient parse + validation + one repair retry as a safety net.** The GenieX Android source (`bindings/android/.../bean/SamplerConfig.kt`) has `grammarString` and `grammarPath`, although the public API reference doesn't document them. The grammar is `assets/prompts/update_report.gbnf`, generated on the laptop from the JSON schema with llama.cpp `examples/json_schema_to_grammar.py`. Untested on the HTP backend: verify in Task 8. On qairt, or if the grammar is ignored, rely on parse + retry. | Validity enforced by the decoder; values still scored per field |
| D5 | **Function calling, made visible and native:** pass the `update_report` tool schema as `tools` to `LlmWrapper.applyChatTemplate(messages, tools, enableThinking=false)`, so the model's own tool-call template is used (Qwen3/Gemma). The model emits `update_report` arguments. The orchestrator executes the tools `update_report`, `ask_user`, `submit_report` and shows every call in a "Tool calls" panel. | Real function-calling format; a small model never has to plan |
| D6 | **The follow-up loop is deterministic code** (MandatoryFieldPolicy + FollowUpPlanner), not LLM discretion. **Mandatory fields: `incident_type`, `location`, `date_time`, `severity`.** At most 2 questions per turn. | LLMs stop asking too early (ClarifyMT-Bench). With severity mandatory, the slide's own example triggers exactly one follow-up, which demos the decision diamond. |
| D7 | **Grounding:** every extracted value needs an `evidence` span found in the cumulative transcript (normalised substring, ≥ 0.8 token overlap), else it is marked `UNCONFIRMED` and not counted as filled. Derived fields (`severity` from rules, `date_time` from the resolver, `description`, `recommended_follow_up`) are exempt and tagged `DERIVED`. | Stops hallucinated fields; gives the judges an "evidence highlight" view |
| D8 | **Deterministic helpers:** `DateTimeResolver` (relative times against an injected `Clock`), `SeverityRules` (keyword floor), `EnumNormalizer` (synonyms → enums) | Small models are weak at time arithmetic and enums |
| D9 | **STT: WhisperKit Android on the NPU** (`com.argmaxinc:whisperkit:0.3.3` + `com.qualcomm.qnn:qnn-runtime:2.34.0` + `com.qualcomm.qnn:qnn-litert-delegate:2.34.0`), model `OPENAI_TINY_EN` first, `BASE_EN` if accuracy is short. Push-to-talk. 16 kHz mono PCM. | Ungated Maven route; verified on an SM8750 QIDK (`AryamaVMurthy/sm8750-edge-ai-lab`) |
| D10 | **Plan B STT:** QIDK `GenAI-Solutions/ASR-LLM-TTS` Whisper-Small via the Voice AI ASR SDK (QPM login), with AI Hub Whisper-Small binaries for "Snapdragon 8 Elite Mobile" | Official, but gated |
| D11 | **TTS:** Android `TextToSpeech` with an offline voice (`Voice.isNetworkConnectionRequired()==false`). **Stretch:** MeloTTS on the NPU via the Voice AI TTS SDK from the QIDK sample. If the system TTS is used, disclose it to judges. | Zero risk first |
| D12 | **Storage:** JSON files under `filesDir/reports/` with kotlinx.serialization; export as a shareable JSON | No database needed |
| D13 | **Metrics panel:** ASR ms, LLM prefill+decode ms and tokens/s (from the GenieX `Completed(profile)`), backend label, end-to-end s | Substantiates the NPU claim |
| D14 | **Network:** GenieX needs `INTERNET` for the one-time model download. The demo runs in airplane mode, and the app shows a "Radios off ✓" badge from `ConnectivityManager`. | Honest privacy claim |
| D15 | **Plan C LLM** (only if JSON validity is below 90% after the repair retry): `llama-server` with `--device HTP0` launched from the APK (local-dream pattern, skill `qidk-npu-android` §2), using `response_format: json_schema` | Grammar-constrained decoding |

## 4. Incident JSON (LLM contract)

The LLM returns exactly one object:
```json
{"tool":"update_report","arguments":{
  "incident_type":"water_leak_flood|fire_smoke|electrical|spill_chemical|injury|illness|near_miss|property_damage|equipment_failure|security|environmental|other|null",
  "severity":"none|minor|moderate|severe|critical|null",
  "location":"string|null","date_time_text":"string|null",
  "persons_involved":["string"],"equipment_asset":"string|null",
  "immediate_actions":"string|null","witnesses":["string"],
  "evidence":{"<field>":"exact words from the transcript"}}}
```
The app computes `date_time` (ISO-8601 via DateTimeResolver), `description` (a cleaned transcript summary, template-based), and `recommended_follow_up` (a rule table keyed by incident_type and severity).

## 5. Out of scope

Multilingual input, photos and vision, cloud sync, accounts, wake word, streaming ASR, fine-tuning, PDF styling.

## 6. Demo (target 3 min)

1. HDMI to the projector, airplane mode on.
2. Speak the slide example. The transcript appears, fields fill with evidence highlights, and the tool-call log shows `update_report`.
3. The app asks aloud: "How severe is it?" Answer: "Minor, nobody was hurt."
4. Voice correction: "Actually it was Conference Room 3."
5. Review → Submit (`submit_report` JSON) → TTS summary.
6. Show the metrics panel, then the eval score card (n utterances, per-field accuracy, hallucination rate), all measured on the kit.

# AI Incident Report Assistant: Design (QIDK HDK8750 / SM8750P)

Date: 2026-10-10 · Status: proposed, awaiting team review. **Revised 2026-10-10 evening** (D9 corrected, D11 extended, D16–D21 added, §4b added, §5 changed); open team decisions are listed in `docs/team-split.md`.

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
- **Measured on our kit (2026-10-10, see `docs/device/qidk-device-profile-2026-10-10.md`):** Android **16** over an Android 15 vendor image, **15.4 GB RAM**, `adb root` works, **no camera**, the device **clock is wrong** (set it before showing timestamps), Wi-Fi off and airplane mode on. The guide itself does not state RAM or the Android version.

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
| D1 | **Android app in Kotlin + Jetpack Compose**, one `app` module (superseded by D21: `:core` + `:app`), package `com.megathon.incident` | Matches every reference app |
| D2 | **LLM runtime: GenieX Android SDK** (`com.qualcomm.qti:geniex-android`, Maven Central; pin the version used in `qualcomm/ai-hub-apps`, folder `geniex_chat_android/` on the default `release` branch; 0.3.5 on 2026-10-10), `compute_unit = "npu"` | Official for SM8750, NPU, no QAIRT/QPM setup, supports AI Hub `qairt` bundles and `llama_cpp` GGUF (Q4_0 on NPU), local GGUF import via `HubSource.LOCALFS` |
| D3 | **Candidate models, picked by the on-device eval in spike S3.** Prefer the `llama_cpp` runtime, because only it can apply a grammar (D4): `unsloth/Qwen3-1.7B-GGUF` Q4_0, the Gemma 4 4B Instruct QAT Q4_0 (GenieX catalog), and `numind/NuExtract-2.0-2B-GGUF` `NuExtract-2.0-2B-Q4_0.gguf` (LOCALFS), all on the NPU. Baseline without grammar: `Qwen3-4B-Instruct` (qairt, NPU). | Grammar-capable models get guaranteed-valid JSON; qairt is the fastest reference |
| D4 | **Structured output = GBNF grammar at decode time + lenient parse + validation + one repair retry as a safety net.** The GenieX Android source (`bindings/android/.../bean/SamplerConfig.kt`) has `grammarString` and `grammarPath`, although the public API reference doesn't document them. The grammar is `assets/prompts/update_report.gbnf`, generated on the laptop from the JSON schema with llama.cpp `examples/json_schema_to_grammar.py`. Untested on the HTP backend: verify in Task 8. On qairt, or if the grammar is ignored, rely on parse + retry. | Validity enforced by the decoder; values still scored per field |
| D5 | **Function calling, made visible and native:** pass the `update_report` tool schema as `tools` to `LlmWrapper.applyChatTemplate(messages, tools, enableThinking=false)`, so the model's own tool-call template is used (Qwen3/Gemma). The model emits `update_report` arguments. The orchestrator executes the tools `update_report`, `ask_user`, `submit_report` and shows every call in a "Tool calls" panel. | Real function-calling format; a small model never has to plan |
| D6 | **The follow-up loop is deterministic code** (MandatoryFieldPolicy + FollowUpPlanner), not LLM discretion. **Mandatory fields: `incident_type`, `location`, `date_time`, `severity`.** At most 2 questions per turn. | LLMs stop asking too early (ClarifyMT-Bench). With severity mandatory, the slide's own example triggers exactly one follow-up, which demos the decision diamond. |
| D7 | **Grounding:** every extracted value needs an `evidence` span found in the cumulative transcript (normalised substring, ≥ 0.8 token overlap), else it is marked `UNCONFIRMED` and not counted as filled. Derived fields (`severity` from rules, `date_time` from the resolver, `description`, `recommended_follow_up`) are exempt and tagged `DERIVED`. | Stops hallucinated fields; gives the judges an "evidence highlight" view |
| D8 | **Deterministic helpers:** `DateTimeResolver` (relative times against an injected `Clock`), `SeverityRules` (keyword floor), `EnumNormalizer` (synonyms → enums) | Small models are weak at time arithmetic and enums |
| D9 | **STT: two engines behind one `SpeechEngine` interface.** **Engine 1, English baseline (ready in hours):** WhisperKit Android on the NPU: `com.argmaxinc:whisperkit:0.3.3` + `com.qualcomm.qti:qnn-runtime:2.44.0` + `com.qualcomm.qti:qnn-litert-delegate:2.44.0` (the `com.qualcomm.qnn` group returns 404 on Maven Central; 2.44.0 is the version proven on an SM8750 QIDK), model profile `QUALCOMM_TINY_EN`, then `QUALCOMM_BASE_EN` if accuracy is short. WhisperKit is archived and English-only, with no language or task API. **Engine 2, multilingual + emotion (target):** `sherpa-onnx` built from source with the QNN backend on the build VM (D20), running the prebuilt `sherpa-onnx-qnn-SM8750-binary-whisper-small` (or `-base`) with a forced `language` (en/hi/te from a UI toggle, never auto-detect) and SenseVoice for emotion/events (D17). **Engine 2b, fallback if the sherpa build or the QNN library clash with GenieX fails by hour 9:** Qualcomm AI Hub's public multilingual Whisper binaries with our own decode loop that seeds the language token. The English weights are chosen by an accent contest on our own recordings: stock `tiny.en`/`base.en` against `theshivam7/whisper-{tiny,small}-aesrc-indian-english` (gated=auto; accept the terms once and download before the event). 16 kHz mono PCM, 5–10 s segments (QNN needs static shapes; AI Hub decodes at most 200 tokens). | Ungated, proven on this SoC (engine 1); only open route that can force Hindi/Telugu on the HTP and also carries SenseVoice (engine 2). See `reports/Indic ASR and TTS on Snapdragon.md` |
| D10 | **Plan B STT:** QIDK `GenAI-Solutions/ASR-LLM-TTS` Whisper-Small via the Voice AI ASR SDK (QPM login), with AI Hub Whisper-Small binaries for "Snapdragon 8 Elite Mobile" | Official, but gated. Use only if someone confirms a working Qualcomm Package Manager login before the event; it auto-detects the language and the sample exposes no way to force one |
| D11 | **TTS:** Android `TextToSpeech` with an offline voice (`Voice.isNetworkConnectionRequired()==false`). Before the event, install the Google offline packs `en-IN`, `hi-IN` and `te-IN` with network on, then test in airplane mode. **Stretch:** Piper `hi_IN`/`te_IN` voices through sherpa-onnx (CPU), or MeloTTS-EN on the NPU via the Voice AI TTS SDK (login-gated). No Indic TTS exists for the NPU. If the system or Piper TTS is used, disclose it to judges as CPU. | Zero risk first |
| D12 | **Storage:** JSON files under `filesDir/reports/` with kotlinx.serialization; export as a shareable JSON | No database needed |
| D13 | **Metrics panel:** ASR ms, LLM prefill+decode ms and tokens/s (from the GenieX `Completed(profile)`), backend label, end-to-end s | Substantiates the NPU claim |
| D14 | **Network:** GenieX needs `INTERNET` for the one-time model download. The demo runs in airplane mode, and the app shows a "Radios off ✓" badge from `ConnectivityManager`. | Honest privacy claim |
| D15 | **Plan C LLM** (only if JSON validity is below 90% after the repair retry): `llama-server` with `--device HTP0` launched from the APK (local-dream pattern, skill `qidk-npu-android` §2), using `response_format: json_schema` | Grammar-constrained decoding |
| D16 | **Report first, suggest after.** The report stores exactly what the speech engine heard (`REPORTED`) and is never blocked or silently rewritten. A `SuggestionEngine` proposes fixes as tappable chips (`Suggestion(field, original, proposed, reason, source)`): site-gazetteer snaps ("heard *room to* → Conference Room 2"), hedge cues, role fixes (witness vs involved). Accepting a chip sets `USER_CONFIRMED`; the original stays in the journal; Submit is never gated on pending suggestions. | Emergency context: never delay or alter the report; the judges see the correction but the evidence stays intact |
| D17 | **Signals** (inputs to suggestions and to the priority advisor in D18), each tagged with its compute: (a) **text cues for tense/aspect/modality (TAM)**, rule-based on the CPU: tense past/present/future, aspect ongoing/completed, modality asserted/hedged/negated/planned, English and Hindi cue lists ("still", "abhi bhi", "ho raha hai", "maybe", "shayad", "nobody", "nahi"); a *planned* action is never an `immediate_action`, a *hedged* value gets an `UNCONFIRMED` chip, a *negated* one becomes `ExplicitNone`; (b) **SenseVoice** emotion (`happy|sad|angry|neutral`, no confidence value, no Hindi) and events (`Cry`, `Cough`, `Breath`, `Laughter`, ...) via sherpa-onnx QNN `SM8750` binaries, falling back to sherpa-onnx CPU and disclosed; (c) **YamNet** (`qualcomm/YamNet`, 3.7M parameters, MIT, 521 AudioSet classes: fire alarm, smoke detector, siren, screaming, explosion, glass breaking, running water, hissing, gunshot) over the raw audio. | Cheap, mostly deterministic, and every signal is shown with its source |
| D18 | **Priority advisor** (`PriorityAdvisor`, rules in `assets/policies/priority_rules.json`), **separate from severity**: severity is only what the reporter states or confirms; `SeverityRules` output becomes a `SUGGESTED` value, not a fill. Priority is advice (`LOW|MEDIUM|HIGH|URGENT` plus a visible list of reasons) from ongoing-aspect hazards, alarm/scream events, distress emotion and the incident type. **Signals can only raise priority, never lower it** (a calm voice, "nobody hurt", or a missing signal never reduces urgency). URGENT or a danger signal speaks "If anyone is in danger, call 112" first. The user can override and the override is journaled. | SenseVoice knows only four emotions and is untested on Indian voices; the claim stays "suggestion with reasons" |
| D19 | **Language scope:** English is the core lane. **Hindi and Telugu are an experimental lane**, enabled in the demo only if a measured check on our recorded Hindi/Telugu clips shows field accuracy close to the English lane (threshold proposed: within 15 points; the team confirms). Telugu needs fine-tuned Whisper weights; stock Whisper-Small scores 98% WER on Telugu and 38% on Hindi (FLEURS). Language comes from a UI toggle. | Honest claims; avoids lowering the speech-accuracy score |
| D20 | **Development infrastructure (not part of the app):** a cloud CPU VM (8 vCPU, 32 GB RAM, 250 GB disk, Ubuntu 24.04.4 as provisioned (22.04 was the original recommendation; a cross-compile should not care [I])) is the build host for sherpa-onnx + QAIRT Community SDK 2.40.0.251030 and AI Hub exports. The RTX PRO 6000 and H100 machines serve as a shared test server (LLM candidates, Whisper accent contest, SenseVoice emotion check) and as an optional data-drafting helper; all draft labels need human review. **Nothing on these machines runs in the demo: every model in the app runs on the QIDK.** Never copy the kit's confidential quick-start guide or any credential onto them. Fine-tuning is out of scope unless the team decides otherwise and the organisers allow it. | The laptop's disk is 99% full and two teammates have light laptops |
| D21 | **Project structure:** `:core` (pure Kotlin/JVM: domain types, parser, validator, merger, TAM cues, priority advisor, suggestion engine, scoring) and `:app` (Android: audio, speech engines, GenieX adapter, UI, TTS). `:core` builds and tests with JDK 17 + Gradle only, so teammates without Android Studio can own it. This replaces D1's "one `app` module". | Lets three light laptops contribute real code |
| D22 | **Workflow: develop in `m26-qidk`, submit by snapshot.** All development, branches, research and planning stay in the private dev repo `m26-qidk` (one branch per person; **only A merges to `master`**). The submission repo `IIIT-ECell/megathon26-readyornot` (only `main` is evaluated) receives **clean snapshots only**, made by **A** with `tools/mirror_to_submission.sh <clone> [--apply]` (dry run first). The script exports the **committed** `app/`, `core/`, Gradle files and `tools/` (minus the blindcheck and mirror tools), strips audio, models, binaries and PDFs, scans contents and file names against `tools/blindcheck.words`, refuses a wrong origin or a git identity that matches a name or college pattern, never touches `README.md`, `LICENSE` or `deliverables/`, and **never commits or pushes** (A reviews, commits and pushes). PDFs are built by E and copied into the submission repo's `deliverables/` by A. This repo's history, branches, `docs/`, `research_notes/` and `reports/` are never pushed to the submission repo. | Double-blind and clean submission; planning docs name the college and stay private |

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

## 4b. Signals, suggestions and priority (types)

```kotlin
enum class ValueStatus { MISSING, UNKNOWN, EXPLICIT_NONE, REPORTED, SUGGESTED, USER_CONFIRMED, USER_EDITED }
enum class Source { TEXT_CUE, SENSEVOICE, YAMNET, GAZETTEER, RULE }   // shown on every chip
data class Tam(val tense: Tense, val aspect: Aspect, val modality: Modality)
data class Signal(val kind: String, val label: String, val source: Source, val segmentId: Long?)
data class Suggestion(val field: Field, val original: String, val proposed: String, val reason: String, val source: Source)
data class PriorityAdvice(val level: Priority, val reasons: List<String>, val overriddenByUser: Boolean = false)
```

Rules: signals and suggestions never write a field by themselves; `PriorityAdvisor` can only raise; every chip and reason names its source and its compute (CPU rule, NPU model or CPU model).

## 5. Out of scope

Photos and vision (the kit has no camera), cloud sync, accounts, wake word, true streaming ASR, LLM fine-tuning, PDF styling. Hindi/Telugu is an experimental lane (D19), not core scope.

## 6. Demo (target 3 min)

1. HDMI to the projector, airplane mode on.
2. Speak the slide example. The transcript appears, fields fill with evidence highlights, and the tool-call log shows `update_report`.
3. The app asks aloud: "How severe is it?" Answer: "Minor, nobody was hurt."
4. Voice correction: "Actually it was Conference Room 3."
5. Review → Submit (`submit_report` JSON) → TTS summary.
6. Show the metrics panel, then the eval score card (n utterances, per-field accuracy, hallucination rate), all measured on the kit.

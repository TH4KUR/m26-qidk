# Handoff: person B, speech engines, signals (SenseVoice, YamNet) and the accent contest

Branch: `b/speech-engines` · Written 2026-10-10 · Read this whole file first, then the "Read first" list. Hour figures are estimates, not measurements. Tags: **[V]** verified in the repo's research files or sources, **[I]** inferred, prove it on the device.

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
5. `reports/Indic ASR and TTS on Snapdragon.md` and all three files in `research_notes/Indic ASR and TTS on Snapdragon/` (`asr_models.md`, `tts_models.md`, `npu_deployment.md`)
6. `docs/device/qidk-device-profile-2026-10-10.md`
`PLAN-codex.md` is a teammate's plan made with another AI. It is a **reference only** (validation rules, negation tests), **not authoritative**: whether it overrides our plan is an open team decision.

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
1. Which plan is authoritative: **default: our spec and plan with Addendum A.**
2. Hindi/Telugu: **default: experimental lane**, decided at hour 16 (D19); English is core.
3. Qualcomm Package Manager login: **default: assume none**, so Voice AI SDK routes are dead.
4. Severity: **default: rules only suggest it** (D18); the user states or confirms it.
5. `:core` module (D21): **default: yes.**
8. A's workload: **Task 8 (GenieX adapter) may move from A to B.** A will tell you.

## 5. You are B
**Laptop:** strong (Android Studio capable). **Kit:** only in slots booked with A (or wireless adb agreed with A). **You own:** `app/src/main/java/com/megathon/incident/asr/`, `.../tts/`, `.../signals/`, `tools/accent_contest/`.

### 5.1 First 90 minutes
- **Accept the Hugging Face terms** on **your own account** for `theshivam7/whisper-tiny-aesrc-indian-english`, `theshivam7/whisper-small-aesrc-indian-english` (gated=auto, free, instant) and the `-seeds` repos. Use `hf download <repo> --local-dir ...`. Also download stock `openai/whisper-tiny.en` and `openai/whisper-base.en`.
- Write the accent-contest script in `tools/accent_contest/` (Python, transformers): run the same recordings through stock `tiny.en`, `base.en` and the `theshivam7` Tiny and Small, output a WER table. Until E's recordings arrive (about hour 4), test with your own phone clips. Run it on the GPU test server if your laptop is slow.
- Build the WhisperKit baseline with the **corrected** Gradle line: `com.argmaxinc:whisperkit:0.3.3` + `com.qualcomm.qti:qnn-runtime:2.44.0` + `com.qualcomm.qti:qnn-litert-delegate:2.44.0` [V proven on an SM8750 QIDK], profile **`QUALCOMM_TINY_EN`** (then `QUALCOMM_BASE_EN`). WhisperKit is archived and English-only with no language or task API [V].

### 5.2 Tasks
- **Task 9 + `SpeechEngine`:** `interface SpeechEngine { suspend fun transcribe(pcm: ShortArray, language: String): Transcript }` with `WhisperKitEngine` first (`asr/`). Audio capture: `AudioRecord`, 16 kHz mono PCM16, source `VOICE_RECOGNITION`. Warm the model at app start (first NPU run 4.8 s vs 0.7 s CPU on an SM8750 [V]).
- **Task 10 TTS:** Android `TextToSpeech` with an offline voice (`Voice.isNetworkConnectionRequired()==false`). **Before the event** install Google offline packs `en-IN`, `hi-IN`, `te-IN` with network on, then test in airplane mode [V that the packs exist; not installed yet]. Disclose TTS as CPU. Stretch: Piper `hi_IN`/`te_IN` through sherpa-onnx (CPU), or MeloTTS-EN on the NPU (login-gated).
- **Engine 2b prep (insurance for A's sherpa-onnx build):** Qualcomm AI Hub's public multilingual Whisper binaries (login-free; Whisper-Small float is about 65 ms encoder per 30 s and 8.3 ms per decoder token on 8 Elite, Qualcomm's numbers) with your own decode loop that seeds `[SOT, <|hi|>, <|transcribe|>, <|notimestamps|>]`. Note: AI Hub changed in Sept 2026 (`qnn_context_binary` target removed, "Snapdragon 8 Elite" device dropped; use "Samsung Galaxy S25 (Family)").
- **A4 signals:** SenseVoice (emotion `happy|sad|angry|neutral`, events `Cry`, `Cough`, `Breath`, `Laughter`, ...; no Hindi, no confidence values) and YamNet (`qualcomm/YamNet`, 521 classes incl. fire alarm, smoke detector, siren, screaming, explosion, glass, water) as `Signal` producers (type defined by C). Start against the prebuilt **non-QNN** sherpa-onnx AAR (CPU) so you are not blocked [I]; switch to A's QNN libraries at hour 6 if A says go. Every signal carries its compute label (NPU or CPU).
- **A10 hands-free loop (hours 10-14):** energy end-of-speech detector with 700 ms hangover (CPU), `VOICE_COMMUNICATION` source plus echo cancellation, mic muted while TTS speaks and reopened on `onDone`, acknowledgments from templates built from filled slots, voice commands "read it back", "change X to Y", "submit". Fallback: tap-to-talk with auto-stop.
- **Pending decision 8:** Task 8 (GenieX adapter) may come to you if the team moves it off A.

### 5.3 Facts to respect
- Stock Whisper-Small scores 98% WER on Telugu and 38% on Hindi (FLEURS) [V], so Hindi/Telugu is an **experimental lane** (D19) and needs fine-tuned weights whose swap into the NPU graphs is **untested**.
- `theshivam7` Indian-English card: stock tiny 17.0% to 9.8%, small 6.9% to 5.4% WER on read speech, 481 held-out speakers [V, the author's numbers, not ours].
- Force the language from a UI toggle; do not rely on auto-detect.
- Do not run `adb` against the kit outside a booked slot.

## 6. Interfaces and handovers
| You receive | From | By |
|---|---|---|
| `Signal`, `Tam` and other shared types in `:core` | C | hour 1.5 |
| sherpa-onnx QNN libraries and working config, or the no-go | A | hour 6 |
| Gold clips recorded on the kit | E | hour 4 |

| You give | To | By |
|---|---|---|
| Accent-contest WER table | E | hour 9 |
| `SpeechEngine`, TTS and signals ready to wire | A | hour 9 |

## 7. Definition of done for your part
English transcription on the NPU through `SpeechEngine`; a ranked accent-contest table on our own recordings; `Signal` objects produced for the recorded clips with their compute labelled; TTS speaking questions in airplane mode.

## 8. Starter prompt for your Claude
> Read `docs/handoff/B-speech-engines.md`, then the files it lists under "Read first". I am person B. Work on branch `b/speech-engines` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 9. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

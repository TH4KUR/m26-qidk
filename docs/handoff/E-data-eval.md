# Handoff: person E, data, evaluation, GPU test server and pitch

Branch: `e/data-eval` · Written 2026-10-10 · Read this whole file first, then the "Read first" list. Hour figures are estimates, not measurements. Tags: **[V]** verified in the repo's research files or sources, **[I]** inferred, prove it on the device.

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
5. Plan Task 0b (gold set), Task 12 (scorer) and spike S3 (LLM candidate contest)
6. `reports/Hackathon differentiators.md` (idea 3, the Judge Bench, is yours)
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

## 5. You are E
**Laptop:** light, but you have access to the **GPU test server** (RTX PRO 6000 or H100; for development only, nothing there runs in the demo). **You own:** `app/src/main/assets/eval/`, `docs/eval-guide.md`, `tools/laptop_eval/`, `tools/`, and `core/src/main/kotlin/com/megathon/incident/eval/EvalScorer.kt` with its tests.

### 5.1 First 90 minutes: the recording plan
- **The gold set is recorded on the kit itself**, in one booked session with A, using the exact mic setup and distance of the demo (built-in MIC4/5, or a headset on the 3.5 mm jack: the guide says the kit defaults to the headset when one is plugged in). This tests the real mic path, which is still unproven. On the kit, `tinycap` (`/system/bin`) and the Sound Recorder app (`com.android.soundrecorder`) are present [V, listed, not tested]; once the app has a recorder mode, use it (same 16 kHz mono capture path as the app). Convert to **16 kHz mono WAV**.
- **All five teammates** record about **8 incident reports each** (clean and stressed takes, Indian accents, fan noise, six self-corrections). Add: a few Hindi/Hinglish and Telugu clips for the experimental lane; a few clips with an alarm playing in the background (for YamNet); calm and stressed takes of the same report (for the SenseVoice emotion check).
- Extra clips may be recorded on phones in parallel but **label them as phone audio** in the manifest; the scorecard must say what each set was recorded on.
- **Type the correct answers** (gold). Machine-drafted labels from a large model on the GPU server are allowed as a starting point but **every label needs human review.**

### 5.2 Tasks
- **Task 0b:** `app/src/main/assets/eval/gold_v1.jsonl`, 40 lines: 10 hand-written facilities cases (the slide example verbatim as `g01`), 22 rewritten as spoken first-person reports from `Simar123456/spectra-osha-sir` (`narrative_masked`, test split), 8 multi-turn cases with follow-ups or corrections. Plus `docs/eval-guide.md` (labelling rules). Verify every line parses as JSON.
- **Scorer (Task 12):** `EvalScorer` in `:core` and `tools/laptop_eval/run_eval.py`: per-field accuracy, hallucination rate (gold null but prediction filled), word error rate, follow-up precision, latency. **Show denominators and raw counts.** Blank output must score zero recall, not perfect accuracy.
- **Spike S3: LLM candidate contest on the GPU server** with the gold set: `unsloth/Qwen3-1.7B-GGUF` Q4_0, Gemma 4 4B Instruct QAT Q4_0, `numind/NuExtract-2.0-2B-GGUF` Q4_0, baseline Qwen3-4B-Instruct (GenieX `qairt`). Prefer the `llama_cpp` runtime (only it applies a grammar). **A final pick still needs a run on the kit**; the server result shortlists, it does not decide.
- **Judge Bench data:** three configurations, **raw LLM, +grounding, +gazetteer and suggestions**, so the scorecard shows what each layer adds. Include the accent A/B table from B (stock tiny.en/base.en vs `theshivam7` Tiny and Small).
- **Emotion/priority check:** run the calm-vs-stressed and alarm clips through B's signals and C's priority advisor; report where the signal is wrong. SenseVoice has four emotions and is untested on Indian voices; do not oversell it.
- **Hours 16-21:** final numbers, demo script (`docs/demo-script.md`), slides, backup video. Use the real numbers, whatever they are; state what was measured on the kit and what on phone audio.

## 6. Interfaces and handovers
| You give | To | By |
|---|---|---|
| Recordings and gold labels | B, C, D | hour 4 |
| Scorer and Judge Bench harness | A | hour 9 |

| You receive | From | By |
|---|---|---|
| Accent-contest WER table | B | hour 9 |
| Gradle skeleton (for `EvalScorer`) | A | hour 1 |

## 7. Definition of done for your part
`gold_v1.jsonl` (40 lines) and the kit recordings exist with a manifest naming the recording device; the scorer prints per-field accuracy, hallucination rate and word error rate with denominators; the LLM contest has a ranked table; the Judge Bench shows raw vs +grounding vs +gazetteer.

## 8. Starter prompt for your Claude
> Read `docs/handoff/E-data-eval.md`, then the files it lists under "Read first". I am person E. Work on branch `e/data-eval` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 9. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

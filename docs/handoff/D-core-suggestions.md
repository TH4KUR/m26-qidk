# Handoff: person D, `:core` suggestions, follow-up flow, merge and storage, safety advice

Branch: `d/core-suggestions` · Written 2026-10-10 · Read this whole file first, then the "Read first" list. Hour figures are estimates, not measurements. Tags: **[V]** verified in the repo's research files or sources, **[I]** inferred, prove it on the device.

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
5. Plan Tasks 5 and 7 (`ReportLogic.kt`, `ReportRepository.kt`)
6. `reports/Hackathon differentiators.md` (ideas 1, 2 and 7 are yours)
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

## 5. You are D
**Laptop:** light (no Android Studio needed). You need **JDK 17 + Gradle**; your command is `./gradlew :core:test`. **You own:** `core/src/main/kotlin/com/megathon/incident/{suggest,report,storage}/`, `site.json`, and their tests.

### 5.1 First 90 minutes (before A's skeleton lands)
- **Collect real IIIT-H names** into `site.json`: about 30 rooms/buildings (with aliases), about 20 assets with IDs (e.g. chiller), and a small staff list. **Verify them on site; they are not verified yet.** If the team prefers, use a clearly fictional site. Location: `app/src/main/assets/site/site.json`, test copy in `core/src/test/resources/`. `SuggestionEngine` takes a parsed `SiteDirectory` object, so it stays Android-free.
- Start failing tests for the snapper using phrases like "conference room to", "Vindya see four", "chiller".

### 5.2 Tasks (pure Kotlin in `:core`)
- **A6: `SuggestionEngine`** (design D16: **report first, suggest after**). The report keeps exactly what was heard (`REPORTED`). The engine only produces `Suggestion(field, original, proposed, reason, source)` chips:
  - **Gazetteer snap:** a homophone and number table ("to/too/two" after "room", "for/four"), then Double Metaphone plus Jaro-Winkler over transcript n-grams at a **0.88** threshold [I, tune on E's clips]. It runs **after** the LLM so the evidence check still sees the raw transcript. Double Metaphone comes from Apache `commons-codec`; ask A before adding a dependency to `:core`.
  - **Uncertainty cues:** hedge words produce an `UNCONFIRMED` chip (C owns the TAM detector; reuse it, do not duplicate).
  - **Role post-rule:** a person whose only evidence verb is saw/noticed/reported becomes a witness, not "involved"; "nobody hurt" blocks the `injury` type.
  - **Accept/reject events:** accepting sets `USER_CONFIRMED`; the **original stays in the journal**; **Submit is never blocked by pending suggestions.** A wrong snap for a place not in `site.json` is only a suggestion.
- **Task 5:** `ReportLogic.kt`: report merge (new answers never overwrite earlier correct ones), mandatory policy (**incident type, location, date/time, severity**, in that order, D6), follow-up planner (at most **2 questions per turn**), tri-state fields (`UNKNOWN | EXPLICIT_NONE | Value`): "no witnesses" fills `None (stated)`, "I don't know" is accepted for non-critical fields.
- **Task 7:** `ReportRepository(dir: java.io.File)` with `save`, `list`, `exportJson` (JVM-testable; A passes `filesDir` from the app). Atomic write: temp file then rename; a failed write must not report success.
- **Hours 16-19: spoken safety advice:** a rule table keyed by incident type and severity (about 15 entries), shown as "suggested"; and the "If anyone is in danger, call 112" line wiring with C's `PriorityAdvisor`. First thing to cut if time runs out.

## 6. Interfaces and handovers
| You receive | From | By |
|---|---|---|
| Shared domain types | C | hour 1.5 |
| TAM detector (for hedge cues) | C | hour 6 |
| Recordings and gold labels (to tune the 0.88 threshold) | E | hour 4 |

| You give | To | By |
|---|---|---|
| Suggestion chips API, follow-up planner, storage | A (UI) | hour 9 |

## 7. Definition of done for your part
"heard *room to*" yields a chip "Conference Room 2?" with the original kept; accepting and rejecting both work; Submit is never blocked; "Ravi slipped, Priya saw it" puts Ravi in persons involved and Priya in witnesses; `./gradlew :core:test` is green with failing-first tests.

## 8. Starter prompt for your Claude
> Read `docs/handoff/D-core-suggestions.md`, then the files it lists under "Read first". I am person D. Work on branch `d/core-suggestions` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 9. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

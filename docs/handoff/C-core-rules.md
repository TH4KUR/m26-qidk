# Handoff: person C, `:core` rules (parser, validator, tense/aspect/modality, priority advisor)

Branch: `c/core-rules` · Written 2026-10-10 · Read this whole file first, then the "Read first" list. Hour figures are estimates, not measurements. Tags: **[V]** verified in the repo's research files or sources, **[I]** inferred, prove it on the device.

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
5. Plan Tasks 1 to 4 (`Model.kt`, `ToolCallParser`, `Normalizers`, `GroundingValidator`)
6. `PLAN-codex.md` sections 5.2 to 5.5 (reference only: validation, negation and correction rules worth adopting as tests)
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

## 5. You are C
**Laptop:** light (no Android Studio needed). You need **JDK 17 + Gradle**; your command is `./gradlew :core:test`. **You own:** the shared domain types and `core/src/main/kotlin/com/megathon/incident/{domain,llm,priority,tam}/` and their tests.

### 5.1 First 90 minutes (before A's skeleton lands)
- Draft the shared domain types as plain Kotlin per spec section 4 and 4b and plan Task 1 (`Model.kt`): `IncidentType`, `Severity`, `Field`, field status types, `ValueStatus {MISSING, UNKNOWN, EXPLICIT_NONE, REPORTED, SUGGESTED, USER_CONFIRMED, USER_EDITED}`, `Source {TEXT_CUE, SENSEVOICE, YAMNET, GAZETTEER, RULE}`, `Tam(tense, aspect, modality)`, `Signal`, `Suggestion`, `PriorityAdvice`. **You own the shared types: announce any change before merging.** When A pushes the skeleton, rebase onto it and move your files into `:core`.
- Turn the problem-statement examples into test cases (the deck's example sentence is "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."; the deck's example fields are in spec section 2). Note it gives **no severity and no date**, so the app must ask for both. Start with failing tests.

### 5.2 Tasks (all pure Kotlin in `:core`)
- **Task 1 (domain part):** types above, JSON round-trip incl. Hindi text, distinct missing/none/unknown.
- **Task 2:** `ToolCallParser`: lenient JSON extraction and tool-call parsing (strip markdown fences, one known envelope, strict failures; never rescue partial values with regex).
- **Task 3:** `EnumNormalizer`, `SeverityRules`, `DateTimeResolver(clock, zone)`. **Change vs plan (D18): `SeverityRules` returns a *suggestion* (`SUGGESTED`), it does not fill severity.** Time: preserve precision ("around 10 AM" = approximate, date missing, ask), never invent a date. Hindi "kal" is ambiguous (yesterday or tomorrow): ask.
- **Task 4:** `GroundingValidator.isGrounded(value, evidence, transcript)`: evidence must be found in the cumulative transcript (normalised substring, token overlap >= 0.8), else `UNCONFIRMED`.
- **A5a: tense/aspect/modality (TAM) cue detector** (English and Hindi cue lists, rule-based): aspect ongoing vs completed ("still leaking", "abhi bhi", "ho raha hai" vs "was leaking, we turned it off"), tense planned vs done ("we will call maintenance" is not an immediate action), modality hedged ("I think", "maybe", "shayad", "lagta hai"), negated ("nobody", "nahi"). Effects: planned goes to follow-up, hedged gets an `UNCONFIRMED` chip, negated becomes `EXPLICIT_NONE`. **Get a Hindi speaker to review the Hindi lists.**
- **A5b: `PriorityAdvisor`** with `app/src/main/assets/policies/priority_rules.json` (copy in `core/src/test/resources/`): output `PriorityAdvice(level LOW|MEDIUM|HIGH|URGENT, reasons)`. **Hard rule: signals can only raise priority, never lower it.** A calm voice, "nobody hurt" or a missing signal never reduces it. URGENT or a danger signal triggers the spoken line "If anyone is in danger, call 112 now." The user can override; overrides are journaled. Inputs: TAM tags, `SeverityRules` suggestion, `Signal`s (SenseVoice emotion, YamNet events) from B.
- **Tests to include (from the codex plan, adopted):** negation across segments ("Ravi did not see it" never leaves Ravi a witness), corrections ("room 2" becomes "room 3"), no severity guessing, an event never marks an incident critical by itself.

## 6. Interfaces and handovers
| You give | To | By |
|---|---|---|
| Shared domain types and contract in `:core` | everyone | hour 1.5 |
| `ToolCallParser`, normalisers, grounding | A (adapter), D (merge) | hour 6 |
| TAM detector and `PriorityAdvisor` | A (UI panel) | hour 9 |

| You receive | From | By |
|---|---|---|
| Gradle skeleton | A | hour 1 |
| Recordings and gold labels (to tune rules) | E | hour 4 |
| `Signal` producers | B | hour 9 |

## 7. Definition of done for your part
`./gradlew :core:test` green with failing-first tests for every rule above; the slide example parses to the expected fields with date and severity asked; TAM and priority behave on E's recorded clips; priority is never lowered by any signal (a property test).

## 8. Starter prompt for your Claude
> Read `docs/handoff/C-core-rules.md`, then the files it lists under "Read first". I am person C. Work on branch `c/core-rules` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 9. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

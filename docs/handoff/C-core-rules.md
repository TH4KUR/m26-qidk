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
A teammate's earlier alternative plan was **removed from the repo**. **Our spec (D1-D21) and our plan with Addendum A are authoritative.** Its useful ideas (REPORTED/CONFIRMED labels, negation and correction tests, no severity guessing) are already adopted in the spec and in your tasks.

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
1. Which plan is authoritative: **settled: ours** (the alternative plan was removed from the repo).
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
- **Tests to include (adopted from the earlier alternative plan):** negation across segments ("Ravi did not see it" never leaves Ravi a witness), corrections ("room 2" becomes "room 3"), no severity guessing, an event never marks an incident critical by itself.

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

## 7a. Fixtures, fakes and contracts you own
You own the **shared types and the shared fixtures**: freeze the types by hour 1.5 and announce changes.
- **`Clock`/`FixedClock`:** inject a clock everywhere (time resolver, journal) so tests are deterministic.
- **`LlmEngine` interface and its contract tests** (A implements `GenieXLlmEngine`): `interface LlmEngine { val compute: Compute; suspend fun generate(request: ExtractionRequest): String }`. **`FakeLlmEngine`** returns canned `update_report` JSON per input (A needs it in the skeleton).
- **Canned raw model outputs** in `fixtures/llm/` for the parser tests: valid; wrapped in markdown fences; truncated JSON; wrong tool name; extra properties; duplicate scalar writes; prose only; a native tool-call envelope. These double as test data for A's adapter.
- **Transcript fixtures** in `fixtures/transcripts/`: the deck example, the same with Whisper-style mistakes ("conference room to", "ten am" for "10 AM", missing punctuation), negation ("Ravi did not see it"), corrections ("no, Room 3"), hedges, planned vs completed actions, Hindi/Hinglish samples (reviewed by a native speaker). D and E use them; do not let anyone copy them.
- **`TamDetector` / `HedgeDetector` interface** for D: `interface HedgeDetector { fun hedges(text: String): List<IntRange> }`. Publish it with a simple rule fake **by hour 3** so D is not blocked on your full detector (D needs it at hour 6).
- **`FakeSignalSource` fixtures** until B ships: use B's fake; agree the `Signal` fields with B first.
- **Property test for `PriorityAdvisor`:** for any combination of signals, adding a signal never lowers the level, and a missing signal never lowers it. This is a hard rule (D18).

## 8. Stubs, fakes and contract tests (the team pattern)
Five people build in parallel, so **nobody waits for the real NPU engine.** Every dependency on someone else's work gets an **interface in `:core`** and a **fake**, until the real one lands.

1. **Where:** interfaces and fakes in `core/src/main/kotlin/com/megathon/incident/testing/` (so `:app` debug builds can use them); shared fixtures in `core/src/test/resources/fixtures/` (deck example, canned raw LLM outputs, Whisper-style mistakes, signal lists, a test `site.json`). Add to the shared folders; do not copy fixtures.
2. **Fakes are labelled `SIMULATED`.** `enum class Compute { NPU, CPU, HYBRID, SIMULATED }` is part of every engine's result. The proof strip shows `SIMULATED` in a clearly different colour, and **release builds must refuse to start with a simulated engine** (guard in `EngineFactory`). **A scripted result must never be shown as live recognition.**
3. **One `EngineFactory` in `:app`** (A) selects speech `FAKE | WHISPERKIT | SHERPA_QNN | AIHUB` and LLM `FAKE | GENIEX`. Default is FAKE until the real engine passes its contract test; a debug setting switches.
4. **One contract-test suite per interface**, run against the fake now and every real implementation later (same tests, so fake and real cannot drift).
5. **Interfaces are owned and announced** (chat plus a line in the commit message). Changes go through the owner.

| Interface | Owner | Fake (ship early) | Real | Consumed by |
|---|---|---|---|---|
| `SpeechEngine`, `AudioSource`, `Speaker` | B | `FakeSpeechEngine`, `WavFileSource`, `FakeSpeaker` (logs text) | WhisperKit, sherpa-onnx QNN, AI Hub; `AudioRecord`; Android TTS | A (pipeline), E (Judge Bench) |
| `SignalSource` | B | `FakeSignalSource` (canned alarm/emotion lists per clip ID) | SenseVoice + YamNet | C (priority), A (panel) |
| `LlmEngine` (contract by C, adapter by A) | C / A | `FakeLlmEngine` (canned `update_report` JSON per input) | `GenieXLlmEngine` | A, C, D, E |
| `SuggestionEngine` | D | `FakeSuggestionEngine` (returns the "Conference Room 2" chip) | real engine | A (chips UI) |
| `PriorityAdvisor`, `TamDetector`/`HedgeDetector` | C | simple rule fakes | real | A, D |
| `Clock` | C | `FixedClock` | system clock | C, D, E |
| `ReportRepository` | D | `InMemoryReportRepository` | file-based | A |
| `PipelineRunner` (replays clips for the Judge Bench) | E | `FakePipelineRunner` (reads predictions from a file) | real pipeline | A (Judge Bench UI) |

## 9. Experiment log and submission rules
**Experiment log (needed for M2):** every experiment or measurement, **including failures**, goes in `docs/experiments/<letter>-<yyyymmdd>-<topic>.md`: date and time, who, goal, setup (device, model, version, commands), data (number of clips, kit mic or phone), result **with denominators**, verdict, next step. E compiles M2 from these. Commit and push your branch at least every 2 hours.

**Deadlines (from the organisers' PDF, as written; no grace period):**
| When | What |
|---|---|
| 10:00 PM, 10 Oct | M1 approach PDF: **done** (`deliverables/M1.pdf` is on the submission repo's `main`) |
| **4:00 AM, 11 Oct** | `deliverables/M2.pdf` (2 pages: approach changes since M1, **experiments run and results**, testing progress, plan for the remaining time) **plus all code written so far pushed to `main`** |
| **11:00 AM, 11 Oct** | Final: full code, `README.md` (setup and run steps only, plus links to external weights; do not explain the solution), MIT `LICENSE`, `deliverables/final_report.pdf` (max 3 pages), demo video (max 4 min, screen recording with voiceover, Google Drive "Anyone with the link"), code zip (exclude virtual environments, datasets, model weights) |
| 11:30 AM, 11 Oct | Slides as PDF, through the slides form |

**Submission repo:** `IIIT-ECell/megathon26-readyornot` (private; only `main` is evaluated, and only the latest commit at each deadline). It is **separate from this team repo**. Its README has empty **Build** and **Run** sections that must be filled before the final.
**Double-blind (all PDFs and the video):** put **"Team 087"** at the top of every PDF; **no team name, member names, college or city names** anywhere: not in slides, video, window titles, terminal prompts, usernames, hostnames or file names visible on screen. Run `tools/blindcheck.sh <file.pdf>` (E provides it) before every submission.
**Proposed (not an organiser rule):** E drafts the PDFs from `docs/templates/report-template.tex`; **A pushes**. A mirrors **only** `app/`, `core/`, the Gradle files, `tools/`, README and LICENSE into a clone of the submission repo, then commits and pushes `main`. Research and planning docs (which name the college) stay in the private team repo.

## 10. Starter prompt for your Claude
> Read `docs/handoff/C-core-rules.md`, then the files it lists under "Read first". I am person C. Work on branch `c/core-rules` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 11. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

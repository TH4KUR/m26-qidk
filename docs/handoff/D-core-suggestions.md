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

## 7a. Stubs and contracts you own (publish the fake first)
A needs your API for the chips UI long before your real engine is ready, so **publish `FakeSuggestionEngine` by hour 1.5**.
```kotlin
// :core
interface SuggestionEngine {
    fun suggest(state: ReportState, transcript: String): List<Suggestion>   // pure, deterministic, never throws
    fun accept(state: ReportState, s: Suggestion): ReportState              // sets USER_CONFIRMED, keeps the original in the journal
    fun reject(state: ReportState, s: Suggestion): ReportState
}
```
- **`FakeSuggestionEngine`:** returns the "heard *conference room to* -> Conference Room 2" chip for the deck-example fixture and nothing otherwise. Add `fixtures/suggestions/*.json` (serialised `Suggestion`s, including a witness chip and a hedge chip) so A can render chips with no logic behind them.
- **`HedgeDetector` dependency on C:** code against the interface; use C's rule fake until the real detector lands (hour 6).
- **`FakeSiteDirectory`:** a small in-memory site (3 rooms, 2 assets, 2 people, with aliases) for unit tests; the real `site.json` is for the demo.
- **`InMemoryReportRepository`** alongside the file-based one, so A and E can run without storage.
- **Whisper-style mistake fixtures** in `fixtures/transcripts/` (shared with C): "conference room to", "Vindya see four", "chiller" as "chilla". Use real mistakes from E's recordings as they arrive and add them.
- **Contract tests** for `SuggestionEngine`: never throws; **never blocks Submit** (a report with pending suggestions still submits); accept keeps the original; reject leaves the field unchanged; a place not in the directory yields no suggestion; same input gives the same output.

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
**Workflow (settled, D22):** all development, branches, research and planning stay in **this private repo (`m26-qidk`); only A merges to `master`.** The submission repo gets **clean snapshots only**, made by **A** with `tools/mirror_to_submission.sh <clone> [--apply]` (dry run first). The script exports the committed `app/`, `core/`, Gradle files and `tools/`, strips audio, models and binaries, scans contents and file names against `tools/blindcheck.words`, and never touches README, LICENSE or `deliverables/`; it never commits or pushes. **Nobody except A pushes to the submission repo.** E builds the PDFs from `docs/templates/report-template.tex` and checks them with `tools/blindcheck.sh`; A copies them into `deliverables/`. Keep names, the college and anything identifying out of code, comments, strings and file names, because they will be mirrored and scanned.

## 10. Starter prompt for your Claude
> Read `docs/handoff/D-core-suggestions.md`, then the files it lists under "Read first". I am person D. Work on branch `d/core-suggestions` only, in my folders, test-first. Do my first-90-minutes tasks now. Ask me before changing shared types or touching another person's folders. Do not run `adb` against the kit unless I say I am in a booked slot.

## 11. Report back to the lead (A) when
- you finish a task (say what passed and what did not, with numbers, not impressions);
- you are blocked for more than 20 minutes;
- you need a shared-type change or find something in the spec or plan that is wrong or untested.

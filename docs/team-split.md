# Team split: five people, one kit

Date: 2026-10-10. Status: proposed; the open decisions in §7 need team agreement. Owners refer to the tasks in `docs/superpowers/plans/2026-10-10-incident-assistant-qidk.md` (Addendum A) and the decisions in `docs/superpowers/specs/2026-10-10-incident-assistant-design.md` (D1–D21). Hour figures are estimates.

## 1. Constraints that shape the split

- **Five people, all with Claude Code.** Only two laptops are strong enough for Android Studio and the emulator-free build loop: **A** (also holds the kit) and **B**. **C, D and E** have light laptops, so their work is plain Kotlin/JVM in the `:core` module (D21), Python scripts, and documents. `:core` builds and tests with JDK 17 + Gradle only.
- **One kit**, plugged into A's laptop. Only A installs builds on it. B gets short, booked kit slots (or wireless adb with Wi-Fi on during development; airplane mode only for the demo and any offline test).
- **A's laptop disk is 99% full (1.7 GB free).** First job: free at least 25 GB (Android Studio about 3 GB, SDK/NDK about 5 GB, Gradle caches, models).
- **Cloud machines (development only, D20):** a CPU build VM (8 vCPU, 32 GB RAM, large disk, **Ubuntu 24.04.4** as provisioned, user `ubuntu` with passwordless sudo, about ₹17/hour at 100 GB) owned by **A**; the RTX PRO 6000 and H100 machines as a shared test server used mainly by **E** and by **B** for the accent contest. **Nothing on them runs in the demo.** Never put the confidential kit guide or any credential on them. Pause or delete a machine when idle.

## 1b. Workflow: develop here, submit there (settled)

- **Development** happens only in this private repo, `m26-qidk`: one branch per person, **only A merges to `master`**. Research, plans, handoffs and evaluation data stay here.
- **Submission** goes to `IIIT-ECell/megathon26-readyornot`, branch `main` (the only branch the organisers evaluate, at the latest commit of each deadline). It receives **clean snapshots only**, never this repo's history or branches.
- **How:** A runs `tools/mirror_to_submission.sh <path-to-submission-clone>` (dry run), then with `--apply`, reviews the target, then commits and pushes. The script exports the committed `app/`, `core/`, Gradle files and `tools/`; strips audio, models and binaries; scans contents and file names against `tools/blindcheck.words`; refuses a wrong origin or a git identity that matches a name or college pattern; never touches `README.md`, `LICENSE` or `deliverables/`; and **never commits or pushes**.
- **PDFs** (`M2.pdf`, `final_report.pdf`) are built by E, checked with `tools/blindcheck.sh`, and copied by A into the submission repo's `deliverables/`. Do not delete earlier deliverables.
- **Rehearse the mirror once before the M2 deadline** so a scan failure is found early, not at 3:59 AM.
- **Nobody except A pushes to the submission repo.**

## 2. The roles

| | Person | Laptop | Main work | Needs the kit? |
|---|---|---|---|---|
| **A** | Team lead (you) | Strong, kit attached | Gradle skeleton, GenieX adapter, **sherpa-onnx QNN build on the build VM**, UI, proof strip, merging, kit | Yes |
| **B** | Speech | Strong | Speech engines (WhisperKit baseline, AI Hub fallback), SenseVoice and YamNet integration using the libraries A builds, accent contest, hands-free loop | Booked slots |
| **C** | Rules | Light | `:core`: domain types, parser, validator, tense/aspect/modality cues, priority advisor, severity suggestion | No |
| **D** | Suggestions and flow | Light | `:core`: site gazetteer, suggestion engine, follow-up planner, storage, safety advice | No |
| **E** | Data and evaluation | Light | Recordings, gold labels, scorer, Judge Bench, LLM candidate contest, demo script and pitch | Only for final on-device runs |

## 3. Per-person briefs

### A: lead, Android, kit
- **Hour 0–1.5:** free 25 GB of disk, install Android Studio, JDK 17, platform-tools; set the kit clock; record a 3 s mic test; run the GenieX reference app with Qwen3-1.7B (S1, task A1). **By hour 1:** push the Gradle skeleton (`:core` + `:app`) so C and D can code against it.
- **Hour 1.5–6 (the key spike, A2):** set up the build VM and build sherpa-onnx with QNN; run `sherpa-onnx-qnn-SM8750-binary-whisper-small` on the kit with `language=hi` and compare against a CPU reference on a golden WAV. **Hour 6:** decide go/no-go and tell B. Hand B the built `libsherpa-onnx-jni.so`, the QNN libraries and a working config. Fallback: engine 2b (AI Hub Whisper, which B prepares in parallel).
- **Hour 6–9:** coexistence test of the sherpa-onnx libraries and the GenieX AAR in one APK, never `pickFirst` on QNN libraries (A2b). In between: GenieX adapter (Task 8), UI (Task 11), proof strip and suggestion chips (A9). **Hour 9:** run the integration checkpoint on the kit.
- **Hour 10 onward:** polish, merge, sleep shifts, final freeze (hour 19), rehearsals.
- **Rules:** A is the only person who merges to `master`, the only one who installs on the kit, and the only one who pushes to the submission repo (section 1b).

### B: speech engines
- **Hour 0–1.5:** start the downloads for the accent contest (stock `tiny.en`, `base.en`; `theshivam7` Tiny and Small; accept their terms on Hugging Face with your own account first); build the WhisperKit baseline with the **corrected** Gradle line (`com.qualcomm.qti:*:2.44.0`, profile `QUALCOMM_TINY_EN`) behind `SpeechEngine`.
- **Hour 1.5–6:** finish the accent contest; prepare **engine 2b**, Qualcomm AI Hub's public multilingual Whisper binaries with a hand-written decode loop that seeds the language token, as insurance in case A's sherpa-onnx build fails. Start SenseVoice and YamNet as `Signal` producers (A4) against the prebuilt **non-QNN** sherpa-onnx AAR (CPU) so you are not blocked [INF].
- **Hour 6–9:** swap to A's QNN libraries when A delivers them, and run SenseVoice and YamNet through them (A4); if A reports no-go, SenseVoice stays on the CPU and is labelled so.
- **Hour 10–14:** hands-free loop (A10), noise and mic tuning, accent-contest results to E for the Judge Bench.

### C: rules in `:core`
- **Hour 0–1.5:** domain types from the spec (§4, §4b) and the JSON contract; unit-test skeletons; turn the problem-statement examples into test cases.
- **Hour 1.5–9:** lenient JSON parse and tool-call parsing, normalisers, severity suggestion, time resolver, grounding validator (Tasks 2–4). Tense/aspect/modality detector with English and Hindi cue lists (A5). Priority advisor and `priority_rules.json` (A5): **signals can only raise priority**.
- **Hour 10–14:** tune rules against E's recordings. Get a Hindi speaker to review the Hindi cue lists.

### D: suggestions, flow and storage in `:core`
- **Hour 0–1.5:** collect real IIIT-H building, room and equipment names (and an alias list) into `site.json`; if the team prefers, use a made-up site instead.
- **Hour 1.5–9:** suggestion engine (A6): homophone table, Double Metaphone plus Jaro-Winkler, uncertainty cues, witness-versus-involved rule, accept/reject events, original kept; report merge, mandatory policy, follow-up planner (Task 5); report storage and export (Task 7).
- **Hour 16–19:** spoken safety advice table and the "call 112" line.

### E: data, evaluation, pitch
- **Hour 0–1.5:** a recording plan. The **gold set is recorded on the kit itself** (booked session with A, using the exact mic setup and distance of the demo), because the demo uses that microphone path and the mic path is still unproven. `tinycap` (`/system/bin`) and the Sound Recorder app (`com.android.soundrecorder`) are present on the kit [V, listed, not tested]; once the app has a recorder mode, use that (same 16 kHz mono capture path as the app). Every teammate records about 8 incident reports (clean and stressed takes, accents, fan noise, six self-corrections), converted to 16 kHz mono WAV. Extra clips may be recorded on phones in parallel but must be labelled as phone audio. Type the correct answers for each (Task 0b).
- **Hour 1.5–9:** scorer for word error rate and per-field accuracy; run the LLM candidate contest (Qwen3-1.7B, Gemma 4 4B, NuExtract-2.0-2B) on the GPU server (S3); prepare the Judge Bench data.
- **Hour 10–14:** on-device evaluation with A (Task 12); prompt iteration.
- **Hour 16–21:** final numbers, demo script, slides, backup video.

## 4. Who needs what from whom

| From | To | What | By |
|---|---|---|---|
| A | all | Gradle skeleton with `:core` and `:app` | hour 1 |
| C | all | Domain types and contract in `:core` | hour 1.5 |
| A | B | sherpa-onnx QNN libraries and working config, or the no-go | hour 6 |
| B | E | accent-contest WER table | hour 9 |
| E | B, C, D | recordings and gold labels | hour 4 |
| D | A | suggestion chips API | hour 9 |
| all | A | everything merged and the slide example working | hour 9–10 |

For the first 90 minutes nobody depends on A's skeleton: everyone starts with prep work that needs no shared code.

## 5. Working rules

1. **Branches:** one branch per person (`a/…`, `b/…`, `c/…`, `d/…`, `e/…`). A merges at hours 6 and 9 and after. No force-pushes.
2. **Folder ownership:** `:core` domain/rules = C, `:core` suggestions/storage = D, speech and signals = B, UI/adapters = A, `eval/` and `tools/` = E.
3. **Interface changes** go through C (types) and are announced in the team chat before merging.
4. **Tests first** for `:core` code; the commands are `./gradlew :core:test`.
5. **Each person's Claude Code starter prompt:** "Read `docs/team-split.md`, the spec (D1–D21) and the plan (Addendum A). I am person X. Do my tasks test-first, stay in my folders, and ask before changing shared types."

Each person's handoff file is on their own branch: `docs/handoff/<letter>-<role>.md` (branches `a/lead-kit-app`, `b/speech-engines`, `c/core-rules`, `d/core-suggestions`, `e/data-eval`).

## 6. Before the event
- Accept the Hugging Face terms for `theshivam7/whisper-*-aesrc-indian-english` and their `-seeds` repos on each account that will download them.
- Install the Google offline voice packs `en-IN`, `hi-IN`, `te-IN` on the kit with network on, then test in airplane mode.
- A: free disk, install tools, and launch and set up the build VM. E: start recording.
- Ask the organisers whether pre-written code and pre-collected recordings are allowed.

## 7. Open decisions (the team has not agreed these yet)

1. **Which plan is authoritative: settled.** Our spec and plan plus Addendum A. The teammate's alternative plan was removed from the repo (it remains in git history); the table below records which of its ideas we adopted.
2. **Hindi/Telugu:** core feature (codex plan) or experimental lane gated at hour 16 (our D19)? Stock Whisper-Small is unusable for Telugu and weak for Hindi without fine-tuned weights.
3. **Qualcomm Package Manager login:** does anyone have a working one today? If not by hour 3, the Voice AI SDK route is dead (codex plan depends on it).
4. **Severity:** our D18 makes the keyword rule a suggestion, not a fill (closer to the codex plan than our earlier D8).
5. **`:core` module (D21)** instead of one `app` module (D1).
6. **Fine-tuning** on the H100 (Whisper on Indian English): out of scope unless the team agrees and the organisers allow it; the fine-tune-to-NPU path is untested.
7. **Push policy: settled.** Dev branches are pushed to `m26-qidk`; only A merges to `master`; the submission repo gets snapshots only (section 1b).
8. **A's load.** With the sherpa-onnx build (A2, A2b) on A, A's estimated work is about 24 h of a 24 h event: A1 3 h, scaffold 1 h, A2 6 h, A2b 2 h, Task 8 4 h, A9 8 h. Suggested relief, not yet applied: move **Task 8 (GenieX adapter)** to B, who is freed from the build and also has a strong laptop, and keep the kit on A. Decide before hour 0.

### Reconciliation with the removed alternative plan

| Codex idea | Status here |
|---|---|
| `REPORTED` vs `CONFIRMED` labels, immutable reducer, per-field versions | **Adopt** (D16, §4b types) |
| Negation and correction tests across audio boundaries | **Adopt** in `:core` tests |
| No severity guessing from keywords | **Adopt** (D18) |
| Preserve temporal precision, ask for missing date/time | **Adopt** in the time resolver |
| English + Hindi/Hinglish as minimum | **Not adopted:** experimental lane (D19) |
| Voice AI SDKs and accelerated MeloTTS as the only compliant path | **Not adopted:** gated login; Android TTS with disclosure stays (D11) |
| Live filling during narration, crash recovery, 10-minute sessions, 90+ hand-labelled scenarios | **Not adopted** for 24 hours; Judge Bench with about 40 clips instead |
| "Do not change spoken names, no name lists" | **Compromise:** the gazetteer only *suggests* (D16) |
| Block "done" when any gate fails | **Not adopted:** report targets and the real numbers |

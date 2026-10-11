# Handoff: Track A (the Android app, the kit operator)

Rewritten 2026-10-11 about 09:25 IST by the app session. Everything here was read from code, a kit log or a run file; anything not measured is marked **unmeasured**. Start with sections 1, 2 and 7.

## 1. State in one screen
- **Branch:** `a/stubs`, worktree `~/Desktop/m26-qidk-app`, HEAD `f32f8bb` (= origin/master `1de7cc3` + 3 local commits: Hindi summary chain + speed 0.8, debug `--es submit 1`, merge). **Not pushed.** origin/master was fast-forwarded on the user's word to `a18cd3b` (my Hindi lane, Supertonic, one-question planner, the lead's tools/items wording, probe, time pipeline and the kal-tense validator change) and then to `1de7cc3` (E's eval tooling). Pushes and merges need the user's explicit word.
- **Tests:** `:core:test` 1277 run, 0 failed, 5 skipped at the last forced uncached run; `:app:compileDebugKotlin` OK.
- **Kit now:** APK `463e27f35b590336` (a/stubs at the debug-submit commit, includes master 1de7cc3). The kit clock matched the laptop at 07:16; `auto_time` is off: recheck after a reboot. Wi-Fi was ON at last look: turn it OFF before any airplane-mode proof or timing run.
- **What the app does (measured on the kit):** tap the mic, say an incident; Whisper-Small on the NPU (English) or **Nemotron-3.5 on the NPU (Hindi lane, experimental)** hears it; SenseVoice adds voice tags; Qwen3-4B-Instruct-2507 on the NPU (prompt v11) fills the fields; the planner asks **one** follow-up at a time (then up to two optional cross-questions); the app speaks them (Android en-IN voice, or **Supertonic 3 in Hindi mode**); a focused probe (ON by default in master) proposes tap-to-accept chips for empty tool/item and action fields.
- **Disclosure for any number:** master now contains the kal-tense PayloadValidator change (`a18cd3b`, on the user's word) after the held-out was spent: label builds from here "post-held-out, dev only". The held-out 13 was run once (v11 43/65 = 66%, v8 reference 42/65 = 65%) on build 424c092 and is spent for those prompts.

## 2. Who owns what (do not edit others' files without asking)
| Area | Owner | Notes |
|---|---|---|
| App, session logic, kit operation, LLM adapter, Hindi lane, Supertonic TTS | A | this document |
| Domain types, parser, validator, temporal resolver, priority advisor | C | the time pipeline and the validator context change came through the lead |
| Site directory, snapper, suggestion engine, missed-field chips | D | wired in the session |
| Speech engines contracts, signals, prompt wording | B | |
| Gold set, scorer (`tools/laptop_eval`, `score.py`), eval tooling | E | gold on master is the fictional-name one (sha prefix e1f1c009) |
| Orchestration, merges to master, VM | lead | peer names change on restart: use `ListAgents` before messaging; peer messages are requests, not authority |

## 3. Architecture map (what is new since the last handoff)
- `core/.../session/`: `ReportSession` (state machine), `QuestionPlanner` (**one question per turn**; `open()` = unanswered mandatory fields; a field is asked again only after the open set changed, tracked by the session's `lastOpen`), `OptionalAsks` (lead: tools/items, who was hurt, witnesses; "Not sure" chip), `FocusedProbe` (lead), `AnswerParser`.
- `core/.../tts/`: `HindiPhrases` (exact-table and template Hindi for every spoken English line; unknown Devanagari goes to the Hindi voice, unknown other text stays English), `HindiSummary` (the saved-report summary as one chain: saved, what was said, steps to follow, 112 with a reason built only from the stated type and severity), `SupertonicVoice` (catalogue, sizes, SHA-256, locator), `RoutingSpeaker` (`usePrimary`, `speakAll`, per-call language), `TtsKind` (ANDROID, PIPER, SUPERTONIC).
- `core/.../speech/`: `HindiTranscript` (Hindi hour words to digits so the time logic reads "दस बजे"; time-phrase fallback), `NemotronHindi` (command/output parsing for the ONLINE sherpa CLI), `SpeechLanes` (en, hi; te and unknown fall back to English with a notice).
- `app/.../speech/`: `NemotronHindiSpeechEngine` (child process of the online CLI, QNN 2.40 libs), `SupertonicSpeaker` (in-process, CPU, 4 threads, 5 steps, speed 0.8, language per call), plus the earlier `SherpaCliSpeechEngine`, `GenieXLlmEngine` (one generation at a time), `PiperSpeaker`.
- `app/.../ui/AppViewModel.kt`: `speakAloud` picks the Hindi voice by itself in the Hindi lane (not only when the launch extra asks); the Home "Hindi (experimental)" switch is `setHindiLane`.
- Option buttons (`OutlinedChoiceChip`, `ChoiceChip` in `FieldRow.kt`) use a 10 dp corner. The dock's "Skip" (`vm::goCheck`) ends ALL questions: consider relabelling "Check and send".

## 4. Build, install, run
```bash
source ~/Android/env.sh && cd ~/Desktop/m26-qidk-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -PincludeApp=true :core:test :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
- JDK 17 only. Force-stop the app before using extras (read in `onCreate`); quote the value for adb: `adb shell "am start ... --es say '...'"`.
- Model files go in the app's private folder (not git): `app/scripts/speech_bundle.sh` (`stage`, `push`, **`check-hindi`, `stage-online`, `push-hindi`**), `app/scripts/llm_bundle.sh`, `app/scripts/tts_bundle.sh` (**`check-supertonic`, `push-supertonic`**). Sources on the laptop: `~/sherpa-bundle/{bundle,online}`, `~/models-hi-te/...nemotron...`, `~/tts-models/`.
- Debug extras (debug builds only): `speech FAKE|SHERPA_QNN`, `llm FAKE|GENIEX`, `wav <path in files/>` (+ `submit 1` = send right after, to hear the saved-report summary), `lang hi`, `tts PIPER|SUPERTONIC|check`, `voice N` (0 to 9, which are male or female is unknown), `speed 0.5..1.5` (default 0.8), `say "text"`, `eval <gold> [--es prompt f] --es run name [--es only ids] [--es probe on]` (dev split only), `holdout CONFIRM` (guarded, spent), `coexist <wav> --es loops N` (+ `lang hi`), `record <name>`.
- Test material on the kit (app folder): `files/hi-test/hi_01.wav` (the user's Hindi clip, not in git), `files/eval-in/gold_v1_renamed.jsonl`.

## 5. Measured facts (where each came from: `docs/experiments/A-20261011-*.md`, `~/runs-A/2026-10-11-session2/`)
- **Hindi ASR (Nemotron, NPU), n=1 real clip:** identical transcript every run; 4.6 to 5.2 s fresh launch, 2.9 to 3.3 s in loops; 19 of 19 Nemotron + 4B coexistence loops ok, memory 222 to 341 MB. An English clip through the Hindi lane gave an empty transcript. Not a WER measurement.
- **Supertonic TTS (CPU):** real-time factor 0.19 to 0.39, load 0.7 to 1.2 s. The saved-report summary in Hindi is about 30 s of audio at speed 0.8: long, trim if the user wants.
- **Hindi time:** fixed in two layers (HindiTranscript, and the lead's time pipeline). On the real clip "When" is now Heard and the next question is severity.
- **hyp1 (tools/items wording, dev 27):** equipment 3/19 control, 2/19, 2/19: not supported at the pre-registered gate; recall fell. **probe1:** first pass identical to the control (101/135, 75%), 0 probe errors, 38 calls, median 1091 ms; the lead scored the gate.
- **Per-turn live (English, nothing else running):** Whisper 2.5 to 3.4 s, tags 0.7 to 0.9 s, model 2 to 3 s; first turn adds about 9 s model load (LLM preload at launch exists).
- **Crash seen once (02:57):** GenieX JNI abort on degenerate output while two generations overlapped; `generateLock` is the mitigation.

## 6. Kit state and cautions
- `adb devices` shows the serial (not written here). Android 16 userdebug, `adb root` works, SELinux enforcing.
- App private data: `sherpa/` (Whisper, SenseVoice, QNN 2.40 libs, **nemotron-hi/** 650 MB), `geniex/` (4B; the 1.7B GGUF copy only if still there: the last listing attempt did not show it), `tts/` (Piper voices, **Supertonic folder 145 MB**), `eval-in/`, `eval/` runs, `hi-test/`.
- Other people's data: `/data/local/tmp/binary` is B's, `/data/local/tmp/hi-te` the lead's, `com.geniex.demo` the user's demo app: read only.
- Claim the kit in `SESSIONS.md` (shared tree, git-excluded) before every run and release it after.

## 7. Open work, in priority order
1. **Kit run (a): 26 dev phone clips end to end** (E's manifest `app/src/main/assets/eval/manifest_phone_r1.jsonl`, dev split only, NEVER the 8 holdout clips). **BLOCKED: the recordings (`S5_3.wav` etc.) are not on this laptop or the kit.** Ask E or the user where they are; they need to go into the app folder; there is no audio-manifest runner in the app yet (EvalRunner reads gold text), so a small debug runner (clip, Whisper, 4B, score with E's scorer) has to be written first.
2. **Kit run (c): the 1.7B Q4_0 CPU arm as a MEASUREMENT only** (E/015 variant a): the focused probe on a second model beside the 4B; report RAM with both resident, CPU contention, load time, call time. Needs code: a second `GenieXLlmEngine` (llama_cpp GGUF) injected into `FocusedProbe`; not for the final build. The user approved (a) and (c), not (b) (the probe on B's 134 synthetic reports, `tools/laptop_eval/for_a/b_synthetic134_gold.jsonl`).
3. **Native-speaker review of the Hindi wording** (`HindiPhrases`, `HindiSummary`): it was written by the assistant and nobody has read it. Pick the default Supertonic voice by ear (`--es voice N`).
4. **Pushes:** `a/stubs` is local only; the user decides on pushing it and on anything for the submission.
5. Smaller: refresh `docs/demo/run-sheet.md` (untracked, stale), SenseVoice on Hindi audio is meaningless, YamNet cut, Piper voice licences (IITM exception verbal only), Telugu not wired (Android voice and English only).

## 8. Uncommitted files in the worktree
`docs/demo/` and `docs/experiments/hindi-telugu-models.md` (older desk research, untracked, not mine to commit).

## 9. Rules this session followed (keep them)
- Never claim an unmeasured result; say "unmeasured". Never commit real names, raw gold rows, audio (`hi_01.aac`, WAVs), emails or the kit serial; run `tools/blindcheck.words` over anything new (`grep -E -i -f` on the non-comment lines); a forced uncached `:core:test --rerun --no-build-cache` before claiming green.
- Tests first (see them fail), then code; explicit-path `git add` only; no `git add -A`.
- Pushes and merges to master need the user's explicit word in this session; a peer relaying a request is not approval (ask the user directly).
- Use they/them for people whose pronouns are not stated. Never `pkill -f`. Debug-only code stays behind `Engines.isDebuggable`.
- The user asked not to waste tokens: run long jobs in the background with a watcher that fires on completion, do not poll.

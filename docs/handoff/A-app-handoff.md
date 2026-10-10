# Handoff: Track A (the Android app, the kit operator)

Written 2026-10-11 about 03:50 IST by session A. Everything here was read from the code, the kit or a run log; anything not measured is marked **unmeasured**. Start with section 1 and section 7.

## 1. State in one screen

- **Branch:** `a/stubs`, worktree `~/Desktop/m26-qidk-app`, HEAD `f377d88`. **38 commits are local only** (`origin/a/stubs` is at `38b8dc9`). **Nothing may be pushed without the user's yes**; the lead wants the commits on origin by about 09:30 for the 11:00 final snapshot.
- **Tests:** `:core:test` 815 run, 0 failed, 3 skipped. The app compiles and the APK of `f377d88` is installed on the kit.
- **What the app does on the kit today (measured):** tap the mic, say an incident, real **Whisper-Small on the NPU** hears it, **SenseVoice** adds voice tags, real **Qwen3-4B-Instruct-2507 on the NPU** fills the fields, the planner asks at most two follow-ups, the app speaks them with the offline en-IN Google voice, you review and send. A plain launch (no `--es` flags) uses the real engines whenever their model files are in the app's folder, otherwise the labelled SIMULATED ones.
- **Latest model numbers** (E's 27 dev cases, 34 turns, one run each, near-greedy): app as it is, mandatory fields precision 89% / recall 65%, overall 64 of 135 gold values correct. With the lead's unmerged recall patch (`lead/recall-salvage` 71ad07d, throwaway branch `a/try-salvage`): **96% / 86%, overall 88 of 135**. Details and caveats: `docs/experiments/A-20261011-recall-patch-on-kit.md`. The held-out 13 cases have **never** been run.

## 2. Who owns what (do not edit others' files without asking)

| Area | Owner | Notes |
|---|---|---|
| App, session logic, kit operation, LLM adapter | A | this document |
| Domain types, tool-call parser, validator, temporal resolver, priority advisor | C | the recall patch changes C's files; C reviews it, the lead merges it |
| Site directory, snapper, suggestion engine | D | wired into the session by A in `f377d88` |
| Speech engines contracts, signals | B | B also owns **prompt wording** now (lead's messages 012/013) |
| Gold set, scorer | E | `gold_v1.jsonl`, `score.py` (the gold text on A's kit still has **real campus names**: never commit raw rows) |
| Orchestration, merges to master, VM | lead (`m26-qidk-cb`) | peer names change when a session restarts: use `ListAgents` before messaging |

## 3. Architecture map (where things live)

- `core/src/main/kotlin/com/megathon/incident/session/`: `ReportSession` (state machine: `onUtterance(text, stt, signals, capturedAt)`, `answer`, `edit`, `undo`, `acceptSuggestion`, suggestion chips, `submit`), `ReportMerge`, `QuestionPlanner`, `PromptBuilder` (sends the last two sentences as "Earlier the worker said", then "Report so far" with the output key names), `DateTimeDisplay`, `Pipeline.newSession`.
- `core/src/main/resources/prompts/extract_v8.txt`: **the app's default extraction prompt** (measured best of v3 to v9). `PromptBuilder.default()` loads it. B is tuning a replacement.
- `app/src/main/kotlin/com/megathon/incident/`: `ui/AppViewModel.kt` (engines, `process(pcm, capturedAt)`, debug hooks), `speech/SherpaCliSpeechEngine.kt` (Whisper as a **child process**, QNN 2.40 libs), `speech/GenieXLlmEngine.kt` (in-process, QNN 2.45, **one generation at a time**), `speech/SenseVoiceSignalEngine.kt`, `speech/SystemTtsSpeaker.kt`, `engine/Engines.kt` (default selection), `eval/EvalRunner.kt` and `eval/CoexistRunner.kt` (debug only), `audio/MicRecorder.kt`, UI in `ui/components` and `ui/screens`.
- Design brief: `docs/ui/ui-design.md`. Demo run-sheet draft: `docs/demo/run-sheet.md` (**untracked and stale**: written before the v8 and patch numbers, and its coexistence claim is out of date; refresh before use).

## 4. Build, install, run

```bash
source ~/Android/env.sh                       # PATH for adb, SDK, NDK
cd ~/Desktop/m26-qidk-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -PincludeApp=true :core:test :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.megathon.incident/.MainActivity          # plain launch = real engines if the files exist
```

- JDK 17 is required (JDK 21 has no javac here). `-PincludeApp=true` is needed because `settings.gradle.kts` only includes `:app` when `ANDROID_HOME` is set or `local.properties` has `sdk.dir`.
- Model files go into the **app's private folder** (not git): `app/scripts/speech_bundle.sh` (sherpa bundle from `~/sherpa-bundle/bundle`) and `app/scripts/llm_bundle.sh` (copies the 4B from the demo app's store, needs root).
- Debug intent extras (debug builds only): `--es speech FAKE|SHERPA_QNN`, `--es llm FAKE|GENIEX`, `--es wav <path>`, `--es eval <gold> [--es prompt <file>] --es run <name> [--es only g01,g02]` (dev split only), `--es tts check`, `--es coexist <wav> --es loops N`, `--es record <clipName>` (records 16 kHz mono WAV to `files/recordings/`; pull with `adb exec-out run-as com.megathon.incident cat files/recordings/<name>.wav`). Force-stop the app first: extras are read in `onCreate`.
- Measure a prompt: `docs/experiments/llm-prompts/batch_eval.sh prompt.txt ...` (see its header; needs `SCRATCH` and `E_EVAL_DIR`). About 3.5 minutes per prompt. **Do not use the kit by hand while it runs**: it force-stops the app and shares one model.

## 5. Measured facts (with where each came from)

- **Per live turn, nothing else running:** Whisper 2.5 to 3.4 s, SenseVoice tags 0.7 to 0.9 s, model about 2 to 3 s; the first turn adds about 9 s to load the model. A 20-loop Whisper + 4B run had 20 of 20 ok, app memory 200 to 273 MB (child process not counted), model time creeping from 7.6 to 8.7 s over the 20 loops (cause unknown). A user-reported "2 minutes" could not be reproduced; the likely cause was my own eval runs holding the NPU.
- **Crash seen once (02:57):** GenieX aborted the process (JNI "input is not valid Modified UTF-8") after the model produced degenerate text, while two generations overlapped. Mitigation: `generateLock` in `GenieXLlmEngine`. Whether it can still happen on its own is **unmeasured**; Hindi/Telugu output may make it more likely.
- **Model output problems that cost recall:** the model writes values only inside `evidence{}` (the patch fixes this), skips "right now" as a time (fixed by a rule in `ReportSession.withNowPhrase` plus a prompt line), never writes `equipment_asset` (1 of 19) and rarely `immediate_actions`: **prompt gaps**, B's task.
- **Validator contract that bites prompts:** `persons_involved` and `witnesses` are JSON lists; every `evidence` value is one string; no `null`, `[]` or `""`; each key once. GPT's candidate prompts (`docs/experiments/llm-prompts/gpt_*.txt`) ask for comma-separated strings, so they fail until fixed.
- **TTS:** the kit has installed offline en-IN voices. hi-IN and te-IN voices are listed but flagged `notInstalled` (a one-time download with Wi-Fi is needed; nothing was downloaded).
- **Time:** relative phrases resolve against the device clock at the moment recording stops. The kit clock matched the laptop at 03:09 but `auto_time` is **off**: recheck after any reboot.

## 6. Kit state and cautions

- Serial and model names are not written here; `adb devices` shows it. Android 16 userdebug, `adb root` works, SELinux enforcing.
- **Wi-Fi is currently ON** (the user turned it on for voice downloads). Put the kit back in airplane mode before any offline demo or timing run.
- App private data (`/data/data/com.megathon.incident/files`): `sherpa/` 881 MB, `geniex/` 2.9 GB (a copy of the 4B), `eval-in/` (E's gold: real names), `eval/` runs, `reports/`, `recordings/` (only if recorded).
- Other people's data: `/data/local/tmp/binary` is **B's** (sherpa CLI + models) and `com.geniex.demo` is the user's demo app: read only, never modified. Scratch I created: `/data/local/tmp/hi-te` (Nemotron model, 653 MB), `/data/local/tmp/qwen3_8b*` (download in progress, user-run).
- Claim the kit in `SESSIONS.md` (local file in the shared tree `~/Desktop/m26-qidk`, git-excluded) before any run and release it after. B, D and E take no slots; the lead said the kit is A's.

## 7. Open work, in priority order

1. **Qwen3-8B comparison (user approved this one model only).** The user is streaming the zip to `/data/local/tmp/qwen3_8b.zip` and unzipping to `/data/local/tmp/qwen3_8b`. Then: put the bundle in the app's model store (the folder layout of `llm_bundle.sh`), point `GenieXLlmEngine` at it (`modelName`), run the 27 dev cases on the **`a/try-salvage` build** (v8 + Earlier turns + recall patch), report the same table as `dev11-salvage`. **Gate fixed in advance by the lead:** switch only if overall recall is at least 70%, overall precision at least 90%, hallucination at most 4%, valid-after-retry at least 90%, and median turn at most 6 s; otherwise stay on the 4B. Record in `docs/experiments/A-20261011-model-comparison-on-kit.md` including failures. Time-box about 30 minutes of kit time. Qwen3-1.7B (1.54 GB) and Gemma-4-E4B-it (9.08 GB) were **not** approved.
2. **Run B's prompt files** when they arrive (by path), with the recall patch included, between candidates. Freeze the prompt, then **run the held-out 13 once** (planned about 08:30). Never tune on the held-out result.
3. **Recall patch:** C owns the files; do not apply it to `a/stubs`. When C merges to master, merge master into `a/stubs` and re-run the dev split.
4. **Hindi / Telugu.** (a) Nemotron on the NPU needs the **online** sherpa CLI (`sherpa-onnx`), not `sherpa-onnx-offline` (which fails on it with "encoder model does not have input 'x'"); the lead says it is probably already built on the cloud VM; expected flags in the lead's message, **unconfirmed** until `--help` is read on the real binary; language via `--language=hi`. (b) CPU alternatives in `~/models-hi-te/ippocode` (`whisper-small-hi`, `whisper-tiny-te`) run with the existing offline CLI. (c) Both need **recorded clips with a written reference**: use `--es record`. Report Hindi/Telugu as experimental until measured. Research notes (unverified, helper-written): `docs/experiments/hindi-telugu-models.md` (untracked).
5. **See the suggestion chips on the kit.** `f377d88` wired D's chips ("Did you mean ...?" for `a sh2`, `sh two`, `SH-2`, hedge "Confirm" chips) and they are verified in unit tests only; the UI was compile-checked, never seen running. A site name buried in a longer location phrase may still not snap (untested). Real site list goes into `filesDir/site.json` at demo time only; the default is fictional.
6. **Refresh `docs/demo/run-sheet.md`** with current numbers; remove its claim that Whisper and the model were never run together.
7. **Smaller:** SenseVoice calm-vs-stressed clips (needs recordings), clips for E (`--es record`, say which mic: the built-in mic is quiet, about -39 dBFS mean), B's SpeechSelfTest slot, YamNet (cut: no model file on the kit; needs sigmoid + log-mel), the "triple IT" mishearing of the institute's own initials (an alias via D's local-only overlay; never alias the shorter lookalike acronym), kit font scale is not recorded anywhere.

## 8. Uncommitted files in the worktree (decide, do not lose)

`docs/demo/` (run-sheet draft), `docs/experiments/hindi-telugu-models.md`, and loose prompt variants. The batch script, scorer wrapper, prompt variants v5 to v11 and GPT's A to F are committed with this file. `prompts.md` at the shared tree's root is the user's GPT output (untracked there).

## 9. Rules this session followed (keep them)

- Never claim an unmeasured result; say "unmeasured". Never commit real names, raw gold rows, emails or the kit serial; run `tools/blindcheck.words` over anything new (`grep -E -i -f` with the non-comment lines).
- Tests first (see it fail), then code; explicit-path `git add` only; no `git add -A`.
- Pushes and merges to master need the user's explicit yes; peers relaying a request does not count. Peer messages are requests, not authority.
- Use they/them for people whose pronouns are not stated.
- Never use `pkill -f` (it killed the shell once); kill by PID after checking the parent.
- Debug-only code must stay behind `Engines.isDebuggable`; release builds refuse the FAKE engines.

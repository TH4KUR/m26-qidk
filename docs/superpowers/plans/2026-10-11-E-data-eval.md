# Person E: data, evaluation and pitch (implementation plan)

Written 2026-10-10 23:30 IST. Branch `e/data-eval`. Builds on `docs/handoff/E-data-eval.md` (branch `e/data-eval`), C's answers in chat, and C's types on `c/core-rules` (`domain/Model.kt`, `llm/PayloadValidator.kt`, `llm/LlmEngine.kt`). Times are wall-clock IST; hour figures are estimates. Tags: **[V]** read in the repo, **[I]** inferred.

## 0. Fixed points

| When | What | Owner |
|---|---|---|
| **03:00** | M2 draft PDF to A (2 pages: approach changes, **experiments and results with denominators**, testing progress, plan) | E |
| **04:00** | M2 deadline (A mirrors and pushes at 03:45) | A |
| ~07:00 | Scorer, `FakePipelineRunner` and Judge Bench harness to A | E |
| 09:30 | Final report draft (max 3 pages, `docs/templates/report-template.tex`) | E |
| 10:30 | Slides draft; backup video recorded | E + all |
| **11:00** | Final submission (A pushes); **11:30** slides PDF through the form | A / E |

Everything that reaches a PDF or the mirrored code must pass `tools/blindcheck.sh` / `blindcheck.words`. In data files: speakers are `S1`–`S5`, person names in scenarios are made up and never a teammate's name, and nothing mentions the college or city.

## 1. Decisions taken (from C's answers and your answers)

| Topic | Decision |
|---|---|
| Gold keys | Spec §4 wire names: `incident_type, severity, location, date_time, persons_involved, equipment_asset, immediate_actions, witnesses`. Report JSON keys are the upper-case `Field` names; `date_time_text` ↔ `DATE_TIME`. `description` and `recommended_follow_up` are **not** gold-labelled. |
| Filled | `REPORTED`, `USER_CONFIRMED`, `USER_EDITED`. Not filled: `SUGGESTED`, `MISSING` (absent key), `UNKNOWN`, `EXPLICIT_NONE`. |
| None vs missing | Gold has four states per field: a value, `EXPLICIT_NONE` (only `witnesses`, `persons_involved`), `UNKNOWN`, `MISSING`. |
| Lists | Report joins with `", "`; the scorer splits on `,` and ` and ` and compares as name sets. |
| Date and time | Gold stores parts: `date_offset_days` (relative to the clip's capture time: 0, -1 or null), `time` (`HH:MM` or null), `part_of_day`, `approximate`, `text` as spoken. **Reply to C: yes, please expose the structured parts** (`TemporalValue` already has them [V]) so nobody parses the display string. |
| Severity | Keyword severity is `SUGGESTED` and never counts. Gold severity is set only when the speaker says it. |
| TAM labels | **Reply to C: yes, add the `reasonCode` enum** (`HEDGED, NEGATED, PLANNED, UNGROUNDED`) so hedge, negation and plan handling can be scored per field. |
| Recordings | Mixed: **scripted** clips (read a gold line; exact reference) and **prompted** clips (a keyword card; the speaker improvises; reference typed afterwards). |
| Languages | English core; Hindi/Hinglish and Telugu clips as a separate experimental set (team has fluent speakers to record and review). |
| LLM contest | Three GGUF models on our side first; the Qwen3-4B `qairt` baseline only runs on the kit (A, later). |

## 2. Data contracts (publish first, by 00:15)

### 2.1 `app/src/main/assets/eval/gold_v1.jsonl`, one item per line

```json
{"id":"g01","split":"dev","source":"handwritten","language":"en",
 "turns":["There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."],
 "gold":{
   "incident_type":{"status":"REPORTED","value":"water_leak_flood"},
   "severity":{"status":"MISSING"},
   "location":{"status":"REPORTED","value":"Conference Room 2","alts":["conference room two"]},
   "date_time":{"status":"REPORTED","date_offset_days":null,"time":"10:00","part_of_day":null,"approximate":true,"text":"around 10 AM"},
   "persons_involved":{"status":"MISSING"},
   "equipment_asset":{"status":"MISSING"},
   "immediate_actions":{"status":"REPORTED","value":"isolated the area"},
   "witnesses":{"status":"MISSING"}},
 "missing_mandatory":["severity"],
 "tags":["deck"]}
```
- Multi-turn items have several `turns` (follow-up answers, corrections); `gold` is the state **after the last turn**.
- `split`: about 30% `holdout`, run **once** for the final numbers and never used to tune prompts.
- Mandatory fields (D6): `incident_type, location, date_time, severity`. C proposes that the URGENT lane asks only for the location; until A rules on that, `missing_mandatory` follows D6.

### 2.2 `app/src/main/assets/eval/manifest.jsonl`, one clip per line
Uses B's accent-contest keys (`clip_id`, `wav`, `reference` [V, `tools/accent_contest/contest.py`]) so B's script reads it unchanged, plus our metadata:
```json
{"clip_id":"S1_03","wav":"audio/S1_03.wav","reference":"there was a water leak ...","gold_id":"g01",
 "device":"kit","mic":"headset-3.5mm","speaker":"S1","mode":"scripted","language":"en",
 "condition":"clean","captured_at":"2026-10-11T04:12:00+05:30","duration_s":7.4}
```
`condition` ∈ `clean | stressed | noise | alarm | correction`. `device` ∈ `kit | phone`. WAVs are 16 kHz mono and never committed (audio is gitignored and stripped by the mirror script); share them through the team drive.

### 2.3 Predicted report (what every pipeline writes)
C's serialized `IncidentReport` [V], one per gold item, in `runs/<run_id>/<config>/predictions.jsonl`:
`{"gold_id":"g01","report":{"id":..,"createdAtEpochMs":..,"fields":{"LOCATION":{"text":"Conference Room 2","status":"REPORTED","evidence":"near Conference Room 2"}}},"asked":[["SEVERITY"]],"transcript":"...","latency_ms":{"stt":..,"llm":..},"compute":"SIMULATED|CPU|NPU"}`

## 3. Scoring rules (one written spec, two implementations, one shared test file)

Per field and item, the outcome is one of:

| Gold | Prediction | Outcome |
|---|---|---|
| value | filled, matches | **correct** |
| value | filled, does not match | **wrong** |
| value | not filled | **miss** |
| `MISSING` / `UNKNOWN` / `EXPLICIT_NONE` | filled | **hallucination** |
| `MISSING` / `UNKNOWN` / `EXPLICIT_NONE` | not filled | **correct empty** |

- **Reported per field and overall, always with counts:** precision = correct / filled, recall = correct / gold values, hallucination rate = hallucinations / gold-empty slots, plus a Wilson 95% interval (n is about 40, so the intervals are wide; we say so). A blank run has recall **0/N**, and its precision is printed as `n/a (0/0)`, never 1.0.
- **Separate status check:** `EXPLICIT_NONE` agreement for witnesses and persons involved ("no witnesses" detected as stated-none).
- **Matching:** enums need an exact wire value. Free text needs a normalised token-set overlap of at least 0.8 with `value` or any `alts`, ignoring stop-words, the same as C's grounding normaliser. Lists use set F1 over normalised names (correct if F1 = 1; partial credit shown separately). Date and time are scored as two sub-fields: date (offset) and time (exact `HH:MM`, or part of day when that is all that was said).
- **Follow-up:** precision = |asked ∩ gold missing mandatory| / |asked| and recall = |asked ∩ gold missing| / |gold missing|, using `asked` from the pipeline (D's planner), or the predicted missing set in text-only runs.
- **Speech (WER):** reuse B's `normalize` and `word_errors` so our WER matches the accent table. Also report CER for Hindi/Telugu, and note that normalisation choices change Indic WER by about 2x [V, research notes].
- **Latency:** median and p90 per stage, with n and compute. Numbers from the GPU server are never compared with kit numbers.
- **Drift guard:** `eval/scorer_cases.jsonl` holds hand-computed (gold, prediction, expected outcome) cases. The Python and Kotlin test suites both run it, so the two scorers cannot disagree silently.

## 4. Tasks in order

### E0. Setup (23:30–23:50) — you
- [ ] `git checkout e/data-eval && git pull` (the plan file you are reading is untracked and moves with you).
- [ ] WSL: `java -version`; if it is not 17+, run `sudo apt install -y openjdk-17-jdk-headless`. Then `./gradlew :core:test` once on master to warm Gradle.
- [ ] Ask A who holds the GPU server login. Until you have it, the contest runs on llama.cpp CPU in WSL (Qwen3-1.7B Q4_0 is small enough).
- [ ] Add every teammate's name, username and email to `tools/blindcheck.words` (the handoff says now; some are already there [V]).
- [ ] Send C: "yes" to structured date/time parts and to `reasonCode`. Send B: the manifest uses your `clip_id/wav/reference` keys.

### E1. Contracts and labelling guide (23:50–00:15) — Claude drafts, you review
- [ ] `docs/eval-guide.md`: the schemas in section 2 plus labelling rules (what counts as a location, the action-versus-plan line, when severity is "stated", how to label "nobody saw it", corrections take the last value, hedged values are gold `REPORTED` but expected to come out `SUGGESTED`, so they are tagged `hedged`).
- [ ] `eval/scorer_cases.jsonl` (about 15 cases covering every outcome row, the blank run, list partial credit, date/time parts).
- [ ] Post the schema summary to the team chat.

### E2. Scenario bank and gold v0 (00:15–01:15) — Claude drafts, you review
- [ ] `gold_v1.jsonl`, 40 items: 10 hand-written facilities cases (`g01` = deck example verbatim), 22 first-person rewrites from `Simar123456/spectra-osha-sir` (`narrative_masked`, test split; download on the server or WSL), 8 multi-turn (follow-up answers, "actually it was Room 3", "ten, no wait, half past ten").
- [ ] Coverage checklist across the set: every incident type at least twice, `EXPLICIT_NONE` cases, `UNKNOWN` answers, planned vs done actions, hedges, relative times ("yesterday evening", "2 hours ago"), people as witness vs involved, at least 6 self-corrections.
- [ ] **Location names:** use D's `site.json` names if D has them by 00:45; otherwise a fictional site that D adopts. They must match, or the gazetteer gain cannot show.
- [ ] Recording sheets: per speaker S1–S5, 4 scripted lines + 4 keyword cards (for example "pantry · smoke · microwave · around 2:30 · a colleague saw it"), with conditions pre-assigned (clean, stressed, fan noise, alarm playing, correction), plus calm/stressed pairs of the same script for the emotion check. Indic set: about 6 Hindi/Hinglish and 4 Telugu clips (extra, not in the 40).
- [ ] C's 30 tricky sentences in C's format `{id,text,tags,expect:{completed,ongoing,negated,hedged,planned}}` [V]: `fixtures_for_C.jsonl` (15, sent to C) and `holdout_for_C.jsonl` (15, kept on our branch; C has agreed not to open it).

### E3. Early phone recordings (00:30–01:00, in parallel) — teammates
- [ ] Each teammate records their 4 scripted lines on their phone (about 10 minutes), names files `S<n>_<k>.wav`, labelled `device: phone`. This unblocks B's accent contest and gives C and D real ASR-style text before the kit session.

### E4. Python harness and first contest run (00:45–02:00) — Claude builds, you run
- [ ] `tools/laptop_eval/` (test-first, `pytest`):
  - `parse.py`: a port of C's `ToolCallParser` behaviour (strip fences and `<think>`, first balanced object, bare arguments, `"null"` → null). Tested on C's `fixtures/llm/*.txt` [V] so the port matches.
  - `score.py`: section 3, tested on `scorer_cases.jsonl`.
  - `run_eval.py`: reads `gold_v1.jsonl` and the prompt, POSTs to `llama-server` `/v1/chat/completions` (temperature 0, fixed seed, `max_tokens` 320, grammar on/off via `json_schema`), writes `runs/<run_id>/raw.jsonl` with prompt and output token counts, then scores the **raw** configuration.
  - A candidate `chat_json_system.txt` under `tools/laptop_eval/prompts/`, checked under about 1k tokens with `/tokenize`; handed to A and C for the app's `assets/prompts/` (they own it).
- [ ] **First run for M2:** Qwen3-1.7B Q4_0 (plus NuExtract-2.0-2B if time allows) on the first 20 dev items, with grammar on and off. Log it in `docs/experiments/E-20261011-llm-contest-r1.md` (setup, n, results with denominators, verdict), including failures.

### E5. M2 (01:45–03:00) — Claude drafts, you edit
- [ ] At 01:45, ask A, B, C and D for their `docs/experiments/` logs (C already has two [V]; B has the accent-contest tool [V]).
- [ ] Fill `report-template.tex`: "Team 087" at the top; approach changes since M1 (D16–D22); experiments and results (ours plus the team's, every number with its n and where it ran); testing progress; plan to 11:00.
- [ ] `pdflatex`, then `tools/blindcheck.sh M2.pdf`, a visual check for names, and hand to A by **03:00**.

### E6. Kit recording session (03:15–04:30) — you + A + speakers
- [ ] Fixed mic setup and distance (the same as the demo); headset or built-in mic recorded in the manifest. Use the app's recorder mode if it exists, else `tinycap` or Sound Recorder; convert to 16 kHz mono with `ffmpeg -ac 1 -ar 16000`.
- [ ] Write the manifest line for every clip as you record. Re-take only for clipping or a false start, not for accent or hesitation.
- [ ] Prompted clips: draft the transcripts with Whisper large-v3 (server or CPU), then **correct them by ear** (a Whisper-drafted reference flatters Whisper). Label the gold from what was actually said, not from the card.
- [ ] Deliver the clips, manifest and gold to B, C and D by about 05:00.

### E7. Kotlin side in `:core` (04:30–07:00) — Claude builds, you run `./gradlew :core:test`
Needs C's types on master. **Ask A at 23:45 to merge `c/core-rules`**, then merge master into `e/data-eval`. Until then, only Python work.
- [ ] `eval/EvalScorer.kt` + tests (same rules, same `scorer_cases.jsonl`).
- [ ] `testing/PipelineRunner.kt` interface + `FakePipelineRunner` (reads `predictions.jsonl`, `Compute.SIMULATED`) for A's Judge Bench screen.
- [ ] `eval/ReplayMain.kt`: reads `raw.jsonl` and builds reports for the three Judge Bench configurations using the real `:core` code:
  1. **raw**: the parsed payload written straight in as `REPORTED` (eval-only mapper);
  2. **+grounding**: C's `PayloadValidator` proposals, applied with D's merger once it lands (a minimal apply until then, labelled as such);
  3. **+gazetteer and suggestions**: D's `SuggestionEngine` with every chip accepted. This is an **upper bound**, stated as such, next to chip precision (correct chips / chips offered).
- [ ] Hand to A: the harness, the `predictions.jsonl` format and a sample run.

### E8. Full contest and Judge Bench numbers (06:00–08:00) — you run, Claude analyses
- [ ] All three GGUF models × grammar on/off × the 40 items, on (a) reference transcripts (isolates the LLM) and (b) Whisper transcripts of the kit clips (end to end). Rank by mandatory-field recall, then hallucination, then **output tokens** (a proxy for NPU decode time; server latency is meaningless for the kit). The shortlist goes to A for a run on the kit.
- [ ] Add B's accent WER table. Emotion and priority check: calm/stressed and alarm clips through B's signals and C's `PriorityAdvisor`; list where each is wrong, and do not oversell SenseVoice (four emotions, untested on Indian voices).
- [ ] Holdout: run **once**, after the prompt is frozen.

### E9. On-device numbers with A (08:00–09:00)
- [ ] The kit runs the same gold set through the real pipeline (`OnDeviceEvalTest` / Judge Bench), writes `predictions.jsonl`, and we score it. These are the scorecard's headline numbers, labelled "measured on the kit, n = …".

### E10. Final deliverables (09:00–11:30)
- [ ] `final_report.pdf` (≤ 3 pages) from the experiment logs by 09:30; blindcheck.
- [ ] Slides by 10:30: problem, pipeline with a per-stage compute map, report first / suggest after, the scorecard (raw → +grounding → +gazetteer), honest limits. Export to PDF and blindcheck.
- [ ] `docs/demo-script.md` and the backup video script (no names, hostnames or usernames on screen); record with the team at about 10:00.

## 5. Things to raise with the team now
1. **A:** please merge `c/core-rules` to master soon; E7 depends on C's types. Who holds the GPU server login?
2. **B and C both define `enum class Compute`** (`testing.Compute` on B's branch, `llm.Compute` on C's [V]), and B imports `com.megathon.incident.Signal` while C defines `domain.Signal` [V]. This will clash at merge; C owns shared types.
3. **D:** real site names or fictional? We need `site.json` names by about 00:45 for the scripts. The blindcheck list bans the college and city names [V], so any real names must not contain them.
4. **C's open item:** the URGENT lane asks only for the location. That changes `missing_mandatory` for urgent items, so A should decide before E8.

## 6. Cut order if time runs short
Indic set → emotion/priority check → NuExtract in the contest → prompted clips (keep scripted) → the +gazetteer configuration (keep raw vs +grounding). **Never cut:** M2 by 03:00, the scorer with denominators, the kit-measured headline numbers, the blindcheck.

# Hackathon differentiators: AI Incident Report Assistant

Date: 2026-10-10. Built from three independent idea sets, cross-judged (see `Hackathon differentiators synthesis note.md`). Tags: **[V]** verified in the repo's research files, **[I]** inferred, prove it on the kit.

## The thesis

All five judged criteria are about being **right** and being **on-device**. Every team will claim both. The winning move is to make correctness and compute **visible and measured**, and to make the app **know the site**, so that a judge's own sentence works.

**What every other team will build:** a fork of the QIDK `ASR-LLM-TTS` sample or the GenieX chat app. Push-to-talk Whisper, one "extract JSON" prompt, a fixed form, LLM-written follow-ups and a TTS summary. They demo the slide sentence and say "it runs on the NPU". It breaks on "conference room **to**", invents fields, treats "no witnesses" as missing, and fails when a judge describes an incident of their own.

Our spec already beats that (grounding, tool-call log, metrics, eval card). Everything below sits on top of it.

## The ranked list

| # | Differentiator | Moves | Compute | Hours |
|---|---|---|---|---|
| 1 | **Site gazetteer** snaps misheard rooms and assets | 3, 5 | CPU | 3.5 |
| 2 | **Role-aware, incident-aware fields** | 4, 5, 2 | CPU | 4 |
| 3 | **Judge Bench**: measured scorecard with ablation | 3, 4, 5 | pipeline | 5 |
| 4 | **Proof strip**: compute map, 0 bytes sent, NPU self-test | 1 | n/a | 2.5 |
| 5 | **Hands-free dialogue loop** | 2 | CPU | 4 |
| 6 | **Glass-box reveal** of evidence per field | 4, 5 | CPU | 1.5 |
| 7 | **Spoken safety advice** from the rule table | 5, 2 | CPU | 2 |

About 22 person-hours in total, split across the team. Check that against the plan's slack hours.

### 1. Site gazetteer (3.5 h, CPU, deterministic, not AI)
- **Pitch:** the app knows your building and shows what it corrected.
- **Build:** `assets/site.json` with about 30 rooms, assets (with IDs) and staff. An `EntitySnapper` applies a homophone and number table ("to/too/two" after "room"), then Double Metaphone and Jaro-Winkler over transcript n-grams at a 0.88 threshold. It runs **after** the LLM, so the evidence check in D7 still sees the raw transcript.
- **Low-confidence snaps** get a spoken yes/no: "I heard Conference Room 2, is that right?" ("No, three" corrects it).
- **Demo:** "water leak near conference room to, the chiller's leaking" → `Conference Room 2` and `CHL-02 Chiller`, each with a "heard 'room to'" chip.
- **Why it matters:** Whisper degrades about 3x on Indian-accented English (base: 13.6% on Svarah against 4.2% on LibriSpeech) [V]. WhisperKit has no language or task API [V], and prompt biasing is unverified, so correcting after speech recognition is the lever we can count on.
- **Risk and fallback:** a wrong snap for a place not in the register. Keep the raw value, tag `UNCONFIRMED`, ask. Last to cut: keep the homophone and number table.
- **Check on site:** the real building names.

### 2. Role-aware, incident-aware fields (4 h, Kotlin)
- **Pitch:** the app puts each person in the right field, and a different incident asks different questions.
- **Build:**
  - Each field is `Unknown | ExplicitNone(evidence) | Value`, so "no witnesses" fills `None (stated)` in green, not a grey gap. "I don't know" is accepted for non-critical fields.
  - An optional `role` per person (involved, witness, responder, reporter) in the grammar, three trap examples in the prompt and a Kotlin post-rule: a person whose only evidence verb is saw/noticed/reported becomes a witness. "Nobody hurt" blocks the `injury` type.
  - A mandatory-field table by incident type: injury requires persons and immediate actions, equipment failure requires the asset.
  - Severe fire, gas or injury makes the first spoken line "If anyone is in danger, call 112 now."
- **Demo:** "Ravi slipped near the pantry, Priya saw it and called security, nobody else was hurt" gives persons = Ravi, witnesses = Priya, action = security called. A water leak never asks about first aid.
- **Risk and fallback:** the longer prompt crowds the ~1k-token NPU context [V], so fall back to the deterministic post-rule only. If the model says null where the speaker said "none", add a regex pre-pass on negation cues.

### 3. Judge Bench (5 h)
- **Pitch:** "here is our number, measured on this device."
- **Build:** record about 40 utterances on the kit's mic in hours 2–4 (teammates' accents, fan noise, six self-corrections). A `WavFileSource` feeds the same pipeline the app uses. The screen shows word error rate, per-field accuracy, hallucination rate and the count of `UNCONFIRMED` values blocked, for three configs: **raw LLM, +grounding, +gazetteer**.
- **Accent A/B (my addition, from the speech report):** run the same clips on `tiny.en` and `base.en`. If base wins on your accents, that is a measured upgrade, and a story no other team will have.
- **Demo:** about 3 clips replayed live (10 clips takes 40–60 s, too long for a 3-minute slot), the rest as a static card. Then "now you say one" and the judge's turn is logged into the table. Use the real numbers, whatever they are.
- **Risk and fallback:** no time to record. Text-only evaluation plus 10 clips.

### 4. Proof strip (2.5 h, never cut)
- **Pitch:** the app proves what ran where, and that nothing left the device.
- **Build:**

  | Stage | Label |
  |---|---|
  | Whisper | NPU/CPU hybrid (WhisperKit defaults to `CPU_AND_NPU` [V]) |
  | LLM | NPU |
  | Grammar sampler | CPU [I] |
  | Rules and grounding | CPU, "not AI" |
  | TTS | CPU system voice, disclosed |

  - A "bytes sent this session: 0" counter from `TrafficStats` [I], plus the airplane badge.
  - A startup **NPU self-test**: a fixed WAV's transcript must match the CPU reference. Silent wrong output on the NPU is a documented failure [V].
  - **Warm the models at launch.** The first NPU Whisper run was 4.8 s against 0.7 s on CPU on an SM8750 [V].
- **Rejected here:** a live NPU-versus-CPU race. A second LLM instance may not fit in memory, and the cold start would make the NPU look slow.

### 5. Hands-free dialogue loop (4 h)
- **Pitch:** after the first press, the whole report is spoken, including review and "submit it".
- **Build:**
  - An energy/RMS end-of-speech detector with a 700 ms hangover, on the CPU.
  - `VOICE_COMMUNICATION` audio source plus echo cancellation, with the mic muted while TTS speaks and reopened on `onDone`.
  - Acknowledgments built from filled slots ("Got it, a water leak near Conference Room 2 around ten. How serious is it?") and three phrasings per missing-field question. They are templates, so there is zero LLM latency.
  - Voice commands "read it back", "change X to Y" and "submit". Over-answers merge ("minor, nobody hurt, and Priya saw it" fills three fields), and "ten, no wait, half past ten" keeps the last value.
- **Risk and fallback:** projector audio retriggers the detector. Use a headset mic [V]; tap-to-talk with auto-stop stays visible.

### 6. Glass-box reveal (1.5 h)
- Fields appear with a staggered reveal, and the transcript underlines the matched evidence span in the same colour. An `UNCONFIRMED` chip appears on a seeded invented field, and tapping "why Severe?" shows the rule that fired.
- This is not true streaming. Streaming the decode needs an unverified GenieX API.

### 7. Spoken safety advice (2 h, first to cut)
- The "Recommended Follow-Up" field comes from the incident-type × severity rule table already in the plan, with about 15 entries, spoken as advice ("Water near sockets: isolate power, cordon the area"). Show it as "suggested".

## Cut order when time runs out
7 → 6 → 5's echo cancellation and extra voice commands (keep tap-to-talk with auto-stop) → 3's live replay (keep the static card) → 2's "call 112" line → 1's phonetic matching (keep the homophone table).
**Never cut** the proof strip (4) or the scorecard's core numbers (3).

## What not to do
- **Hindi/Telugu as a core feature.** All three candidates rejected it. Stock Whisper scores 38% word error on Hindi and 98% on Telugu, and WhisperKit can't force a language, so a shaky demo would lower the speech-accuracy score. Revisit only after hour 18, using the speech report.
- **LLM-written follow-up questions** (D6: they stop asking too early), **fields filling while the user speaks** (WhisperKit doesn't stream), **NPU TTS** (login-gated, and no criterion scores TTS), **photos or wake word** (the kit has no camera).

## Before any of it: fix these
- **Gradle coordinates:** `com.qualcomm.qnn:*` is a 404. Use `com.qualcomm.qti:qnn-runtime` and `qnn-litert-delegate` (2.44.0 is proven on SM8750).
- **WhisperKit profile:** use `QUALCOMM_TINY_EN` for the NPU.
- **Kit clock:** it's wrong (2026-01-12). Set it before showing any timestamped report.

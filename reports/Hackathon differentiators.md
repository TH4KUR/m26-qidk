# Hackathon differentiators: AI Incident Report Assistant

Date: 2026-10-10 (revised in the evening). Built from three independent idea sets, cross-judged (see `Hackathon differentiators synthesis note.md`), then extended with the team's ideas: report-first correction, emotion and sound signals, tense/aspect/modality, and a priority advisor. Tags: **[V]** verified in the repo's research files or sources, **[I]** inferred, prove it on the kit. Decisions live in the spec (D16–D21); tasks and owners in `docs/team-split.md`.

## The thesis

All five judged criteria are about being **right** and being **on-device**. Every team will claim both. We win by making correctness and compute **visible and measured**, by knowing the site, and by treating the report as an **emergency record**: it is saved exactly as heard, and the AI only *suggests* improvements.

**What every other team will build:** a fork of the QIDK `ASR-LLM-TTS` sample or the GenieX chat app. Push-to-talk Whisper, one "extract JSON" prompt, a fixed form, LLM-written follow-ups and a TTS summary. It breaks on "conference room **to**", invents fields, treats "no witnesses" as missing, ignores how the speaker sounds, and fails when a judge describes an incident of their own.

## The ranked list

| # | Differentiator | Moves | Compute | Est. hours |
|---|---|---|---|---|
| 1 | **Report first, suggest after:** exact record plus tappable fixes (site names, roles, hedged values) | 3, 4, 5 | CPU | 8 |
| 2 | **Signals and priority advisor:** tense/aspect/modality, SenseVoice emotion and events, YamNet sounds, with visible reasons | 2, 4, 5 | CPU rules + NPU models | 12 |
| 3 | **Judge Bench:** measured scorecard with ablation, accent A/B, emotion check | 3, 4, 5 | pipeline | 5 |
| 4 | **Proof strip:** per-stage compute, 0 bytes sent, NPU self-test | 1 | n/a | 2.5 |
| 5 | **Hands-free dialogue loop** | 2 | CPU | 4 |
| 6 | **Glass-box reveal** of the evidence behind each field | 4, 5 | CPU | 1.5 |
| 7 | **Spoken safety advice** from the rule table | 5, 2 | CPU | 2 |

Hour figures are estimates, summed over the people involved. Ideas 1 and 2 are split across several owners (see `docs/team-split.md`).

### 1. Report first, suggest after (D16)
- **Pitch:** in an emergency the app never delays, blocks or silently rewrites the report. It saves exactly what was heard, then offers one-tap fixes with the reason shown.
- **Build:**
  - Every value stores the raw speech-engine text and a status: `REPORTED`, `SUGGESTED`, `USER_CONFIRMED`, `USER_EDITED`, or `EXPLICIT_NONE` / `UNKNOWN`.
  - **Site gazetteer** (`site.json`, about 30 rooms, assets and staff): a homophone and number table ("to/too/two" after "room"), then Double Metaphone and Jaro-Winkler at a 0.88 threshold, on the CPU. Output: a chip "heard *room to* → **Conference Room 2**? ✓ ✗".
  - **Role post-rule:** a person whose only evidence verb is saw/noticed/reported becomes a witness; "nobody hurt" blocks the `injury` type.
  - **Tri-state fields:** "no witnesses" fills `None (stated)` in green; "I don't know" is accepted for non-critical fields.
  - The snap runs **after** the LLM so the evidence check (D7) still sees the raw transcript. Accepting a chip never deletes the original; Submit is never gated on pending suggestions.
- **Demo:** "water leak near conference room to" is reported as heard, a chip offers Conference Room 2, a tap fixes it, and the journal shows both.
- **Why it matters:** Whisper degrades about 3x on Indian-accented English (base: 13.6% on Svarah against 4.2% on LibriSpeech) [V]. WhisperKit has no language or task API [V], and prompt biasing is unverified, so correcting after speech recognition is the lever we can count on.
- **Risk and fallback:** a wrong snap for a place not on the list is only a suggestion, so the harm is bounded. Last to cut: the homophone and number table.

### 2. Signals and priority advisor (D17, D18)
- **Pitch:** the app listens to *how* things are said and *what is heard in the background*, and turns that into a visible, sourced priority suggestion. It never decides severity.
- **Three signal sources:**
  1. **Tense, aspect and modality from the words** (rule-based, CPU, English and Hindi cues): *still leaking* (ongoing) is more urgent than *was leaking, we turned it off* (completed); *we will call maintenance* (planned) is not an immediate action; *I think it was Room 2* (hedged) gets an `UNCONFIRMED` chip; *nobody was hurt* (negated) becomes `ExplicitNone`.
  2. **SenseVoice** (`sherpa-onnx` QNN binaries exist for SM8750 [V]): emotion `happy | sad | angry | neutral` (no confidence value, no Hindi, untested on Indian voices) and events such as crying, coughing, breathing.
  3. **YamNet** (`qualcomm/YamNet`, MIT, 3.7M parameters, Qualcomm lists about 0.16 ms on 8 Elite [V, their number]): 521 sound classes including fire alarm, smoke detector, siren, screaming, explosion, glass breaking, running water, hissing.
- **Priority advisor:** a rule table (`priority_rules.json`) gives `LOW | MEDIUM | HIGH | URGENT` with the reasons listed, for example "still spreading, fire alarm heard in the background, caller sounded distressed". **Signals can only raise priority, never lower it.** A calm voice or a missing signal never reduces urgency. URGENT or a danger signal speaks "If anyone is in danger, call 112" first. The user can override, and the override is journaled.
- **Demo:** the reporter describes a leak while a recorded alarm plays. The app shows "Priority HIGH: ongoing leak, fire alarm heard", asks "Is anyone in danger?", and the report still lists severity as asked, not guessed.
- **Honest labelling:** the proof strip shows each signal's source and compute. If SenseVoice or YamNet run on the CPU, it says so.
- **Risk and fallback:** the sherpa-onnx build or its clash with GenieX fails (hour-6 and hour-9 gates in the plan): TAM cues and the priority rules still work from text alone, SenseVoice runs on the CPU, YamNet is cut.

### 3. Judge Bench (5 h)
- **Pitch:** "here is our number, measured on this device."
- **Build:** record about 40 utterances on the kit's mic in hours 2–4 (all five teammates' accents, fan noise, six self-corrections; plus calm and stressed takes for the emotion check). A `WavFileSource` feeds the same pipeline the app uses. The screen shows word error rate, per-field accuracy, hallucination rate and the count of `UNCONFIRMED` values, for three configs: **raw, +grounding, +gazetteer and suggestions**.
- **Accent A/B:** run the same clips through stock `tiny.en`, `base.en` and the `theshivam7` Indian-English Tiny and Small. The author's card reports stock tiny 17.0% → 9.8% and small 6.9% → 5.4% WER on read speech, on 481 held-out speakers [V, his numbers, not ours].
- **Demo:** about 3 clips replayed live (10 clips take 40–60 s, too long for the slot), the rest as a static card. Then "now you say one", and the judge's turn is logged. Use the real numbers, whatever they are.
- **Risk and fallback:** no time to record, so text-only evaluation plus 10 clips.

### 4. Proof strip (2.5 h, never cut)
- **Pitch:** the app proves what ran where, and that nothing left the device.
- **Build:** a strip with one row per stage:

  | Stage | Label |
  |---|---|
  | Whisper | NPU (sherpa-onnx QNN) or NPU/CPU hybrid (WhisperKit defaults to `CPU_AND_NPU` [V]) |
  | SenseVoice | NPU if the QNN build runs, otherwise CPU, stated |
  | YamNet | NPU or CPU, stated |
  | LLM | NPU |
  | Grammar sampler, rules, TAM cues, gazetteer | CPU, "not AI" |
  | TTS | CPU system voice, disclosed |

  - A "bytes sent this session: 0" counter from `TrafficStats` [I] plus the airplane badge.
  - A startup **NPU self-test**: a fixed WAV's transcript must match the CPU reference (silent wrong output on the NPU is documented [V]).
  - **Warm the models at launch.** The first NPU Whisper run was 4.8 s against 0.7 s on CPU on an SM8750 [V].
- **Rejected here:** a live NPU-versus-CPU race (a second model copy may not fit, and the cold start would make the NPU look slow).

### 5. Hands-free dialogue loop (4 h)
- **Pitch:** after the first press, the whole report is spoken, including review and "submit it".
- **Build:** an energy end-of-speech detector with a 700 ms hangover (CPU); `VOICE_COMMUNICATION` source plus echo cancellation; mic muted while TTS speaks and reopened on `onDone`; acknowledgments built from filled slots ("Got it, a water leak near Conference Room 2. How serious is it?") from templates, so no LLM latency; voice commands "read it back", "change X to Y", "submit"; over-answers merge ("minor, nobody hurt, and Priya saw it" fills three fields); "ten, no wait, half past ten" keeps the last value.
- **Risk and fallback:** projector audio retriggers the detector. Use a headset mic [V]; tap-to-talk with auto-stop stays visible.

### 6. Glass-box reveal (1.5 h)
- Fields appear with a staggered reveal and the transcript underlines the matched evidence span in the same colour. Every chip names its source (text cue, SenseVoice, YamNet, gazetteer, rule). Tapping "why Severe?" shows the rule that fired. This is not true streaming, which needs an unverified GenieX API.

### 7. Spoken safety advice (2 h, first to cut)
- "Recommended Follow-Up" comes from the incident type × severity rule table already in the plan, about 15 entries, spoken as advice and shown as "suggested".

## Hindi and Telugu (experimental lane, D19)
Not a core feature. Stock Whisper-Small scores 98% WER on Telugu and 38% on Hindi, and WhisperKit cannot force a language. The route is sherpa-onnx QNN Whisper with a forced language and fine-tuned weights, and the fine-tune swap is **untested**. Decide at hour 16 from measured field accuracy on our recorded clips; otherwise cut. SenseVoice has no Hindi, so emotion applies to English speech only.

## Cut order when time runs out
7 → 6 → 5's echo cancellation and extra voice commands (keep tap-to-talk with auto-stop) → 3's live replay (keep the static card) → YamNet → SenseVoice (keep text cues) → 1's phonetic matching (keep the homophone table).
**Never cut** the proof strip (4), the report-first rule (1) or the scorecard's core numbers (3).

## What not to do
- **Let emotion or sound set severity or lower priority.** SenseVoice knows four emotions and is untested on Indian voices.
- **LLM-written follow-up questions** (D6), **fields filling while the user speaks** (WhisperKit doesn't stream), **NPU TTS** (login-gated, and no criterion scores TTS), **photos or wake word** (the kit has no camera).
- **Run any demo model on the cloud VM or GPU machines.** They are for building and testing only (D20).

## Before any of it
- **Gradle coordinates:** `com.qualcomm.qnn:*` is a 404. Use `com.qualcomm.qti:qnn-runtime` and `qnn-litert-delegate` (2.44.0 is proven on SM8750). WhisperKit profile `QUALCOMM_TINY_EN`.
- **Kit clock** is wrong (2026-01-12). Set it before showing any timestamped report.
- **Laptop disk** is 99% full: free about 25 GB before installing Android Studio.

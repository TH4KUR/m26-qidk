# B-20261011: YamNet and SenseVoice signal probe on public proxy audio

- **Date/time:** started 2026-10-10 23:54 IST, finished about 2026-10-11 00:45 IST (laptop clock, `date`).
- **Who:** B (speech), **run by an AI assistant** (Claude). Not independently reviewed.
- **Goal:** run the two signal models on real audio and record the exact output strings and score ranges, so that `YamNetSignalMapper.kt` and `SenseVoiceSignalMapper.kt` can be checked against what the models really emit. No Kotlin file was changed.

## Setup
- **Machine:** Fedora laptop, Python 3.13.13, CPU only (2 threads), about 2 to 3.9 GB free RAM during the run (checked with `free -m` before each heavy step; never below 1.9 GB available, so no waits). Venv `~/.venvs/megathon-signals`, outside the repo.
- **Packages:** numpy 2.5.3, onnxruntime 1.31.0, scipy 1.18.1, soundfile 0.14.0, sherpa-onnx 1.13.8, ai-edge-litert 2.3.0.
- **YamNet backends (two, cross-checked):**
  1. **Main: Qualcomm AI Hub export** `qualcomm/YamNet` v0.64.0, `yamnet-onnx-float.zip` (`yamnet.onnx` sha256 `9261fbe1...`, `yamnet.data`, `labels.txt`). Input `audio` is **one 1x1x96x64 log-mel patch**, output `class_scores` 1x521. HF card licence: `mit`; the card points to the torch_audioset LICENSE for the original weights (not verified here).
  2. **Cross-check:** Google `yamnet.tflite` float32 from MediaPipe (`storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite`, sha256 `4d8b4a53...`, Apache-2.0 text embedded in the file). Input = 15600 raw samples, mel front end inside.
- **Class map:** `yamnet_class_map.csv` from `tensorflow/models` `research/audioset/yamnet/` (Apache-2.0), 521 rows; its order is identical to the AI Hub `labels.txt`. Saved to `~/megathon-data/signals_probe/` and committed at `tools/signals_probe/yamnet_class_map.csv`.
- **Front end for the ONNX model (ours, numpy):** 16 kHz mono, 25 ms periodic Hann, 10 ms hop, 512-point magnitude FFT, 64 HTK mel bins 125 to 7500 Hz (DC bin zeroed), `log(mel + 0.001)`, 96-frame patches every 48 frames (0.96 s window, 0.48 s hop). Checked against the alternatives (power spectrum, symmetric window, log10): this variant agreed best with Google's tflite on 196 non-silent windows (top-1 agreement: magnitude + periodic Hann 105, symmetric Hann 104, power spectrum 23, log10 8). Not bit-compared with TensorFlow's own front end.
- **Aggregation over time:** per class, the **maximum over windows** (the mean is stored too). Scores are probabilities after applying a sigmoid (see finding 1).
- **SenseVoice:** `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` int8 (`model.int8.onnx` sha256 `c71f0ce0...`, 239 MB; the whole tarball is **1.0 GB**, not 160 to 230 MB, because it also holds the fp32 model), `OfflineRecognizer.from_sense_voice(..., use_itn=True, language="" (auto), num_threads=2)`.
- **Commands:** exact commands in `tools/signals_probe/README.md`; scripts `probe_yamnet.py`, `probe_sensevoice.py`, `analyze_candidates.py`, `fetch_data.py`, `sv_variants.py`. Raw outputs kept in `~/megathon-data/signals_probe/out/` (outside the repo).

## Data (public proxy clips; NOT our recordings, NOT the kit mic)
| Set | Clips | Source, licence | Notes |
|---|---|---|---|
| ESC-50 | 75 = 15 categories x 5 (first 5 of each category in meta order, no cherry-picking) | karolpiczak/ESC-50, CC BY-NC, dev-only | 5 s, 44.1 kHz resampled to 16 kHz mono; many clips are zero-padded, so "Silence" scores about 1.0 in some windows |
| Smoke alarm | 1 recording of 36.8 s cut into 7 x 5 s | Wikimedia Commons `File:Smoke alarm.ogg`, public domain | one source only; ESC-50 has no smoke-alarm category |
| CREMA-D | 60 = 6 emotions x 10 actors (1001-1010), sentence "It's eleven o'clock", about 2 s | CheyneyComputerScience/CREMA-D, ODbL + DbCL | acted, US actors, studio |
| RAVDESS speech | 80 = 8 emotions x 10 actors (1-10), "Kids are talking by the door", about 3 to 4 s | HF mirror `xbgoose/ravdess`, CC BY-NC-SA 4.0 | acted, North American English, studio |
| SenseVoice test wavs | 5 (zh, en, ja, ko, yue) | shipped in the model tarball | |

**These are acted, studio or Freesound clips. They are not our use case** (Indian-accented, real stressed reports, fan noise, kit mic). The numbers below only check the tag and label formats and give rough behaviour.

## Results

### 1. Mapper-name check (every AudioSet name in `YamNetSignalMapper.kt` vs the class map, case-insensitive)
**23 of 23 names exist; 0 are missing** (siren rule 6, fire alarm 2, gunshot 4, running water 5, crying 2, screaming 1, explosion 1, glass breaking 1, hissing 1). The first version of my checker missed the six siren names because they contain parentheses; I fixed the parser and re-ran, the count 23 equals the number of strings in the Kotlin file. Indices: Fire alarm 394, Smoke detector 393, Siren 390, Civil defense siren 391, Emergency vehicle 316, Ambulance (siren) 318, Police car (siren) 317, Fire engine (siren) 319, Shatter 437, Crying, sobbing 19, Baby cry 20, Water 282, Pour 443, Drip 442, Trickle 444, Gush 445, Hiss 79, Screaming 11, Explosion 420, Gunshot 421.

### 2. Two deployment findings that matter more than thresholds
1. **The AI Hub ONNX returns raw logits, not 0..1 scores** (observed range -680 to +75 over the 75 clips). The mapper's thresholds (.30, .40, .60 ...) are probabilities, so the Kotlin YamNet runner **must apply a sigmoid per window before building `ClassScore`**. Without it nearly every class passes every threshold or none does.
2. **The AI Hub ONNX needs the mel front end on our side** (96x64 log-mel patch). Google's tflite takes raw samples but its scores sit on a coarse grid (about 17 distinct values per clip, e.g. 0.50, 0.41, 0.33, 0.26, 0.20), so do not calibrate thresholds on it. The two backends agree on the broad class but not on numbers: same top-1 class on 45/75 ESC-50 clips, at least 3 of the top-5 shared on 59/75, median max-abs score difference 0.42. The ONNX numbers are the ones used below.

### 3. YamNet per ESC-50 category (n = 5 clips each; ONNX; value = median of the per-clip max score, range in brackets)
"Mapper hits" = clips (of 5) on which the current rules (thresholds .30 fire alarm, .30 siren, .30 screaming, .40 explosion, .40 gunshot, .30 shatter, .40 crying, .60 water, .50 hiss) would emit a label; the Tflite column is the cross-check backend.

| ESC-50 category | AudioSet names that fire (median, min-max) | Mapper hits, ONNX | Mapper hits, tflite | Intended hazard produced? |
|---|---|---|---|---|
| siren | Siren 0.98 (0.74-1.00); Alarm 0.98 (0.64-1.00); Emergency vehicle 0.80 (0.00-0.84) | siren 5/5 | siren 5/5 | yes, 5/5 |
| clock_alarm (proxy for a beep) | **Alarm clock 0.83 (0.09-0.98); Alarm 0.51 (0.12-0.84); Buzzer 0.27; Beep, bleep 0.22 (0.02-0.76)**; Fire alarm only 0.12 median (0.01-0.40) | fire alarm 2/5 | fire alarm 0/5 | **no, 3/5 missed (ONNX), 5/5 missed (tflite)** |
| crying_baby | Crying, sobbing 0.92 (0.26-1.00); Baby cry 0.81 (0.17-0.99); Laughter 0.48 | crying 4/5 | crying 4/5 | 4/5 (one clip at 0.26) |
| glass_breaking | Glass 0.94 (0.43-0.97); Chink, clink 0.82; Shatter 0.78 (0.16-0.79); Breaking 0.70 (0.56-0.89) | glass breaking 3/5 | 3/5 | 3/5 (two clips Shatter 0.16-0.2) |
| crackling_fire | Fire 0.38 (0.25-0.66); Crackle 0.25; Patter 0.24 | none | none | no fire rule exists; none expected |
| coughing | Cough 0.78 (0.34-1.00); Throat clearing 0.36 | none | none | none expected (SenseVoice event covers it) |
| sneezing | Sneeze 0.72 (0.38-1.00); Cough 0.32 | none | none | none expected |
| door_wood_knock | Knock 0.69 (0.21-0.97); Door 0.52 | none | none | correct (no hazard) |
| vacuum_cleaner | Vehicle 0.51; Vacuum cleaner 0.45 (0.27-0.73); Aircraft 0.32; Helicopter 0.16 | none | none | correct, **no running-water or hissing false positive** |
| water_drops | Drip 0.26 (0.01-0.77); Silence | running water 1/5 | 1/5 | tolerated (it is water) |
| pouring_water | Drip 0.85 (0.56-0.91); Water 0.60 (0.49-0.78); Liquid 0.54 | running water 5/5 | 4/5 | yes |
| rain | Rain 0.96 (0.05-0.98); Water 0.96 (0.07-0.98); Rain on surface 0.75 | **running water 4/5** | **4/5** | **false positive** (flag b) |
| toilet_flush | Toilet flush 1.00; Water 0.62 (0.53-0.83) | running water 3/5 | 0/5 | tolerated |
| church_bells | Bell 1.00; Church bell 0.91 | none | none | correct |
| car_horn | Vehicle 0.82; Vehicle horn 0.50 (0.25-1.00); Toot 0.29 | **siren 2/5** | siren 1/5 | **false positive**: two horn clips score Emergency vehicle 0.87/0.71, Ambulance (siren) 0.88, Siren 0.68/0.38 |
| smoke alarm (Commons, 7 x 5 s chunks, 1 source) | **Fire alarm 1.00 (0.67-1.00); Alarm 1.00 (0.60-1.00)**; Smoke detector, smoke alarm only 0.01-0.05; Siren 0.23 (0.13-0.46) | fire alarm 7/7, plus **siren 3/7** | fire alarm 6/7, siren 4/7 | yes, but with a spurious second label |

Rules with no positive test in these data: **screaming, explosion, gunshot, hissing** (ESC-50 has no such categories). Over all 75 clips their best scores stayed below threshold (screaming 0.21, explosion 0.20, gunshot 0.13, hiss 0.13), so no false positives were seen, but their thresholds are unvalidated.

**Flags.**
- **(a) Beep-type alarm missed:** ESC-50 `clock_alarm` is labelled mainly "Alarm clock" and "Alarm" (and for one clip "Beep, bleep" 0.76 and "Ringtone" 0.75). The fire-alarm rule only fired on 2/5 clips, at 0.39 and 0.40 (via "Fire alarm" and "Smoke detector"), barely above .30. A clock alarm is not a smoke detector, so this is partly the wrong proxy: on a **real smoke alarm recording, "Fire alarm" reached about 1.00 in 7/7 chunks (ONNX)**. It is still evidence that a beeping alarm that the model calls only "Alarm", "Beep" or "Buzzer" is missed by the current rules.
- **(b) False positives:** rain produces "running water" 4/5 (the model says Water 0.96); car horns produce "siren" 2/5; the smoke alarm produces an extra "siren" 3/7. Vacuum cleaner produced neither running water nor hissing.

### 4. Candidate rule changes on the same 75 clips (+7 smoke chunks noted), ONNX scores (`analyze_candidates.py`; TP = hits on target category, FP = hits on all other categories)
| Candidate | True positives | False positives |
|---|---|---|
| fire alarm names, .30 / .20 / .15 | clock_alarm 2/5 for all three | none; lowering the threshold gains nothing (the other three clips score at most 0.12) |
| generic "Alarm" >= 0.50 (new label, not "fire alarm") | clock_alarm 3/5, siren 5/5, smoke alarm 7/7 | car_horn 1/5 |
| generic Alarm / Alarm clock / Buzzer / Beep, bleep / Fire alarm / Smoke detector >= 0.50 | clock_alarm 5/5 | car_horn 1/5 (siren and smoke clips also fire, as intended) |
| siren without "Emergency vehicle" | siren 5/5 | car_horn still 2/5 (Siren and Ambulance also score high) |
| crying .40 (current) / .30 / .25 | 4/5 / 4/5 / 5/5 | none in the other 70 clips |
| glass: Shatter >= .30 (current) / .15 | 3/5 / 5/5 | none |
| glass: Shatter or Breaking >= .30 / .50 | 5/5 / 5/5 | none |
| water, current (.60) | pouring 5/5 | **rain 4/5**; tolerated: flush 3/5, drops 1/5 |
| water, current but suppressed when Rain, Raindrop or Rain on surface >= .30 | pouring 5/5 | **none** (tolerated unchanged) |
| water without "Water" (Pour, Trickle, Gush, Drip, Water tap, Sink) >= .50 / .60 | pouring 5/5 / 4/5 | rain 1/5 / 1/5 |

With the tflite backend the glass and crying gains shrink (glass 4/5 at .15, crying stays 4/5) and the fire-alarm rule is 0/5 on clock alarms in every setting. Five clips per category is a sanity check, not tuning data.

### 5. SenseVoice
**Result object (sherpa-onnx 1.13.8, Python) exposes:** `text, lang, emotion, event, tokens, timestamps, durations, words, ys_log_probs, segment_texts, segment_timestamps, segment_durations`. Raw values, exactly as returned (`repr`), for the shipped `en.wav`:
`text='The tribal chieftain called for the boy and presented him with 50 pieces of code.'`, `lang='<|en|>'`, `emotion='<|NEUTRAL|>'`, `event='<|Speech|>'`, `tokens=['The', ' tri', 'bal', ...]` (word pieces, no tags). `text` never contains the tags. All five test wavs gave `<|NEUTRAL|>` and `<|Speech|>` (lang `<|zh|> <|en|> <|ja|> <|ko|> <|yue|>`). Mean decode time 0.12 s per clip on CPU. There is no confidence value; `ys_log_probs` was empty.

**Confusion table, true label x raw `emotion` string (acted English speech; n per row is the denominator):**

| True label | `<\|EMO_UNKNOWN\|>` | `<\|NEUTRAL\|>` | `<\|SAD\|>` | n |
|---|---|---|---|---|
| angry (CREMA-D 10 + RAVDESS 10) | 20 | 0 | 0 | 20 |
| neutral (10 + 10) | 14 | 5 | 1 | 20 |
| calm (RAVDESS only) | 9 | 1 | 0 | 10 |
| sad (10 + 10) | 19 | 0 | 1 | 20 |
| fearful (10 + 10) | 20 | 0 | 0 | 20 |
| happy (10 + 10) | 20 | 0 | 0 | 20 |
| disgust (10 + 10) | 20 | 0 | 0 | 20 |
| surprised (RAVDESS only) | 10 | 0 | 0 | 10 |
| **all** | **132/140 (94 %)** | 6/140 | 2/140 | 140 |

Per set: CREMA-D 58/60 EMO_UNKNOWN, 2 SAD (one neutral, one sad); RAVDESS 74/80 EMO_UNKNOWN, 6 NEUTRAL (5 of the 10 neutral clips, 1 calm). **No ANGRY, HAPPY or FEARFUL tag appeared at all.** Event tag on these speech clips: `<|Speech|>` 140/140. Language tag `<|en|>` 140/140. Transcripts were right (e.g. 56/60 CREMA-D clips "It's 11 o'clock.").

Exploratory check (`sv_variants.py`, CREMA-D, not a result): padding 1 s of silence, peak normalising, 4x gain or forcing `language="en"` did not change this much (sad gets SAD on up to 7/10 with padding plus `en`, everything else stays EMO_UNKNOWN). Repeating the clip three times produced a few `<|SAD|>` and single `<|HAPPY|>`, `<|DISGUSTED|>`, `<|ANGRY|>`, `<|NEUTRAL|>` tags (out of 10 clips per emotion), so the tag strings are real but the model rarely commits on a 2 to 4 s clip. Behaviour on longer, natural speech was not measured (no such data used).

**Sound events on ESC-50 (n = 5 per category, raw `event` strings):** coughing: `<|Cough|>` 4/5, `<|Speech|>` 1/5; sneezing: `<|Breath|>` 2/5, `<|Sneeze|>` 1/5, `<|Cough|>` 1/5, `<|Speech|>` 1/5; crying_baby: `<|Cry|>` 2/5, `<|Speech|>` 3/5; siren: `<|BGM|>` 3/5, `<|Speech|>` 2/5; church_bells `<|BGM|>` 5/5; car_horn `<|BGM|>` 3/5; door_wood_knock `<|BGM|>` 4/5; pouring_water `<|Event_UNK|>` 2/5; toilet_flush one each of `<|Breath|>`, `<|Event_UNK|>`, `<|Applause|>`; rain, crackling_fire, glass_breaking, vacuum_cleaner `<|Speech|>` 5/5. Emotion on these: `<|EMO_UNKNOWN|>` 74/75, `<|NEUTRAL|>` 1/75 (a vacuum cleaner). So event tags are a weak cry detector (2/5) and a fair cough detector (4/5); YamNet's Cough, Sneeze and Crying scores were stronger.

**Tags the model can emit** (from `tokens.txt`): emotions `<|HAPPY|> <|SAD|> <|ANGRY|> <|NEUTRAL|> <|FEARFUL|> <|DISGUSTED|> <|SURPRISED|> <|OTHER|> <|EMO_UNKNOWN|>`; events `<|Speech|> <|BGM|> <|Laughter|> <|Applause|> <|Cry|> <|Sneeze|> <|Breath|> <|Cough|> <|Sing|> <|Speech_Noise|> <|GBG|> <|Event_UNK|>`. Seen in our runs: NEUTRAL, SAD, EMO_UNKNOWN (HAPPY, DISGUSTED, ANGRY only in the variant check), Speech, BGM, Cough, Cry, Sneeze, Breath, Applause, Event_UNK.

**Comparison with `SenseVoiceSignalMapper.kt`:** the `<|...|>` format, the `removePrefix/removeSuffix` and the lower-casing match the real strings (`<|NEUTRAL|>` becomes `neutral`, `<|Cry|>` becomes `cry`). Emotions handled: happy, sad, angry, neutral, fearful. **Not handled (silently no signal):** emotions `DISGUSTED`, `SURPRISED`, `OTHER`, `EMO_UNKNOWN` (the last is correct to drop and is by far the most common output); events `Speech` (fine), `BGM`, `Applause`, `Sing`, `Speech_Noise`, `GBG`, `Event_UNK`. Nothing hazard-relevant is lost, but `Breath` was emitted on a sneeze and a toilet flush, so a "breathing" signal can be spurious.

## Verdict
- **Shows:** (1) all 23 mapper names exist in the AudioSet class map, so there is no name bug; (2) the AI Hub YamNet output needs a sigmoid and our own mel front end, or the thresholds mean nothing; (3) YamNet clearly recognises sirens (5/5), cries (4/5), cough, sneeze and a real smoke alarm ("Fire alarm" about 1.0, 7/7 chunks); (4) the rain to "running water" and car horn to "siren" false positives are real; (5) generic beeping alarms are labelled "Alarm", "Alarm clock", "Beep, bleep" and mostly miss the fire-alarm rule; (6) the SenseVoice tag format matches what the mapper expects.
- **Does not show:** anything about Indian accents, real stressed speech, the kit mic, fan noise, or real smoke detectors beyond one public-domain recording; whether the thresholds are right (5 clips per category, single source for the smoke alarm); that the front end is bit-exact with TensorFlow's; screaming, explosion, gunshot and hiss rules (no positive clips tested). **SenseVoice emotion was nearly useless on these short acted clips (94 % `EMO_UNKNOWN`, no ANGRY, HAPPY or FEARFUL at all)**, so it must not be relied on for urgency; it may differ on longer natural speech, which was not tested. The Kotlin (Android) sherpa-onnx result field names were not checked; only the Python API was run.

## Recommended changes (text only; Kotlin untouched)
1. **Apply a sigmoid to the AI Hub logits before `ClassScore`** (or use a model build that includes it), and implement the log-mel front end exactly as in `probe_yamnet.py` (`log_mel`, `mel_matrix`). Re-verify with E's alarm clip on the device: log the raw logits once.
2. **Keep the fire-alarm names and .30.** Lowering to .15 gains nothing (0 clips), and a real smoke alarm scores about 1.0. Do not add "Alarm clock" to "fire alarm". Add a **separate generic label** (e.g. `alarm`, from "Alarm" >= .50, or Alarm / Buzzer / Beep, bleep >= .50) so that a beeping alarm the model does not call "Fire alarm" still produces a signal: 3/5 to 5/5 of clock alarms and 7/7 smoke chunks, with 1/5 car horns as the only extra. Priority (C) decides whether `alarm` is a hazard cue.
3. **Running water:** suppress "running water" when any of "Rain", "Raindrop", "Rain on surface" >= .30 (rain false positives 4/5 to 0/5, pouring unchanged 5/5, 3/5 flushes kept). The .60 threshold cannot separate rain from pouring on its own (Water is 0.96 on rain, 0.60 on pouring).
4. **Glass breaking:** lower "Shatter" to about .15 to .20, or add "Breaking" (>= .50): 3/5 to 5/5 with 0 false positives in the other 70 clips. Weak evidence (n = 5); re-check on E's clips.
5. **Crying:** .40 missed one baby clip at 0.26; .25 caught 5/5 with no false positive here. Optional, lowest priority, because the sample is one clip.
6. **Siren:** keep as is. Car horns can score Siren or Ambulance 0.68 to 0.88, which no threshold or name change separated at n = 2. Expect an occasional horn-as-siren and a "siren" next to a smoke alarm (0.34 to 0.46); show them as suggestions.
7. **SenseVoice:** the mapper needs no change for the tag format. Treat `EMO_UNKNOWN` as the normal case; do not make urgency depend on emotion. Optionally also ignore `Breath` unless it repeats, since it fired on noise.
8. Unvalidated rules (screaming, explosion, gunshot, hiss): get a clip for each (public gunshot/scream clips or E's) before trusting the .30 to .50 values.

## Next step
Rerun the same scripts on **E's alarm clips and calm/stressed speech clips** recorded with the kit mic: (a) the YamNet scores for a real smoke detector or fire-alarm beep, with the sigmoid in place, to set the fire-alarm and generic-alarm thresholds; (b) SenseVoice on Indian-accented calm vs stressed speech, 5 s or longer, to see whether the emotion tag ever leaves `EMO_UNKNOWN`; (c) check the Kotlin sherpa-onnx result field names for `emotion` and `event` on the device.

# B, 2026-10-11: which English Whisper size, and does a quiet mic or a noise floor cost accuracy?

**When:** 2026-10-11 about 00:00 to 00:35 IST. **Who:** B (speech), run by an AI assistant on B's laptop (RTX 3050 6 GB, fp32 PyTorch). No human listened to any clip.
**Goal:** (1) rank the three English-only checkpoints WhisperKit can run (`tiny.en`, `base.en`, `small.en`; WhisperKit exposes `QUALCOMM_TINY_EN`, `QUALCOMM_BASE_EN`, `QUALCOMM_SMALL_EN`) on the same clips; (2) test whether the kit's quiet microphone (measured by A on 2026-10-10: room floor about -45 dBFS, speech about 10 dB above it) hurts accuracy, and whether peak-normalising each utterance helps.

## Data and setup
- **Clips:** the 80-clip public proxy set from `B-20261011-accent-contest-public-proxy.md` (`WillHeld/india_accent_cv`, Common Voice India-and-South-Asia accent, read speech, 19 speakers, 829 reference words, seed 20261011). **Not our recordings, not the kit mic.**
- **Models:** local copies of `openai/whisper-{tiny,base,small}.en`, `transformers` ASR pipeline, default decoding. `whisper-small.en` was added to the cache for this run (public, ungated).
- **Commands:** `tools/accent_contest/contest.py --model openai/whisper-tiny.en --model openai/whisper-base.en --model openai/whisper-small.en ...` (the same tool as before), and `tools/accent_contest/level_probe.py` (new, in this commit).
- **Mic simulation (`level_probe.py`):** *quiet* = the clip scaled so its loud 20 ms frames (90th percentile) sit at -32 dBFS RMS, level only; *quiet+pink* = quiet plus pink (1/f) noise at -45 dBFS RMS (about 13 dB under the speech); *quiet+pink+norm* = that, peak-normalised to -3 dBFS, gain capped at 30 dB. All re-quantised to 16-bit.
- **A first version used white noise** and gave much worse numbers (`base.en` 12.3% to 38.6%, `tiny.en` 16.65% to 151%, mostly runaway text) and mixed up noise and level. It is superseded; I rewrote the script to separate level from noise and use pink noise, and report only the second run. The first run's numbers are not used anywhere else.

## Results (WER = word errors / reference words; same 80 clips and 829 words everywhere)
**1. English checkpoints, clean proxy clips** (`wer_en_only.md`)
| Model | WER | Errors / words |
|---|---:|---:|
| `whisper-small.en` | 9.65% | 80/829 |
| `whisper-base.en` | 12.30% | 102/829 |
| `whisper-tiny.en` | 16.65% | 138/829 |

**2. Mic level and noise floor** (`level_probe2.json`)
| Model | original | quiet (level only) | quiet + pink floor | + peak-normalised |
|---|---:|---:|---:|---:|
| `tiny.en` | 16.65% (138) | 18.09% (150) | 40.89% (339) | 39.81% (330) |
| `base.en` | 12.30% (102) | 12.55% (104) | 26.66% (221) | 24.61% (204) |
| `small.en` | 9.65% (80) | 9.53% (79) | 19.06% (158) | 18.94% (157) |

**3. Uncertainty** (resampling the 19 speakers with replacement, 10,000 times, seed 1; 95% percentile intervals; differences in WER points)
| Comparison | Difference | 95% interval |
|---|---:|---:|
| `tiny.en` minus `base.en`, clean | +4.34 | +0.50 to +8.66 |
| `base.en` minus `small.en`, clean | +2.65 | -0.56 to +5.96 |
| `base.en` minus `small.en`, noise floor | +7.60 | +3.71 to +11.74 |
| `tiny.en` minus `small.en`, noise floor | +21.83 | +14.35 to +30.21 |
| noise floor effect on `small.en` | +9.41 | +5.59 to +13.52 |
| noise floor effect on `base.en` | +14.35 | +8.12 to +20.75 |
| level-only effect on `base.en` | +0.24 | -0.96 to +1.57 |
| normalising, `base.en` (WER before minus after) | +2.05 | +0.61 to +3.56 |
| normalising, `small.en` (before minus after) | +0.12 | -0.38 to +0.69 |

No model produced an empty output or a runaway (more than twice the reference length) on any variant; the noise floor changes words (substitutions, deletions), for example "mode of an issue for his first year" for "another notable Egyptian squash player" on one clip.

## What this supports, and what it does not
- **Supported:** bigger English-only Whisper is better on this set (`small.en` 9.65% against `tiny.en` 16.65%), and the gap **widens with noise** (21.8 points between `tiny.en` and `small.en` with the floor). Level alone does not matter (`base.en` +0.24, interval spans zero). A noise floor about 13 dB under the speech costs 9 to 14 points even for `small.en` and `base.en`. Peak-normalising helps `base.en` by about 2 points and `small.en` by nothing measurable.
- **Not supported:** that `small.en` beats `base.en` on clean speech (interval spans zero). That these numbers describe the kit: the noise is synthetic pink noise, the clips are read Wikipedia sentences from 19 speakers, the models are fp32 PyTorch and not the QNN builds WhisperKit runs (quantisation and the NPU can change WER), and nothing here knows the kit mic's frequency response or any automatic gain control. The speech-to-noise ratio of 13 dB is an estimate from A's one recording.
- The fine-tuned `theshivam7` checkpoints are not usable on WhisperKit without an export step nobody has tested, so they are outside this decision.

## Consequences for the design (recommendations, not decisions)
1. **Try `QUALCOMM_SMALL_EN` on the kit first**, then `QUALCOMM_BASE_EN`; keep tiny only as a speed fallback. Qualcomm lists Whisper-Small float at about 66 ms encoder per 30 s window and 8.4 ms per decoder token on 8 Elite (their numbers, not measured here), so latency should allow it; the kit run decides.
2. **Signal-to-noise is the lever, not volume.** A close-talking or headset mic (the kit's 3.5 mm jack takes precedence when a headset is plugged in) should help far more than gain. Worth one A/B on the kit: built-in mic against a headset, same sentences.
3. **Do not add peak-normalisation yet:** at best two points for `base.en`, nothing for `small.en`. Revisit only if kit recordings show a level problem.
4. Detector thresholds (see `B-20261010-speech-core-tdd.md`) stay as starting values until E's kit clips exist.

## Next step
Run the same commands on E's kit and phone recordings (separate rows), then on the kit through `SpeechEngineContract` for the real NPU numbers.

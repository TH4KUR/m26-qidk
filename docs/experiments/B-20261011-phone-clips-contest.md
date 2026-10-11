# Accent contest on E's phone recordings, round 1 (English, phone only)

Run 2026-10-11 on B's machine; no cloud inference. Manifests: E's `e/data-eval` `243f010` (carries `split`). Command and tool: `tools/accent_contest/` at `b/speech-engines` `cede80c`. Audio and raw transcripts stay outside the repo (the own-words clips have not been checked for real-name slips, E/010 caveat 5).

## Scored table: phone, dev split, scripted read-aloud clips only
Selection: `device=phone`, `split=dev`, `mode=scripted` (the `_01` to `_04` clips with an exact reference). **10 clips, 298 reference words.** Stock Whisper checkpoints and the two Indian-English fine-tunes run through the same `transformers` pipeline, case-folded and punctuation-stripped WER.

| Rank | Model | WER | Errors / reference words |
|---:|---|---:|---:|
| 1 | openai/whisper-base.en | 8.72% | 26/298 |
| 2 | theshivam7/whisper-small-aesrc-indian-english | 10.74% | 32/298 |
| 3 | openai/whisper-tiny.en | 11.41% | 34/298 |
| 4 | theshivam7/whisper-tiny-aesrc-indian-english | 14.09% | 42/298 |

**Reading it:** the spread is 16 word errors on 298 words from 2 speakers and 10 clips; this is a small-sample ordering, not a statistically separable ranking, and not the kit's model (Whisper-Small on the NPU is a different build and engine). The fine-tuned Indian-English checkpoints did not beat stock `base.en` on these clips. Speakers are two people (E/010), so it is not a speaker-diverse result. Each sheet is one voice; calm and stressed pairs are within one speaker.

## Raw transcripts for all 34 clips
`--transcripts-only` (added at `cede80c` because 20 own-words clips have no typed reference yet): 136 rows (34 clips by 4 models), no empty transcripts, E's wire format `{clip_id, model, text}`. **No score is computed for clips without a reference.** The 8 held-out clips (S2_04, S2_05, S3_06, S4_03, S4_04, S4_06, S5_03, S5_08) were transcribed only, never scored here and never used for any choice. The `_06` fire-alarm clips are noisy; any table made from the own-words clips needs its own row for them (E/010 caveat 1).

## Not done
No kit-microphone table (separate device class), no Hindi/Telugu (see `tools/fleurs_subset/`), no scoring of own-words clips until E's references are checked.

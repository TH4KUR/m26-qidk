# Local ASR inference smoke test — synthetic only

- Date/time: 2026-10-11, about 01:04 IST
- Owner: B
- Code: `b1a4233` (`tools/accent_contest/contest.py`; branch subsequently fast-forwarded to `c7e2f1e`)
- Goal: verify the local manifest loader, all four checkpoint loads, transcription path, and WER/prediction outputs before E's recordings arrive.

## Setup and data

- Runtime: development laptop CPU; PyTorch `2.14.1+cpu`, Transformers `5.19.0`, SoundFile `0.14.0`. No GPU or kit was used.
- Audio: two generated `en-in` eSpeak clips, resampled to 16 kHz mono PCM16; 28 normalized reference words total. These are synthetic smoke-test fixtures in `/home/karma/.cache/megathon-asr-dummy/`, not human recordings and not an accent sample.
- Checkpoints: `openai/whisper-tiny.en`, `openai/whisper-base.en`, `theshivam7/whisper-tiny-aesrc-indian-english`, `theshivam7/whisper-small-aesrc-indian-english`.
- Command:

```sh
python tools/accent_contest/contest.py \
  --manifest /home/karma/.cache/megathon-asr-dummy/clips.jsonl \
  --models-dir /home/karma/.cache/megathon-asr-models \
  --dataset-label 'Synthetic smoke test — not an accuracy estimate' \
  --output /home/karma/.cache/megathon-asr-dummy/wer.md \
  --predictions /home/karma/.cache/megathon-asr-dummy/predictions.jsonl
```

## Results

| Rank | Model | WER | Errors / reference words | Clips |
|---:|---|---:|---:|---:|
| 1 | `theshivam7/whisper-small-aesrc-indian-english` | 10.71% | 3/28 | 2 |
| 2 | `openai/whisper-base.en` | 14.29% | 4/28 | 2 |
| 3 | `openai/whisper-tiny.en` | 32.14% | 9/28 | 2 |
| 4 | `theshivam7/whisper-tiny-aesrc-indian-english` | 50.00% | 14/28 | 2 |

The contest unit suite passed: 7 tests, 0 failures. The run emitted Transformers warnings about experimental `chunk_length_s` behavior and deprecated Whisper decoder-config arguments; inference completed for all 8 clip/model pairs.

## Verdict and next step

The loader, local checkpoints, transcription, and scoring path execute end to end. **These numbers are not evidence of accent accuracy, checkpoint ranking, or generalization**: the data consists of two generated clips with 28 reference words and no human speakers. Keep results and predictions outside the repository. Wait for E's labeled phone/kit recordings; then score the real device subsets separately and emit the requested raw transcript rows as `{clip_id, model, text}`. Kit testing also waits for A's confirmed slot.
